# 成本算法修复方案

> 基于代码审计发现的三项问题，按优先级排列如下。

---

## 🔴 P0：修复 `weighted_avg` 的 `netInvestment` 逻辑缺陷

### 问题
`CostCalculator.java` 第 78 行，`weighted_avg` 模式的净投入直接返回 `totalBuy`（总买入金额），完全忽略卖出交易。

```java
case weighted_avg:
    return totalBuy;  // ❌ 卖出交易被无视
```

导致：
- **回本进度被压低**：`dividendRecoveryRate = totalDividend / totalBuy`（分母虚高）
- **预计回本年限被拉长**：`(totalBuy - totalDividend) / predictedAnnualDividend`
- **示例失真**：买入 10000 → 卖出 6000 → 分红 500，净投入实际 3500，但显示 10000

### 修改

**文件**：`fund-tracker-backend/.../service/CostCalculator.java`

```java
case weighted_avg:
    // 加权平均：至少扣除卖出金额，保持净投入合理
    return totalBuy.subtract(totalSell).max(BigDecimal.ZERO);
```

**逻辑**：
- 净投入 = 总买入 - 总卖出（不低于 0）
- 不加分红冲减（与 `weighted_avg` 的保守定位一致）
- `max(0)` 防止卖出超过买入时出现负数

**影响范围**：
- `recalculateHoldingMetrics` 中的 `netInvestment` → `dividendRecoveryRate` → `estimatedRecoveryYears`
- 后端返回给前端的 `HoldingDTO.netInvestment` 自动修正
- 前端无需改动

---

## 🟠 P1：负成本时的息率展示优化

### 问题
当使用 `diluted` 算法且累计分红超过净投入时（`totalDividend > totalBuy - totalSell`）：
- `costPerShare` 变为负值
- `dividendRate = predictedDividendPerShare / costPerShare` 变为**负百分比**
- 前端直接展示 `-3.5%`，用户困惑（实际上分红是正收益，成本已全部收回）

涉及三个展示点：

| 位置 | 当前代码 | 问题 |
|------|---------|------|
| 持仓详情页 Hero 卡片 | `{{ holding.dividendRate }}%` | 可能为负 |
| 持仓详情页 Hero 卡片 | `{{ holding.priceDividendRate }}%` | 可能为负 |
| 首页持仓卡片 | `{{ holding.dividendRate.toFixed(2) + '%' }}` | 可能为负 |
| 持仓详情页成本行 | `¥{{ costPerShareFormatted }}/份` | 可能为负 |

### 修改方案

#### 方案 A（推荐）：后端统一处理

**文件**：`CostCalculator.java` 的 `calculateDividendRate()` 方法

```java
public BigDecimal calculateDividendRate(BigDecimal predictedDividendPerShare,
                                         BigDecimal costPerShare) {
    if (costPerShare.compareTo(BigDecimal.ZERO) <= 0) {
        // 成本已收回，息率标记为 -1 供前端特殊展示
        return new BigDecimal("-1");
    }
    if (costPerShare.compareTo(BigDecimal.ZERO) == 0) {
        return BigDecimal.ZERO;
    }
    return predictedDividendPerShare.divide(costPerShare, 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_UP);
}
```

同理处理 `calculatePriceDividendRate()`：

```java
public BigDecimal calculatePriceDividendRate(BigDecimal predictedDividendPerShare,
                                              BigDecimal latestPrice) {
    if (latestPrice.compareTo(BigDecimal.ZERO) <= 0) {
        return new BigDecimal("-1");
    }
    ...
}
```

**前端处理**（三个展示点统一处理）：

**HoldingDetailPage.vue** — 新增 computed：

```ts
// 成本已收回判断
const isCostRecovered = computed(() => {
  return holding.value?.costPerShare != null && holding.value.costPerShare <= 0
})

// 息率展示（特殊值 -1 → "已收回"）
const dividendRateDisplay = computed(() => {
  const rate = holding.value?.dividendRate
  if (rate == null) return '--'
  if (rate === -1) return '已收回'
  return rate.toFixed(2) + '%'
})
const priceDividendRateDisplay = computed(() => {
  const rate = holding.value?.priceDividendRate
  if (rate == null) return '--'
  if (rate === -1) return '已收回'
  return rate.toFixed(2) + '%'
})

// 成本展示（负值时特殊处理）
const costPerShareDisplay = computed(() => {
  if (!holding.value) return '0.0000'
  if (holding.value.costPerShare <= 0) return '成本已收回'
  return '¥' + holding.value.costPerShare.toLocaleString('zh-CN', { minimumFractionDigits: 4, maximumFractionDigits: 4 })
})
```

模板替换：

