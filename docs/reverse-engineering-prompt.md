# 项目反向推导提示词 — 「种树」稳健收息管理

> 本文档从现有项目代码逆向推导，用于指导项目的重构/重写。
> 
> **用途分工：**
> - **Google AI (Gemini / NotebookLM)** → 消费 **Layer 4（UI 设计）+ Layer 5（技术约束）** 进行前端 UI 设计
> - **DeepSeek** → 消费 **Layer 0–3（项目宪法/数据模型/API/业务逻辑）+ Layer 5** 进行后端设计
> 
> **结构：** Layer 0–5 分层递进，每层可独立喂给 AI。

---

# Layer 0 — 项目宪法

## 0.1 项目身份

```yaml
project:
  name: "种树 — 稳健收息管理"
  name_en: "Fund Dividend Tracker (stitch)"
  version: "1.0.0"
  description: |
    一款面向个人投资者的持仓管理与分红追踪工具。
    核心理念：通过定期定额投资（DCA）建立分红资产组合，
    追踪每笔分红事件，计算分红覆盖率，实现"被动收入覆盖生活支出"的财务目标。
  target_user:
    - 有定投习惯的个人投资者
    - 关注股息/分红的长期投资者
    - 追求被动收入覆盖支出的 FIRE 人群
  platforms:
    - Mobile Web (PWA, viewport-fit=cover)
    - 未来可扩展为 React Native / Flutter App
```

## 0.2 核心价值主张

| 价值点 | 说明 |
|--------|------|
| **分红追踪** | 自动抓取基金分红数据，生成分红日历和预测 |
| **DCA 定投** | 管理多个定投计划，自动执行，统计投入产出 |
| **分红覆盖率** | 将预测分红 vs 生活支出可视化，里程碑激励 |
| **资产全貌** | 多维度资产概览（美股/黄金/红利/现金/加密货币） |
| **成本算法** | 支持三种成本计算方式（分红摊薄/摊薄成本/加权平均） |

## 0.3 非功能需求

```yaml
non_functional:
  performance:
    - 页面首屏加载 < 2s (mobile 4G)
    - API 响应 < 500ms (P95)
    - 分红爬虫支持批量处理，间隔防封
  reliability:
    - 定时任务（DCA 执行、分红爬取）保证幂等性
    - 汇率 API 失败时降级为硬编码默认值
    - 分红数据优先本地库，降级为实时爬取
  security:
    - Bearer Token 认证
    - CORS 允许所有来源（开发阶段，生产需收紧）
  deploy:
    - 前端静态资源 + 后端 JAR 包
    - 支持 ngrok 内网穿透
    - 数据库 MySQL 8.x
```

## 0.4 不变与可变

```yaml
immutable:  # 重构时必须保留
  - 核心业务算法：三种成本计算、分红预测、覆盖率计算、DCA 执行逻辑
  - 数据模型语义：所有实体的字段含义和关系
  - 外部数据源：天天基金（东方财富）分红/净值数据
  - 用户认证方式：Bearer Token

optimizable:  # 可以改进
  - 代码架构：可引入 DDD 分层、Service 拆分
  - 前端状态管理：可从 localStorage 升级为 Pinia
  - 前端组件复用：可提取更多通用组件
  - API 设计：可统一响应格式、添加分页规范
  - 错误处理：可引入全局错误边界

replaceable:  # 可以替换
  - 前端框架：Vue3 → React / Svelte / Next.js
  - UI 库方案：TailwindCSS → 任意 UI 框架
  - HTTP 客户端：原生 fetch → Axios / TanStack Query
  - 后端框架：Spring Boot → Go / Node.js / Python FastAPI
  - ORM：JPA → MyBatis / Prisma / SQLAlchemy
  - 数据库：MySQL → PostgreSQL / SQLite
```

---

# Layer 1 — 数据模型

## 1.1 ER 图（文字描述）

```
UserProfile (1) ─── (1) UserSettings
     │
     │ (无 FK，逻辑关联)
     │
Holding ──< Transaction (holdingId)
  │
  ├──< DividendEvent (holdingId)
  ├──< DcaPlan (holdingId)
  │
FundDividendRecord (独立，fundCode 关联 Holding.code)
FundNavRecord (独立，fundCode 关联 Holding.code)
ExchangeRate (独立)
LiveExpense (独立)
ManualAsset (独立)
AssetSnapshot (独立，每日快照)
CoverageCategory (独立)
AuthToken (独立)
```

## 1.2 实体详细定义

### 1.2.1 Holding（持仓）— 核心实体

```yaml
table: holdings
description: 用户持有的投资标的，是系统的核心聚合根

fields:
  id: { type: UUID, pk: true }
  name: { type: String, desc: "持仓名称，如「沪深300ETF」" }
  code: { type: String, desc: "基金代码，如「510300」" }
  type: 
    type: enum
    values: [fund, cny_asset, ETF, A股, 港股, 美股, 自定义]
    desc: "持仓类型，决定数据抓取策略和市场推断"
  assetCategory:
    type: enum
    values: [us_stock, gold, dividend, null]
    desc: "资产大类，用于资产概览分类统计"
  costAlgorithm:
    type: enum
    values: [diluted, diluted_only, weighted_avg]
    desc: |
      成本计算算法：
      - diluted: (总买入-总卖出-总分红)/当前份额
      - diluted_only: (总买入-总卖出)/当前份额
      - weighted_avg: 总买入金额/总买入份额
  shares: { type: BigDecimal(18,4), desc: "当前持有份额" }
  costPerShare: { type: BigDecimal(18,4), desc: "每股成本（根据 costAlgorithm 计算）" }
  cost: { type: BigDecimal(18,2), desc: "总成本 = costPerShare × shares" }
  marketValue: { type: BigDecimal(18,2), desc: "当前市值 = latestPrice × shares" }
  latestPrice: { type: BigDecimal(18,4), desc: "最新净值/价格" }
  priceDate: { type: LocalDate, desc: "价格日期" }
  predictedDividend: { type: BigDecimal(18,2), desc: "预测年分红总额" }
  dividendRate: { type: BigDecimal(10,4), desc: "成本息率 = 预测每股分红/每股成本" }
  priceDividendRate: { type: BigDecimal(10,4), desc: "股价息率 = 预测每股分红/最新价" }
  totalDividendReceived: { type: BigDecimal(18,2), desc: "累计已收分红" }
  netInvestment: { type: BigDecimal(18,2), desc: "净投入" }
  dividendRecoveryRate: { type: BigDecimal(10,2), desc: "分红回本进度 %" }
  estimatedRecoveryYears: { type: BigDecimal(10,2), desc: "预计回本年限" }
  color: { type: String, desc: "前端展示颜色" }
  deleted: { type: boolean, desc: "软删除标记" }
```

