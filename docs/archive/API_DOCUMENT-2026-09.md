# 种树 - 基金分红追踪器 RESTful API 接口文档

> 基础路径：`/api`  
> 数据格式：`JSON`  
> 字符编码：`UTF-8`

---

## 目录

1. [通用约定](#1-通用约定)
2. [用户模块](#2-用户模块)
3. [首页看板](#3-首页看板)
4. [持仓模块](#4-持仓模块)
5. [分红事件模块](#5-分红事件模块)
6. [交易记录模块](#6-交易记录模块)
7. [分红记录模块](#7-分红记录模块)
8. [分红预测模块](#8-分红预测模块)
9. [分红覆盖类目模块](#9-分红覆盖类目模块)
10. [汇率模块](#10-汇率模块)
11. [月度洞察模块](#11-月度洞察模块)
12. [用户设置模块](#12-用户设置模块)

---

## 1. 通用约定

### 1.1 通用响应格式

所有接口统一返回格式：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

### 1.2 通用错误响应

```json
{
  "code": 400,
  "message": "参数错误",
  "errors": {
    "name": ["名称不能为空"],
    "quantity": ["数量必须大于 0"]
  }
}
```

### 1.3 分页请求参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | number | 否 | 1 | 页码，从 1 开始 |
| pageSize | number | 否 | 20 | 每页条数 |

### 1.4 分页响应结构

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [],
    "total": 100,
    "page": 1,
    "pageSize": 20,
    "totalPages": 5
  }
}
```

### 1.5 认证方式

本应用为**单用户模式**，使用 Bearer Token 做简单鉴权。

**鉴权方式：**

```
Authorization: Bearer <token>
```

**说明：**
- 所有接口（除 `/api/auth/*` 外）均需携带 Token
- Token 由后端在首次启动时自动生成，或在 `GET /api/auth/token` 中获取
- Token 校验失败返回 `401 Unauthorized`
- 前端将 Token 存储在 `localStorage` 中，每次请求通过 Axios 拦截器自动携带

**认证流程：**

```
首次启动 → GET /api/auth/token → 获取并存储 Token
后续请求 → 在 Header 中携带 Authorization: Bearer <token>
Token 过期 → 返回 401 → 前端重新获取 Token
```

### 1.6 成本算法说明

持仓的成本计算支持三种算法，在创建/编辑持仓时选择，可随时修改：

| # | 算法名称 | 公式 | 说明 |
|---|---------|------|------|
| 1 | **分红摊薄**（默认） | (累计买入 - 累计卖出 - 累计已收分红) ÷ 当前持仓份额 | 分红直接摊薄持仓成本，是默认算法 |
| 2 | **摊薄成本** | (累计买入 - 累计卖出) ÷ 当前持仓份额 | 分红不影响成本，仅通过买入卖出摊薄 |
| 3 | **加权平均** | 总买入金额 ÷ 总买入份额 | 按均价出库，卖出价不影响成本，成本永不小于 0 |

> 持仓详情中展示的「成本息率」和「每股成本」会根据所选算法动态变化。

### 1.7 预测分红口径

预测每股分红基于历史分红数据计算，为用户**全局设置**，可随时在个人中心修改：

| 口径 | 说明 |
|------|------|
| 近 1 年均值 | 最近 1 年分红数据均值 |
| **近 3 年均值（默认）** | 最近 3 年分红数据均值 |
| 近 5 年均值 | 最近 5 年分红数据均值 |
| 自定义 | 用户手动输入预测值 |

**计算逻辑：**
- 按**除权日所在年份**归集历年分红数据
- 计算每份年均派息
- 预测年分红 = 预测每股分红 × 当前持仓份额

### 1.8 错误码说明

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证或 Token 过期 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 资源冲突（如重复创建） |
| 429 | 请求太频繁 |
| 500 | 服务器内部错误 |
| 1001 | 持仓不存在 |
| 1002 | 持仓代码已存在 |
| 2001 | 分红事件不存在 |
| 3001 | 交易记录不存在 |
| 3002 | 卖出份额不足 |
| 3003 | 无效的交易类型 |
| 4001 | 刷新过于频繁，请稍后再试 |

---

## 2. 用户模块

### 2.1 获取用户信息

获取当前登录用户的个人信息。

```
GET /api/user/profile
```

**请求参数：** 无

**成功响应 (200)：**

| 字段 | 类型 | 必填 | 说明 | 示例值 |
|------|------|------|------|--------|
| id | string | 是 | 用户 ID | "u1" |
| name | string | 是 | 用户昵称 | "稳健投资者" |
| avatar | string | 是 | 头像 URL | "https://..." |
| membership | string | 是 | 会员类型: pro/free | "pro" |
| membershipExpiry | string | 是 | 会员到期日 | "2025-12-31" |
| phone | string | 是 | 已脱敏手机号 | "138\*\*\*\*8888" |
| version | string | 否 | App 版本号 | "2.4.0" |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "u1",
    "name": "稳健投资者",
    "avatar": "https://...",
    "membership": "pro",
    "membershipExpiry": "2025-12-31",
    "phone": "138****8888",
    "version": "2.4.0"
  }
}
```

### 2.2 更新用户信息

```
PUT /api/user/profile
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 | 示例值 |
|------|------|------|------|--------|
| name | string | 否 | 用户昵称 | "新的昵称" |
| avatar | string | 否 | 头像 URL | "https://..." |
| phone | string | 否 | 手机号 | "13812348888" |

**成功响应 (200)：** 返回更新后的用户信息，结构同上。

---

## 3. 首页看板

### 3.1 获取首页看板摘要

获取首页展示的汇总统计数据。

```
GET /api/dashboard
```

**请求参数：** 无

**成功响应 (200)：**

| 字段 | 类型 | 必填 | 说明 | 示例值 |
|------|------|------|------|--------|
| consecutiveDays | number | 是 | 已连续收息天数 | 365 |
| predictedAnnualDividend | number | 是 | 预测年度分红总额(元) | 24500 |
| tenYearExpectedReturn | number | 是 | 10年预期收益(倍) | 3.2 |
| monthlyPredictedDividend | number | 是 | 当月预计派息(元) | 1200 |
| monthlyMessage | string | 是 | 通知横幅文案 | "稳稳的幸福，本月预计收息1200元" |
| totalHoldings | number | 是 | 持仓总数 | 3 |
| coveredCategories | number | 是 | 已覆盖生活类目数 | 4 |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "consecutiveDays": 365,
    "predictedAnnualDividend": 24500,
    "tenYearExpectedReturn": 3.2,
    "monthlyPredictedDividend": 1200,
    "monthlyMessage": "稳稳的幸福，本月预计收息1200元",
    "totalHoldings": 3,
    "coveredCategories": 4
  }
}
```

---

## 4. 持仓模块

### 4.1 获取持仓列表

```
GET /api/holdings
```

**请求参数 (Query)：**

| 参数 | 类型 | 必填 | 默认值 | 说明 | 示例值 |
|------|------|------|--------|------|--------|
| page | number | 否 | 1 | 页码 | 1 |
| pageSize | number | 否 | 20 | 每页条数 | 20 |
| type | string | 否 | - | 按类型筛选: fund/cny_asset | "fund" |
| keyword | string | 否 | - | 按名称/代码搜索 | "招商" |

**成功响应 (200)：**

| 字段 | 类型 | 必填 | 说明 | 示例值 |
|------|------|------|------|--------|
| id | string | 是 | 持仓 ID | "h1" |
| name | string | 是 | 基金/资产名称 | "招商中证白酒" |
| code | string | 是 | 基金/资产代码 | "161725" |
| type | string | 是 | 类型: fund/cny_asset | "fund" |
| costAlgorithm | string | 是 | 成本算法: diluted/diluted_only/weighted_avg | "diluted" |
| shares | number | 是 | 持有份额 | 12000 |
| cost | number | 是 | 总成本(元) | 41200 |
| marketValue | number | 是 | 当前市值(元) | 45820 |
| predictedDividend | number | 是 | 预测年分红(元) | 1200 |
| dividendRate | number | 是 | 成本息率(%) | 5.2 |
| priceDividendRate | number | 是 | 股价息率(%) | 3.8 |
| totalDividendReceived | number | 是 | 累计已获分红(元) | 2300 |
| netInvestment | number | 是 | 净投入(元) | 82700 |
| dividendRecoveryRate | number | 是 | 分红回本进度(0-100) | 15 |
| estimatedRecoveryYears | number | 是 | 预计回本年限 | 12.5 |
| color | string | 是 | 标识色 | "#FF7A45" |

### 4.2 获取单个持仓详情

```
GET /api/holdings/:id
```

**路径参数：**

| 参数 | 类型 | 必填 | 说明 | 示例值 |
|------|------|------|------|--------|
| id | string | 是 | 持仓 ID | "h1" |

**成功响应 (200)：** 返回单个持仓对象，结构同 4.1 列表项。

### 4.3 创建持仓

```
POST /api/holdings
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 | 示例值 |
|------|------|------|------|--------|
| name | string | 是 | 基金/资产名称 | "易方达蓝筹精选" |
| code | string | 是 | 基金/资产代码 | "005827" |
| type | string | 是 | 类型: fund/cny_asset | "fund" |
| costAlgorithm | string | 否 | 成本算法，默认 diluted | "diluted" |
| shares | number | 是 | 持有份额 | 5000 |
| cost | number | 是 | 总成本(元) | 50000 |

> **说明：** `marketValue`（当前市值）由服务端自动计算，公式为 `(cost / shares) * shares = cost`（即以成本价作为初始市值）。后续可通过 `PUT /api/holdings/:id` 手动更新市值。

### 4.4 更新持仓

```
PUT /api/holdings/:id
```

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | string | 是 | 持仓 ID |

**请求体：**

| 字段 | 类型 | 必填 | 说明 | 示例值 |
|------|------|------|------|--------|
| name | string | 否 | 名称 | "易方达蓝筹精选" |
| costAlgorithm | string | 否 | 成本算法 | "weighted_avg" |
| shares | number | 否 | 持有份额 | 6000 |
| cost | number | 否 | 总成本(元) | 60000 |
| marketValue | number | 否 | 当前市值(元) | 63000 |

### 4.5 删除持仓

```
DELETE /api/holdings/:id
```

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | string | 是 | 持仓 ID |

**成功响应 (200)：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true
  }
}
```

---

## 5. 分红事件模块

### 5.1 获取分红事件列表

```
GET /api/events
```

**请求参数 (Query)：**

| 参数 | 类型 | 必填 | 默认值 | 说明 | 示例值 |
|------|------|------|--------|------|--------|
| page | number | 否 | 1 | 页码 | 1 |
| pageSize | number | 否 | 20 | 每页条数 | 20 |
| holdingId | string | 否 | - | 按持仓筛选 | "h1" |
| month | string | 否 | - | 按月份筛选 | "2024-11" |
| dateFrom | string | 否 | - | 开始日期 | "2024-11-01" |
| dateTo | string | 否 | - | 结束日期 | "2024-11-30" |
| type | string | 否 | - | 事件类型 | "payout" |
| status | string | 否 | - | 状态 | "pending" |

**事件类型枚举：**

| 类型 | 说明 | 日历标记色 |
|------|------|-----------|
| registration | 股权登记 | 🔵 蓝色 |
| ex_dividend | 除权除息 | 🔴 红色 |
| payout | 派息日 | 🟢 绿色 |
| announcement | 公告 | ⚪ 灰色 |

**状态枚举：** `pending`（待处理） | `distributed`（已到账） | `cancelled`（已取消）

### 5.2 获取指定日期的分红事件

```
GET /api/events/date/:date
```

**路径参数：**

| 参数 | 类型 | 必填 | 说明 | 示例值 |
|------|------|------|------|--------|
| date | string | 是 | 日期 | "2024-11-15" |

**成功响应 (200)：** 返回 DividendEventItem 数组

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": "e1",
      "holdingId": "h1",
      "holdingName": "招商中证白酒",
      "type": "payout",
      "date": "2024-11-15",
      "amount": 350,
      "status": "pending",
      "description": "预计到账 350.00 元"
    }
  ]
}
```

### 5.3 创建分红事件

```
POST /api/events
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 | 示例值 |
|------|------|------|------|--------|
| holdingId | string | 是 | 持仓 ID | "h1" |
| type | string | 是 | 事件类型 | "payout" |
| date | string | 是 | 日期 | "2024-12-01" |
| amount | number | 否 | 金额(元) | 350 |
| description | string | 否 | 描述 | "预计到账 350.00 元" |

### 5.4 标记分红已到账

```
PUT /api/events/:id/distribute
```

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | string | 是 | 事件 ID |

**成功响应 (200)：** 返回状态变为 `distributed` 的事件对象。

### 5.5 取消分红事件

```
PUT /api/events/:id/cancel
```

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | string | 是 | 事件 ID |

**成功响应 (200)：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "e1",
    "status": "cancelled",
    "updatedAt": "2024-11-16T10:30:00Z"
  }
}
```

> **说明：** 取消操作为**软删除**，仅将事件状态变更为 `cancelled`，保留记录用于审计和历史追溯。已标记为 `distributed` 的事件不可取消。

---

## 6. 交易记录模块

### 6.1 获取交易记录

```
GET /api/transactions
```

**请求参数 (Query)：**

| 参数 | 类型 | 必填 | 默认值 | 说明 | 示例值 |
|------|------|------|--------|------|--------|
| page | number | 否 | 1 | 页码 | 1 |
| pageSize | number | 否 | 20 | 每页条数 | 20 |
| holdingId | string | 否 | - | 按持仓筛选 | "h1" |
| type | string | 否 | - | 交易类型 | "buy" |
| dateFrom | string | 否 | - | 开始日期 | "2023-01-01" |
| dateTo | string | 否 | - | 结束日期 | "2023-12-31" |

**交易类型枚举：**

| 类型 | 说明 |
|------|------|
| buy | 买入 |
| sell | 卖出 |
| bonus_share | 送股 |
| reinvest | 分红复投 |

### 6.2 创建交易记录

```
POST /api/transactions
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 | 示例值 |
|------|------|------|------|--------|
| holdingId | string | 是 | 持仓 ID | "h1" |
| type | string | 是 | 交易类型 | "buy" |
| date | string | 是 | 交易日期 | "2024-11-15" |
| quantity | number | 是 | 数量(份) | 5000 |
| price | number | 是 | 单价(元) | 0.824 |
| fee | number | 否 | 交易费用(元)，默认 0 | 5.00 |

**成功响应 (200)：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "t5",
    "holdingId": "h1",
    "type": "buy",
    "date": "2024-11-15",
    "quantity": 5000,
    "price": 0.824,
    "fee": 5.00,
    "total": 4125
  }
}
```

> **注意：** `total` 由服务端计算：`total = quantity * price + fee`

### 6.3 删除交易记录

```
DELETE /api/transactions/:id
```

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | string | 是 | 交易记录 ID |

---

## 7. 分红记录模块

### 7.1 获取分红记录

```
GET /api/dividend-records
```

**请求参数 (Query)：**

| 参数 | 类型 | 必填 | 默认值 | 说明 | 示例值 |
|------|------|------|--------|------|--------|
| page | number | 否 | 1 | 页码 | 1 |
| pageSize | number | 否 | 20 | 每页条数 | 20 |
| holdingId | string | 否 | - | 按持仓筛选 | "h1" |
| year | number | 否 | - | 按年份筛选 | 2024 |
| status | string | 否 | - | 状态 | "distributed" |

**成功响应 (200)：** 分页响应，每项包含：

| 字段 | 类型 | 必填 | 说明 | 示例值 |
|------|------|------|------|--------|
| id | string | 是 | 记录 ID | "dr1" |
| holdingId | string | 是 | 持仓 ID | "h1" |
| holdingName | string | 是 | 持仓名称 | "招商中证白酒" |
| date | string | 是 | 派息日期 | "2024-06-20" |
| amount | number | 是 | 派息金额(元) | 350 |
| status | string | 是 | 状态: pending/distributed | "distributed" |
| exDividendDate | string | 否 | 除权除息日 | "2024-06-18" |
| registrationDate | string | 否 | 股权登记日 | "2024-06-17" |

---

## 8. 分红预测模块

### 8.1 获取持仓分红预测

```
GET /api/holdings/:id/forecast
```

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | string | 是 | 持仓 ID |

**请求参数 (Query)：**

| 参数 | 类型 | 必填 | 默认值 | 说明 | 示例值 |
|------|------|------|--------|------|--------|
| period | string | 否 | "12m" | 预测周期: 12m/5y | "5y" |

**成功响应 (200)：**

| 字段 | 类型 | 必填 | 说明 | 示例值 |
|------|------|------|------|--------|
| holdingId | string | 是 | 持仓 ID | "h1" |
| period | string | 是 | 预测周期 | "5y" |
| series | array | 是 | 预测数据点数组 | - |
| trendPercentage | number | 是 | 增长趋势百分比 | 45 |

**series 项：**

| 字段 | 类型 | 必填 | 说明 | 示例值 |
|------|------|------|------|--------|
| label | string | 是 | 标签 | "2025" |
| value | number | 是 | 预测值(元) | 5600 |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "holdingId": "h1",
    "period": "5y",
    "series": [
      { "label": "2025", "value": 5600 },
      { "label": "2026", "value": 6200 },
      { "label": "2027", "value": 7100 },
      { "label": "2028", "value": 7800 },
      { "label": "2029", "value": 8500 }
    ],
    "trendPercentage": 45
  }
}
```

---

## 9. 分红覆盖类目模块

### 9.1 获取分红覆盖类目

```
GET /api/coverage-categories
```

**请求参数：** 无

**成功响应 (200)：**

| 字段 | 类型 | 必填 | 说明 | 示例值 |
|------|------|------|------|--------|
| id | string | 是 | 类目 ID | "c1" |
| name | string | 是 | 类目名称 | "话费" |
| icon | string | 是 | Material Symbol 图标名 | "phone_android" |
| percentage | number | 是 | 覆盖百分比(0-100) | 80 |
| color | string | 是 | 标识色 | "#FF7A45" |

```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "id": "c1", "name": "话费", "icon": "phone_android", "percentage": 80, "color": "#FF7A45" },
    { "id": "c2", "name": "养车", "icon": "directions_car", "percentage": 40, "color": "#4CAF50" },
    { "id": "c3", "name": "娱乐", "icon": "confirmation_number", "percentage": 30, "color": "#9C27B0" },
    { "id": "c4", "name": "医药", "icon": "medical_services", "percentage": 25, "color": "#2196F3" },
    { "id": "c5", "name": "午餐", "icon": "restaurant", "percentage": 60, "color": "#FF9800" }
  ]
}
```

### 9.2 更新覆盖类目

```
PUT /api/coverage-categories/:id
```

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | string | 是 | 类目 ID |

**请求体（全部可选）：**

| 字段 | 类型 | 必填 | 说明 | 示例值 |
|------|------|------|------|--------|
| name | string | 否 | 类目名称 | "交通" |
| icon | string | 否 | 图标名 | "directions_car" |
| percentage | number | 否 | 覆盖百分比 | 50 |
| color | string | 否 | 标识色 | "#FF9800" |

---

## 10. 汇率模块

### 10.1 获取汇率

```
GET /api/exchange-rates
```

**请求参数：** 无

**成功响应 (200)：**

| 字段 | 类型 | 必填 | 说明 | 示例值 |
|------|------|------|------|--------|
| pair | string | 是 | 货币对 | "HKD/CNY" |
| label | string | 是 | 展示标签 | "港币/人民币" |
| rate | number | 是 | 汇率值 | 0.9245 |
| updatedAt | string | 是 | 更新时间 | "2023-10-27 15:30" |

```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "pair": "HKD/CNY", "label": "港币/人民币", "rate": 0.9245, "updatedAt": "2023-10-27 15:30" },
    { "pair": "USD/CNY", "label": "美金/人民币", "rate": 7.2341, "updatedAt": "2023-10-27 15:30" }
  ]
}
```

### 10.2 刷新汇率

```
POST /api/exchange-rates/refresh
```

**请求参数：** 无

**成功响应 (200)：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| rates | array | 是 | 更新后的汇率列表 |
| refreshedAt | string | 是 | 刷新时间 |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "rates": [ ... ],
    "refreshedAt": "2024-11-15 10:30:00"
  }
}
```

> ⚠️ **频率限制：** 同一用户 **30 秒内仅允许刷新一次**，超出返回错误码 `4001` 及提示 `"刷新过于频繁，请稍后再试"`。

---

## 11. 月度洞察模块

### 11.1 获取月度洞察

```
GET /api/insights/monthly
```

**请求参数 (Query)：**

| 参数 | 类型 | 必填 | 默认值 | 说明 | 示例值 |
|------|------|------|--------|------|--------|
| year | number | 是 | - | 年份 | 2024 |
| month | number | 是 | - | 月份(1-12) | 11 |

**成功响应 (200)：**

| 字段 | 类型 | 必填 | 说明 | 示例值 |
|------|------|------|------|--------|
| richestSource.holdingName | string | 是 | 最丰厚来源名称 | "沪深300指数" |
| richestSource.amount | number | 是 | 金额(元) | 420.00 |
| dividendIntensity.monthOverMonth | number | 是 | 环比变化百分比 | 12 |
| dividendIntensity.monthOverMonthText | string | 是 | 环比展示文案 | "+12%" |
| dividendIntensity.progressPercentage | number | 是 | 进度条百分比 | 75 |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "richestSource": {
      "holdingName": "沪深300指数",
      "amount": 420.00
    },
    "dividendIntensity": {
      "monthOverMonth": 12,
      "monthOverMonthText": "+12%",
      "progressPercentage": 75
    }
  }
}
```

---

## 12. 用户设置模块

### 12.1 获取用户设置

```
GET /api/user/settings
```

**请求参数：** 无

**成功响应 (200)：**

| 字段 | 类型 | 必填 | 说明 | 示例值 |
|------|------|------|------|--------|
| currency | string | 是 | 本位币 | "CNY" |
| currencyLabel | string | 是 | 本位币标签 | "人民币" |
| forecastHorizon | string | 是 | 预测分红口径: 1y/3y/5y/custom | "3y" |
| customForecastValue | number | 否 | 自定义预测值(元)，口径为 custom 时必填 | 0.05 |
| notificationsEnabled | boolean | 是 | 通知开关 | true |

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "currency": "CNY",
    "currencyLabel": "人民币",
    "forecastHorizon": "3y",
    "notificationsEnabled": true
  }
}
```

### 12.2 更新用户设置

```
PUT /api/user/settings
```

**请求体（全部可选）：**

| 字段 | 类型 | 必填 | 说明 | 示例值 |
|------|------|------|------|--------|
| currency | string | 否 | 本位币 | "CNY" |
| currencyLabel | string | 否 | 本位币标签 | "人民币" |
| forecastHorizon | string | 否 | 预测分红口径 | "5y" |
| customForecastValue | number | 否 | 自定义预测值(元) | 0.05 |
| notificationsEnabled | boolean | 否 | 通知开关 | false |

---

## 附录

### A. 接口总览表

| 方法 | 路径 | 说明 | 前端页面 |
|------|------|------|---------|
| GET | /api/auth/token | 获取访问 Token | 鉴权 |
| GET | /api/user/profile | 获取用户信息 | 个人中心 |
| PUT | /api/user/profile | 更新用户信息 | 个人中心 |
| GET | /api/user/settings | 获取用户设置 | 个人中心 |
| PUT | /api/user/settings | 更新用户设置 | 个人中心 |
| GET | /api/dashboard | 获取首页看板 | 首页 |
| GET | /api/holdings | 获取持仓列表 | 首页 |
| GET | /api/holdings/:id | 获取持仓详情 | 持仓详情 |
| POST | /api/holdings | 创建持仓 | 持仓详情 |
| PUT | /api/holdings/:id | 更新持仓 | 持仓详情 |
| DELETE | /api/holdings/:id | 删除持仓 | 持仓详情 |
| GET | /api/events | 获取分红事件列表 | 分红日历 |
| GET | /api/events/date/:date | 获取指定日期事件 | 分红日历 |
| POST | /api/events | 创建分红事件 | - |
| PUT | /api/events/:id/distribute | 标记分红到账 | - |
| PUT | /api/events/:id/cancel | 取消分红事件 | - |
| GET | /api/transactions | 获取交易记录 | 持仓详情 |
| POST | /api/transactions | 创建交易 | 添加交易 |
| DELETE | /api/transactions/:id | 删除交易 | - |
| GET | /api/dividend-records | 获取分红记录 | 持仓详情 |
| GET | /api/holdings/:id/forecast | 获取分红预测 | 持仓详情 |
| GET | /api/coverage-categories | 获取覆盖类目 | 首页 |
| PUT | /api/coverage-categories/:id | 更新覆盖类目 | 首页 |
| GET | /api/exchange-rates | 获取汇率 | 个人中心 |
| POST | /api/exchange-rates/refresh | 刷新汇率 | 个人中心 |
| GET | /api/insights/monthly | 获取月度洞察 | 分红日历 |

### B. 前端已生成的类型文件

前端项目中已包含对应的 TypeScript 类型定义，方便前后端类型共享：

> `src/types/api.ts`

该文件包含所有请求参数、请求体、响应体的类型定义，以及业务错误码枚举 `ErrorCode`。
