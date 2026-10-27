# 项目规则 — 基金分红追踪器

## AI 行为准则

当用户说"你有什么想法？"、"你的看法"、"说说你的思路"等类似表达时，AI 必须先**复述用户的需求/问题 + 给出方案/思路/看法**，等待用户确认后再开始开发。不得跳过确认步骤直接动手改代码。目的是确保双方理解一致，避免做无用功。

### 🔴 硬性守卫：生产端操作必须经用户明确授权

**此条为最高优先级规则，AI 不得以任何理由自行跳过。**

#### 触发条件
以下操作被定义为"生产端操作"，**必须**经用户明确授权后方可执行：
- `git push` / `git commit`（将变更写入远程仓库）
- `scp` 上传文件到服务器
- `ssh` 执行服务器命令（部署、重启、配置修改）
- `gh` / GitHub CLI 操作远程仓库

#### 执行流程（AI 必须严格遵守）

```
[修复代码完成] 
    ↓
┌─ 步骤 1：向用户报告变更摘要 ──────────────────────┐
│  "XX 问题已修复。变更包括：xxx                      │
│   需要推送 GitHub 和部署服务器吗？"                  │
└──────────────────────────────────────────────────┘
    ↓
┌─ 步骤 2：等待用户明确输入授权词 ──────────────────┐
│  授权词必须是以下之一（或其组合）：                  │
│  - "推送" / "push" / "提交" / "commit"            │
│  - "部署" / "deploy" / "上传服务器" / "更新服务器" │
│  - "推送github和服务器" 等明确组合                  │
│                                                    │
│  以下 NOT 视为授权：                                │
│  × "好" / "可以" / "需要" / "改好了" / "完成了"    │
│  × 仅回复"确认修复"或"继续"等模糊表达               │
└──────────────────────────────────────────────────┘
    ↓
┌─ 步骤 3：收到授权词后 → 执行安全审查 → 执行操作 ─┐
│  (已有规则：审查敏感信息、评估安全影响)              │
└──────────────────────────────────────────────────┘
```

#### 关键约束
1. **禁止连续授权**：上一次授权仅对当次生效。下次需要推送/部署时，必须重新经历步骤 1-2
2. **禁止推断授权**：用户同意"修复代码" ≠ 同意"推送和部署"。修复和生产操作是两件事
3. **禁止自我授权**：AI 不得在用户提问格式中夹带"是否要推送"等诱导与代码修复在同一回合完成，必须修复完成后单独提出

在将代码推送至 GitHub 之前，AI 必须审查本次待推送的变更内容（源码、配置文件、注释、commit message）中是否包含硬编码的密码、Token、API Key、服务器 IP、邮箱授权码等敏感信息。若发现，则阻止推送并告知用户，待移除或替换为环境变量占位符后再推送。

在将代码部署至服务器之前，AI 必须评估部署操作对服务器安全的影响：不引入暴露敏感信息的配置、不修改已有的安全加固措施（防火墙规则、SSH 配置、端口绑定等）、不引入新的安全风险。若存在安全隐患，应提示用户并暂缓部署。

## 项目概述
一个个人基金投资管理工具，功能包括：持仓管理、交易记录、分红追踪、成本收益指标、定投计划自动化、现金记账、分红预测、个人总资产概览、数据审计与对账。

- **前端**：Vue 3 (Composition API + `<script setup lang="ts">`) + Vue Router 4 + Vite + TypeScript + Tailwind CSS + Material Symbols 图标 + ECharts（资产历史走势图）+ Vitest（tests/ 测试）
- **后端**：Spring Boot 3.2.0 + Spring Data JPA + Hibernate + MySQL 8.0 + Maven
- **开发端口**：前端 `http://localhost:5173`，后端 `http://localhost:8080`