### 1.2.2 Transaction（交易记录）

```yaml
table: transactions
fields:
  id: { type: UUID, pk: true }
  holdingId: { type: FK→holdings }
  type: { type: enum, values: [buy, sell, bonus_share, reinvest], desc: "买入/卖出/送股/分红复投" }
  date: { type: LocalDate, desc: "交易日期" }
  quantity: { type: BigDecimal(18,4), desc: "交易数量（买入为正，卖出为负）" }
  price: { type: BigDecimal(18,4), desc: "交易单价" }
  fee: { type: BigDecimal(18,2), desc: "手续费" }
  total: { type: BigDecimal(18,2), desc: "交易总金额（含手续费）" }
  source: { type: String, values: [manual, dca], desc: "来源：手动录入/DCA自动执行" }
  dcaPlanId: { type: FK→dca_plans, nullable: true, desc: "关联的定投计划" }
```

### 1.2.3 DividendEvent（分红事件）

```yaml
table: dividend_events
fields:
  id: { type: UUID, pk: true }
  holdingId: { type: FK→holdings }
  holdingName: { type: String, desc: "冗余字段，便于展示" }
  type:
    type: enum
    values: [registration, ex_dividend, payout, announcement]
    desc: "登记日/除权日/发放日/公告日"
  date: { type: LocalDate, desc: "事件日期" }
  amount: { type: BigDecimal(18,2), desc: "分红金额" }
  status:
    type: enum
    values: [pending, distributed, cancelled]
    desc: "待处理/已到账/已取消"
  description: { type: String(500), desc: "事件描述" }
  
unique_constraint: (holdingId, type, date)  # 同一持仓同一类型同一天唯一
```

### 1.2.4 DcaPlan（定投计划）

```yaml
table: dca_plans
fields:
  id: { type: UUID, pk: true }
  holdingId: { type: FK→holdings }
  amount: { type: BigDecimal(18,2), desc: "每期定投金额" }
  frequency:
    type: enum
    values: [daily, weekly, biweekly, monthly]
    desc: "执行频率"
  day: { type: Integer, desc: "月频率：1-31；周频率：1(周一)-7(周日)" }
  tradingMarket: { type: String, values: [china, us, crypto], desc: "交易市场" }
  status: { type: String, values: [active, paused, ended], desc: "计划状态" }
  totalInvested: { type: BigDecimal(18,2), desc: "累计投入金额" }
  totalShares: { type: BigDecimal(18,4), desc: "累计获得份额" }
  totalExecutions: { type: Integer, desc: "累计执行次数" }
  nextExecutionDate: { type: LocalDate, desc: "下次执行日" }
  lastExecutedAt: { type: LocalDateTime, desc: "上次执行时间" }
  startedAt: { type: LocalDate, desc: "计划开始日期" }
  endedAt: { type: LocalDate, nullable: true, desc: "计划结束日期" }
```

### 1.2.5 FundDividendRecord（基金分红记录）

```yaml
table: fund_dividend_records
description: 从天天基金爬取的分红历史数据
fields:
  id: { type: String(36), pk: true }
  fundCode: { type: String(10), desc: "基金代码" }
  exDate: { type: LocalDate, desc: "除权日" }
  regDate: { type: LocalDate, desc: "登记日" }
  payDate: { type: LocalDate, desc: "发放日" }
  dividendPerShare: { type: BigDecimal(10,4), desc: "每份分红金额" }
  dividendYear: { type: Integer, desc: "分红所属年份" }
  source: { type: String, default: "scrape" }

unique_constraint: (fundCode, exDate)
```

### 1.2.6 FundNavRecord（基金净值记录）

```yaml
table: fund_nav_records
description: 从天天基金爬取的净值历史数据
fields:
  id: { type: String(36), pk: true }
  fundCode: { type: String(10) }
  navDate: { type: LocalDate, desc: "净值日期" }
  unitNav: { type: BigDecimal(10,4), desc: "单位净值" }
  accumulatedNav: { type: BigDecimal(10,4), desc: "累计净值" }
  source: { type: String, default: "scrape" }

unique_constraint: (fundCode, navDate)
```

### 1.2.7 LiveExpense（生活支出）

```yaml
table: live_expenses
fields:
  id: { type: UUID, pk: true }
  name: { type: String, desc: "支出名称" }
  icon: { type: String(50), desc: "图标标识" }
  monthlyAmount: { type: BigDecimal(10,2), desc: "月度金额" }
  sortOrder: { type: Integer, desc: "排序序号" }
  deleted: { type: boolean, default: false }
```

### 1.2.8 AssetSnapshot（资产快照）

```yaml
table: asset_snapshots
description: 每日资产快照，用于计算资产变化趋势
fields:
  id: { type: UUID, pk: true }
  date: { type: LocalDate, unique: true }
  totalValue: { type: BigDecimal(18,2), desc: "总资产" }
  cashValue: { type: BigDecimal(18,2), desc: "现金类" }
  cryptoValue: { type: BigDecimal(18,2), desc: "加密货币" }
  usStockValue: { type: BigDecimal(18,2), desc: "美股" }
  goldValue: { type: BigDecimal(18,2), desc: "黄金" }
  dividendValue: { type: BigDecimal(18,2), desc: "红利类" }
  breakdownJson: { type: TEXT, desc: "持仓明细 JSON" }
```

