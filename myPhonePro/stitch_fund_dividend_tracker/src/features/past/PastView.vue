<script setup lang="ts">
/**
 * 过去面：一条统一的「钱的痕迹」。
 * 月份切换 → 月汇总（买入/卖出/分红到账）→ 近 30 天走势 → 筛选 chips → 按日分组事件流。
 * 数据全部来自真实接口；交易、分红、定投执行统一成同一条时间轴。
 */
import { computed, ref } from 'vue'
import SectionLabel from '@/shared/components/SectionLabel.vue'
import EventRow from '@/shared/components/EventRow.vue'
import TrendLine from '@/shared/components/TrendLine.vue'
import EmptyState from '@/shared/components/EmptyState.vue'
import ErrorState from '@/shared/components/ErrorState.vue'
import SkeletonRows from '@/shared/components/SkeletonRows.vue'
import MonthSwitcher from './components/MonthSwitcher.vue'
import MonthSummary from './components/MonthSummary.vue'
import FilterChips from './components/FilterChips.vue'
import { PAST_FILTERS, usePastData, type PastFilter } from './usePastData'
import { currentMonth, dateGroupLabel } from '@/shared/date'
import { fmtMoney } from '@/shared/money'
import { openSheet } from '@/shared/ui'
import type { MoneyEvent } from '@/shared/stream'

const month = ref(currentMonth())
const filter = ref<PastFilter>('all')
const past = usePastData(month, filter)

const {
  monthText,
  isCurrentMonth,
  canGoNext,
  goPrev,
  goNext,
  goCurrent,
  ready,
  loading,
  error,
  load,
  stats,
  filterCounts,
  groups,
  filteredEvents,
  emptyMonth,
  trendLoading,
  trendError,
  trendData,
  trendPoints,
  trendHasData,
  trendFirst,
  trendLast,
  trendLoad,
} = past

function openEvent(event: MoneyEvent): void {
  openSheet('event', { event })
}

const flowCountText = computed(() =>
  loading.value ? '加载中…' : `共 ${filteredEvents.value.length} 条`,
)

const trendFootRight = computed(() => {
  const last = trendLast.value
  return last ? `最新 ${fmtMoney(last.value)} · ${last.label}` : ''
})
</script>

<template>
  <div class="past">
    <MonthSwitcher
      :month="month"
      :can-next="canGoNext"
      :is-current="isCurrentMonth"
      @prev="goPrev"
      @next="goNext"
      @current="goCurrent"
    />

    <template v-if="loading && !ready">
      <SkeletonRows :rows="4" />
    </template>
    <ErrorState v-else-if="error && !ready" :message="error" @retry="load()" />
    <template v-else-if="ready">
      <MonthSummary :stats="stats" :month-text="monthText" :is-current="isCurrentMonth" />

      <div class="divider" />

      <SectionLabel text="近 30 天资产走势">
        <template #action>
          <span class="micro">快照口径</span>
        </template>
      </SectionLabel>
      <template v-if="trendLoading && !trendData">
        <SkeletonRows :rows="2" />
      </template>
      <template v-else-if="trendHasData">
        <TrendLine :points="trendPoints" :height="72" aria-label="近 30 天总资产走势" />
        <div class="trend-foot">
          <span class="micro">{{ trendFirst?.label ?? '' }}</span>
          <span class="micro">{{ trendFootRight }}</span>
        </div>
      </template>
      <EmptyState
        v-else-if="trendError && !trendData"
        compact
        title="走势加载失败"
        :description="trendError"
      >
        <button class="text-key" type="button" @click="trendLoad()">重新加载</button>
      </EmptyState>
      <EmptyState
        v-else
        compact
        title="暂无走势数据"
        description="资产快照生成后，这里会显示近 30 天的总资产变化。"
      />

      <div class="divider" />

      <FilterChips
        :model-value="filter"
        :options="PAST_FILTERS"
        :counts="filterCounts"
        @update:model-value="filter = $event"
      />

      <SectionLabel :text="`${monthText} 流水`">
        <template #action>
          <span class="micro">{{ flowCountText }}</span>
        </template>
      </SectionLabel>

      <template v-if="groups.length">
        <section v-for="group in groups" :key="group.date" class="day">
          <h3 class="day-label micro">
            <span>{{ dateGroupLabel(group.date) }}</span>
            <span class="day-count">{{ group.items.length }} 条</span>
          </h3>
          <EventRow
            v-for="event in group.items"
            :key="event.id"
            :event="event"
            tap
            @click="openEvent(event)"
          />
        </section>
      </template>
      <EmptyState
        v-else-if="emptyMonth"
        compact
        title="这个月没有记录"
        description="用底部「＋」记一笔买入 / 分红，或切换月份查看历史。"
      >
        <button class="text-key" type="button" @click="openSheet('action')">记一笔 ›</button>
      </EmptyState>
      <EmptyState
        v-else
        compact
        title="当前筛选没有记录"
        description="切换到「全部」查看本月全部流水。"
      />

      <p v-if="error && ready" class="inline-error" role="alert">
        <span>{{ error }}</span>
        <button class="text-key" type="button" @click="load()">重试</button>
      </p>
    </template>
  </div>
</template>

<style scoped>
.past {
  padding-top: var(--space-1);
  padding-bottom: var(--space-8);
}
.divider {
  height: 1px;
  background: var(--hairline);
  margin: var(--space-5) 0 var(--space-4);
}
.trend-foot {
  display: flex;
  justify-content: space-between;
  gap: var(--space-2);
  margin-top: var(--space-2);
}
.trend-foot .micro {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.day {
  margin-top: var(--space-2);
}
.day-label {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
  padding: var(--space-2) 0 var(--space-1);
}
.day-count {
  color: var(--ink-3);
}
.text-key {
  min-height: 44px;
  padding: 0 18px;
  border-radius: var(--radius-key);
  border: 1px solid var(--edge);
  background: var(--canvas);
  box-shadow: var(--el-key);
  color: var(--accent);
  font-size: var(--text-label);
  font-weight: 600;
}
.text-key:active {
  box-shadow: var(--press);
}
.inline-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-top: var(--space-4);
  padding: var(--space-2) var(--space-3);
  border: 1px solid rgba(166, 27, 27, 0.28);
  border-radius: var(--radius-key);
  background: var(--surface-1);
  color: var(--ink);
  font-size: var(--text-label);
  line-height: 1.5;
}
</style>
