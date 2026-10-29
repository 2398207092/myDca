# 多 AI 协作挖漏洞方案（临时文档 · 不进 docs · 暂不入库）

> 目的：多个 AI 并行挖漏洞 → 交叉验证 → 串行修复，避免互相打架、避免幻觉 bug。
> 创建：2026-10-29 ｜ 状态：待用户确认后启动

---

## 0. 一句话原则

**先把「钱算错、权限破」的可能路径挖干净，再动手修。挖的阶段全部只读；修的阶段一次只一个人改一个域。**

---

## 1. 范围

| 在范围 | 不在范围 | 绝对禁区 |
|---|---|---|
| 后端 6 条钱路径（Holding / Transaction / DcaPlan / Snapshot / Dividend / CostCalculator）<br>账户体系与归属约束<br>API 鉴权、参数校验、幂等<br>前端金额显示口径<br>数据库精度/约束/数据一致性 | 视觉美化、新功能<br>性能优化（刷新价格已完成）<br>架构重构 | **生产环境**：只允许 SELECT 查数据，禁止任何写操作、禁止部署<br>任何「凭感觉」的伪 bug |

---

## 2. 四条挖掘线（交叉覆盖，不是四选一）

### 线 1 · 账本不变量（最有效，优先做）

直接用 SQL / 接口对比验证下列不变量，**任一违反即为 bug**：

| 编号 | 不变量 | 验证方式 |
|---|---|---|
| INV-1 | 总资产 = Σ分类（cash+crypto+dividend+us_stock+gold） | `/api/asset-overview` 交叉验证 |
| INV-2 | 每笔 buy：份额增量 = quantity，且账户现金减少 = total | 交易前后对比 |
| INV-3 | 非迁入持仓：cost = Σ买入 total − 卖出成本；costPerShare = cost/shares | SQL 聚合对比 |
| INV-4 | 迁入持仓（costEstimated=1）：成本不被任何代码路径重算覆盖 | 全路径审查 + 模拟夜间任务 |
| INV-5 | 资产历史曲线点 = 同日快照值，任何分类不得双计 | total-series vs asset_snapshots |
| INV-6 | 定投累计统计 = Σ其交易；nextExecutionDate 单调递增 | SQL 对比 |
| INV-7 | 无游离资产：所有 holdings / manual_assets / dca_plans 的 accountId 非空 | SQL 查 NULL |
| INV-8 | Σ各账户总额 = 总资产（账户视角与全局视角一致） | `/api/accounts` vs `/api/asset-overview` |
| INV-9 | 同日 Σ(holding_snapshots) 与 asset_snapshots 分类值一致（crypto 单一来源） | SQL join |
| INV-10 | 分红：已分配总额 ≤ 已收分红，且无重复入账 | SQL 分组计数 |

### 线 2 · 缺陷类型 × 层矩阵

**已知会「成片出现」的 8 种类型**（每条都要全库扫，不允许只修点名的那一处）：

1. **精度不足** —— 份额/币数用 scale=4 或 2 → 全库扫 entity 注解 + 实际列类型
2. **缺 @Transactional 的写操作** —— 逐 service 甄别（先排除纯查询服务）
3. **迁入持仓成本保护缺失** —— 所有调用 `recalculateHoldingMetrics` 的位置
4. **双计 / 口径分裂** —— 所有「持仓聚合 + 快照叠加」的组合
5. **异常吞噬** —— 钱路径内 `catch` 只 log 的地方，逐个判定是否该吞
6. **前端金额口径** —— 所有 ¥ / USDT / 枚 / 份 出现处，是否与记录层一致
7. **校验缺失** —— 所有 Create/Update 请求体：必填、正数、归属匹配
8. **幂等缺失** —— 所有 POST 写接口：重复提交、重复执行、重复抓取

### 线 3 · 动态验证（边界数据）

本地库造数据：0 份额、负现金、极端汇率（走 7.2 兜底）、并发执行定投、同日重复执行、删除后再执行、跨日边界。
**每条发现都要给：可复现步骤 + 实际输出（不接受"理论上会"）。**

### 线 4 · 契约与权限

