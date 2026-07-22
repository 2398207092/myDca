# 项目规则 — 基金分红追踪器

## AI 行为准则

当用户说"你有什么想法？"、"你的看法"、"说说你的思路"等类似表达时，AI 必须先**复述用户的需求/问题 + 给出方案/思路/看法**，等待用户确认后再开始开发。不得跳过确认步骤直接动手改代码。目的是确保双方理解一致，避免做无用功。

## 项目概述
一个个人基金投资管理工具，功能包括：持仓管理、交易记录、分红追踪、成本收益指标、定投计划自动化、现金记账、分红预测、个人总资产概览、数据审计与对账。

- **前端**：Vue 3 (Composition API + `<script setup lang="ts">`) + Vue Router 4 + Vite + TypeScript + Tailwind CSS + Material Symbols 图标
- **后端**：Spring Boot 3.2.0 + Spring Data JPA + Hibernate + MySQL 8.0 + Maven
- **开发端口**：前端 `http://localhost:5173`，后端 `http://localhost:8080`

## 启动方式
系统禁用了 PowerShell 脚本执行策略，必须使用 `-ExecutionPolicy Bypass`：
```powershell
# 后端
cd myPhonePro/fund-tracker-backend && mvn spring-boot:run

# 前端（新开终端）
powershell -ExecutionPolicy Bypass -Command "cd 'myPhonePro/stitch_fund_dividend_tracker'; npm run dev"
```

## 构建命令
- 后端：`cd myPhonePro/fund-tracker-backend && mvn clean compile`
- 前端（需要时）：`cd myPhonePro/stitch_fund_dividend_tracker && npm run build`

## 缓存/编译注意事项
- 后端如果修改了 Repository 方法签名（如 `void` → `int`），需要 `mvn clean compile` 强制重新编译，否则 Spring Boot 运行时抛出 `Unresolved compilation problems`（利用了旧 .class 文件）
- 前端 `<KeepAlive>` 缓存组件后，Vite HMR 可能无法正确热更新生命周期钩子（如 `onMounted` → `onActivated`），需要浏览器硬刷新

## 服务器性能配置（2核 2G）
- **HikariCP 连接池**：`maximum-pool-size: 5`、`minimum-idle: 2`、`connection-timeout: 5000`、`idle-timeout: 300000`（`application.yml` 中 `spring.datasource.hikari`）
- **JVM 启动参数**：`-Xmx384m -Xms256m`（在 `pom.xml` 的 `spring-boot-maven-plugin` 中通过 `<jvmArguments>` 配置）
- **JPA 索引**：
  - `idx_transactions_holding_id` on `Transaction.holdingId`
  - `idx_holdings_deleted` on `Holding.deleted`
  - 通过实体类 `@Table(indexes = @Index(...))` 声明

## 代码结构

### 前端 (`myPhonePro/stitch_fund_dividend_tracker/src/`)
```
├── api/                            # API 请求层（17 个模块）
│   ├── request.ts                  # 基于 fetch 的请求封装，json.code !== 200 时抛异常
│   ├── auth.ts                     # 认证（获取/刷新 Token）
│   ├── holding.ts / transaction.ts # 持仓 / 交易 CRUD
│   ├── event.ts / dca.ts           # 分红事件 / 定投计划
│   ├── dashboard.ts / insight.ts   # 首页看板 / 月度洞察
│   ├── assetOverview.ts / manualAsset.ts  # 总资产概览 / 手动资产
│   ├── fund.ts / exchangeRate.ts   # 基金数据 / 汇率
│   ├── coverage.ts / expense.ts    # 分红覆盖类目 / 生活开销
│   ├── audit.ts / metrics.ts       # 审计日志 / 指标配置
│   └── user.ts                     # 用户信息与设置
├── components/
│   ├── dca/                        # 定投相关弹窗组件
│   │   ├── DcaCreateSheet.vue      # 定投计划创建弹窗
│   │   └── DcaExecuteSheet.vue     # 定投执行弹窗
│   └── shared/                     # 通用组件
│       ├── AppHeader.vue           # 顶部导航（含层级控制）
│       ├── BottomNav.vue           # 底部导航栏
│       ├── DividendCard.vue        # 分红卡片
│       └── PageState.vue           # 页面状态（loading/empty/error）
├── router/
│   └── index.ts                    # 路由配置，meta.level 控制导航层级
├── types/
│   ├── api.ts                      # API 响应类型
│   └── index.ts                    # 业务类型
├── data/
│   └── mock.ts                     # Mock 数据
├── assets/styles/
│   └── main.css                    # 全局样式
├── views/
│   ├── home/HomePage.vue                  # 首页（level 1）：持仓列表 + 指标卡片
│   ├── calendar/CalendarPage.vue          # 分红日历（level 1）
│   ├── discover/DiscoverPage.vue          # 个人总资产概览（level 1）
│   ├── profile/{ProfilePage,ToolboxPage}.vue  # 我的 + 工具箱（level 1）
│   ├── holding-add/HoldingAddPage.vue     # 添加标的（level 2）
│   ├── holding-detail/HoldingDetailPage.vue  # 持仓详情（level 2）
│   ├── trade-add/TradeAddPage.vue         # 添加交易（level 2）
│   ├── transactions/TransactionListPage.vue  # 交易明细列表（level 2）
│   ├── dividends/DividendHistoryPage.vue  # 分红历史（level 2）
│   ├── coverage/{CoveragePage,SettingsPage,AddExpenseModal}.vue  # 分红覆盖（level 2）
│   ├── dca/DcaPlanDetailPage.vue          # 定投计划详情（level 2）
│   └── metrics/MetricSettings.vue         # 指标设置（level 2，实时预览，最多选6项）
├── App.vue                         # 根组件，KeepAlive + 导航栏控制
├── main.ts                         # 入口
└── env.d.ts                        # Vite 环境变量类型
```

