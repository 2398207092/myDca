# 后端代码优化方案

> 审计日期：2026-08-07
> 审计范围：`myPhonePro/fund-tracker-backend` 全部 8 个维度
> 发现问题：42 个（🔴17 高 / 🟡16 中 / 🟢9 低）

---

## 完成状态汇总

| 期次 | 问题范围 | 状态 | 完成时间 | 部署 commit |
|------|----------|------|----------|-------------|
| 第一期 | #1 #2 #3 #4 #5 #6 + #23（顺带） + 单元测试 | ✅ 已完成并部署 | 2026-08-07 | `1704843` |
| 第二期 | #7 #8 #9 #10 #11 #12 | ✅ 已完成（待推送部署） | 2026-08-08 | — |
| 第三期 | #13 #14 #15 #16 #17 | ⏳ 待启动 | — | — |
| 第四期 | #18 #19 #20 #21 #22 | ⏳ 待启动 | — | — |

> 第一期验证：本地快速回归清单 7 项全部通过（首页加载/持仓详情市值净值/交易更新/删除级联/发现页总资产/5年预测动态年份/后端日志无异常）。第三、第四期内容已被架构优化 3→1→2→4 替代（2026-08-08），详见 commit 10a9190。

---

## 第一期：安全与数据正确性（🔴最高优先级）✅ 已完成

### #1 大量接口缺少 userId 校验（跨用户越权） ✅ 已修复

- **问题类别**：认证/授权缺失（用户数据隔离）
- **严重程度**：🔴高
- **文件**：HoldingService、TransactionService、EventService、DcaPlanService 等 20 处
- **大白话**：很多接口在查数据时不检查"这个数据是不是你的"。比如删除持仓时只按持仓 ID 查，不检查是不是属于当前登录用户。如果有人知道别人的持仓 ID，就能直接修改、删除别人的数据。就像银行转账时不检查你是哪个账户的主人，谁都能操作你的钱。
- **越权点清单**：

| 文件 | 行号 | 方法 | 越权操作 |
|------|------|------|----------|
| HoldingService.java | 247 | updateHolding | findByIdAndDeletedFalse(id) — 任何用户可编辑任意持仓 |
| HoldingService.java | 582 | refreshSingleHolding | findByIdAndDeletedFalse(holdingId) — 无 userId |
| TransactionService.java | 52 | createTransaction | findByIdAndDeletedFalse(holdingId) — 可为他人持仓创建交易 |
| TransactionService.java | 153 | updateTransaction | findById(id) — 可修改任意交易 |
| TransactionService.java | 222 | deleteTransaction | findById(id) — 可删除任意交易 |
| TransactionService.java | 239 | deleteTransaction | findByIdAndDeletedFalse(holdingId) — 无 userId |
| EventService.java | 83 | createEvent | findByIdAndDeletedFalse(holdingId) — 可为他人创建事件 |
| EventService.java | 104 | markDistributed | findById(id) — 可标记任意事件为已到账 |
| EventService.java | 168 | cancelEvent | findById(id) — 可取消任意事件 |
| EventService.java | 187 | convertToReinvest | findById(id) — 可操作任意事件复投 |
| EventService.java | 113,201 | markDistributed/convertToReinvest | findByIdAndDeletedFalse — 无 userId |
| ForecastService.java | 23 | getForecast | findByIdAndDeletedFalse(holdingId) — 可查看任意持仓预测 |
| DcaPlanService.java | 170 | createPlan | findByIdAndDeletedFalse(holdingId) — 无 userId |
| DcaPlanService.java | 224 | getPlan | findById(id) — 可查看任意定投计划 |
| DcaPlanService.java | 232 | updatePlan | findById(id) — 可修改任意定投计划 |
| DcaPlanService.java | 276 | deletePlan | findById(id) — 可删除任意定投计划 |
| DcaPlanService.java | 288 | executePlan | findById(id) — 可执行任意定投计划 |
| DividendEventSyncService.java | 139 | syncEventsForHolding | findByIdAndDeletedFalse(holdingId) — 无 userId |
| SnapshotListService.java | 28-33 | listSnapshots | findAll(pageable) — 返回所有用户的快照 |
| HoldingController.java | 50-54 | updateHolding | Controller 层未传递 userId 到 Service |
| HoldingController.java | 65-70 | getForecast | Controller 层未传递 userId 到 Service |