### 1.2.9 其他实体

```yaml
ExchangeRate:
  table: exchange_rates
  fields: { pair: String(unique), label: String, rate: BigDecimal(10,6), updatedAt: LocalDateTime }
  pairs: ["USD/CNY", "HKD/CNY"]

ManualAsset:
  table: manual_assets
  fields: { name: String(50), type: [crypto, cash], amount: BigDecimal(18,2), currency: [CNY, USD], note: String(200) }

UserProfile:
  table: user_profiles
  fields: { name: String, avatar: String, membership: [pro, free], membershipExpiry: LocalDate, phone: String }

UserSettings:
  table: user_settings
  fields: { currency: String(default:CNY), currencyLabel: String, forecastHorizon: [_1y, _3y, _5y, custom], customForecastValue: BigDecimal, notificationsEnabled: boolean }

CoverageCategory:
  table: coverage_categories
  fields: { name: String, icon: String, percentage: BigDecimal(5,2), color: String(10) }

AuthToken:
  table: auth_tokens
  fields: { token: String(unique), createdAt: LocalDateTime, expiresAt: LocalDateTime, active: boolean }
```

---

# Layer 2 — API 契约

## 2.1 通用规范

```yaml
api_conventions:
  base_url: "/api"
  auth: "Bearer Token (Header: Authorization: Bearer {token})"
  content_type: "application/json"
  response_wrapper:
    success: { code: 200, data: T }
    error: { code: error_code, message: string }
  pagination:
    params: { page: int(default:0), size: int(default:20) }
    response: { content: T[], totalElements: long, totalPages: int, number: int }
  error_codes:
    401: "未认证/Token过期 → 自动重新获取Token"
    400: "参数校验失败"
    200+业务码: "业务异常"
    500: "服务器内部错误"
```

## 2.2 完整 API 端点清单（48 个）

### Auth
| # | 方法 | 路径 | 说明 | 请求体 | 响应 |
|---|------|------|------|--------|------|
| 1 | GET | `/api/auth/token` | 获取/刷新 Token | - | `{ token }` |

### Dashboard
| # | 方法 | 路径 | 说明 | 请求体 | 响应 |
|---|------|------|------|--------|------|
| 2 | GET | `/api/dashboard` | 仪表盘核心指标 | - | `DashboardDTO` |

**DashboardDTO 字段：**
```yaml
predictedAnnualDividend: "预测年分红总额"
predictedMonthlyDividend: "预测月均分红"
coveredExpenseCount: "已覆盖支出数"
totalDividendReceived: "累计已收分红"
totalCost: "总投入成本"
totalMarketValue: "总市值"
avgCostDividendRate: "平均成本息率"
avgPriceDividendRate: "平均股价息率"
consecutiveDividendDays: "连续收息天数"
tenYearProjection: "10年预期收益"
```

### Holdings（持仓）
| # | 方法 | 路径 | 说明 | 请求体/参数 | 响应 |
|---|------|------|------|-------------|------|
| 3 | GET | `/api/holdings` | 持仓列表 | `?type=&keyword=` | `HoldingDTO[]` |
| 4 | GET | `/api/holdings/{id}` | 持仓详情 | - | `HoldingDTO` |
| 5 | POST | `/api/holdings` | 创建持仓 | `CreateHoldingReq` | `HoldingDTO` |
| 6 | PUT | `/api/holdings/{id}` | 更新持仓 | `UpdateHoldingReq` | `HoldingDTO` |
| 7 | DELETE | `/api/holdings/{id}` | 删除持仓 | - | `{ success }` |
| 8 | GET | `/api/holdings/{id}/forecast` | 分红预测 | `?period=12m|5y` | `ForecastResp` |
| 9 | GET | `/api/holdings/dividend-info` | 分红信息查询 | `?code=&type=&method=&horizon=` | `DividendInfoDTO` |
| 10 | PUT | `/api/holdings/{id}/category` | 更新资产分类 | `{ assetCategory }` | `HoldingDTO` |
| 11 | GET | `/api/holdings/search` | 搜索基金 | `?keyword=` | `HoldingSearchResult[]` |

**CreateHoldingReq 字段：**
```yaml
name: string
code: string
type: HoldingType (fund/cny_asset/ETF/A股/港股/美股/自定义)
shares: number
costPerShare: number
costAlgorithm: CostAlgorithm
date: LocalDate
fee: number
dividendMethod: ex_date|report_period
forecastHorizon: _1y|_3y|_5y|custom
customForecastValue: number
allowNegativeCost: boolean
```

### Transactions（交易）
| # | 方法 | 路径 | 说明 | 请求体/参数 | 响应 |
|---|------|------|------|-------------|------|
| 12 | GET | `/api/transactions` | 交易列表 | `?holdingId=` | `TransactionDTO[]` |
| 13 | POST | `/api/transactions` | 创建交易 | `CreateTransactionReq` | `TransactionDTO` |
| 14 | PUT | `/api/transactions/{id}` | 更新交易 | `UpdateTransactionReq` | `TransactionDTO` |
| 15 | DELETE | `/api/transactions/{id}` | 删除交易 | - | `{ success }` |

### Events（分红事件）
| # | 方法 | 路径 | 说明 | 请求体/参数 | 响应 |
|---|------|------|------|-------------|------|
| 16 | GET | `/api/events` | 事件列表 | `?holdingId=&month=&dateFrom=&dateTo=&type=&status=` | `DividendEventDTO[]` |
| 17 | GET | `/api/events/date/{date}` | 按日期查事件 | - | `DividendEventDTO[]` |
| 18 | POST | `/api/events` | 创建事件 | `CreateEventReq` | `DividendEventDTO` |
| 19 | PUT | `/api/events/{id}/distribute` | 标记已到账 | - | `DividendEventDTO` |
| 20 | PUT | `/api/events/{id}/cancel` | 取消事件 | - | `CancelEventResp` |
| 21 | POST | `/api/events/sync/{fundCode}` | 同步单基金事件 | - | `{ fundCode, created }` |
| 22 | POST | `/api/events/sync-all` | 全量同步事件 | - | `{ totalCreated }` |
| 23 | GET | `/api/dividend-records` | 分红记录查询 | `?holdingId=&year=&status=` | `DividendEventDTO[]` |