### 后端 (`myPhonePro/fund-tracker-backend/src/main/java/com/fundtracker/`)
```
├── FundTrackerApplication.java      # 启动入口
├── config/                          # Spring 配置
│   ├── AuthInterceptor.java         # Token 认证拦截器
│   ├── CorsConfig.java              # 跨域配置
│   ├── DataInitializer.java         # 启动时初始化种子 Token、默认用户等
│   └── WebConfig.java               # 注册 AuthInterceptor，拦截 /api/**
├── controller/                      # REST Controller 层（17 个）
│   ├── AssetOverviewController.java # 总资产概览
│   ├── AuditLogController.java      # 审计日志
│   ├── AuthController.java          # 认证
│   ├── CoverageCategoryController.java  # 分红覆盖类目
│   ├── DashboardController.java     # 首页看板
│   ├── DbBackupController.java      # 数据库备份
│   ├── DcaPlanController.java       # 定投计划
│   ├── DividendRecordController.java    # 分红记录
│   ├── EventController.java         # 分红事件
│   ├── ExchangeRateController.java  # 汇率
│   ├── FundDividendController.java  # 基金分红数据
│   ├── HoldingController.java       # 持仓
│   ├── InsightController.java       # 月度洞察
│   ├── LiveExpenseController.java   # 生活开销
│   ├── ManualAssetController.java   # 手动资产（现金/BTC）
│   ├── TransactionController.java   # 交易记录
│   ├── UserController.java          # 用户信息与设置
│   └── ValueChangeController.java   # 资产变动
├── service/                         # 业务服务层（24 个）
│   ├── HoldingService.java          # 持仓核心逻辑：创建/删除（物理级联）、成本重算
│   ├── TransactionService.java      # 交易 CRUD + 份额重算（recalculateSharesFromScratch）
│   ├── CostCalculator.java          # 三种成本算法实现
│   ├── DcaPlanService.java          # 定投计划 + DcaScheduler 定时执行
│   ├── EventService.java            # 分红事件生命周期
│   ├── DividendRecordService.java   # 分红记录同步
│   ├── DividendInfoService.java     # 分红信息查询
│   ├── DividendEventSyncService.java    # 分红事件同步
│   ├── FundDividendScrapeService.java   # 抓取天天基金分红数据
│   ├── FundNavScrapeService.java    # 抓取基金净值
│   ├── FundSearchService.java       # 基金代码搜索
│   ├── ForecastService.java         # 分红预测
│   ├── ManualAssetService.java      # 手动资产 + adjustCash 现金记账
│   ├── AssetOverviewService.java    # 总资产汇总 + 快照
│   ├── DashboardService.java        # 首页看板
│   ├── InsightService.java          # 月度洞察
│   ├── ValueChangeService.java      # 资产变动
│   ├── CoverageCategoryService.java # 分红覆盖类目
│   ├── LiveExpenseService.java      # 生活开销
│   ├── ExchangeRateService.java     # 汇率
│   ├── AuthService.java             # Token 发放
│   ├── UserService.java             # 用户信息
│   └── TradingCalendar.java         # 交易日历（读取 holidays.json）
├── scheduler/                       # 定时任务
│   ├── DataAuditor.java             # 每日数据审计（6 条规则）
│   ├── DcaScheduler.java            # 定投计划定时执行
│   └── FundDividendScheduler.java   # 基金分红数据定时同步
├── model/
│   ├── entity/                      # JPA 实体（14 个）
│   │   ├── Holding.java             # 持仓（含 assetCategory 字段）
│   │   ├── Transaction.java         # 交易
│   │   ├── DividendEvent.java       # 分红事件
│   │   ├── FundDividendRecord.java  # 基金历史分红
│   │   ├── FundNavRecord.java       # 基金历史净值
│   │   ├── DcaPlan.java             # 定投计划
│   │   ├── ManualAsset.java         # 手动资产（现金/BTC）
│   │   ├── AssetSnapshot.java       # 每日资产快照
│   │   ├── LiveExpense.java         # 生活开销
│   │   ├── CoverageCategory.java    # 分红覆盖类目
│   │   ├── ExchangeRate.java        # 汇率
│   │   ├── UserProfile.java         # 用户信息
│   │   ├── UserSettings.java        # 用户设置
│   │   └── AuthToken.java           # 认证 Token
│   ├── dto/                         # 请求/响应 DTO（40+ 个，按模块命名）
│   └── enums/                       # 枚举（8 个）
│       ├── TransactionType.java     # buy/sell/bonus_share/reinvest
│       ├── CostAlgorithm.java       # diluted/diluted_only/weighted_avg
│       ├── HoldingType.java         # fund/cny_asset/ETF/A股/港股/美股/自定义
│       ├── EventType.java           # registration/ex_dividend/payout/announcement
│       ├── EventStatus.java         # pending/distributed/cancelled
│       ├── DcaFrequency.java        # daily/weekly/monthly/quarterly
│       ├── ForecastHorizon.java     # 12m/5y
│       └── MembershipType.java      # pro/free
├── repository/                      # JPA Repository（14 个，与实体一一对应）
├── exception/
│   ├── BusinessException.java       # 业务异常（含 holdingNotFound, transactionNotFound 等工厂方法）
│   └── GlobalExceptionHandler.java  # 全局异常处理，BusinessException 返回 HTTP 200 + 业务 code
└── resources/
    ├── application.yml              # 配置（含 HikariCP、JVM 参数等）
    ├── holidays.json                # 节假日数据（TradingCalendar 读取）
    └── logback-spring.xml           # 日志配置
```