- **修复方案**：
  - 架构层：所有 Repository 的 `findById` 增加 `findByIdAndUserId` 变体；Controller 强制传递 userId；Service 层禁止使用不带 userId 的查询方法
  - 自动化层：编写 CI ArchUnit 测试，扫描所有 `@RestController` 方法是否将 `request.getAttribute("userId")` 传递给 Service
  - 运行时层：增加集成测试，用用户 A 的 Token 访问用户 B 的资源，应返回 403/404

---

### #2 事务中调外部 HTTP（连接池耗尽） ✅ 已修复

- **问题类别**：事务完整性 + 性能（长事务持锁）
- **严重程度**：🔴高
- **文件**：HoldingService.java:58-80（@Transactional + refreshMarketValue → fundNavScrapeService.incrementalUpdate）
- **大白话**：你点开持仓详情时，后端会先去天天基金抓最新净值，抓完再更新数据库。但这个过程包在一个"数据库事务"里——抓净值那几秒钟，数据库连接一直被占着不释放。服务器只有 5 个数据库连接，如果同时有 5 个人打开持仓详情，连接就全被占满了，整个后端直接卡死。createHolding（line 124）也有同样问题。
- **修复方案**：将净值抓取移出事务边界——先抓取净值（无事务），再在短事务中更新 DB。或将 `@Transactional` 改为 `@Transactional(propagation = REQUIRES_NEW)` 仅包裹 DB 写操作。

---

### #3 复投失败分红凭空消失 ✅ 已修复

- **问题类别**：事务完整性（部分失败、数据不一致）
- **严重程度**：🔴高
- **文件**：EventService.java:119-154（markDistributed 复投分支）
- **大白话**：标记分红"已到账"时，如果设置了分红复投，系统先把分红加到"已收分红"里，然后尝试买入更多份额。但如果买入失败了，系统只记个日志就过去了——结果是：分红已标记"已到账"、已收分红也加了，但你既没拿到现金、也没拿到份额，这笔分红消失了。
- **修复方案**：复投失败时回滚整个操作（抛出异常触发 @Transactional 回滚），或将事件状态改回 pending。当前 `catch (Exception e) { log.error(...) }` 必须移除或改为抛出运行时异常。

---

### #4 删除持仓未清理 DCA 计划和快照 ✅ 已修复

- **问题类别**：级联操作完整性（孤儿数据）
- **严重程度**：🔴高
- **文件**：HoldingService.java:297-309（deleteHolding）
- **大白话**：删除持仓时只删了交易记录和分红事件，不删定投计划和历史快照。定投定时任务还会尝试给已删除的持仓执行买入（然后报错）；历史快照残留在数据库里污染资产走势图。此外删除持仓时未回退初始买入交易扣减的现金。就像拆了一栋楼，但住户登记表和水电费记录还留着。
- **修复方案**：`deleteHolding` 中增加 `dcaPlanRepository.deleteByHoldingId(id)` 和 `holdingSnapshotRepository.deleteByHoldingId(id)`；评估是否需要回退现金。

---

### #5 异常信息泄露 ✅ 已修复

- **问题类别**：安全问题（信息泄露）
- **严重程度**：🔴高
- **文件**：GlobalExceptionHandler.java:42-48
- **大白话**：后端出错时（比如数据库异常），系统把完整的错误信息（包含表名、字段名、SQL 语句）直接返回给前端。相当于把家里的户型图和门锁型号给了陌生人。
- **修复方案**：对客户端返回通用消息"服务器内部错误，请稍后重试"；使用 `log.error` 记录完整异常堆栈到日志。

---

### #6 ForecastService 全是硬编码假数据 ✅ 已修复

- **问题类别**：数据一致性（口径不统一 + 硬编码）
- **严重程度**：🔴高
- **文件**：ForecastService.java:30-49
- **大白话**：分红预测页面 5 年走势的年份写死了（2025-2029），增长率也是编的（每年涨 12%、25%、40%、55%）。不基于任何实际数据，纯属虚构。到了 2027 年打开还是从 2025 开始算。你看到的"预测"其实是假的。
- **修复方案**：年份基于 `LocalDate.now().getYear()` 动态生成；增长率基于历史分红数据的 CAGR 或线性回归推算；`trendPercentage` 从实际数据计算。如暂无法实现真实预测，应明确标注为"示例数据"。