- 所有接口：无 token → 401；访问他人资源 → 403 或空；参数越界 → 400
- 后端 DTO ↔ 前端 TypeScript 类型逐字段比对
- 鉴权白名单里的接口是否存在写能力

---

## 3. 分工（6 个 AI 并行，全部只读）

| 角色 | 负责 | 产出要求 |
|---|---|---|
| **A 账本审计** | 线 1 全部 10 条不变量 | 违反清单，每条附 SQL 与输出 |
| **B 钱路径代码** | HoldingService / TransactionService / DcaPlanService / SnapshotGenerationService | 缺陷 + 文件:行号 + 触发路径 |
| **C 接口权限** | Controller 层：鉴权、参数校验、幂等、错误码 | 契约与安全问题 |
| **D 前端口径** | 所有金额显示、状态机、错误处理、缓存 | 显示不一致清单 + 代码位置 |
| **E 数据结构** | 实体精度、索引、约束、僵尸数据、迁移遗留 | 结构与数据问题 |
| **F 协调** | 汇总去重、定级、安排交叉验证 | 候选池 + 分级表 + 修复排期 |

---

## 4. 候选池格式（所有 AI 只往这里追加自己的行）

| ID | 现象 | 证据（SQL 输出 / 文件:行号 / 接口响应 / 日志） | 根因 | 影响量级 | 定级 | 状态 | 提出 | 验证 |
|---|---|---|---|---|---|---|---|---|
| V-001 | BTC 平均成本与执行记录不符 | dca_plans.total_shares=0.0001 但交易记录 0.00008648 | 列精度 18,4 | 每期偏差 14% | P1 | 已修 | A | B |

**定级标准**（沿用 `docs/03-问题.md`）：
- **P0**：钱会算错 / 可越权（匿名改数据、绕过登录）
- **P1**：数据失真或静默失效（显示错但账目对、任务失败无告警）
- **P2**：工程债（不阻塞但会持续制造问题）

---

## 5. 协作流程（6 步）

1. **并行挖**：A–E 各自独立只读，只写候选池自己的行
2. **去重**（F）：按「现象 + 根因」双键；**同现象不同根因算两条**
3. **交叉验证**（关键）：A 挖的由 B 复现，反之亦然；**复现不了直接判「幻觉」丢弃，不进入修复**
4. **定级排序**：P0 → P1 → P2
5. **串行修**：一次只一个 AI 改，按文件域划分避免冲突 —— 钱路径 / 账户体系 / 接口层 / 前端 / 数据修复
6. **每次改动闸门**：130 例测试 + 相关接口冒烟；钱路径改动必须先加测试用例；数据修复必须先备份并附回滚 SQL

---

## 6. 红线（违反即停止）

1. 挖阶段只读；生产只允许 SELECT，**禁止任何写、禁止部署**
2. 修复前先备份（本地库 dump + 代码 commit）
3. 一次只改一个域，不并行改同一文件
4. 不接受「看起来可能」——必须给证据
5. 数据修复必须附回滚脚本
6. 多个 AI 的报告先入候选池，**不允许各自直接改代码**

---

## 7. 已知种子（今天的实战成果，从这里往外扩）

| 种子 | 现状 | 下一步要查什么 |
|---|---|---|
| 精度 18,4 | 已修 shares / transactions.quantity+price / dca_plans.total_shares / holding_snapshots.shares | **`Holding.java:49` 仍是 18,4（待确认字段）**；AssetSnapshot 全 18,2（是否含币数） |
| 迁入持仓成本被重算 | 已修 createTransaction / deleteTransaction / refreshSingleHoldingInternal 三处 | **是否存在第四条调用路径**（如 updateHolding、启动补快照、事件监听） |
| crypto 双计 | 已修 TotalAssetSeriesService | dividend / gold / us_stock 是否有同类「聚合 + 叠加」 |
| 无 @Transactional | 8 个 service 未标（含纯查询） | 逐个甄别是否有写操作漏事务 |
| 账户归属约束 | 持仓/定投/手动资产均已强制 | 旧数据是否仍违反（INV-7）、update 路径是否可绕过 |
| 前端口径 | 部分已修（USDT 后缀、精度、账户卡片） | 全量扫 ¥ / USDT / 枚 / 份 的语境混用 |
| 定投幂等 | 定时任务有 nextExecutionDate 保护，**手动执行无当日防重** | 已登记 P1-6，需补 |

