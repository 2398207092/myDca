# 种树 · 基金追踪器

> 个人基金/ETF 投资追踪工具，自动抓取净值与分红数据，支持定投计划与现金记账。

---

## 功能总览

| 优先级 | 功能 | 状态 | 说明 |
|--------|------|------|------|
| P0 | 持仓管理 | ✅ | 添加/编辑/删除基金、ETF、股票持仓 |
| P0 | 交易记录 | ✅ | 记录买入/卖出交易，自动更新份额与成本 |
| P0 | 资产概览 | ✅ | 总资产、分类汇总、涨跌幅 |
| P0 | 净值自动同步 | ✅ | 从东方财富自动抓取基金净值 |
| P1 | 分红追踪 | ✅ | 自动抓取分红记录，生成分红日历事件 |
| P1 | 定投计划 | ✅ | 每日/每周/每月定投，自动执行 |
| P1 | 涨跌幅分析 | ✅ | 1周/1月/1年维度价值变化 |
| P1 | 现金/比特币 | ✅ | 手动资产录入，自动记账 |
| P1 | 数据导出 | ✅ | 一键备份数据库（.sql.gz） |
| P2 | 成本算法切换 | ✅ | 支持摊薄成本/加权平均三种算法 |
| P2 | 支出管理 | ✅ | 日常支出记录与覆盖率分析 |
| P2 | 洞察报表 | ✅ | 月度/年度投资报表 |

---

## 数据模型

### 核心表结构

#### Holdings（持仓）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID | 主键 |
| name | String | 持仓名称 |
| code | String | 基金代码 |
| type | Enum | fund / ETF / stock |
| shares | Decimal | 持有份额 |
| cost | Decimal | 总成本 |
| costPerShare | Decimal | 每股成本 |
| marketValue | Decimal | 市值 |
| latestPrice | Decimal | 最新净值/价格 |
| predictedDividend | Decimal | 预测年分红总额 |
| dividendRate | Decimal | 成本息率(%) |
| priceDividendRate | Decimal | 股价息率(%) |
| totalDividendReceived | Decimal | 累计已收分红 |
| netInvestment | Decimal | 净投入 |
| costAlgorithm | Enum | diluted / diluted_only / weighted_avg |
| assetCategory | String | us_stock / gold / dividend |

**关系：** 1 个持仓 → N 条交易记录

#### Transactions（交易）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID | 主键 |
| holding_id | FK → Holdings | 所属持仓 |
| type | Enum | buy / sell / reinvest / bonus_share |
| quantity | Decimal | 份额 |
| price | Decimal | 单价 |
| fee | Decimal | 手续费 |
| total | Decimal | 总金额(=数量×单价+手续费) |
| date | Date | 交易日期 |
| source | String | manual / dca |
| dca_plan_id | FK → DcaPlans | 所属定投计划 |

**关系：** N 条交易 → 1 个持仓

#### ManualAssets（现金/比特币）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID | 主键 |
| type | String | cash / crypto |
| amount | Decimal | 金额 |
| isPrimary | Boolean | 是否为主账户（自动现金记账操作此账户） |

**行为：** 买入/定投交易自动扣 cash 金额，卖出/分红到账自动加 cash 金额

#### DcaPlans（定投计划）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID | 主键 |
| holding_id | FK → Holdings | 定投标的 |
| amount | Decimal | 每期金额 |
| frequency | Enum | daily / weekly / monthly |
| status | String | active / paused |
| nextExecutionDate | Date | 下次执行日期 |
| totalInvested | Decimal | 累计投入 |
| totalShares | Decimal | 累计份额 |

**行为：** 每天 20:00 定时任务检查到期计划，自动创建买入交易

#### DividendEvents（分红事件/日历）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID | 主键 |
| holding_id | FK → Holdings | 所属持仓 |
| type | Enum | registration / ex_dividend / payout |
| date | Date | 事件日期 |
| amount | Decimal | 金额 |
| status | Enum | pending / distributed / cancelled |

**行为：** 由 `fund_dividend_records`（爬虫数据）同步生成；标记 distributed 自动增加现金余额

#### FundNavRecords（净值历史）

| 字段 | 类型 | 说明 |
|------|------|------|
| fundCode | String | 基金代码 |
| navDate | Date | 净值日期 |
| unitNav | Decimal | 单位净值 |

**来源：** 东方财富 `pingzhongdata/{code}.js` 爬取，保留近 1 年

#### FundDividendRecords（分红历史）

| 字段 | 类型 | 说明 |
|------|------|------|
| fundCode | String | 基金代码 |
| exDate | Date | 除息日 |
| dividendPerShare | Decimal | 每份分红 |

**来源：** 天天基金 `fhsp_{code}.html` 爬取，仅取"派现金"记录

### 其他辅助表

| 表名 | 说明 |
|------|------|
| `asset_snapshots` | 每日资产快照（用于趋势图） |
| `live_expenses` | 日常支出记录 |
| `coverage_categories` | 支出覆盖分类配置 |
| `exchange_rates` | 汇率（东方财富爬取） |
| `auth_tokens` | 认证令牌 |
| `user_profiles` | 用户资料 |
| `user_settings` | 用户设置 |

---

## API 接口