---

## 第二期：外部依赖与一致性

### #7 FundNavScrapeService 用 HTTP 而非 HTTPS ✅ 已修复

- **问题类别**：外部依赖脆弱性（安全传输）
- **严重程度**：🔴高
- **文件**：FundNavScrapeService.java:41 和 164
- **大白话**：抓取基金净值时用的是明文 HTTP，不是加密的 HTTPS。明文传输可以被中间人篡改——你抓到的净值可能是被人改过的。同一个项目的分红数据接口已经用了 HTTPS，净值接口还在用 HTTP，两个口径不一致。
- **修复方案**：统一改为 `https://fund.eastmoney.com/pingzhongdata/%s.js`，提取为共享常量。

---

### #8 无重试机制，失败直接静默降级 ✅ 已修复

- **问题类别**：外部依赖脆弱性（无重试 + 静默降级）
- **严重程度**：🔴高
- **文件**：FundNavScrapeService.java:127-130、FundDividendScrapeService.java:92-95、DividendInfoService.java:406-409
- **大白话**：天天基金接口偶尔会抽风（返回 503 或超时），程序一失败就直接返回"无数据"。比如某天净值接口卡了一下，所有持仓的市值就显示为 0 或旧值，第二天看到总资产突然缩水，但实际上只是没抓到数据。没有重试机制，一次失败就放弃。
- **修复方案**：引入 Spring Retry（`@Retryable`）对外部 HTTP 调用进行 2-3 次重试，间隔递增。重试耗尽后返回缓存值而非 null，并在日志中标记数据为"可能过期"。

---

### #9 DividendInfoService 与 FundDividendScrapeService 重复解析同一数据源 ✅ 已修复

- **问题类别**：数据一致性（同源异算）
- **严重程度**：🔴高
- **文件**：DividendInfoService.java:58-113 vs FundDividendScrapeService.java:144-204
- **大白话**：添加基金预览时和点"更新"按钮时，走的是两条不同的代码路径去查同一只基金的分红数据。两条路的解析逻辑不同，算出来的"年均每份分红"可能不一样。同一个基金，在不同入口看到不同的分红预测。
- **修复方案**：统一分红数据源和计算口径为单一 Service，删除冗余路径。`DividendInfoService` 已有降级到 `fundDividendScrapeService.fetchAndCalculate` 的逻辑，应彻底统一为仅使用数据库 + fhsp 页面口径。

---

### #10 MIN_VALID_EX_DATE 硬编码 2022 ✅ 已修复

- **问题类别**：硬编码风险
- **严重程度**：🟡中
- **文件**：FundDividendScrapeService.java:38
- **大白话**：为了过滤"基金代码被复用导致的脏分红数据"，代码里写死了一个日期 2022-01-01，这之前的分红记录全部丢弃。但 2022 年之前成立的正常老基金（比如 002528 泰康安益纯债）的合法分红记录也会被误删。
- **修复方案**：改为从基金详情接口获取 `establishDate`（基金成立日期），**按每个基金的实际成立日期过滤**。
  - 数据源：天天基金 `FundMNBasicInformation` 接口的 `ESTABDATE` 字段（如 016452 → `2022-11-29`）
  - `getEstablishDate(fundCode)`：带内存缓存（成立日期是静态属性）+ HTTP 重试
  - `parseDividendTable` 过滤逻辑：`exDate < 成立日期` → 丢弃（成立日期前的分红属于代码复用前的旧基金）
  - 兜底：仅当成立日期接口获取失败时使用保守常量 `FALLBACK_MIN_EX_DATE = 2020-01-01`（非配置项）

---

### #11 定时任务执行顺序不合理 ✅ 已修复

- **问题类别**：数据一致性（定时任务顺序）
- **严重程度**：🟡中
- **文件**：FundDividendScheduler.java:33（6:00）、FundNavScheduler.java:20（22:00）、HoldingSnapshotScheduler.java:20（1:00）、DataAuditor.java:37（3:00）
- **大白话**：凌晨 1:00 先拍快照 → 3:00 审计 → 6:00 才刷新分红数据 → 22:00 才刷新净值。快照和审计跑在刷新之前，拍的是昨天的旧数据。就像每天早上先拍照存档，然后才洗脸换衣服。
- **修复方案**：调整顺序：6:00 分红刷新 → 22:00 净值刷新 → 次日 1:00 快照 → 3:00 审计。或合并 6:00 和 22:00 为一次全量刷新。