---

## 8. 建议节奏

| 轮次 | 内容 | 预期 |
|---|---|---|
| 第一轮（~2h） | A–E 并行挖，F 汇总 | 20–40 条候选 |
| 第二轮（~1h） | 交叉验证去伪 | 砍掉约 30% 幻觉 |
| 第三轮 | P0/P1 串行修 + 回归 | 逐条闭环 |
| 第四轮 | P2 清理 + 文档收尾（03 增删、04 记一条） | 收口 |

---

## 9. 待你确认的点

1. **AI 数量与角色**：是否按上面 6 个角色开（A–E 挖、F 协调）？要不要合并成 4 个？
2. **交叉验证是否强制**：我建议强制（今天的操作验收代理就产出过假问题：把「HTTP 200 + 业务码 404」误判为缺陷）
3. **范围是否包含前端**：前端口径问题数量可能不少（视觉验收曾列 5 类），是否纳入这一轮？
4. **本文件归属**：临时文件，我默认不入库；要不要加进 `.gitignore` 防止误提交？

---

## 附录 A · 给外部 AI（GLM）的指令模板（复制整段给 GLM 会话）

> 你在一个个人自用的基金/ETF 记账 App 项目里做**漏洞与缺陷挖掘**。
> 项目根目录：`D:/mySpace/资产管理APP_WorkBuddy`
> 技术栈：Spring Boot 3.2（后端 myPhonePro/fund-tracker-backend）+ Vue 3（前端 myPhonePro/stitch_fund_dividend_tracker）+ MySQL 8
>
> **任务**：按 `VULN-HUNT.md` 的四条挖掘线，独立、全量地挖缺陷，不要只挑一个角度。
> **方法**：
> 1. 先读 `VULN-HUNT.md` 的「2. 四条挖掘线」与「7. 已知种子」
> 2. 线 1（账本不变量）：连本地 MySQL（root / 990428 / fund_tracker），逐条跑 INV-1 ~ INV-10，输出实际数值对比
> 3. 线 2（类型矩阵）：8 种缺陷类型全库扫，特别是「精度 18,4」「缺 @Transactional」「迁入持仓成本被重算」这三类曾成片出现
> 4. 线 3（动态验证）：可读代码推理边界，不要在生产库造数据
> 5. 线 4（契约权限）：Controller 鉴权/参数校验/幂等 + DTO 与前端类型一致性
>
> **输出格式（严格按这个，便于汇总）**：
> ```
> 【现象】一句话
> 【证据】SQL 输出 / 文件:行号 / 接口响应 / 日志（必须具体，不接受"可能"）
> 【根因】一句话
> 【影响】量级（如"每期份额偏差 14%"、"总资产虚高 6205 元"）
> 【定级】P0（钱错/越权）/ P1（数据失真/静默失效）/ P2（工程债）
> ```
>
> **红线**：只读代码与数据库；禁止修改任何文件、禁止执行写操作、禁止访问生产服务器。
> 不复现的猜想不要报。

---

## 附录 B · 汇总规则（我方执行）

1. 所有报告统一转成候选池表格行，双键去重（现象 + 根因）
2. **同一个 bug 被 ≥2 个 AI 独立发现 → 直接判真**（置信度高）
3. 只有 1 个 AI 发现 → 由另一方**复现验证**，复现不了判「幻觉」丢弃
4. 定级冲突 → 取高者，并在备注写明争议
5. 全部入池后按 P0→P1→P2 排序，串行修复

---

# 第 10 部分 · 第一轮挖掘结果（2026-10-29，三路并行）

原始 64 条（Explore-1 线1 12 条 / Explore-2 线2 19 条 / Explore-3 线4+结构 33 条），去重后 **40 条独立问题**。
「命中」列：几个独立 AI 报告了同一问题（≥2 = 高置信，无需再验证）。

