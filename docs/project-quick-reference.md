# 「种树」项目速查表

> 人读版本，快速理解项目全貌。AI 版提示词见 [reverse-engineering-prompt.md](./reverse-engineering-prompt.md)

---

## 一、这是什么？

**种树 — 稳健收息管理**，一款个人投资持仓管理与分红追踪工具。

核心逻辑：记录你的基金/股票持仓 → 自动抓取分红数据 → 追踪每笔分红事件 → 计算分红覆盖生活支出的进度 → 管理 DCA 定投计划。

---

## 二、技术栈

| 层 | 技术 |
|----|------|
| 前端 | Vue 3.4 + Vite 5.4 + TypeScript + TailwindCSS |
| 后端 | Spring Boot 3.2 + JPA + MySQL 8.x |
| 认证 | Bearer Token |
| 爬虫 | Jsoup（天天基金数据） |
| UI 风格 | Material Design 3，手机端优先，max-width 600px |

---

## 三、数据模型（14 张表）

```
UserProfile ─── UserSettings
Holding ──< Transaction (买入/卖出/送股/复投)
  ├──< DividendEvent (登记日/除权日/发放日/公告日)
  └──< DcaPlan (定投计划)
FundDividendRecord (爬取的基金分红历史)
FundNavRecord (爬取的基金净值历史)
LiveExpense (生活支出项)
ManualAsset (手动资产：现金/加密货币)
AssetSnapshot (每日资产快照)
CoverageCategory (覆盖类目)
ExchangeRate (USD/CNY, HKD/CNY)
AuthToken
```

---

## 四、API 概览（53 个端点，16 个 Controller）

| 模块 | 端点 | 说明 |
|------|------|------|
| Auth | `GET /api/auth/token` | 获取 Token |
| Dashboard | `GET /api/dashboard` | 首页看板指标 |
| Holdings | CRUD + 搜索 + 预测 + 分红信息 | 持仓管理 |
| Transactions | CRUD | 交易记录 |
| Events | CRUD + 标记到账/取消 + 同步 | 分红事件 |
| Funds | 刷新分红 + 查询记录 | 基金数据爬取 |
| DCA Plans | CRUD + 执行 + 预算 | 定投管理 |
| Expenses | CRUD + 覆盖率 | 支出与覆盖 |
| Asset Overview | 概览 + 历史 + 快照 | 资产总览 |
| Exchange Rates | 列表 + 刷新 | 汇率 |
| Insights | 月洞察/月明细/年洞察 | 数据洞察 |
| User | 资料 + 设置 | 用户 |
| Manual Assets | CRUD | 手动资产 |
| Coverage | 类目 CRUD | 覆盖类目 |

---

## 五、核心业务算法

### 成本计算（3 种）
- **分红摊薄**: (总买入 - 总卖出 - 已收分红) / 份额
- **摊薄成本**: (总买入 - 总卖出) / 份额
- **加权平均**: 总买入金额 / 总买入份额

### DCA 定投
- 频率: 每日/每周/双周/每月
- 执行: 取最新净值 → 份额=金额/净值 → 创建交易 → 更新统计 → 推进下次日期
- 定时: 每天 20:00 自动执行到期计划

### 分红爬虫
- 源: `fundf10.eastmoney.com` 分红表格
- 频率识别: 间隔天数判断月度/季度/年度/不定期
- 预测: 单次均值 × 年频率 × 份额

### 分红覆盖率
- 按支出金额从小到大排序
- 用预测年分红逐步覆盖
- 里程碑: 初出茅庐→小有所成→渐入佳境→收益达人→财务自由

---

## 六、前端页面（13 个）

| 页面 | 路由 | 说明 |
|------|------|------|
| 首页看板 | `/` | Hero 指标 + 持仓列表 |
| 分红日历 | `/calendar` | 月历 + 事件 + 洞察 |
| 资产概览 | `/discover` | 总资产 + 分类 + DCA |
| 个人中心 | `/profile` | 用户 + 汇率 + 设置 |
| 持仓详情 | `/holding/:id` | Hero + 图表 + 操作 |
| 添加交易 | `/trade/add` | 分段表单 |
| 添加持仓 | `/holding/add` | 三步向导 |
| 分红覆盖 | `/coverage` | 里程碑 + 支出列表 |
| 支出设置 | `/coverage/settings` | 支出 CRUD |
| 指标设置 | `/metrics/settings` | 首页指标开关 |
| 交易列表 | `/holding/:id/transactions` | 交易明细 |
| 分红记录 | `/holding/:id/dividends` | 分红历史 |
| 定投详情 | `/dca-plans/:id` | 计划详情 + 执行 |

---

## 七、定时任务

| 任务 | 时间 | 说明 |
|------|------|------|
| DCA 自动执行 | 每天 20:00 | 执行到期的定投计划 |
| 分红数据爬取 | 每天 06:00 | 抓取最新分红数据并同步事件 |

---

## 八、已知问题

1. 单用户设计，无多用户支持
2. Service 职责过重（HoldingService 承担太多）
3. 无接口幂等性保证
4. 硬编码配置（Token、数据库密码、外部 URL）
5. 类型定义分散重复
6. 无单元测试和集成测试
7. 前端无全局 loading/error 处理
8. 前端无请求缓存和去重

---

## 九、快速启动

```bash
# 前端
cd myPhonePro/stitch_fund_dividend_tracker
npm install
npm run dev
# → http://localhost:5173

# 后端
cd myPhonePro/fund-tracker-backend
mvn spring-boot:run
# → http://localhost:8080
# Swagger: http://localhost:8080/swagger-ui.html
```

数据库要求: MySQL 8.x，数据库名 `fund_tracker`，自动建表。
