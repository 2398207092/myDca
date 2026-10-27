# 分红功能升级 — 三阶段开发计划

> 创建日期：2026-07-22
> 最后更新：2026-07-22
> 状态：P0 已完成 | P1 待实现 | P2 待实现

---

## 问题背景

博时红利低波100 的分红记录显示了该基金全部历史分红（2024/2025），但用户当时尚未持有该标的，这些分红实际上并未收到。

暴露三个核心问题：
1. **数据过滤缺失**：分红同步未按首次买入日期过滤
2. **金额计算错误**：使用当前份额（`holding.getShares()`）倒推历史分红，而非当时的实时份额
3. **日历同样受影响**：分红日历也使用当前份额计算历史金额

同时提出新需求：
1. 分红复投功能 + 开关控制
2. 分红功能整体升级，围绕分红打造核心功能
3. 分红看板替代/增强现有年度总览页面

---

## 总体架构

```
三层漏斗（现有）：
  天天基金抓取 → FundDividendRecord → DividendEventSyncService → DividendEvent（用户事件）

P0 修复漏斗：
  DividendEventSyncService 增加：首笔买入日期过滤 + 实时份额计算

P1 新增漏斗出口：
  DividendEvent（标记 distributed）→ 检查复投开关 → 自动创建 reinvest 交易 → 重算份额

P2 新建看板：
  DividendEvent 数据聚合 → 月柱状图 / 标的占比 / 预计下次
```

---

## P0 — 数据修复（核心正确性） ✅ 已完成

### 设计方案变化

**最终方案**：保留所有历史分红记录，用 `participated` 标签区分是否参与。

| 场景 | participated | amount | 前端展示 |
|------|:-----------:|:------:|---------|
| 首笔买入之前的分红 | `false` | ¥0.00 | 显示"未参与" |
| 首笔买入之后但有份额时 | `true` | 按实时份额计算 | 显示金额 |
| 首笔买入之后但当时无份额 | `false` | ¥0.00 | 显示"未参与" |

### 改动清单

#### 1. `DividendEvent` 实体新增 `participated` 字段