## 批次 1 · 立即修（安全洞 + 钱算错，共 8 条）

| ID | 问题 | 定级 | 命中 | 修复位置 |
|---|---|---|---|---|
| V-01 | **匿名可重置任意邮箱密码（账号接管）**：`/api/auth/set-password` 在鉴权白名单内，不校验验证码/旧密码/登录态，知道邮箱即可改密码并登录 | **P0** | X3-01 | AuthInterceptor:40-42 + AuthService:303-321 |
| V-02 | 匿名可领 365 天「应用级 Token」（userId=null），凭它访问全部 API | **P0** | X3-04 | AuthController:30-34 + AuthService:66-84 |
| V-03 | `PUT /coverage-categories/{id}` 不校验归属（IDOR 越权写他人分类） | **P0** | X3-02 | CoverageCategoryService:57-66 |
| V-04 | `GET /asset-history/snapshots` 返回全库快照（越权读，且为前端死接口） | **P0** | X3-03 | HoldingSnapshotController:80-86 + SnapshotListService |
| V-05 | 迁入持仓 **netInvestment 被重算覆盖**（cost 保住了，净投入没保住）：BTC 6,186.81 → 46.84，连带收益率 +13,102%、快照盈亏虚增 6,210 | **P0** | P0-1 + INV-4-D1 + 实测 | HoldingService:451-459 |
| V-06 | `updateTransaction` 无迁入保护：编辑一笔交易 → 份额与成本**同时被抹平**（6,186.81 → 46.84） | **P0** | P0-2 + INV-4-D2 | TransactionService:228-231 |
| V-07 | `updateHolding` 传错保护参数：只改名/费率就覆盖迁入成本 | **P0** | P0-3 + INV-4-D3 | HoldingService:363 |
| V-08 | 分红「到账」无状态守卫：连点/重试重复入账。**准确现象（Explore-1 更正）**：holding.totalDividendReceived 会被下一次 calculateTotalDividendReceived 重算自愈，**不会自愈的是现金**（每次 +amount，且无现金流水表 → 永久不可对账）与**复投模式**（每次重复 createTransaction，份额/成本永久虚增）；且 3 条等额事件（registration/ex_dividend/payout 各带全额）都能被标到账 | **P0** | P0-4 + INV-10-D1 + X3-10 | EventService:103-167 |

## 批次 2 · P1（数据失真 / 校验缺失，共 14 条）

| ID | 问题 | 命中 | 修复位置 |
|---|---|---|---|
| V-09 | 账户视图**完全不含持仓**，三账户合计比总资产少 4.6 万（44%） | INV-8 + X3-16 | AccountService.toDTO / AccountDTO |
| V-10 | 持仓分类无枚举校验 → 置空或写错值即从总资产整体消失；且两处聚合对「未分类」处理相反 | P1-2 + INV-1-D1 + X3-09 | HoldingService:68-74/340 + AssetOverviewService |
| V-11 | 手动执行定投无当日防重（**库内已有 2 次真实双扣：016452 07-21、017641 08-21**），并会把 nextExecutionDate 推快一期 | P1-4 + INV-6-D2 + X3-11 | DcaPlanService:416-526 |
| V-12 | 手工买卖加密币不扣 USDT → 总资产双计整笔金额 | P1-1 | TransactionService:140-149 |
| V-13 | 定投扣款不校验余额：可扣成负 USDT/负现金；无可扣资产时只 warn「仅记账」 | P1-3 + X3-06 | DcaPlanService:539-570 |
| V-14 | 定投计划 accountId 不校验存在/归属/本位币匹配 | X3-06 | DcaPlanService:276-291/352-354 |
| V-15 | `matchesAssetType` 不校验 userId 与 deleted → 可挂他人账户/已删账户，并可枚举他人账户 | X3-05 | AccountService:284-294 |
| V-16 | `holding_snapshots.shares` 仍为 `decimal(18,4)`（实体+库表都没修）→ BTC 份额 0.01139738 存成 0.0115（+0.9%，54.8 元） | P2-1 + INV-9-D1 + X3-13 | HoldingSnapshot:40 + DDL + 回填 |
| V-17 | 资产历史曲线 **70 天里 36 天没有点**（持仓快照每月 5 天，x 轴只取它） | INV-5-D1 | TotalAssetSeriesService:80 |
| V-18 | 分红源接口无 admin 校验：任意用户可删全局分红记录 → 级联删光所有人的分红事件 | X3-07 | FundDividendController:46-67 |
| V-19 | `PUT /holdings|transactions` 缺 `@Valid`，DTO 上的 `@Positive` 全是死代码；负数量 → 买入反向加现金 | P1-6 + X3-08 | HoldingController:63 / TransactionController:39 |
| V-20 | 定投统计只增不减：016452 执行 36 期/470 元，实际 35 笔/450 元 | INV-6-D1 | DcaPlanService + deleteTransaction |
| V-21 | 定投总投入列混币种：加密计划 totalInvested 记 USDT，交易表记 CNY（差 6.7 倍） | P1-2 + INV-6-D3 | DcaPlanService:502 |
| V-22 | 历史遗留：1 持仓 + 1 archived 资产无归属；该用户名下连账户都没有 | INV-7 + X3-15 | adoptOrphans 补 holdings |