## 启动方式
系统禁用了 PowerShell 脚本执行策略，必须使用 `-ExecutionPolicy Bypass`：
```powershell
# 方式一：双击根目录 start.bat / stop.bat（GBK 编码，cmd.exe 正常显示中文）
#   start.bat = 后端 mvn spring-boot:run -Dspring-boot.run.profiles=local + 前端 npm run dev

# 方式二：手动（新开终端）
cd myPhonePro/fund-tracker-backend && mvn spring-boot:run -Dspring-boot.run.profiles=local
cd myPhonePro/stitch_fund_dividend_tracker && npm run dev
```

## 构建命令
- 后端：`cd myPhonePro/fund-tracker-backend && mvn clean package -DskipTests`
- 后端测试：`cd myPhonePro/fund-tracker-backend && mvn test`
- 前端：`cd myPhonePro/stitch_fund_dividend_tracker && npm run build`
- 前端类型检查：`cd myPhonePro/stitch_fund_dividend_tracker && npx vue-tsc --noEmit`

## 缓存/编译注意事项
- 后端如果修改了 Repository 方法签名（如 `void` → `int`），需要 `mvn clean compile` 强制重新编译，否则 Spring Boot 运行时抛出 `Unresolved compilation problems`（利用了旧 .class 文件）
- 前端 `<KeepAlive>` 缓存组件后，Vite HMR 可能无法正确热更新生命周期钩子（如 `onMounted` → `onActivated`），需要浏览器硬刷新
- PowerShell 不支持 heredoc 语法（`<<'EOF'`），`git commit` 多行 message 用单行 `-m` 或写临时文件
- PowerShell 不支持 `&&` 连接命令，用 `;`；ssh/scp 带密码查询勿用命令行内嵌真实密码（会落入 shell 历史），走环境变量或凭据文件
- PowerShell 嵌套引号易被外层吃掉，复杂脚本写到 `.ps1`/`.bat` 文件用 `-File` 参数执行
- `.trae/rules/project_rules.md` 是**本地文件、不被 git 跟踪**，删除工作区文件会导致丢失且无法从 git 恢复，需人工重建

## 敏感信息安全（硬约束，最高优先级）

### 禁止硬编码
- DB 密码、服务器 IP、邮箱授权码、API Token、私钥、手机号、AK/SK **一律不得出现在源码、commit message、注释、文档中**
- 历史泄露已发生：MySQL 口令、服务器 IP、邮箱授权码、旧应用 Token 曾被推送到 GitHub。**具体值已从本归档版移除**；如需核实请用 `git log -S` 查历史，并确保相关凭证已在生产环境轮换。

### 必须用环境变量占位符
`application.yml` 中所有敏感配置使用 `${VAR_NAME:DEFAULT}` 格式，默认值必须是无意义占位符（如 `CHANGE_ME`），非敏感默认值可用真实值（如 `fundapp`）：

| 配置项 | 变量名 | 用途 |
|--------|--------|------|
| `spring.datasource.password` | `DB_PASSWORD` | 数据库密码 |
| `spring.datasource.username` | `DB_USERNAME` | 数据库用户名（默认 `fundapp`）|
| `spring.datasource.url` | `DB_URL` | JDBC 连接串 |
| `app.auth.token` | `APP_AUTH_TOKEN` | 认证 Token |

GitHub Actions 通过 Secrets 注入：
- `SERVER_HOST` - 服务器 IP
- `SERVER_SSH_KEY` - SSH 私钥
- `DB_MYSQL_PASSWORD` - MySQL root 密码（备份用）
- `DB_BACKUP_REPO` - 备份仓库 git 地址

### .gitignore 必须覆盖
```
application-local.yml          # Spring 本地配置（含真实 SMTP/DB 凭证）
application-local.properties
*.zip / *.tar.gz / *.tar       # 日志/归档
start.bat / stop.bat           # 本地批处理（含绝对路径）
.env                           # 前端本地自动登录凭据
test_*.json                    # 调试残留
.trae/                         # 本地项目规则（必须人工备份）
```

