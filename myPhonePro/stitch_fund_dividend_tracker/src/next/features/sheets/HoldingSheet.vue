<script setup lang="ts">
/** 持仓详情 Sheet：份额/成本/市值/收益/分红口径 + 分红再投开关 + 加仓/卖出入口。 */
import { computed } from 'vue'
import { getHolding, updateDividendReinvest, type HoldingItem } from '@/api/holding'
import { getAnnualizedReturn, type AnnualizedReturn } from '@/api/assetHistory'
import SheetFrame from '@/next/shared/components/SheetFrame.vue'
import DataRow from '@/next/shared/components/DataRow.vue'
import SwitchKey from '@/next/shared/components/SwitchKey.vue'
import BtnKey from '@/next/shared/components/BtnKey.vue'
import SkeletonRows from '@/next/shared/components/SkeletonRows.vue'
import ErrorState from '@/next/shared/components/ErrorState.vue'
import { useAsyncData } from '@/next/shared/useAsync'
import { closeSheet, openSheet, refreshData, toast } from '@/next/shared/ui'
import { deltaTone, fmtMoney, fmtNav, fmtPercent, fmtSignedMoney, fmtShares, toNumber } from '@/next/shared/money'

const props = defineProps<{
  holdingId: string
  holding?: HoldingItem
}>()

interface HoldingDetail {
  holding: HoldingItem
  annualized: AnnualizedReturn | null
}

const asyncData = useAsyncData<HoldingDetail>(async () => {
  const holding = props.holding ?? (await getHolding(props.holdingId))
  const annualized = await getAnnualizedReturn(props.holdingId).catch(() => null)
  return { holding, annualized }
})

const holding = computed(() => asyncData.data.value?.holding ?? props.holding ?? null)
const annualized = computed(() => asyncData.data.value?.annualized ?? null)

const profit = computed(() => {
  const h = holding.value
  if (!h) return 0
  return toNumber(h.marketValue) - toNumber(h.cost)
})

const profitTone = computed(() => deltaTone(profit.value))

const annualizedText = computed(() => {
  const a = annualized.value
  if (!a) return '—'
  if (a.status === 'insufficient') return '数据不足'
  if (a.status === 'abnormal') return '异常'
  if (a.irr !== null && a.irr !== undefined) return fmtPercent(a.irr * 100)
  if (a.annualizedReturn !== null && a.annualizedReturn !== undefined) return fmtPercent(a.annualizedReturn)
  return '—'
})

async function toggleReinvest(value: boolean): Promise<void> {
  const h = holding.value
  if (!h) return
  try {
    const updated = await updateDividendReinvest(h.id, value)
    if (asyncData.data.value) asyncData.data.value.holding = updated
    toast(value ? '已开启分红再投' : '已关闭分红再投')
    refreshData()
  } catch (e) {
    toast(e instanceof Error ? e.message : '操作失败', 'error')
  }
}

function trade(type: 'buy' | 'sell'): void {
  const h = holding.value
  if (!h) return
  openSheet('transaction', { mode: 'create', type, holdingId: h.id, holdingName: h.name })
}
</script>

<template>
  <SheetFrame title="持仓详情" @close="closeSheet">
    <SkeletonRows v-if="asyncData.loading.value && !holding" :rows="3" />
    <ErrorState
      v-else-if="asyncData.error.value && !holding"
      :message="asyncData.error.value"
      @retry="asyncData.load()"
    />
    <template v-else-if="holding">
      <div class="hero">
        <div class="hero-head">
          <h3 class="name">{{ holding.name }}</h3>
          <span class="micro">{{ holding.code }} · {{ holding.type }}</span>
        </div>
        <div class="value num">{{ fmtMoney(holding.marketValue) }}</div>
        <p class="micro">持有 {{ fmtShares(holding.shares) }} · 净值 {{ fmtNav(holding.latestPrice) }}（{{ holding.priceDate || '—' }}）</p>
      </div>

      <div class="rows">
        <DataRow label="市值" :value="fmtMoney(holding.marketValue)" />
        <DataRow label="成本" :value="fmtMoney(holding.cost)" />
        <DataRow label="盈亏" :value="fmtSignedMoney(profit)" :tone="profitTone" />
        <DataRow label="预测年分红" :value="fmtMoney(holding.predictedDividend)" />
        <DataRow label="成本息率" :value="fmtPercent(holding.dividendRate)" />
        <DataRow label="市值息率" :value="fmtPercent(holding.priceDividendRate)" />
        <DataRow label="累计已收分红" :value="fmtMoney(holding.totalDividendReceived)" />
        <DataRow label="分红回收率" :value="fmtPercent(holding.dividendRecoveryRate)" />
        <DataRow label="预估回本" :value="`${holding.estimatedRecoveryYears ?? '—'} 年`" />
        <DataRow label="年化收益（IRR）" :value="annualizedText" />
        <div class="switch-row">
          <span class="label-text">分红再投</span>
          <SwitchKey
            :model-value="Boolean(holding.dividendReinvest)"
            label="分红再投"
            @update:model-value="toggleReinvest"
          />
        </div>
      </div>

      <div class="actions">
        <BtnKey variant="secondary" block @click="trade('buy')">加仓</BtnKey>
        <BtnKey variant="secondary" block @click="trade('sell')">卖出</BtnKey>
      </div>
      <p class="note micro">
        盈亏 = 市值 − 成本（展示口径）；成本算法：{{ holding.costAlgorithm || 'diluted' }}。分红再投只影响后续事件处理，不改历史。
      </p>
    </template>
  </SheetFrame>
</template>

<style scoped>
.hero {
  padding: var(--space-2) 0 var(--space-3);
}
.hero-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
}
.name {
  font-size: var(--text-heading);
  font-weight: 600;
}
.value {
  font-size: var(--text-readout-lg);
  line-height: 1.1;
  font-weight: 600;
  letter-spacing: -0.01em;
  margin: var(--space-2) 0 var(--space-1);
}
.rows {
  margin-top: var(--space-2);
}
.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 56px;
  border-bottom: 1px solid var(--hairline);
}
.label-text {
  font-size: var(--text-label);
  color: var(--ink-2);
}
.actions {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-5);
}
.note {
  margin-top: var(--space-3);
  line-height: 1.6;
}
</style>