```html
<!-- 成本息率 -->
<span class="font-display text-xs font-semibold"
      :class="holding.dividendRate === -1 ? 'text-success' : 'text-brand'">
  {{ dividendRateDisplay }}
</span>

<!-- 股价息率 -->
<span class="font-display text-xs font-semibold"
      :class="holding.priceDividendRate === -1 ? 'text-success' : 'text-brand'">
  {{ priceDividendRateDisplay }}
</span>

<!-- 成本展示 -->
<p class="font-display text-md font-semibold text-text-primary mt-0.5">
  {{ costPerShareDisplay }}
</p>
```

**HomePage.vue** — 持仓列表的股息率：

```ts
// 首页列表也需要这个判断
function dividendRateText(rate: number | undefined | null): string {
  if (rate == null) return '--'
  if (rate === -1) return '已收回'
  return rate.toFixed(2) + '%'
}
```

```html
<p class="font-body text-xs font-medium tabular-nums"
   :class="holding.dividendRate === -1 ? 'text-success' : 'text-brand'">
  {{ dividendRateText(holding.dividendRate) }}
</p>
```

#### 方案 B（更简单）：前端只判断 `costPerShare <= 0`

如果不想改后端，纯前端判断 `holding.costPerShare <= 0` 即可，但后端返回的 `dividendRate` 仍然是负值，前端只是不展示它。

---

## 🟡 P2：添加算法说明气泡

### 问题
添加标的页面的三种成本算法只有三个按钮 + 一个帮助图标，无任何说明，用户难以理解差异。

**文件**：`HoldingAddPage.vue` 第 276-284 行

### 修改

将帮助图标改为可点击的说明弹窗（tooltip popover）：

```html
<!-- 当前成本算法选择器 -->
<div class="flex items-center gap-2">
  <label class="font-body text-xs font-medium text-error whitespace-nowrap">*当前成本</label>
  <div class="flex-1 flex bg-card-alt rounded-lg p-[3px] max-w-[280px]">
    <button ...>分红摊薄</button>
    <button ...>摊薄成本</button>
    <button ...>加权平均</button>
  </div>
  <!-- 帮助图标 + 气泡 -->
  <div class="relative" @click.stop="showAlgorithmHelp = !showAlgorithmHelp">
    <span class="material-symbols-outlined text-text-tertiary text-[18px] cursor-pointer hover:text-brand transition-colors">help</span>
    <Transition name="fade">
      <div v-if="showAlgorithmHelp"
           class="absolute right-0 top-8 w-64 bg-card-bg rounded-xl p-md card-shadow border border-border-light z-50"
           @click.stop>
        <div class="space-y-3">
          <div>
            <p class="font-body text-xs font-medium text-text-primary mb-1">分红摊薄</p>
            <p class="font-body text-[11px] text-text-tertiary leading-relaxed">收到的分红会冲减持仓成本，成本越来越低，息率越来越高。适合长期收息视角。</p>
          </div>
          <div>
            <p class="font-body text-xs font-medium text-text-primary mb-1">摊薄成本</p>
            <p class="font-body text-[11px] text-text-tertiary leading-relaxed">只看买入卖出净额，分红不参与成本计算。折中方案，息率相对保守。</p>
          </div>
          <div>
            <p class="font-body text-xs font-medium text-text-primary mb-1">加权平均</p>
            <p class="font-body text-[11px] text-text-tertiary leading-relaxed">只看买入总金额和总份额，不考虑卖出和分红。最保守，成本永远为正。</p>
          </div>
        </div>
        <div class="mt-2 pt-2 border-t border-border-light">
          <p class="font-body text-[10px] text-text-tertiary">默认使用「分红摊薄」，可在持仓详情页随时切换</p>
        </div>
      </div>
    </Transition>
  </div>
</div>
```

新增响应式状态：

```ts
const showAlgorithmHelp = ref(false)
// 点击外部关闭
function handleClickOutside(e: MouseEvent) {
  if (showAlgorithmHelp.value) showAlgorithmHelp.value = false
}
onMounted(() => document.addEventListener('click', handleClickOutside))
onUnmounted(() => document.removeEventListener('click', handleClickOutside))
```

---

## 修改文件清单

| # | 文件 | 改动 |
|---|------|------|
| 1 | `CostCalculator.java` | `weighted_avg` 的 `netInvestment` 修复 |
| 2 | `CostCalculator.java` | `calculateDividendRate` / `calculatePriceDividendRate` 负成本返回 -1 |
| 3 | `HoldingDetailPage.vue` | 负成本息率展示优化 + 成本展示 |
| 4 | `HomePage.vue` | 持仓列表股息率负成本展示 |
| 5 | `HoldingAddPage.vue` | 算法说明气泡 |

---

## 不动的地方

- **前端 API 类型**（`api.ts` / `holding.ts`）：`dividendRate` 字段类型不变，仍是 `number`
- **`HoldingDTO.java`**：字段不变
- **其他页面**（DiscoverPage、CalendarPage 等）：不涉及成本算法展示