### 日志禁止打印
- Token 明文、密码明文、授权码明文
- `DataInitializer` 只能打印 `length=` 或脱敏前缀，不得 `println(configuredToken)`
- `System.out.println` 在生产代码中慎用，优先用 SLF4J logger 并脱敏

### 本地真实凭证
- 放 `application-local.yml`（Spring Profile `local`），通过 `--spring.profiles.active=local` / `-Dspring-boot.run.profiles=local` 激活
- 前端自动登录凭据放 `.env`（`VITE_DEV_EMAIL` / `VITE_DEV_PASSWORD`），仅 localhost 生效、部署自动禁用
- 这些文件**必须**被 `.gitignore` 覆盖，永不入 git

## 服务器安全加固（部署基线）

### MySQL
- `bind-address = 127.0.0.1`（禁止外网访问）
- `mysqlx = OFF`（禁用 X Protocol 33060 端口）
- 应用账号 `fundapp` 仅授权 `fund_tracker.*`，备份用 root 仅本地执行

### firewalld
封禁端口外网访问：`3306`（MySQL）、`8080`（后端）、`8888`（宝塔）、`33060`（MySQL X）
仅开放 `22`（SSH）、`80/443`（Nginx）

### SSH
- `PermitRootLogin no`（禁 root 登录）
- 优先用密钥认证
- fail2ban：5 次失败登录 / 10 分钟内封禁 IP

### systemd
- `override.conf` 存放环境变量（`DB_PASSWORD`、`APP_AUTH_TOKEN` 等）
- 文件权限 `600`，路径 `/etc/systemd/system/fund-tracker.service.d/override.conf`
- 修改后执行 `sudo systemctl daemon-reload && sudo systemctl restart fund-tracker`

### Workbench / 数据库连接
- MySQL 已绑定 127.0.0.1，**禁止外网直连 3306**
- Workbench 走 SSH 隧道（Standard TCP/IP over SSH）：SSH Host=服务器IP:22, MySQL Host=127.0.0.1, MySQL Port=3306

## 服务器性能配置（2核 2G）
- **HikariCP 连接池**：`maximum-pool-size: 5`、`minimum-idle: 2`、`connection-timeout: 5000`、`idle-timeout: 300000`
- **JVM 启动参数**：`-Xmx384m -Xms256m`（`pom.xml` 的 `spring-boot-maven-plugin` `<jvmArguments>`）
- **JPA 索引**：`idx_transactions_holding_id` on `Transaction.holdingId`、`idx_holdings_deleted` on `Holding.deleted`

## 代码结构