---

### #12 weeklyChange/monthlyChange 口径错误 ✅ 已修复

- **问题类别**：数据一致性（口径错误）
- **严重程度**：🔴高
- **文件**：AssetOverviewService.java:65-84
- **大白话**：发现页显示的"周变化"实际对比的是"最近一次快照"（可能是今天的），"月变化"实际对比的是 7 天前的快照（不是 30 天）。变量名叫周变化/月变化，但实际算的都不是。
- **修复方案**：`weeklyChange` 应对比 7 天前快照；`monthlyChange` 应对比 30 天前快照。修正 `minusDays(7)` 为 `minusDays(30)`。

---

## 第三期：性能优化

### #13 DividendEventSyncService N+1 查询（3500 次 DB 操作）

- **问题类别**：性能问题（N+1 查询）
- **严重程度**：🔴高
- **文件**：DividendEventSyncService.java:71-109（syncEventsForFund）
- **大白话**：同步分红事件时，对每个持仓的每条分红记录要查 7 次数据库。如果一只基金有 50 条分红记录、你有 10 个持仓，就是 3500 次数据库查询。在 2 核 2G 的小服务器上，这个操作会让数据库忙好几秒。
- **修复方案**：批量查询该持仓已有的所有事件（1 次查询），在内存中比对后批量删除/插入。或使用 `@Query` 的批量 DELETE + 批量 INSERT。

---

### #14 InsightService 12 次独立查询

- **问题类别**：性能问题（N+1 查询）
- **严重程度**：🔴高
- **文件**：InsightService.java:150-170（getAnnualInsight）
- **大白话**：月度洞察页面要展示 12 个月的分红数据，代码里循环 12 次，每月单独查一次数据库。其实一条 `GROUP BY 月份` 的 SQL 就能搞定，但现在要查 12 次（外加全年数据 1 次，共 13 次）。
- **修复方案**：在 DividendEventRepository 增加 `@Query("SELECT MONTH(e.date) ... GROUP BY MONTH(e.date)")` 聚合查询，一次获取 12 个月数据。

---

### #15 DataAuditor 60 次 N+1 查询

- **问题类别**：性能问题（N+1 查询）
- **严重程度**：🔴高
- **文件**：DataAuditor.java:118-119（auditCashFlow）、154（auditHoldingShares）、217（auditCostConsistency）
- **大白话**：数据审计任务要检查每个持仓的交易记录。代码先查所有持仓，再循环每个持仓单独查交易。20 个持仓 = 20 次查询，3 个审计规则 = 60 次查询。可以一次性查全部交易在内存里分组。
- **修复方案**：一次性查询该用户所有交易（`findByUserIdOrderByDateDesc(userId)`），在内存中按 `holdingId` 分组。

---

### #16 AssetOverviewService 重复查询同分类

- **问题类别**：性能问题（重复查询）
- **严重程度**：🟡中
- **文件**：AssetOverviewService.java:52-54 + 216
- **大白话**：发现页计算总资产时，先按"美股/黄金/红利"分别查一次数据库拿到市值合计，然后构建分类详情时，对同样的三个分类又查了一次。6 次查询拿到的是完全相同的数据。
- **修复方案**：`getOverview` 中一次性查询用户所有持仓，在内存中按 `assetCategory` 分组。

---

### #17 ddl-auto: update 生产不安全

- **问题类别**：安全问题（生产配置风险）
- **严重程度**：🟡中
- **文件**：application.yml:17
- **大白话**：生产环境用的是 `ddl-auto: update`，意思是 Hibernate 启动时会自动改数据库表结构（加列、加索引）。这在生产环境很危险——可能锁表导致服务不可用，且无法追踪谁改了什么。
- **修复方案**：生产环境设为 `validate` 或 `none`，引入 Flyway/Liquibase 管理 schema 迁移。通过 `spring.profiles.active` 区分开发（update）和生产（validate）。

---

## 第四期：代码质量

### #18 三种 HTTP 客户端混用