### Funds（基金数据）
| # | 方法 | 路径 | 说明 | 请求体/参数 | 响应 |
|---|------|------|------|-------------|------|
| 24 | POST | `/api/funds/{code}/dividends/refresh` | 刷新单基金分红 | - | `RefreshResult` |
| 25 | POST | `/api/funds/dividends/refresh-all` | 刷新全部分红 | - | `RefreshAllResult` |
| 26 | GET | `/api/funds/{code}/dividends` | 查询分红记录 | - | `FundDividendRecord[]` |

### DCA Plans（定投计划）
| # | 方法 | 路径 | 说明 | 请求体/参数 | 响应 |
|---|------|------|------|-------------|------|
| 27 | GET | `/api/dca-plans/budget` | 定投预算 | `?year=&month=` | `DcaBudgetVO` |
| 28 | GET | `/api/dca-plans` | 计划列表 | `?holdingId=` | `DcaPlanVO[]` |
| 29 | GET | `/api/dca-plans/{id}` | 计划详情 | - | `DcaPlanVO` |
| 30 | POST | `/api/dca-plans` | 创建计划 | `CreateDcaPlanReq` | `DcaPlanVO` |
| 31 | PUT | `/api/dca-plans/{id}` | 更新计划 | `UpdateDcaPlanReq` | `DcaPlanVO` |
| 32 | DELETE | `/api/dca-plans/{id}` | 删除计划 | - | `{ success }` |
| 33 | POST | `/api/dca-plans/{id}/execute` | 手动执行一期 | - | `DcaExecutionResultVO` |

### Expenses（支出管理）
| # | 方法 | 路径 | 说明 | 请求体/参数 | 响应 |
|---|------|------|------|-------------|------|
| 34 | GET | `/api/expenses` | 支出列表 | - | `LiveExpenseDTO[]` |
| 35 | POST | `/api/expenses` | 创建支出 | `CreateExpenseReq` | `LiveExpenseDTO` |
| 36 | PUT | `/api/expenses/{id}` | 更新支出 | `UpdateExpenseReq` | `LiveExpenseDTO` |
| 37 | DELETE | `/api/expenses/{id}` | 删除支出 | - | void |
| 38 | GET | `/api/expenses/coverage` | 分红覆盖率 | - | `CoverageDTO` |

### Asset Overview（资产概览）
| # | 方法 | 路径 | 说明 | 请求体/参数 | 响应 |
|---|------|------|------|-------------|------|
| 39 | GET | `/api/asset-overview` | 资产概览 | - | `AssetOverviewDTO` |
| 40 | GET | `/api/asset-overview/history` | 历史曲线 | `?range=week|month` | `AssetHistoryDTO[]` |
| 41 | POST | `/api/asset-overview/snapshot` | 生成快照 | - | void |

### Exchange Rates（汇率）
| # | 方法 | 路径 | 说明 | 请求体/参数 | 响应 |
|---|------|------|------|-------------|------|
| 42 | GET | `/api/exchange-rates` | 汇率列表 | - | `ExchangeRateDTO[]` |
| 43 | POST | `/api/exchange-rates/refresh` | 刷新汇率 | - | `RefreshRatesResp` |

### Insights（洞察）
| # | 方法 | 路径 | 说明 | 请求体/参数 | 响应 |
|---|------|------|------|-------------|------|
| 44 | GET | `/api/insights/monthly` | 月洞察 | `?year=&month=` | `MonthlyInsightResp` |
| 45 | GET | `/api/insights/monthly-detail` | 月明细 | `?year=&month=` | `MonthlyDetailResp` |
| 46 | GET | `/api/insights/annual` | 年洞察 | `?year=` | `AnnualInsightResp` |

### User（用户）
| # | 方法 | 路径 | 说明 | 请求体/参数 | 响应 |
|---|------|------|------|-------------|------|
| 47 | GET/PUT | `/api/user/profile` | 用户资料 | `UpdateUserProfileReq` | `UserProfileDTO` |
| 48 | GET/PUT | `/api/user/settings` | 用户设置 | `UpdateUserSettingsReq` | `UserSettingsDTO` |

### Manual Assets（手动资产）
| # | 方法 | 路径 | 说明 | 请求体/参数 | 响应 |
|---|------|------|------|-------------|------|
| 49 | GET | `/api/manual-assets` | 手动资产列表 | - | `ManualAssetDTO[]` |
| 50 | GET/POST/PUT/DELETE | `/api/manual-assets[/{id}]` | CRUD | 对应 Req | 对应 DTO |

### Coverage Categories
| # | 方法 | 路径 | 说明 | 请求体/参数 | 响应 |
|---|------|------|------|-------------|------|
| 51 | GET | `/api/coverage-categories` | 覆盖类目列表 | - | `CoverageCategoryDTO[]` |
| 52 | PUT | `/api/coverage-categories/{id}` | 更新类目 | `UpdateCoverageCategoryReq` | `CoverageCategoryDTO` |

### Value Change
| # | 方法 | 路径 | 说明 | 请求体/参数 | 响应 |
|---|------|------|------|-------------|------|
| 53 | GET | `/api/holdings/value-change` | 持仓价值变化 | - | `ValueChangeDTO` |

---

# Layer 3 — 业务逻辑

## 3.1 成本计算（三种算法）