### 前端 (`myPhonePro/stitch_fund_dividend_tracker/src/`)
```
├── api/                            # API 请求层（19 个模块）
│   ├── request.ts                  # fetch 封装：json.code !== 200 抛异常、401 自动重登录、15s 超时
│   ├── auth.ts                     # 认证（验证码/密码登录、注册、设置密码、退出）
│   ├── holding.ts / transaction.ts # 持仓 / 交易 CRUD
│   ├── event.ts / dca.ts           # 分红事件 / 定投计划
│   ├── dashboard.ts / insight.ts   # 首页看板 / 月度洞察
│   ├── assetOverview.ts / assetHistory.ts / manualAsset.ts  # 总资产概览 / 资产历史走势 / 手动资产
│   ├── fund.ts / exchangeRate.ts   # 基金分红刷新 / 汇率
│   ├── expense.ts / audit.ts / metrics.ts / monitor.ts / scheduler.ts  # 生活开销 / 审计 / 指标 / 监控 / 定时任务
│   └── user.ts                     # 用户信息与设置
├── components/
│   ├── dca/                        # 定投相关弹窗组件（DcaCreateSheet / DcaExecuteSheet）
│   └── shared/                     # 通用组件（AppHeader / BottomNav / PageState / ToastNotification）
├── router/ index.ts                # 路由配置，meta.level 控制导航层级
├── types/ api.ts + index.ts        # 共享类型
├── data/ changelog.ts              # 首页更新公告数据
├── utils/ format.ts                # 格式化工具
├── views/
│   ├── home/HomePage.vue           # 首页（level 1）：持仓列表 + 指标卡片 + 更新公告
│   ├── calendar/CalendarPage.vue   # 分红日历（level 1）：月度/年度洞察 + 手动更新
│   ├── discover/DiscoverPage.vue   # 个人总资产概览（level 1）
│   ├── profile/ProfilePage.vue     # 我的（含工具箱功能）（level 1）
│   ├── scheduler/SchedulerPage.vue # 定时任务管理（含监控面板）
│   ├── asset-history/AssetHistoryPage.vue   # 资产历史（level 2）：走势图(多分类折线) + 持仓卡片
│   ├── holding-add/HoldingAddPage.vue       # 添加标的（level 2）
│   ├── holding-detail/HoldingDetailPage.vue # 持仓详情（level 2）
│   ├── trade-add/TradeAddPage.vue           # 添加交易（level 2）
│   ├── transactions/TransactionListPage.vue # 交易明细列表（level 2）
│   ├── dividends/DividendHistoryPage.vue    # 分红历史（level 2）
│   ├── coverage/{CoveragePage,SettingsPage,AddExpenseModal}.vue  # 分红覆盖（level 2）
│   ├── dca/DcaPlanDetailPage.vue  # 定投计划详情（level 2）
│   └── metrics/MetricSettings.vue # 指标设置（level 2）
├── tests/                          # Vitest 测试（smoke / scenarios / audit / invariants）
├── App.vue                         # 根组件，KeepAlive + 导航栏控制
├── main.ts / env.d.ts
```

### 后端 (`myPhonePro/fund-tracker-backend/src/main/java/com/fundtracker/`)
```
├── FundTrackerApplication.java      # 启动入口
├── config/                          # AuthInterceptor / CorsConfig / DataInitializer / WebConfig
├── controller/                      # REST Controller（19 个，含 HoldingSnapshotController）
├── service/                         # 业务服务层（含 provider/ 外部数据源层）
│   ├── provider/                    # 外部数据源 Provider：DividendTableProvider(东方财富分红页爬虫)、
│   │                                #   EstablishDateProvider(成立日期)、FundSearchProvider、
│   │                                #   NavDataProvider(净值)、ExchangeRateProvider(汇率)
│   ├── HoldingSnapshotService / TotalAssetSeriesService / HoldingSeriesService  # 资产历史快照
│   ├── FundDividendScrapeService / DividendTableProvider   # 分红爬虫
│   ├── CostCalculator / TradingCalendar / ForecastService  # 成本算法 / 交易日历 / 预测
│   └── ...                          # 其余 20+ 服务
├── scheduler/                       # DataAuditor / DcaScheduler / FundDividendScheduler /
│                                    #   DividendAutoDistributeScheduler / HoldingSnapshotScheduler
├── listener/ TransactionEventListener.java   # 交易变更→分红事件同步（事务后事件）
├── event/ TransactionChangedEvent.java
├── model/ entity/ + dto/ + enums/
├── repository/
├── exception/ BusinessException + GlobalExceptionHandler
└── resources/ application.yml + holidays.json + logback-spring.xml
```

## 关键业务规则

### 成本算法（3 种）
| 枚举值 | 名称 | 计算公式 |
|--------|------|----------|
| `diluted` | 分红摊薄 | (总买入 - 总卖出 - 总分红) / 当前份额 |
| `diluted_only` | 纯摊薄成本 | (总买入 - 总卖出) / 当前份额 |
| `weighted_avg` | 加权平均 | 总买入金额 / 总买入份额 |

### 物理级联删除
删除持仓会**物理删除**该持仓及其关联的所有交易记录和分红事件。