### 认证

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/auth/token` | 获取访问令牌 |

所有其他接口需在 Header 携带 `Authorization: Bearer <token>`。

### 持仓

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/holdings` | 持仓列表（支持 type/keyword 过滤） |
| GET | `/api/holdings/{id}` | 持仓详情（含预测分红、回本年限） |
| POST | `/api/holdings` | 创建持仓（自动触发净值/分红爬取） |
| PUT | `/api/holdings/{id}` | 更新持仓 |
| DELETE | `/api/holdings/{id}` | 删除持仓（级联删除交易） |
| GET | `/api/holdings/{id}/forecast` | 分红预测详情 |
| GET | `/api/holdings/dividend-info` | 添加持仓时查询分红数据 |
| PUT | `/api/holdings/{id}/category` | 设置持仓资产分类 |
| GET | `/api/holdings/search` | 搜索基金 |

### 交易

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/transactions` | 交易列表（支持 holdingId 过滤） |
| POST | `/api/transactions` | 创建交易 → 自动更新份额/成本/现金 |
| PUT | `/api/transactions/{id}` | 更新交易 |
| DELETE | `/api/transactions/{id}` | 删除交易 → 反向恢复份额/现金 |

### 资产概览

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/asset-overview` | 总资产、分类占比 |
| GET | `/api/asset-overview/history` | 资产走势（日维度） |
| POST | `/api/asset-overview/snapshot` | 手动生成今日快照 |

### 涨跌幅

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/holdings/value-change` | 1周/1月/1年维度各持仓涨跌幅 |

### 分红

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/funds/{code}/dividends` | 基金历史分红记录 |
| POST | `/api/funds/{code}/dividends/refresh` | 手动刷新该基金分红 |
| POST | `/api/funds/dividends/refresh-all` | 刷新所有持仓分红 |
| GET | `/api/dividend-records` | 已到账分红列表 |
| GET | `/api/events` | 分红日历事件列表 |
| GET | `/api/events/date/{date}` | 按日期查分红事件 |
| POST | `/api/events` | 手动创建分红事件 |
| PUT | `/api/events/{id}/distribute` | 标记分红到账 → 自动加现金 |
| PUT | `/api/events/{id}/cancel` | 取消分红事件 |
| POST | `/api/events/sync/{fundCode}` | 同步某基金的分红事件 |
| POST | `/api/events/sync-all` | 同步所有分红事件 |

### 定投

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/dca-plans` | 定投计划列表 |
| GET | `/api/dca-plans/{id}` | 定投计划详情 |
| POST | `/api/dca-plans` | 创建定投计划 |
| PUT | `/api/dca-plans/{id}` | 更新定投计划 |
| DELETE | `/api/dca-plans/{id}` | 删除定投计划 |
| POST | `/api/dca-plans/{id}/execute` | 手动执行一次定投 |
| GET | `/api/dca-plans/budget` | 定投预算概览 |

### 现金/手动资产

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/manual-assets` | 资产列表 |
| GET | `/api/manual-assets/{id}` | 资产详情 |
| POST | `/api/manual-assets` | 创建资产（首个现金自动标记为主账户） |
| PUT | `/api/manual-assets/{id}` | 更新资产 |
| DELETE | `/api/manual-assets/{id}` | 删除资产 |

### 其他

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/dashboard` | 首页看板数据 |
| GET | `/api/user/profile` | 用户资料 |
| GET | `/api/user/settings` | 用户设置 |
| GET | `/api/exchange-rates` | 汇率列表 |
| POST | `/api/exchange-rates/refresh` | 刷新汇率 |
| GET | `/api/expenses` | 支出列表 |
| GET | `/api/expenses/coverage` | 支出覆盖率 |
| GET | `/api/coverage-categories` | 覆盖分类列表 |
| GET | `/api/insights/monthly` | 月度洞察 |
| GET | `/api/insights/annual` | 年度洞察 |
| GET | `/api/admin/db/backup` | 一键备份数据库 |

---

## 前端页面

| 路由 | 页面文件 | 说明 |
|------|---------|------|
| `/` | `HomePage.vue` | 首页仪表盘 |
| `/discover` | `DiscoverPage.vue` | 资产概览页面 |
| `/holdings/:id` | `HoldingDetailPage.vue` | 持仓详情 |
| `/holdings/add` | `HoldingAddPage.vue` | 添加持仓 |
| `/transactions` | `TransactionListPage.vue` | 交易明细列表 |
| `/trades/add` | `TradeAddPage.vue` | 添加交易 |
| `/dividends` | `DividendHistoryPage.vue` | 分红历史 |
| `/calendar` | `CalendarPage.vue` | 分红日历 |
| `/dca/:id` | `DcaPlanDetailPage.vue` | 定投计划详情 |
| `/expenses` | `CoveragePage.vue` | 支出管理 |
| `/expenses/settings` | `SettingsPage.vue` | 支出分类设置 |
| `/profile` | `ProfilePage.vue` | 个人中心（含备份按钮） |
| `/metrics/settings` | `MetricSettings.vue` | 指标设置 |

---

## 技术栈

| 层 | 技术 |
|----|------|
| 前端框架 | Vue 3 + TypeScript |
| 构建工具 | Vite |
| 后端框架 | Spring Boot 3.2 + Java 17 |
| 数据库 | MySQL 8.0 |
| 爬虫 | Jsoup（东方财富） |
| ORM | Spring Data JPA |
| 部署 | systemd + Nginx |

---

## 本地开发

```bash
# 后端
cd myPhonePro/fund-tracker-backend
mvn spring-boot:run

# 前端
cd myPhonePro/stitch_fund_dividend_tracker
npm run dev
```

## 服务器操作

```bash
# 部署后端
ssh root@<服务器IP>
cd /tmp/build-app && git pull
cd myPhonePro/fund-tracker-backend
mvn package -DskipTests
systemctl restart fund-tracker

# 查看后端日志
journalctl -u fund-tracker -f

# 运行测试
mvn test
```