**文件**：[DividendEvent.java](file:///d:/mySpace/myDca/myPhonePro/fund-tracker-backend/src/main/java/com/fundtracker/model/entity/DividendEvent.java)
```java
@Column(nullable = false)
private Boolean participated;
```

#### 2. `TransactionService` 新增两个工具方法

**文件**：[TransactionService.java](file:///d:/mySpace/myDca/myPhonePro/fund-tracker-backend/src/main/java/com/fundtracker/service/TransactionService.java)

- `getFirstTransactionDate(holdingId)` — 查询首笔交易日期
- `calculateSharesAtDate(holdingId, date)` — 按交易逐笔累加至指定日期，计算当时实时份额

**Repository 新增方法**：
```java
// TransactionRepository.java
List<Transaction> findByHoldingIdOrderByDateAsc(String holdingId);
@Query("SELECT MIN(t.date) FROM Transaction t WHERE t.holdingId = :holdingId")
Optional<LocalDate> findEarliestTransactionDateByHoldingId(String holdingId);
```

#### 3. `DividendEventSyncService` 重写同步逻辑

**文件**：[DividendEventSyncService.java](file:///d:/mySpace/myDca/myPhonePro/fund-tracker-backend/src/main/java/com/fundtracker/service/DividendEventSyncService.java)

核心逻辑（伪代码）：
```java
for (Holding holding : holdings) {
    firstBuyDate = transactionService.getFirstTransactionDate(holding.getId());
    for (FundDividendRecord record : records) {
        // 先删旧的错误事件
        deleteIfExists(holdingId, registration, regDate);
        deleteIfExists(holdingId, ex_dividend, exDate);
        deleteIfExists(holdingId, payout, payDate);

        // 判断是否参与
        if (firstBuyDate != null && exDate >= firstBuyDate) {
            sharesAtDate = calculateSharesAtDate(holdingId, exDate);
            participated = sharesAtDate > 0;
        }

        // 全部创建（participated=false 时 amount=0）
        createEvent(..., sharesAtDate, participated);
    }
}
```

**Repository 新增方法**：
```java
// DividendEventRepository.java
int deleteByHoldingIdAndTypeAndDate(String holdingId, EventType type, LocalDate date);
```

#### 4. `EventService.toDTO` 透传 `participated`

**文件**：[EventService.java](file:///d:/mySpace/myDca/myPhonePro/fund-tracker-backend/src/main/java/com/fundtracker/service/EventService.java)

#### 5. 前端类型 + UI

| 文件 | 改动 |
|------|------|
| [types/index.ts](file:///d:/mySpace/myDca/myPhonePro/stitch_fund_dividend_tracker/src/types/index.ts) | `DividendEvent` 加 `participated: boolean` |
| [types/api.ts](file:///d:/mySpace/myDca/myPhonePro/stitch_fund_dividend_tracker/src/types/api.ts) | `DividendEventItem` 加 `participated: boolean` |
| [CalendarPage.vue](file:///d:/mySpace/myDca/myPhonePro/stitch_fund_dividend_tracker/src/views/calendar/CalendarPage.vue) | 未参与事件显示"未参与"标签而非金额 |
| [DividendHistoryPage.vue](file:///d:/mySpace/myDca/myPhonePro/stitch_fund_dividend_tracker/src/views/dividends/DividendHistoryPage.vue) | 未参与事件显示"未参与"标签而非金额 |

### 验证方式

1. 刷新分红日历页面 → 触发 `syncAllEvents()` 全量重新同步
2. 日历和分红历史页面中，首笔买入之前的分红显示"未参与"标签
3. 首笔买入之后的分红按当时份额计算正确金额

---

## P1 — 复投开关

### 目标
分红到账时，根据开关状态决定是计入现金还是自动再投资。

### 改动清单

#### 1. 后端：`Holding` 实体新增字段

```java
// Holding.java
@Column(name = "dividend_reinvest", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
private Boolean dividendReinvest = false;
```

数据库 DDL（需手动执行）：
```sql
ALTER TABLE holdings ADD COLUMN dividend_reinvest BOOLEAN NOT NULL DEFAULT FALSE;
```

#### 2. 后端：新增 API

```
PUT /api/holdings/{id}/dividend-reinvest
Content-Type: application/json

{
    "dividendReinvest": true
}
```

- `HoldingController.java` — 新增端点
- `UpdateHoldingReq.java` — 或单独 DTO

#### 3. 后端：修改 `EventService.markDistributed()`

```java
// 当前逻辑：
public void markDistributed(Long eventId) {
    DividendEvent event = findById(eventId);
    event.setStatus(EventStatus.DISTRIBUTED);
    
    // 累加 totalDividendReceived
    Holding holding = event.getHolding();
    holding.setTotalDividendReceived(
        holding.getTotalDividendReceived().add(event.getAmount()));
    
    // 调整现金
    manualAssetService.adjustCash(event.getAmount());
}

// 修改后的逻辑：
public void markDistributed(Long eventId) {
    DividendEvent event = findById(eventId);
    event.setStatus(EventStatus.DISTRIBUTED);
    
    Holding holding = event.getHolding();
    BigDecimal amount = event.getAmount();
    
    if (Boolean.TRUE.equals(holding.getDividendReinvest())) {
        // 复投模式：创建 reinvest 交易
        CreateTransactionReq req = new CreateTransactionReq();
        req.setHoldingId(holding.getId());
        req.setType(TransactionType.REINVEST);
        req.setDate(LocalDate.now());
        // reinvest 金额就是分红金额，perSharePrice 用当天净值
        // 需要查询当天或最近净值作为买入价格
        // ...
        transactionService.createTransaction(req);
        // reinvest 不调整现金
    } else {
        // 现金模式（现有逻辑）
        holding.setTotalDividendReceived(
            holding.getTotalDividendReceived().add(amount));
        manualAssetService.adjustCash(amount);
    }
}
```

**注意**：`REINVEST` 交易类型已定义在 `TransactionType` 枚举中，但尚未实际使用。`reinvest` 的份额计算方式：`quantity = amount / nav`，类似于 `buy` 但不涉及现金流水。

#### 4. 前端：复投开关 UI

**位置**：`DividendHistoryPage.vue` 或 `HoldingDetailPage.vue`

```
┌──────────────────────────────────────┐
│  分红复投         ○ 开  ● 关         │
│  开启后，分红到账时自动买入该标的份额 │
└──────────────────────────────────────┘
```

- 调用 `PUT /api/holdings/{id}/dividend-reinvest`
- 成功后刷新页面数据

---

## P2 — 分红看板

### 目标
创建一个分红看板页面，替代或增强现有的年度总览功能，围绕分红做核心数据展示。

**用户原话**："分红日历里的年度总览页面没有什么具体功能，我们可以用分红看板来替代年度总览，或者让年度总览成为分红看板的一个子功能。"

**注意**：用户关于第 2 点尚未补充完整，以下为初步方案，待用户确认后细调。

### 页面布局（初步）

```
┌─ 年度选择器 ──────────────────────────┐
│  ◀  2026  ▶    总分红 ¥3,450  ▲ +15%  │
├─ 月度分红柱状图 ──────────────────────┤
│   ██                                 │
│   ██ ██                              │
│   ██ ██ ██     ██                    │
│   ██ ██ ██  ██ ██  ██               │
│   1  2  3  4  5  6  7  8  9  10 11 12│
├─ 各标的占比（环形图）─────────────────┤
│   ┌────┐  红利低波 40%  ¥1,380       │
│   │    │  黄金ETF  35%  ¥1,208       │
│   │ P  │  中证红利 25%  ¥862         │
│   └────┘                             │
├─ 预计下次分红 ────────────────────────┤
│  华夏黄金ETF联接A                     │
│  预计派息日：2026-08-15               │
│  预计金额：¥123.45（当前份额 × 预估每份）│
├─ 年度明细列表 ────────────────────────┤
│  2026-03-10  红利低波  分红   ¥250   │
│  2026-06-15  黄金ETF   分红   ¥180   │
│  ...                                 │
└──────────────────────────────────────┘
```

### 数据来源

| 模块 | 数据源 | 说明 |
|------|--------|------|
| 总分红/同比 | `dividend_events` 按年份聚合 | 当前年份 vs 上一年 |
| 月度柱状图 | `dividend_events` 按月聚合 | 当年各月实际到账金额 |
| 标的占比 | `dividend_events` 按 holding 聚合 | 饼图/环形图展示各标的贡献 |
| 预计下次 | 最近的 `dividend_events`（pending） | 未到账的事件按当前份额重算 |
| 明细列表 | `dividend_events` 按日期排序 | 当年所有已到账记录 |

### 路由

```
/dividend-dashboard  （level 1，底部导航栏新增）
```

或：
```
/calendar → 分红日历 Tab + 分红看板 Tab（同级 Tab 切换）
/calendar/dashboard  （子路由）
```

### 与现有页面的关系

- **年度总览**：如果保留，作为看板的一个 Tab 或年度选择器的默认视图
- **分红日历**：保持独立，看板是日历的聚合分析视图
- **分红历史**（DividendHistoryPage）：定位为持仓维度的明细，看板是全局维度的聚合

---

## 实施时间线

```
P0 ─────── ████████████████  已完成（2026-07-22）
  ├ 工具方法 calculateSharesAtDate     已完成
  ├ DividendEventSyncService 重构       已完成
  ├ participated 标签体系               已完成
  └ 前后端 UI 适配                     已完成

P1 ─────── ████████████████░░░░  预期：1 天
  ├ Holding 加字段 + API               待实现
  ├ EventService 修改                   待实现
  └ 前端复投开关 UI                    待实现

P2 ─────── ██████████████████░░  预期：1.5 天
  ├ 后端聚合 API                       待实现
  ├ 看板页面布局 + 柱状图              待实现
  └ 环形图 + 预计分红 + 明细列表       待实现

注：实际耗时取决于用户补充的第 2 点需求
```

---

## 待确认事项

1. [ ] **分红看板的第 2 点需求** — 用户消息中断于"2 "，待补充
2. [x] **P0 方案已确认并实现** — 采用 participated 标签方案
3. [ ] **看板路由方案** — 独立页面（底部导航新入口）vs 日历子页面
4. [ ] **年化总览是否保留** — 还是完全被看板替代
5. [ ] **reinvest 价格来源** — 使用除权日净值？派息日净值？还是抓取当天净值？
6. [x] **已有历史错误数据处理** — 通过 force resync（先删后建）自动处理