### 数据库 (`fund_tracker`)
```
# 持仓与交易
holdings                  # 持仓（含 asset_category 字段）
transactions              # 交易记录

# 分红
dividend_events           # 用户自定义分红事件
fund_dividend_records     # 基金历史分红数据

# 净值与汇率
fund_nav_records          # 基金历史净值
exchange_rates            # 汇率

# 定投与现金
dca_plans                 # 定投计划
manual_assets             # 手动资产（现金/BTC）
live_expenses             # 生活开销

# 资产概览
asset_snapshots           # 每日总资产快照
coverage_categories       # 分红覆盖类目

# 用户与认证
user_profiles             # 用户信息
user_settings             # 用户设置
auth_tokens               # 认证 Token
```

## 关键业务规则

### 成本算法（3 种）
| 枚举值 | 名称 | 计算公式 |
|--------|------|----------|
| `diluted` | 分红摊薄 | (总买入 - 总卖出 - 总分红) / 当前份额 |
| `diluted_only` | 纯摊薄成本 | (总买入 - 总卖出) / 当前份额 |
| `weighted_avg` | 加权平均 | 总买入金额 / 总买入份额 |

注：分红摊薄(diluted) 和 纯摊薄(diluted_only) 的区别在于前者将收到的分红也视为成本回收。

### 物理级联删除
删除持仓会**物理删除**该持仓及其关联的所有交易记录和分红事件（调用 `deleteByHoldingId`）。

### 交易后份额重算
创建/编辑/删除交易后，系统通过 `recalculateSharesFromScratch` 从所有交易重新计算份额：
- `buy`/`reinvest`/`bonus_share` → 增加份额
- `sell` → 减少份额
- 然后调用 `recalculateHoldingMetrics` 重新计算成本指标

### 自动刷新机制
- 使用 `<KeepAlive>` 缓存路由组件，通过 `onActivated` 生命周期钩子在页面激活时重新加载数据
- `HomePage` 和 `TransactionListPage` 使用 `onActivated` 实现自动刷新
- `HoldingDetailPage` 使用 `watch(() => route.params.id, ...)` 监听路由参数变化

### 路由导航层级
- **一级页面**（首页/日历/发现/我的）：右上角 👤 用户图标 + 底部导航栏
- **二级以上页面**：右上角 🏠 回首页 + 隐藏底部导航栏
- 通过 `meta.level` 和 `hiddenRoutes` 控制

### 指标设置
- 最多选 6 项，实时预览在设置页顶部 grid-cols-3 显示
- 已满时未选中项变灰不可点击
- Toggle 白点动画通过 `left-[22px]` / `left-[2px]` 实现

### 交易总金额模式
用户输入的是总金额（如 ¥1,230），前端计算 `perSharePrice = totalAmount / quantity` 后发送给后端。

