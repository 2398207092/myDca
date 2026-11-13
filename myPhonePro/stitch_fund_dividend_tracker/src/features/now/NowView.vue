<script setup lang="ts">
/**
 * 现在面：一屏只回答「此刻我有多少」。
 * 深度语法：主读数在海平面（0）；构成 / 账户 / 今日事件各下沉一个台阶（-1），
 * 台阶是结构、不可点；台阶内的可点项只用「平面 + chevron + 按压高亮」，不冒充按钮。
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import Readout from '@/shared/components/Readout.vue'
import SeamDivider from '@/shared/components/SeamDivider.vue'
import TerraceSection from '@/shared/components/TerraceSection.vue'
import DataRow from '@/shared/components/DataRow.vue'
import CompositionBar from '@/shared/components/CompositionBar.vue'
import EventRow from '@/shared/components/EventRow.vue'
import EmptyState from '@/shared/components/EmptyState.vue'
import ErrorState from '@/shared/components/ErrorState.vue'
import SkeletonRows from '@/shared/components/SkeletonRows.vue'
import { useNowData } from './useNowData'
import { fmtDelta, fmtMoney, fmtPercent, toNumber } from '@/shared/money'
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
      <!-- 海平面：主读数 + 三格状态 + 数据日期 -->
      <Readout
        label="总资产"
        :amount="fmtMoney(totalValue)"
        :delta-text="weekText"
        :tone="weekTone"
        caption="本周收益"
      />

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

      <SeamDivider />

      <!-- 台阶 1：构成（展示为主，整块不可点；只有右侧「全部」是动作） -->
      <TerraceSection label="构成">
        <template #action>
          <button class="inline-link" type="button" @click="openComposition">全部 ›</button>
        </template>
        <CompositionBar v-if="compositionSegments.length" :segments="compositionSegments" />
        <div v-else class="empty-line micro">暂无构成数据</div>
        <DataRow
          v-for="group in composition"
          :key="group.key"
          :label="group.label"
          :meta="fmtPercent(group.percentage, 1)"
          :dot-color="group.color"
          :value="fmtMoney(group.value)"
        />
      </TerraceSection>

      <!-- 台阶 2：账户（可导航行：平面 + chevron + 按压高亮，不做凸起） -->
      <TerraceSection label="账户">
        <DataRow
          v-for="account in data.accounts"
          :key="account.id"
          :label="account.name"
          :meta="`${labelOf(ACCOUNT_PURPOSE_LABEL, account.purpose) || '账户'} · ${account.assetCount} 项`"
          :value="fmtMoney(account.totalValueCny)"
          tap
          @click="openAccounts"
        />
      </TerraceSection>

      <!-- 台阶 3：今日事件（事件行可点进详情，同样是行语义） -->
      <TerraceSection label="今日事件">
        <template #action>
          <button class="inline-link" type="button" @click="router.push({ name: 'past' })">全部 ›</button>
        </template>
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
      </TerraceSection>
    </template>
  </div>
</template>

<style scoped>
.now {
  padding-bottom: var(--space-8);
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
.empty-line {
  padding: var(--space-2) 0;
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