## 批次 3 · P2（工程债，共 18 条）

V-23 无事务的写路径（getHolding GET 写库 / refreshAll / createHolding 初始交易）· V-24 建仓扣主现金、交易扣归属账户（口径不一）· V-25 事务内做外部 HTTP 抓取 · V-26 成本由 4 位每份成本反推，误差 0.05 分/份放大 · V-27 快照 pctOfTotal 分母与总览不同口径 · V-28 分红三条里程碑事件各带全额 · V-29 曲线缺失日向前填充（潜在错 6 万）· V-30 缺索引（holdings.user_id、dca_plans 无二级索引等 6 表）· V-31 枚举/日期解析未包 400（全 500）· V-32 参数越界返回 HTTP 200 · V-33 手动资产 amount 无 @Positive（可写负现金）· V-34 事件/交易 fee 无 @Positive · V-35 建仓缺 shares/cost 时 NPE 500 · V-36 1 条 user_id 为 NULL 的历史交易（列表看不到、成本算进去）· V-37 残留备份表 `_bak_*` 未清理 · V-38 泰康纯债被标成 us_stock（大类配置失真）· V-39 user_profile/settings 全局单行（多用户串数据）· V-40 前端类型与后端不符（archived 缺失、latestPrice 必填、归属注释相反）

## 交叉验证已完成（无需再验）

- **三重命中**：V-05（净投入）、V-08（分红幂等）、V-11（定投防重）、V-16（快照精度）
- **双重命中**：V-06、V-07、V-09、V-10、V-19、V-20、V-21、V-22
- **我已独立实测**：V-05（net_investment=46.84）、V-11（两条 source=dca 重复流水）、V-16（落库 0.0115）、V-09（AccountDTO 无 holdings 字段）
- **已排除的误报**：AssetSnapshot 的 18,2（全是人民币金额，合理）；Holding.costPerShare/latestPrice 的 18,4（不截断）；快照/资产快照均幂等；读接口越权已过滤；同 user+date 快照无重复

## 修复顺序建议

1. **V-01~V-04（安全洞，改动小、风险低，优先单独上线）**
2. **V-05~V-08（钱算错，同属「迁入保护/分红幂等」一个域，一起改一起测）**
3. V-09、V-10（聚合口径，涉及账户页/总资产）
4. V-11~V-15（定投与账户校验）
5. V-16（精度 + 历史回填，需备份）
6. 其余 P1 → P2

**授权规则**：修复按「一批一改一测」，每批完成后向你报告并等你确认再继续；部署生产单独请示。

---

# 第 11 部分 · 交叉验证增量（Explore-2 复核）

## 决策项 D-1：迁入持仓的「净投入」语义（**待确认，修复前必须定**）