## 常见问题处理

1. **"持仓不存在"错误**：App.vue 的 KeepAlive 缓存导致切换不同持仓时不重新挂载组件。修复：使用 `watch(() => route.params.id, ...)` 监听路由变化。

2. **删除后端报编译错误**：Repository 方法返回值类型不匹配（如 `void` 但 Service 赋给 `int`）。修复：`mvn clean compile` 强制重编译。

3. **添加交易后页面不刷新**：`router.back()` 可能不触发 `onActivated`。修复：使用 `router.replace({ name: 'transaction-list', params: { id } })` 显式导航。

4. **成本算法前后端不匹配**：前端必须发送 `diluted` / `diluted_only` / `weighted_avg` 三个枚举值之一，不能使用 `undiluted` 或中文值。

## 认证机制
- 后端使用基于数据库 Token 的 HandlerInterceptor 认证（无 Spring Security）
- `AuthInterceptor` 拦截 `/api/**`，白名单：`/api/auth/*`、`/api/funds/*`、`/api/holdings/dividend-info`
- Token 存储在 `auth_tokens` 表，由 `DataInitializer` 在启动时初始化种子 Token `dev-token-2024`
- 拦截器通过 `authTokenRepository.findByTokenAndActiveTrue(token)` 校验 Token 有效性 + 过期时间
- 前端 `request.ts` 启动时自动调用 `GET /api/auth/token` 获取 Token 并存入 localStorage
- 401 时自动重新获取 Token 并刷新页面

## 个人总资产概览（发现页面）
`/discover` 页面是一个**个人总资产 dashboard**，汇总 5 类资产：

### 页面布局
```
[分类 Banner] （有未分类基金时显示，可关闭）
[总资产 Hero]
[持仓占比 Treemap | 资产变动卡片]
[各类资产] （美股/黄金/红利/比特币/现金/未分类卡片）
[添加现金] [添加比特币]
[品牌氛围图]
```
- "未分类"卡片始终在各类资产列表底部展示（不受 Banner 关闭影响）
- 点击未分类持仓项弹出分类弹窗，可设置 `assetCategory` 为 `us_stock` / `gold` / `dividend`

### 资产类别
| 类别 | 类型值 | 数据来源 | 操作方式 |
|------|--------|---------|---------|
| 现金 | `cash` | `manual_assets` 表 | 手动增删改查（底部按钮）|
| 比特币 | `crypto` | `manual_assets` 表 | 手动增删改查（底部按钮）|
| 美股 | `us_stock` | `holdings` 表的 `asset_category` 标记 | 映射配置 |
| 黄金 | `gold` | `holdings` 表的 `asset_category` 标记 | 映射配置 |
| 红利 | `dividend` | `holdings` 表的 `asset_category` 标记 | 映射配置 |

### 数据库新增表
| 表名 | 说明 |
|------|------|
| `manual_assets` | 手动资产（BTC/现金）：id, name, type(crypto/cash), amount, currency, note, created_at, updated_at |
| `asset_snapshots` | 每日总资产快照：date, total_value, cash_value, crypto_value, us_stock_value, gold_value, dividend_value, breakdown_json |

### 修改的表
| 表名 | 变更 |
|------|------|
| `holdings` | 新增 `asset_category` 字段（varchar(20)），值：`us_stock` / `gold` / `dividend` / null |

### API 端点
| 端点 | 方法 | 说明 |
|------|------|------|
| `GET /api/asset-overview` | GET | 总资产概览 |
| `GET /api/asset-overview/history?range=week|month` | GET | 历史快照数据 |
| `POST /api/asset-overview/snapshot` | POST | 生成今日快照 |
| `GET /api/manual-assets` | GET | 手动资产列表 |
| `POST /api/manual-assets` | POST | 新增手动资产 |
| `PUT /api/manual-assets/{id}` | PUT | 编辑手动资产 |
| `DELETE /api/manual-assets/{id}` | DELETE | 删除手动资产 |
| `PUT /api/holdings/{id}/category` | PUT | 设置持仓分类映射（`UpdateHoldingCategoryReq.assetCategory` 允许空字符串）|

### 添加标的页面（`HoldingAddPage.vue`）
- 已移除：市场类型按钮（A股/ETF/基金/港股/美股/自定义）
- 已移除：Step 2 的"资产分类"选择器
- 分类功能统一在发现页面、持仓详情页编辑弹窗中进行

## 前后端交互
- 响应格式：统一 `ApiResponse<T>`，业务成功 `json.code === 200`，业务异常 `json.code !== 200` + `json.message`
- 异常处理：`BusinessException` 返回 HTTP 200 + 业务 code，由前端根据 code 判断
