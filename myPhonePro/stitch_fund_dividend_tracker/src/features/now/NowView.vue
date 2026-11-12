<script setup lang="ts">
/**
 * 现在面：一屏只回答「此刻我有多少」。
 * 读数 → 持仓/现金/成本 → 构成 → 账户 → 今日事件；没有卡片，全部印在同一块表面上。
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import Readout from '@/shared/components/Readout.vue'
import SectionLabel from '@/shared/components/SectionLabel.vue'
import DataRow from '@/shared/components/DataRow.vue'
import CompositionBar from '@/shared/components/CompositionBar.vue'
import EventRow from '@/shared/components/EventRow.vue'
import EmptyState from '@/shared/components/EmptyState.vue'
import ErrorState from '@/shared/components/ErrorState.vue'
import SkeletonRows from '@/shared/components/SkeletonRows.vue'
import { useNowData } from './useNowData'
import { fmtDelta, fmtMoney, fmtPercent, deltaTone, toNumber } from '@/shared/money'
import { openSheet } from '@/shared/ui'
import { labelOf, ACCOUNT_PURPOSE_LABEL } from '@/shared/dict'

const router = useRouter()
const { data, loading, error, load, totalValue, holdingValue, cashValue, costValue, weekly, composition, compositionSegments, todayEvents, isTotallyEmpty, latestPriceDate } = useNowData()

const weekText = computed(() => {
  const w = weekly.value
  if (!w) return ''
  return `${fmtDelta(w.change, w.percent)}`
})

const weekTone = computed(() => {
  const v = toNumber(weekly.value?.change)
  return v > 0 ? 'up' : v < 0 ? 'down' : 'flat'
})

const monthly = computed(() => data.value?.valueChange?.periods?.month ?? null)
const monthTone = computed(() => deltaTone(monthly.value?.change))
const monthText = computed(() => {
  const m = monthly.value
  return m ? `较上月收益 ${fmtPercent(m.percent, 2, true)}` : ''
})

function openComposition(): void {
  openSheet('composition', { groups: composition.value, totalValue: totalValue.value })
}

function openEvent(index: number): void {
  const ev = todayEvents.value[index]
  if (ev) openSheet('event', { event: ev })
}

function openAccounts(): void {
  openSheet('settings')
}
</script>

<template>
  <div class="now">
    <template v-if="loading && !data">
      <SkeletonRows :rows="4" />
    </template>
    <ErrorState v-else-if="error && !data" :message="error" @retry="load()" />
    <template v-else-if="isTotallyEmpty">
      <EmptyState title="还没有资产" description="先用「记一笔」添加持仓或现金，这里会变成你的资产总览。">
        <button class="inline-link" type="button" @click="openSheet('action')">添加第一笔资产 ›</button>
      </EmptyState>
    </template>
    <template v-else-if="data">
      <Readout
        label="总资产"
        :amount="fmtMoney(totalValue)"
        :delta-text="weekText"
        :tone="weekTone"
        caption="本周收益"
      />
      <p v-if="monthText" class="period-line micro" :class="monthTone">{{ monthText }}</p>

      <div class="stat-line">
        <div class="stat">
          <span class="micro">持仓市值</span>
          <b class="stat-value num">{{ fmtMoney(holdingValue) }}</b>
        </div>
        <div class="stat">
          <span class="micro">现金</span>
          <b class="stat-value num">{{ fmtMoney(cashValue) }}</b>
        </div>
        <div class="stat">
          <span class="micro">总成本</span>
          <b class="stat-value num">{{ fmtMoney(costValue) }}</b>
        </div>
      </div>
      <p class="data-note micro">
        <template v-if="latestPriceDate">净值日期 {{ latestPriceDate }} · </template>数据更新 {{ data.loadedAt }}
      </p>

      <div class="divider" />

      <SectionLabel text="构成">
        <template #action>
          <button class="inline-link" type="button" @click="openComposition">全部 ›</button>
        </template>
      </SectionLabel>
      <CompositionBar v-if="compositionSegments.length" :segments="compositionSegments" />
      <div v-else class="micro" style="padding: 8px 0">暂无构成数据</div>
      <DataRow
        v-for="group in composition"
        :key="group.key"
        :label="group.label"
        :meta="fmtPercent(group.percentage, 1)"
        :dot-color="group.color"
        :value="fmtMoney(group.value)"
      />

      <div class="divider" />

      <SectionLabel text="账户" />
      <DataRow
        v-for="account in data.accounts"
        :key="account.id"
        :label="account.name"
        :meta="`${labelOf(ACCOUNT_PURPOSE_LABEL, account.purpose) || '账户'} · ${account.assetCount} 项`"
        :value="fmtMoney(account.totalValueCny)"
        tap
        @click="openAccounts"
      />

      <div class="divider" />

      <SectionLabel text="今日事件">
        <template #action>
          <button class="inline-link" type="button" @click="router.push({ name: 'past' })">全部 ›</button>
        </template>
      </SectionLabel>
      <template v-if="todayEvents.length">
        <EventRow
          v-for="(ev, idx) in todayEvents"
          :key="ev.id"
          :event="ev"
          tap
          @click="openEvent(idx)"
        />
      </template>
      <EmptyState
        v-else
        compact
        title="今天还没有资金事件"
        description="买入、卖出、分红到账、定投执行会出现在这里。"
      />

      <div class="divider" />

      <SectionLabel text="年度概览" />
      <DataRow label="今年已收分红" :value="fmtMoney(data.dashboard.totalDividendReceived)" />
      <DataRow label="预测年分红" :value="fmtMoney(data.dashboard.predictedAnnualDividend)" />
      <DataRow
        label="成本息率"
        :value="fmtPercent(data.dashboard.overallDividendRate)"
      />
      <DataRow label="持仓数量" :value="`${data.dashboard.totalHoldings} 个`" />
    </template>
  </div>
</template>

<style scoped>
.now {
  padding-bottom: var(--space-8);
}
.period-line {
  margin-top: var(--space-1);
}
.stat-line {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-2);
  margin-top: var(--space-5);
}
.stat {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 0;
}
.stat-value {
  font-size: var(--text-num-sm);
  font-weight: 500;
  color: var(--ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.data-note {
  margin-top: var(--space-2);
  color: var(--ink-3);
}
.divider {
  height: 1px;
  background: var(--hairline);
  margin: var(--space-5) 0 var(--space-4);
}
.inline-link {
  font-size: var(--text-micro);
  color: var(--accent);
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  padding: 0 2px;
}
</style>