### 交易后份额重算
创建/编辑/删除交易后调用 `recalculateSharesFromScratch` 重算份额，再 `recalculateHoldingMetrics` 重算成本指标。

### 自动刷新机制
- `<KeepAlive>` 缓存路由组件，`onActivated` 钩子在激活时重新加载数据
- `HoldingDetailPage` 使用 `watch(() => route.params.id, ...)` 监听路由参数变化（固定 bug：KeepAlive 缓存导致切换持仓不重新挂载）

### 路由导航层级
- 一级页面：右上角 👤 用户图标 + 底部导航栏
- 二级以上页面：右上角 🏠 回首页 + 隐藏底部导航栏
- 通过 `meta.level` 和 `hiddenRoutes` 控制

### 指标设置
最多选 6 项，实时预览在设置页顶部 grid-cols-3 显示，已满时未选中项变灰不可点击。

### 交易总金额模式
用户输入总金额，前端计算 `perSharePrice = totalAmount / quantity` 后发送后端。

### 基金分红爬虫防伪规则（重要！）
基金代码可能被复用（如 `016452` 曾是其他基金，后被纳指100复用），爬虫会拿到旧基金的分红数据，必须按日期严格过滤：

- `DividendTableProvider.isValidExDate` 过滤条件（**唯一**的日期过滤点）：
  - `exDate == null` → 无效（无法解析）
  - `exDate < 基金成立日期` → 无效（代码复用导致的旧基金遗留数据）
  - **允许 `exDate > 今天`（重要变更，2026-09-06 生效）**：基金公告已发、除息日还未到期的分红属于"待发放"正常记录，**必须抓取入库**。曾因"未来日期=异常"过滤导致 021550 在 2026-09-10 发放的分红（09-08 除息）无法入库、日历不显示，已移除该过滤
- **防代码复用靠"成立日期过滤"即可**，不要用"未来日期"作为脏数据判据
- `DividendEventSyncService` 在分红记录被清空时，**必须级联清理**已同步的旧 `DividendEvent`，避免脏事件残留
- 历史案例：纳指100（016452）因未按成立日期过滤，曾出现 12 条假分红记录，导致分红率显示异常

### 资产历史走势图（AssetHistoryPage）
- 市值 tab 渲染"总计线 + 按 assetCategory 分类折线"，颜色与持仓卡片圆点**共用 `catColors` 映射**（us_stock=蓝/gold=金/dividend=橙红/crypto=紫/cash=绿/uncategorized=灰）
- 分类市值由后端 `TotalAssetSeriesService` 按快照当天的 `HoldingSnapshot.marketValue` 按类别聚合，叠加 `asset_snapshots` 的现金/比特币
- 手动记录快照按钮：`triggerSnapshot` 生成当天快照；再次点击同一日期覆盖写入（HoldingSnapshotRepository.deleteBySnapshotDate 已加 `@Modifying(flushAutomatically, clearAutomatically)`，确保 DELETE 先于 INSERT，避免 uk_holding_date 冲突）

### 年化收益率三层保护（HoldingSnapshotService）
1. `holdingDays < 7` 返回 `null`
2. IRR 100 次迭代 `|NPV| > 0.01` 返回 `null`
3. 年化结果超出 `[-95%, 1000%]` 返回 `null`

### 数据库表结构（fund_tracker）
```
holdings / transactions        # 持仓与交易（holdings 含 asset_category 字段）
dividend_events / fund_dividend_records / fund_nav_records   # 分红事件 / 历史分红 / 历史净值
exchange_rates                 # 汇率
dca_plans / manual_assets / live_expenses   # 定投 / 手动资产(BTC·现金) / 生活开销
asset_snapshots / holding_snapshots / coverage_categories    # 资产快照 / 持仓快照 / 分红覆盖类目
user_profiles / user_settings / auth_tokens  # 用户与认证
email_verification_codes       # 邮箱验证码
```