```
算法 1 — diluted (分红摊薄)：
  costPerShare = (总买入金额 - 总卖出收入 - 累计已收分红) / 当前份额
  适用场景：将分红视为成本回收，适合红利策略投资者

算法 2 — diluted_only (摊薄成本)：
  costPerShare = (总买入金额 - 总卖出收入) / 当前份额
  适用场景：不考虑分红影响的纯成本计算

算法 3 — weighted_avg (加权平均)：
  costPerShare = 总买入金额 / 总买入份额
  适用场景：传统加权平均成本法

核心指标计算：
  netInvestment = 总买入金额 - 总卖出收入 - 累计已收分红
  dividendRate = predictedDividendPerShare / costPerShare   （成本息率）
  priceDividendRate = predictedDividendPerShare / latestPrice  （股价息率）
  recoveryRate = totalDividendReceived / netInvestment × 100%  （回本进度）
  recoveryYears = (netInvestment - totalDividendReceived) / predictedAnnualDividend  （回本年限）
  marketValue = latestPrice × shares
```

## 3.2 DCA 定投执行逻辑

```
createPlan 流程:
  1. 根据 holding.type 推断 tradingMarket (美股→us, 其他→china)
  2. 根据 frequency 和 day 计算 nextExecutionDate
  3. 顺延 nextExecutionDate 到最近交易日
  4. 保存计划，状态=active

executePlan 流程:
  1. 获取持仓最新净值 (latestPrice)
  2. 份额 = amount / latestPrice
  3. 创建买入 Transaction (source="dca", type=buy)
  4. 更新计划统计: totalInvested += amount, totalShares += 份额, totalExecutions += 1
  5. 根据 frequency 推进 nextExecutionDate
  6. 顺延到交易日

budget 计算:
  根据频率估算月执行次数:
    daily → 当月交易日数
    weekly → 当月该星期几落在交易日的次数
    biweekly → 2
    monthly → 1
  月预算 = Σ(plan.amount × 月执行次数)

定时任务 (cron: 0 0 20 * * ?):
  每天20:00 查询 status=active 且 nextExecutionDate ≤ today 的计划
  跳过非交易日
  逐个执行 executePlan
```

## 3.3 分红数据抓取与计算

```
数据源: 天天基金 (fund.eastmoney.com)

爬取流程:
  1. fundf10.eastmoney.com/fhsp_{code}.html → Jsoup 解析 HTML 表格
  2. 提取每行：除权日/登记日/发放日/每份分红
  3. 增量保存：已存在的(fundCode, exDate)跳过
  4. 批量抓取间隔 500ms 防封

频率识别 (identifyFrequency):
  1. 取最近 N 条记录的 exDate 列表
  2. 计算相邻日期间隔天数
  3. 分类: 25-35天→月度, 80-100天→季度, 330-400天→年度, 其他→不定期
  4. 不定期按 (总记录数-1)/(最新日期-最早日期)×365 估算年次数

年均分红计算:
  方法1 (ex_date): 
    取最近 1y/3y/5y 记录 → 单次均值 × 年频率
  方法2 (report_period):
    解析累计净值变化 → (ACWorthTrend差值)/份额 = 年度分红
  方法3 (localDB):
    直接从 fund_dividend_records 表计算

预测总分红:
  predictedDividend = 年均每份分红 × 持有份额

净值抓取:
  fund.eastmoney.com/pingzhongdata/{code}.js → 正则解析 Data_netWorthTrend
  保存最近5年净值记录
```

## 3.4 分红覆盖率计算

```
算法:
  1. 获取所有 active 支出项，按月金额从小到大排序
  2. 累计可用分红 = predictedAnnualDividend (所有持仓预测年分红之和)
  3. 从最小支出开始逐项覆盖:
     if 累计可用分红 ≥ 支出月金额 × 12:
       标记为"已覆盖"，累计可用分红 -= 支出月金额 × 12
     elif 之前已覆盖至少一项:
       标记为"进行中"
     else:
       标记为"未覆盖"
  4. 里程碑判定:
     已覆盖 1 项 → 初出茅庐
     已覆盖 2 项 → 小有所成
     已覆盖 3 项 → 渐入佳境
     已覆盖 4 项 → 收益达人
     全部覆盖   → 财务自由
```

## 3.5 交易日历

```
数据源: holidays.json (china 27天, us 9天)
规则:
  - crypto: 365天均为交易日
  - china: 排除周六日 + 中国节假日
  - us: 排除周六日 + 美国节假日
功能:
  - isTradingDay(date, market)
  - nextTradingDay(date, market)
  - previousTradingDay(date, market)
  - countTradingDaysInMonth(year, month, market)
```

## 3.6 资产概览计算

```
分类体系:
  cash → ManualAsset(type=cash)
  crypto → ManualAsset(type=crypto)
  us_stock → Holding(assetCategory=us_stock)
  gold → Holding(assetCategory=gold)
  dividend → Holding(assetCategory=dividend)

变化计算:
  对比最新 AssetSnapshot 快照:
    weekChange = (当前值 - 7天前快照值) / 7天前快照值
    monthChange = (当前值 - 30天前快照值) / 30天前快照值

每日快照:
  每天首次访问 DiscoverPage 时自动生成
  去重逻辑: 同一天只保存一份
```

## 3.7 汇率获取

```
数据源: Frankfurter API (api.frankfurter.app/latest)
支持货币对: USD/CNY, HKD/CNY
限流: 30秒内不可重复刷新
降级: API 失败使用硬编码默认值 (USD=7.25, HKD=0.93)
```

---

# Layer 4 — UI 设计规范

> **此层主要供 Google AI (Gemini) 进行 UI 设计时使用**

## 4.1 设计系统