- **问题类别**：外部依赖脆弱性（HTTP 客户端不一致）
- **严重程度**：🟡中
- **文件**：FundSearchService.java:33-55（HttpURLConnection）、ExchangeRateService（java.net.http.HttpClient）、FundDividendScrapeService（Jsoup.connect）
- **大白话**：项目里有三种不同的 HTTP 工具：HttpURLConnection（古董 API）、HttpClient（Java 11+）、Jsoup（HTML 解析库）。三种工具的超时、重试、连接管理各不相同。就像一个团队三个人各用各的工具箱，互不通用。
- **修复方案**：统一使用 `HttpClient` 或 RestTemplate，集中配置超时、重试、连接池。

---

### #19 无 Actuator 监控

- **问题类别**：日志/可观测性缺失
- **严重程度**：🟢低
- **文件**：pom.xml（缺失依赖）
- **大白话**：没有安装 Spring Boot Actuator，无法检查服务是否健康。在 2 核 2G 服务器上，数据库连接池满了、JVM 内存快爆了都看不到，只能等用户反馈"网站打不开了"才发现。
- **修复方案**：添加 `spring-boot-starter-actuator`，暴露 `health` 和 `metrics` 端点（仅限内网访问）。

---

### #20 核心解析逻辑无测试

- **问题类别**：测试覆盖缺失
- **严重程度**：🟢低
- **文件**：无 FundNavScrapeServiceTest、无 DividendInfoServiceTest、无 ForecastServiceTest
- **大白话**：净值解析和分红数据解析是最容易出错的地方（外部格式说变就变），但没有任何测试。之前 021550 分红数据显示不出来，就是因为解析格式变了但没人测出来。已有测试：CostCalculator（良好）、FundDividendScrapeService（良好）。
- **修复方案**：优先为 `FundNavScrapeService`（净值解析）、`DividendInfoService`（unitMoney 解析）、`ForecastService`（硬编码检测）添加测试。用 JS 字符串片段作为测试输入，验证正则解析的容错性。

---

### #21 System.out.println

- **问题类别**：日志/可观测性缺失
- **严重程度**：🟢低
- **文件**：DataInitializer.java:35
- **大白话**：用了 `System.out.println` 而不是标准的日志工具。虽然内容已脱敏，但 `System.out` 不受日志配置控制（无法按级别过滤、无法写入文件）。
- **修复方案**：改为 `log.info("已初始化默认 AuthToken (length={})", configuredToken.length())`。

---

### #22 代码重复

- **问题类别**：代码重复
- **严重程度**：🟡中
- **文件**：FundNavScrapeService.java:39-131 和 161-237（fetchAndSaveNavRecords 和 incrementalUpdate 80 行重复）、DividendInfoService.java:414-417 和 FundDividendScrapeService.java:292-295（isFund 方法重复）、AnnualizedReturnService.java:84-93 等（DTO 构建重复 5 次）
- **大白话**：净值抓取的两个方法有 80 行几乎一模一样的代码；判断"是不是基金"的方法在两个 Service 里各写了一遍；年化收益率的 DTO 构建重复了 5 次。这些重复代码改一处忘另一处，就是 bug 反复出现的原因之一。
- **修复方案**：
  - 提取 `parseAndSaveNavRecords(String fundCode, LocalDate maxDateInDb)` 公共方法
  - 提取 `PingZhongDataClient` 公共组件，统一 HTTP 客户端、超时、重试策略
  - 提取 `HoldingTypeUtils.isFund(type)` 工具方法
  - 提取 `buildResult(...)` DTO 构建方法

---

## 补充问题清单

### 以下问题也需关注但优先级稍低