| 选项 | 含义 | 后果 |
|---|---|---|
| **A（推荐）** | netInvestment 跟随 cost：迁入成本算作「我投入的钱」 | 回本进度/年限按 6,186.81 计算，符合「冷钱包里的 BTC 也是我花钱买的」 |
| B | netInvestment 只算真实现金投入（迁入成本不算） | 净投入≈0 → 回本进度恒 100%，回本年限无意义 |

**我的判断：选 A**。理由：迁入成本是历史上真实付出的钱，只是记账方式为一次性录入；选 B 会让「回本进度」这个功能对迁入资产彻底失效。
**实现方式**：在 `HoldingService:451-459` 的 skip 分支（costEstimated=true）里，`netInvestment` 直接取 `holding.getCost()`，不走 `calculateNetInvestment`；`HoldingDTO` 保持单字段不变，前端零改动。

## 增量修正（3 条）

| ID | 修正内容 |
|---|---|
| **V-05 补充** | 该缺陷是**持续再犯**：每次 GET 详情（HoldingService:111）、夜间刷新（:706）、建仓（TransactionService:119）、编辑（:363）都会用交易汇总覆盖 net_investment 并 save。**只改数据库数字不修代码，第二天就变回去**（本地已实证：10:33 修成 6,186.81，10:38 看了一次详情就变回 46.84） |
| **V-06 升级说明** | updateTransaction 除了抹成本，**份额也先被 `recalculateSharesFromScratch`（:259-275）按交易全量重算**——迁入份额无对应交易 → 直接归零。即改一笔交易的日期 = 成本 + 份额双丢，marketValue 从 6,257 掉到 46.9。修复必须两处一起改 |
| **V-41（新增 P2）** | **DataAuditor 现金对账恒假警报**：把 reinvest（不动现金）、crypto 买入（不扣人民币）、costEstimated 建仓（无交易对应）三类都计入 totalBuy → 每笔都产生假 warning，真实差错被噪音淹没，审计器等于失效。修法：`DataAuditor:130` 剔除这三类 |

## 精度误差的量化结论（影响核对脚本）

- 误差上界：`|cost − round(costPerShare,4) × shares| ≤ 0.00005 × shares`
- 32,477 份 → ≤1.62 元；10 万份 → ≤5 元；且**每次重算都重新 round，会随刷新次数漂移**
- 因此 INV-3 核对必须带容差：`abs(cost − (Σbuy − Σsell)) > 0.00005*shares + 0.02`，否则产生大量假违反
- 结论：这是 P2（设计使然），不适合一次性数据修复收口；若要根治需把 cost 改为主存字段（成本累加）而非由每份成本反推

---

# 第 12 部分 · 第二轮交叉验证增量（Explore-1 回执）

| ID | 内容 | 定级 |
|---|---|---|
| **V-42（新增）** | **定投「跳期」静默丢单**：`DcaScheduler:43` 用 `nextExecutionDate <= today`（非 `==`），后端停机 N 天后恢复只补执行 1 期，而下一期用 `calculateNextExecutionDate(today,…)`（`DcaPlanService:508`）从**今天**起算 → 中间 N−1 期被静默跳过，无日志/无告警/无补偿。宕机 5 天 = 每日定投少投 4 期（约 80 元/持仓）。当前数据未触发 | P2 |
| V-13 补充 | USDT 扣减无下限 → 可扣成负余额；当前库无 quantity<0 记录，属潜在 | P2（latent） |
| V-10 补充 | 未分类持仓当前 **0 条**，属潜在；曲线末点与头部差异的真因是 V-17（x 轴取 holding_snapshots 日期，36/70 天无点），不是未分类 | P2（latent） |

## 已确认的硬证据

- 迁入持仓 `net_investment − cost = −6,139.97`（BTC），与「净投入被覆盖」结论完全吻合
- 定投双跑定位到**同一 plan 同日两笔**：`609954c7/2026-08-21`（5.8353@1.712/10.00）、`be27a0b6/2026-07-21`（4.4916@2.2264/10.00）——quantity/price 4 位全等 = executePlan 公式输出，**排除手工补录**
- be27a0b6 计数虚高：`total_executions=36 / total_invested=470.00` vs 实际 `35 笔 / 450.00`