```yaml
design_system:
  name: "Material Design 3 (Material You)"
  target: "Mobile-first, max-width 600px"
  
  typography:
    font_family:
      headline: "Plus Jakarta Sans"
      body: "Work Sans"
      icons: "Material Symbols Outlined"
    scale:
      headline_lg: { size: 24px, weight: 500 }
      headline_md: { size: 20px, weight: 500 }
      title: { size: 16px, weight: 500 }
      body_lg: { size: 16px, weight: 400 }
      body_md: { size: 14px, weight: 400 }
      body_sm: { size: 13px, weight: 400 }
      caption: { size: 12px, weight: 400 }
  
  colors:
    primary: "#6750A4" (紫)
    on_primary: "#FFFFFF"
    primary_container: "#EADDFF"
    secondary: "#625B71"
    tertiary: "#7D5260"
    error: "#B3261E"
    surface: "#FFFBFE"
    surface_variant: "#E7E0EC"
    outline: "#79747E"
    # 语义色
    success: "#2E7D32"
    warning: "#ED6C02"
    # 涨跌 (中国股市约定: 涨红跌绿)
    up: "#D32F2F" (红色)
    down: "#2E7D32" (绿色)
  
  spacing:
    gutter: 16px
    sm: 8px
    md: 16px
    lg: 24px
    xl: 32px
  
  border_radius:
    default: 4px
    lg: 8px
    xl: 12px
    full: 9999px
  
  components:
    card:
      background: surface
      border_radius: xl (12px)
      shadow: "0 1px 3px rgba(0,0,0,0.1)"
      padding: md (16px)
    button:
      primary: { background: primary, text: on_primary, radius: full, height: 48px }
      secondary: { background: transparent, border: outline, radius: full }
    fab:
      position: "fixed bottom-24 right-4"
      size: 56px
      radius: 16px
      shadow: "0 4px 12px rgba(0,0,0,0.2)"
    bottom_nav:
      height: 64px
      items: 4 (持仓/日历/发现/我的)
      active: primary 色填充图标 + 文字
      inactive: outline 色轮廓图标 + 文字
    bottomsheet:
      radius: "top-xl (16px)"
      max_height: "80vh"
      backdrop: "rgba(0,0,0,0.4)"
    chip:
      radius: full
      padding: "4px 12px"
      height: 32px
```

## 4.2 页面结构（13 个页面）

### 页面 1：首页·持仓看板 `/`
```
┌──────────────────────────────┐
│  AppHeader (title: "种树")    │
├──────────────────────────────┤
│  Hero 卡片（连续收息天数）      │
│  ┌──────────────────────┐    │
│  │  365 天              │    │
│  │  连续收息              │    │
│  │  预测年分红 ¥XX,XXX   │    │
│  │  10年预期 ¥XXX,XXX    │    │
│  └──────────────────────┘    │
│                               │
│  可展开指标区域 (最多6项)       │
│  ┌────┬────┬────┐            │
│  │总市值│总成本│总已收│ ...      │
│  └────┴────┴────┘            │
│                               │
│  分红覆盖摘要                  │
│  ┌──────────────────────┐    │
│  │ 已覆盖 3/5 项支出      │    │
│  │ ████████░░░░ 60%     │    │
│  └──────────────────────┘    │
│                               │
│  持仓列表                      │
│  ┌──────────────────────┐    │
│  │ ■ 沪深300ETF         │    │
│  │ 预测 ¥1,200/年        │    │
│  │ 市值│成本│份额│息率   │    │
│  └──────────────────────┘    │
│  ┌──────────────────────┐    │
│  │ ■ 标普500ETF         │    │
│  │ ...                  │    │
│  └──────────────────────┘    │
│                               │
│         [+ FAB 按钮]          │
├──────────────────────────────┤
│  BottomNav (持仓/日历/发现/我) │
└──────────────────────────────┘

状态: loading → ready / empty / error
交互: 
  - Hero 卡片点击 → 无
  - 持仓卡片点击 → /holding/:id
  - FAB 点击 → /holding/add
  - 覆盖摘要点击 → /coverage
```

### 页面 2：分红日历 `/calendar`
```
┌──────────────────────────────┐
│  AppHeader (title: "分红日历") │
├──────────────────────────────┤
│  月历视图                      │
│  ┌──────────────────────┐    │
│  │  2026年 6月    ◀ ▶   │    │
│  │ 日 一 二 三 四 五 六  │    │
│  │        1  2 ●3  4  5 │    │
│  │  6  7  8  9 10 11 12 │    │
│  │  ...                 │    │
│  └──────────────────────┘    │
│                               │
│  选中日期事件列表               │
│  📋 登记日 — 沪深300ETF ¥500  │
│  💰 发放日 — 标普500  $120    │
│                               │
│  月度洞察 Bento 卡片           │
│  ┌──────────┬──────────┐     │
│  │ 最丰厚来源 │ 本月动态  │     │
│  ├──────────┼──────────┤     │
│  │ 下次分红   │          │     │
│  └──────────┴──────────┘     │
│                               │
│  [年度总览] [刷新数据]         │
│                               │
├──────────────────────────────┤
│  BottomNav                    │
└──────────────────────────────┘

年度总览 Tab:
  - 12月柱状图（每月分红金额）
  - 基金排名列表
```

### 页面 3：资产概览·发现 `/discover`
```
┌──────────────────────────────┐
│  AppHeader (title: "发现")    │
├──────────────────────────────┤
│  总资产 Hero                   │
│  ¥XXX,XXX                     │
│  周 +1.2%  月 +3.5%  年 +12%  │
│                               │
│  资产配置占比条                 │
│  ████░░░░░░░░  美股 40%       │
│  ██░░░░░░░░░░  红利 20%       │
│  █░░░░░░░░░░░  黄金 10%       │
│                               │
│  分类资产卡片                   │
│  ┌──────────┐ ┌──────────┐   │
│  │ 美股      │ │ 黄金      │   │
│  │ ¥XX,XXX  │ │ ¥XX,XXX  │   │
│  │ +2.3%    │ │ -0.5%    │   │
│  └──────────┘ └──────────┘   │
│                               │
│  DCA 定投概览                  │
│  本月预算 ¥5,000               │
│  3 个计划执行中                │
│                               │
│  [手动资产] [预算详情]         │
├──────────────────────────────┤
│  BottomNav                    │
└──────────────────────────────┘

交互:
  - 分类卡片点击 → 筛选对应分类
  - 手动资产 → BottomSheet CRUD
  - 预算详情 → 预算弹窗
  - 资产变动明细 → BottomSheet
```