| 序号 | 问题 | 严重程度 | 文件 | 大白话 |
|------|------|----------|------|--------|
| #23 | EventService 无净值时用 1 元兜底 ✅ 已修复（随 #3 一并处理） | 🟢低 | EventService.java:136-139, 222-225 | 分红复投时如果没有净值数据，系统用 1 元当净值，导致份额金额完全失真 |
| #24 | createHolding 多处 catch 仅记日志 | 🟡中 | HoldingService.java:174-178 等 | 创建持仓时如果初始交易创建失败或现金扣减失败，持仓已创建但没有交易记录 |
| #25 | TransactionEventListener 异常仅记日志 | 🟡中 | TransactionEventListener.java:20-27 | 交易后分红事件同步失败不重试，用户创建交易后分红事件可能未同步 |
| #26 | DcaPlanService.calculateBudget 硬编码 china 市场 | 🟡中 | DcaPlanService.java:67 | 美股定投用 A 股日历算交易日数，预算估算错误 |
| #27 | CorsConfig 硬编码生产域名 | 🔴高 | CorsConfig.java:21 | 生产域名 retrospectpect.top 硬编码为默认值 |
| #28 | 未配置 open-in-view | 🟡中 | application.yml | Hibernate Session 在整个 HTTP 请求期间保持打开，可能引发 N+1 和长会话锁 |
| #29 | DividendAutoDistributeScheduler 跨用户查询事件 | 🔴高 | DividendAutoDistributeScheduler.java:37-38 | 加载所有用户的 pending 事件到内存，日志打印了金额等敏感信息 |
| #30 | ExchangeRateService 硬编码兜底汇率 | 🟡中 | ExchangeRateService.java:169-189 | API 不可达时用硬编码汇率 7.2341，用户不知情地使用了过期汇率 |
| #31 | CostCalculator 回本年限魔法数字 999 | 🟡中 | CostCalculator.java:141, 162 | 预测年分红为 0 时返回 999，前端需要硬编码判断 |
| #32 | DashboardService "连续收息天数"命名误导 | 🟢低 | DashboardService.java:74-82 | 实际计算的是持有天数，不是连续收息天数 |
| #33 | FundDividendScrapeService 分页硬编码上限 10 页 | 🟢低 | FundDividendScrapeService.java:52 | 部分老基金可能有超过 10 页分红记录会被截断 |
| #34 | DashboardService.todayDividendReceived 在 Java 中过滤 | 🟢低 | DashboardService.java:89-94 | 查询当天所有事件后在 Java 中过滤 status，应在 DB 查询中直接过滤 |
| #35 | HoldingRepository 存在无 userId 的查询方法 | 🟢低 | HoldingRepository.java:11-17 | 可被未来新增的 Service 误用，绕过数据隔离 |
| #36 | FundDividendScrapeService.parseBigDecimal 正则容错不足 | 🟢低 | FundDividendScrapeService.java:445-470 | 依赖特定文本格式"派现金"，东财改格式就会解析失败 |
| #37 | DcaPlanService.executePlan 未校验净值数据新鲜度 | 🔴高 | DcaPlanService.java:302-305 | 数据库中净值是 3 个月前的，定投仍会按过期净值计算份额 |
| #38 | AssetOverviewService 变量命名与逻辑不匹配 | 🔴高 | AssetOverviewService.java:65-84 | weeklyChange 实际是"相比最近快照"，monthlyChange 实际是"相比 7 天前" |
| #39 | FundNavScrapeService 和 incrementalUpdate 逻辑重复 | 🟡中 | FundNavScrapeService.java:39-131 和 161-237 | 80 行重复代码 |
| #40 | DividendInfoService 和 FundNavScrapeService 都解析 pingzhongdata | 🟡中 | DividendInfoService.java:367-410 和 FundNavScrapeService.java:41-47 | 两个 Service 各自实现相同逻辑，用不同 HTTP 客户端 |
| #41 | AnnualizedReturnService 重复构建 DTO | 🟡中 | AnnualizedReturnService.java:84-93 等 | 相同 DTO 构建模式重复 5 次 |
| #42 | isFund 方法在两个 Service 中重复定义 | 🟡中 | DividendInfoService.java:414-417 和 FundDividendScrapeService.java:292-295 | 完全相同的实现 |

---

## 实施顺序建议

1. **第一期**（安全与数据正确性）：#1 → #2 → #3 → #4 → #5 → #6  ✅ 已完成并部署（2026-08-07，commit `1704843`）
2. **第二期**（外部依赖与一致性）：#7 → #8 → #9 → #10 → #11 → #12
3. **第三期**（性能优化）：#13 → #14 → #15 → #16 → #17
4. **第四期**（代码质量）：#18 → #19 → #20 → #21 → #22

每期完成后编译验证 + 推送 GitHub + 部署服务器。

---

## 局限性标注

- 本方案覆盖：代码层面的安全、性能、一致性、错误处理、外部依赖、代码重复
- **不覆盖**：网络层安全（DDoS、WAF 配置）、数据库配置优化（索引使用效率、查询计划）、JVM 调优（GC 策略、内存分配）
- 建议后续补充运行时 APM 监控和渗透测试
