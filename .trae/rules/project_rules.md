# 项目规则 — 基金分红追踪器

## AI 行为准则

当用户说"你有什么想法？"、"你的看法"、"说说你的思路"等类似表达时，AI 必须先**复述用户的需求/问题 + 给出方案/思路/看法**，等待用户确认后再开始开发。不得跳过确认步骤直接动手改代码。目的是确保双方理解一致，避免做无用功。

## 项目概述
一个个人基金投资管理工具，功能包括：添加/管理基金持仓、记录交易、追踪分红、查看成本收益指标。

- **前端**：Vue 3 (Composition API + `<script setup lang="ts">`) + Vue Router 4 + Vite + TypeScript + Tailwind CSS + Material Symbols 图标
- **后端**：Spring Boot 3.2.0 + Spring Data JPA + Hibernate + MySQL 8.0 + Maven
- **开发端口**：前端 `http://localhost:5173`，后端 `http://localhost:8080`

## 启动方式
系统禁用了 PowerShell 脚本执行策略，必须使用 `-ExecutionPolicy Bypass`：
```powershell
# 后端
cd fund-tracker-backend && mvn spring-boot:run

# 前端（新开终端）
powershell -ExecutionPolicy Bypass -Command "cd 'stitch_fund_dividend_tracker'; npm run dev"
```

## 构建命令
- 后端：`cd fund-tracker-backend && mvn clean compile`
- 前端（需要时）：`npm run build`

## 缓存/编译注意事项
- 后端如果修改了 Repository 方法签名（如 `void` → `int`），需要 `mvn clean compile` 强制重新编译，否则 Spring Boot 运行时抛出 `Unresolved compilation problems`（利用了旧 .class 文件）
- 前端 `<KeepAlive>` 缓存组件后，Vite HMR 可能无法正确热更新生命周期钩子（如 `onMounted` → `onActivated`），需要浏览器硬刷新

## 代码结构

### 前端 (`stitch_fund_dividend_tracker/src/`)
```
├── api/
│   ├── request.ts          # API 请求封装（基于 fetch），检查 json.code !== 200 时抛异常
│   ├── transaction.ts      # 交易 CRUD API
│   ├── metrics.ts          # 指标配置中心（11 个指标定义，localStorage 读写）
│   └── ...                 # holding, dividend 等 API
├── router/
│   └── index.ts            # 路由配置，meta.level 控制导航层级
├── views/
│   ├── home/HomePage.vue           # 首页：持仓列表 + 指标卡片，onActivated + KeepAlive
│   ├── holding-detail/HoldingDetailPage.vue  # 持仓详情：指标仪表盘 + 三个功能入口
│   ├── trade-add/TradeAddPage.vue            # 添加交易页（买入/卖出/送股/复投）
│   ├── transactions/TransactionListPage.vue  # 交易明细列表 + 编辑/删除弹窗
│   ├── dividend-records/*                    # 分红记录页
│   └── metrics/MetricSettings.vue            # 指标设置页（实时预览，最多选6项）
├── App.vue                # 根组件，KeepAlive + 导航栏控制
├── main.ts                # 入口
└── style.css              # 全局样式 + Tailwind 配置
```

### 后端 (`fund-tracker-backend/src/main/java/com/fundtracker/`)
```
├── controller/             # REST Controller 层
│   ├── HoldingController.java
│   ├── TransactionController.java
│   └── ...
├── service/
│   ├── HoldingService.java          # 持仓核心逻辑：创建/删除（物理级联）、成本重算
│   ├── TransactionService.java      # 交易 CRUD + 份额重算（recalculateSharesFromScratch）
│   ├── CostCalculator.java          # 三种成本算法实现
│   └── ...
├── model/
│   ├── entity/                      # JPA 实体
│   │   ├── Holding.java (持仓)
│   │   ├── Transaction.java (交易)
│   │   └── ...
│   ├── dto/                         # 请求/响应 DTO
│   │   ├── CreateTransactionReq.java
│   │   ├── UpdateTransactionReq.java
│   │   └── ...
│   └── enums/                       # 枚举
│       ├── TransactionType.java (buy/sell/bonus_share/reinvest)
│       └── CostAlgorithm.java (diluted/diluted_only/weighted_avg)
├── repository/             # JPA Repository
├── exception/
│   ├── BusinessException.java       # 业务异常（含 holdingNotFound, transactionNotFound 等工厂方法）
│   └── GlobalExceptionHandler.java  # 全局异常处理，BusinessException 返回 HTTP 200 + 业务 code
└── ...
```

### 数据库 (`fund_tracker`)
```
holdings          # 持仓表（当前 1 条正常数据）
transactions      # 交易记录表
dividend_events   # 用户自定义分红事件
fund_dividend_records  # 基金历史分红数据
fund_nav_records       # 基金历史净值数据
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
- 后端使用基于固定 Token 的 HandlerInterceptor 认证（无 Spring Security）
- `AuthInterceptor` 拦截 `/api/**`，白名单：`/api/auth/*`、`/api/funds/*`、`/api/holdings/dividend-info`
- 固定 Token：`dev-token-2024`（配置文件 `application.yml` 中定义）
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