### 页面 4：个人中心 `/profile`
```
┌──────────────────────────────┐
│  AppHeader (title: "我的")    │
├──────────────────────────────┤
│  用户信息卡片                   │
│  ┌──────────────────────┐    │
│  │ 👤 用户名    Pro会员   │    │
│  └──────────────────────┘    │
│                               │
│  汇率展示                      │
│  USD/CNY = 7.25  [刷新]      │
│  HKD/CNY = 0.93             │
│                               │
│  功能列表                      │
│  ┌──────────────────────┐    │
│  │ 📱 手机号         >   │    │
│  │ 💰 支出设置        >   │    │
│  │ 📊 数据口径        >   │    │
│  │ 📧 联系我们        >   │    │
│  └──────────────────────┘    │
├──────────────────────────────┤
│  BottomNav                    │
└──────────────────────────────┘
```

### 页面 5：持仓详情 `/holding/:id`
```
┌──────────────────────────────┐
│  AppHeader (title: 持仓名称)   │
│            (showBack: true)   │
├──────────────────────────────┤
│  Hero 卡片                     │
│  预测年分红 ¥1,200             │
│  累计分红 ¥3,500               │
│  市值│成本│份额                │
│                               │
│  分红回本进度条                 │
│  ████████░░░░ 65% 回本       │
│  预计 2.3 年回本              │
│                               │
│  分红预测 SVG 折线图            │
│  [12个月] [5年]               │
│  ┌──────────────────────┐    │
│  │  📈 折线图            │    │
│  └──────────────────────┘    │
│                               │
│  操作网格 (2×2)               │
│  ┌────────┬────────┐        │
│  │ 交易明细│ 分红记录│        │
│  ├────────┼────────┤        │
│  │ 编辑持仓│ 删除持仓│        │
│  └────────┴────────┘        │
│                               │
│  定投计划列表                   │
│  ┌──────────────────────┐    │
│  │ 每周一 ¥500  进行中   │    │
│  │ 累计 ¥6,000 / 12期   │    │
│  └──────────────────────┘    │
│                               │
│  [+ 创建定投计划]              │
└──────────────────────────────┘
```

### 页面 6–13：二级页面

| 页面 | 路径 | 核心 UI |
|------|------|---------|
| 添加交易 | `/trade/add` | 分段控制器(买入/卖出/送股/复投) + 表单 + 预估影响 |
| 添加持仓 | `/holding/add` | 三步向导：搜索标的→填写信息→分红口径 |
| 分红覆盖 | `/coverage` | 里程碑成长路 + 支出覆盖列表 |
| 支出设置 | `/coverage/settings` | 支出列表 + 添加弹窗 |
| 指标设置 | `/metrics/settings` | Toggle 开关列表(最多6项) |
| 交易列表 | `/holding/:id/transactions` | 交易列表 + 编辑/删除 ActionSheet |
| 分红记录 | `/holding/:id/dividends` | 累计汇总 + 事件列表(状态标签) |
| 定投详情 | `/dca-plans/:id` | 摘要卡片(2×2) + 执行记录 + 操作按钮 |

## 4.3 通用组件

### AppHeader
```
固定顶栏，h-14 (56px)，z-50
滚动阴影效果: scrollY > 10px → box-shadow
Props: title, showBack?, showLogo?, rightIcon?, rightAction?
返回按钮: router.back()
```

### BottomNav
```
固定底部，h-16 (64px)，safe-bottom 适配刘海屏
4 个 Tab: 持仓(home)/日历(calendar)/发现(discover)/我的(profile)
active: primary 色填充图标 + primary 色文字
inactive: outline 色轮廓图标 + secondary 色文字
圆角顶部 12px
```

### DividendCard
```
持仓卡片组件
Props: holding, onClick
布局: 顶部彩色左边框(4px) + 名称/代码 + 预测分红 + 4列指标网格
hover: active:scale-[0.98]
```

### PageState
```
四态组件:
  loading: 旋转 spinner + "加载中..."
  empty: inbox 图标 + "暂无数据"
  error: error 图标 + 错误信息 + 重试按钮
  ready: 不渲染（显示子内容）
```

### DcaCreateSheet
```
BottomSheet 创建定投计划
表单: 金额输入 → 频率选择(4个Chip) → 扣款日选择(周:7天按钮/月:数字输入)
验证: 金额≥1, 非daily须选day
```

### DcaExecuteSheet
```
BottomSheet 执行确认
状态机: confirm → loading → success(展示金额/份额/净值/日期) → error(含重试)
```

## 4.4 交互模式

```yaml
patterns:
  navigation:
    type: "底部 Tab + Stack 导航"
    transition: "slide (水平滑动)"
    keep_alive: true  # 所有 Tab 页缓存
  
  data_fetching:
    strategy: "onMounted + onActivated 双触发"
    loading: "PageState 组件统一展示"
    error: "PageState + 重试按钮"
  
  forms:
    validation: "实时校验 + 提交时校验"
    submit: "按钮 loading 状态 + 成功 toast / 失败提示"
  
  bottomsheet:
    trigger: "点击按钮/FAB"
    dismiss: "下滑/点击遮罩/点击关闭"
    animation: "slide-up + fade backdrop"
  
  actionsheet:
    trigger: "长按/点击更多按钮"
    items: "列表选项 + 取消"
  
  pull_to_refresh:
    日历页: "手动刷新分红数据"
  
  empty_state:
    引导用户创建第一条数据
    CTA 按钮跳转创建页
```

---

# Layer 5 — 技术约束

## 5.1 前端技术约束