### 个人总资产概览（发现页面 /discover）
汇总 5 类资产：现金、比特币、美股、黄金、红利。布局：分类 Banner → 总资产 Hero → 持仓占比 Treemap + 资产变动 → 各类资产卡片 → 添加现金/比特币 → 品牌氛围图。
- 未分类持仓项点击弹出分类弹窗，设置 `assetCategory` = `us_stock` / `gold` / `dividend`
- 数据来源：`manual_assets` 表（cash/crypto）+ `holdings.asset_category` 标记（us_stock/gold/dividend）= `asset_snapshots` 每日快照
- 关键 API：`GET /api/asset-overview`、`GET /api/asset-overview/history`、`POST /api/asset-overview/snapshot`、`/api/manual-assets` CRUD、`PUT /api/holdings/{id}/category`（assetCategory 允许空字符串=清除分类）
- 添加标的页面已移除市场类型按钮与 Step 2 资产分类选择器，分类统一在发现页/持仓详情编辑

## 常见问题处理

1. **"持仓不存在"错误**：KeepAlive 缓存导致不重新挂载。修复：`watch(() => route.params.id, ...)`。
2. **删除后端报编译错误**：Repository 返回值类型不匹配。修复：`mvn clean compile`。
3. **添加交易后页面不刷新**：`router.back()` 可能不触发 `onActivated`。修复：`router.replace({ name: 'transaction-list', params: { id } })`。
4. **成本算法前后端不匹配**：只发 `diluted` / `diluted_only` / `weighted_avg`。
5. **分红日历点"更新"后新分红不出现**：检查服务器日志是否存在 `DividendTableProvider` 跳过记录；若 exDate 在未来被跳过 → 已修复，需部署含 2026-09-06 之后的 JAR。

## 认证机制
- 后端使用基于数据库 Token 的 HandlerInterceptor 认证（无 Spring Security）
- `AuthInterceptor` 拦截 `/api/**`，白名单：`/api/auth/*`、`/api/funds/*`、`/api/holdings/dividend-info`
- Token 存在 `auth_tokens` 表，`DataInitializer` 启动时初始化种子 Token（`${APP_AUTH_TOKEN}`），已有活跃 Token 时不插入新值
- 前端 `request.ts` 维护 Token：401 自动清除并跳登录页；本地开发（localhost）支持 `.env` 自动登录
- 切换 Token 流程：改 override.conf → daemon-reload → `UPDATE auth_tokens SET active=0 WHERE token='旧Token'` → restart → curl 验证

## 前后端交互
- 统一 `ApiResponse<T>`：成功 `code===200`，业务异常 `code!==200` + `message`
- 前端 `request.ts` 对 `code!==200` 抛 `Error(message)`，页面 catch 后 Toast/alert 展示

## 部署流程

### 部署的触发条件
只有用户明确说出"推送 github"、"部署到服务器"、"上传服务器"、"更新服务器"、"部署"等包含明确推送/上传/部署意图的指令时才执行 git push / scp / ssh 等操作。

### 前端（自动化）
- 推送 `main` → GitHub Actions `deploy.yml`：`npm ci` → `npm run build` → scp dist → Nginx reload

### 后端（手动）
1. `mvn clean package -DskipTests`
2. `scp target/fund-tracker-*.jar admin@${SERVER_HOST}:/home/admin/fund-tracker.jar`（SERVER_HOST 未设置时用历史 IP）
3. `ssh admin@${SERVER_HOST} "cd ~ && ./deploy.sh"`（只重启，不构建）
4. `ssh admin@${SERVER_HOST} "sudo systemctl status fund-tracker --no-pager"`

### 数据库备份（自动化）
- GitHub Actions `db-backup.yml` 每天 UTC 02:00（北京时间 10:00）自动执行，mysqldump → gzip → 私有备份仓库（保留 30 天）