```yaml
frontend:
  framework: "Vue 3.4+ (Composition API + <script setup>)"
  language: "TypeScript 5.4+ (strict mode)"
  build_tool: "Vite 5.4+"
  css: "TailwindCSS 3.4+"
  router: "vue-router 4.3+ (Hash 模式)"
  state_management: "组件 ref + localStorage (可升级为 Pinia)"
  http_client: "原生 fetch (可升级为 Axios/TanStack Query)"
  
  constraints:
    - "移动端优先，最大宽度 600px"
    - "零第三方 UI 组件库，完全基于 TailwindCSS 手写"
    - "图标使用 Material Symbols 字体"
    - "无 Pinia/Vuex，状态通过 localStorage + 组件 ref 管理"
    - "KeepAlive 缓存所有 Tab 页"
    - "devServer proxy /api → http://localhost:8080"
  
  known_issues_to_fix:
    - "类型定义分散 (src/types/api.ts 和 src/api/*.ts 重复)"
    - "无全局请求 loading/error 处理"
    - "无请求缓存和去重机制"
    - "mock 数据文件未清理 (src/data/mock.ts)"
    - "部分页面组件过大 (~500行)，应拆分"
```

## 5.2 后端技术约束

```yaml
backend:
  framework: "Spring Boot 3.2+"
  language: "Java 17+"
  orm: "Spring Data JPA (Hibernate 6.3+)"
  database: "MySQL 8.x"
  build_tool: "Maven"
  auth: "Bearer Token (拦截器实现)"
  docs: "SpringDoc OpenAPI 2.3+"
  scheduler: "@EnableScheduling + @Scheduled"
  
  constraints:
    - "ddl-auto: update (自动建表，生产应改为 validate)"
    - "CORS 允许所有来源（开发阶段）"
    - "dev-token-2024 硬编码 Token（生产应改为动态生成）"
    - "汇率 API 限流 30 秒"
    - "分红爬虫间隔 500ms 防封"
    - "定时任务：DCA 执行 20:00，分红爬取 06:00"
    - "数据库密码硬编码在 application.yml（应使用环境变量）"
    - "使用 Jsoup 进行 HTML 解析"
    - "使用正则解析天天基金的 JS 变量"
  
  known_issues_to_fix:
    - "Service 职责过重 (HoldingService 含创建/更新/删除/重算/预测/分类等)"
    - "部分 Service 直接调用多个 Repository，缺少 Domain Service 抽象"
    - "Entity 使用 UUID String 而非 Long 自增 ID"
    - "数据初始化使用 CommandLineRunner（耦合度高）"
    - "全局异常处理仅 3 种类型，覆盖不全"
    - "无接口幂等性保证"
    - "无请求日志/审计"
    - "硬编码的外部 URL (fund.eastmoney.com, api.frankfurter.app)"
```

## 5.3 外部依赖

```yaml
external_services:
  - name: "天天基金 (东方财富)"
    urls:
      - "fund.eastmoney.com/pingzhongdata/{code}.js  (净值数据)"
      - "fundf10.eastmoney.com/fhsp_{code}.html  (分红数据)"
      - "fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI  (基金搜索)"
    rate_limit: "500ms 间隔"
    fallback: "本地数据库缓存"
  
  - name: "Frankfurter API"
    url: "api.frankfurter.app/latest"
    purpose: "汇率获取"
    rate_limit: "30 秒"
    fallback: "硬编码默认值 (USD=7.25, HKD=0.93)"
  
  - name: "Google Fonts"
    fonts: ["Plus Jakarta Sans", "Work Sans", "Material Symbols Outlined"]
```

---

# 附录 A — 已知问题与改进建议

## A.1 架构问题

| 问题 | 严重度 | 建议 |
|------|--------|------|
| Service 职责过重 | 高 | 拆分为 DomainService + ApplicationService |
| 无接口幂等性 | 高 | 引入分布式锁或数据库唯一约束 |
| 类型定义重复 | 中 | 统一到 types/api.ts，api/*.ts 引用 |
| 无请求缓存 | 中 | 引入 TanStack Query 或自建缓存层 |
| 硬编码配置 | 中 | 使用环境变量/配置中心 |
| Entity ID 用 UUID String | 低 | 考虑 Long 自增（性能更好）或保持 UUID（分布式友好） |

## A.2 功能缺失

| 功能 | 说明 |
|------|------|
| 多用户支持 | 当前为单用户设计，无用户注册/登录 |
| 数据导出 | 无导出为 CSV/Excel 功能 |
| 通知推送 | 无分红提醒/定投提醒推送 |
| 数据备份 | 无自动备份机制 |
| 测试覆盖 | 未见单元测试和集成测试 |
| 国际化 | 仅支持中文 |

---

# 附录 B — 使用指南

## 给 Google AI (UI 设计) 的提示词片段

```
请基于以下设计系统，重新设计「种树」应用的 UI：

[粘贴 Layer 4 — UI 设计规范 全部内容]

要求：
1. 保持 Material Design 3 风格
2. 移动端优先，最大宽度 600px
3. 输出每个页面的详细布局描述和组件规格
4. 重点优化以下页面：首页看板、分红日历、持仓详情
5. 保持底部 4 Tab 导航结构
6. 涨红色、跌绿色（中国股市约定）
```

## 给 DeepSeek (后端设计) 的提示词片段

```
请基于以下项目规格，设计「种树」应用的后端架构：

[粘贴 Layer 0 — 项目宪法]
[粘贴 Layer 1 — 数据模型]
[粘贴 Layer 2 — API 契约]
[粘贴 Layer 3 — 业务逻辑]
[粘贴 Layer 5 — 后端技术约束]

要求：
1. 保持所有业务逻辑不变（成本计算、DCA、分红爬虫、覆盖率）
2. 改进架构问题：拆分 Service、引入幂等性、统一异常处理
3. 保持 API 契约兼容
4. 输出：数据模型 DDL、API 文档、核心 Service 伪代码
5. 技术栈可以调整，但需要说明理由
```

---

> **文档版本**: v1.0
> **生成日期**: 2026-06-26
> **源项目**: https://github.com/2398207092/myDca.git
