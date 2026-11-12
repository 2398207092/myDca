<script setup lang="ts">
/**
 * 单只持仓历史（卡片体系 · 2026-11-11）
 * 一章一张卡（AurosChapter = .au-card）：01 VALUE 市值走势 / 02 TREND 走势 / 03 SNAPSHOTS 快照记录。
 * 卡内只用 AurosStat / AurosRail / AurosTrend / AurosGroup / AurosRow / AurosDisclosure；
 * 金额/百分比/数量统一走 @/auros-skin/utils/format（¥+千分位+两位小数，不做万/亿压缩）。
 * 业务逻辑、接口与计算口径未变。
 */
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  getHoldingSeries,
  getAnnualizedReturn,
  type HoldingSeries,
  type AnnualizedReturn,
  type HoldingPoint,
  type HistoryRange,
} from '@/api/assetHistory'
import { animateValue } from '@/auros-skin/utils/auros-motion'
import { formatMoney, formatPercent, formatQty } from '@/auros-skin/utils/format'
import AurosNavBar from '@/auros-skin/components/auros/AurosNavBar.vue'
import AurosState from '@/auros-skin/components/auros/AurosState.vue'
import AurosChapter from '@/auros-skin/components/auros/AurosChapter.vue'
import AurosStat from '@/auros-skin/components/auros/AurosStat.vue'
import AurosRail from '@/auros-skin/components/auros/AurosRail.vue'
import AurosTrend from '@/auros-skin/components/auros/AurosTrend.vue'
import AurosGroup from '@/auros-skin/components/auros/AurosGroup.vue'
import AurosRow from '@/auros-skin/components/auros/AurosRow.vue'
import AurosDisclosure from '@/auros-skin/components/auros/AurosDisclosure.vue'

const route = useRoute()
const holdingId = computed(() => route.params.id as string)

// 页面状态
const pageState = ref<'loading' | 'ready' | 'error'>('loading')
const errorMsg = ref('')
const range = ref<HistoryRange>('month')

// 数据
const holdingSeries = ref<HoldingSeries | null>(null)
const annualized = ref<AnnualizedReturn | null>(null)
const chartTab = ref<'value' | 'shares' | 'profit'>('value')

// 走势点选
const trendHit = ref<HTMLElement | null>(null)
const crosshairActive = ref(false)
const crosshairIdx = ref(-1)

const ranges: { key: HistoryRange; label: string }[] = [
  { key: 'month', label: '近 30 天' },
  { key: 'quarter', label: '近 90 天' },
  { key: 'all', label: '全部' },
]
const metrics: { key: 'value' | 'shares' | 'profit'; label: string }[] = [
  { key: 'value', label: '市值' },
  { key: 'shares', label: '份额' },
  { key: 'profit', label: '收益' },
]

// 日期格式化（金额/百分比/数量统一走 @/auros-skin/utils/format）
function fmtDate(dateStr: string): string {
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}
function fmtFullDate(dateStr: string): string {
  const d = new Date(dateStr)
  return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()}`
}
/** 行内状态点：涨 lit / 跌 dim / 平或首次 none */
function rowMark(v: number | null): 'none' | 'lit' | 'dim' {
  if (v == null || v === 0) return 'none'
  return v > 0 ? 'lit' : 'dim'
}
/** 跌用银灰（muted）文字，涨/平保持默认 */
function rowTone(v: number | null): 'default' | 'muted' {
  return v != null && v < 0 ? 'muted' : 'default'
}

// ========== 逐次快照变化计算（口径与原页面一致） ==========
interface SnapshotChange {
  idx: number
  prev: HoldingPoint
  curr: HoldingPoint
  days: number
  mvChange: number
  mvChangePct: number
  sharesChange: number
  sharesChangePct: number
}
const snapshotChanges = computed<SnapshotChange[]>(() => {
  const s = holdingSeries.value?.series
  if (!s || s.length < 2) return []
  const changes: SnapshotChange[] = []
  for (let i = 1; i < s.length; i++) {
    const prev = s[i - 1]
    const curr = s[i]
    const days = Math.round((new Date(curr.date).getTime() - new Date(prev.date).getTime()) / 86400000)
    changes.push({
      idx: i,
      prev,
      curr,
      days,
      mvChange: curr.marketValue - prev.marketValue,
      mvChangePct: prev.marketValue !== 0 ? ((curr.marketValue - prev.marketValue) / Math.abs(prev.marketValue)) * 100 : 0,
      sharesChange: curr.shares - prev.shares,
      sharesChangePct: prev.shares !== 0 ? ((curr.shares - prev.shares) / Math.abs(prev.shares)) * 100 : 0,
    })
  }
  return changes.reverse() // 最新在前
})

const seriesPoints = computed(() => holdingSeries.value?.series ?? [])
const hasSeries = computed(() => seriesPoints.value.length > 0)
const latestPoint = computed(() => seriesPoints.value[seriesPoints.value.length - 1] ?? null)

// ========== 章 01：读数头 ==========
const heroDisp = ref(0)
let heroCancel: (() => void) | null = null
/** 主数给精确两位小数：金额准确是这个 App 的底线，不做万元压缩 */
const statValue = computed(() => formatMoney(Math.max(0, heroDisp.value)))

const rangeDelta = computed(() => {
  const s = seriesPoints.value
  if (s.length < 2) return null
  const first = s[0].marketValue
  const last = s[s.length - 1].marketValue
  const change = last - first
  const pct = first !== 0 ? (change / Math.abs(first)) * 100 : 0
  return { change, pct }
})
/** 区间变化放 caption：精确值只出现一次，变化也只出现一次（不出组件内置 delta 行） */
const statCaption = computed(() =>
  rangeDelta.value
    ? `当前市值 · 区间 ${formatMoney(rangeDelta.value.change, { sign: true })} · ${formatPercent(rangeDelta.value.pct)}`
    : '',
)
const trendConclusion = computed(() => {
  const v = rangeDelta.value?.change ?? 0
  return v > 0 ? '较期初上行' : v < 0 ? '较期初回落' : '较期初持平'
})
const rail = computed(() => {
  const vals = seriesPoints.value.map((p) => p.marketValue)
  if (!vals.length) return { pct: 0, min: 0, max: 0 }
  const min = Math.min(...vals)
  const max = Math.max(...vals)
  const cur = vals[vals.length - 1]
  const pct = max === min ? 50 : Math.min(100, Math.max(0, ((cur - min) / (max - min)) * 100))
  return { pct, min, max }
})
function playHero() {
  heroCancel?.()
  const target = latestPoint.value?.marketValue ?? 0
  heroDisp.value = 0
  heroCancel = animateValue(0, target, 800, (v) => (heroDisp.value = v))
}

interface RowView {
  key: string
  label: string
  sub?: string
  value: string
  mark: 'none' | 'lit' | 'dim'
  tone: 'default' | 'muted'
}

/** 章 01 的持有概况组（≤5 行，不重复章头大数） */
const overviewRows = computed<RowView[]>(() => {
  const a = annualized.value
  if (!a) return []
  const ret = a.annualizedReturn
  const rows: RowView[] = [
    {
      key: 'annualized',
      label: '年化收益率',
      value: ret != null ? formatPercent(ret) : '—',
      sub:
        ret != null
          ? ''
          : a.status === 'abnormal'
            ? '收益波动异常或暂不可信'
            : '数据不足，暂无法计算',
      mark: rowMark(ret),
      tone: rowTone(ret),
    },
    { key: 'invested', label: '总投入', value: formatMoney(a.totalInvested), mark: 'none', tone: 'default' },
    { key: 'days', label: '持有天数', value: `${formatQty(a.holdingDays, 0)} 天`, mark: 'none', tone: 'default' },
    { key: 'first', label: '首次买入', value: fmtFullDate(a.firstTransactionDate), mark: 'none', tone: 'default' },
  ]
  return rows
})

// ========== 章 02：走势 ==========
function seriesLen(): number {
  return seriesPoints.value.length
}
const activeValues = computed<number[]>(() => {
  const s = seriesPoints.value
  if (chartTab.value === 'value') return s.map((p) => p.marketValue)
  if (chartTab.value === 'shares') return s.map((p) => p.shares)
  return s.map((p) => p.profitLoss)
})
const selectedPoint = computed(() => {
  if (crosshairIdx.value < 0 || !holdingSeries.value) return null
  return holdingSeries.value.series[crosshairIdx.value] ?? null
})
const selRatio = computed(() => {
  const n = seriesLen()
  if (n < 2 || crosshairIdx.value < 0) return null
  return (crosshairIdx.value / (n - 1)) * 100
})
const readout = computed<RowView[]>(() => {
  const p = selectedPoint.value
  if (!p) return []
  return [
    { key: 'mv', label: '市值', value: formatMoney(p.marketValue), mark: 'none', tone: 'default' },
    { key: 'shares', label: '份额', value: `${formatQty(p.shares)} 份`, mark: 'none', tone: 'default' },
    {
      key: 'pnl',
      label: '盈亏',
      value: formatMoney(p.profitLoss, { sign: true }),
      mark: rowMark(p.profitLoss),
      tone: rowTone(p.profitLoss),
    },
    {
      key: 'pct',
      label: '收益率',
      value: formatPercent(p.profitLossPct),
      mark: rowMark(p.profitLossPct),
      tone: rowTone(p.profitLossPct),
    },
  ]
})

function onTrendPointer(e: PointerEvent) {
  if (!trendHit.value || seriesLen() === 0) return
  const rect = trendHit.value.getBoundingClientRect()
  if (!rect.width) return
  const ratio = Math.min(1, Math.max(0, (e.clientX - rect.left) / rect.width))
  crosshairIdx.value = Math.min(seriesLen() - 1, Math.max(0, Math.round(ratio * (seriesLen() - 1))))
  crosshairActive.value = true
}
function onTrendMove(e: PointerEvent) {
  if (e.pointerType === 'mouse') onTrendPointer(e)
}
function onDocPointer(e: PointerEvent) {
  if (!crosshairActive.value) return
  const target = e.target as Node | null
  if (trendHit.value && target && !trendHit.value.contains(target)) {
    crosshairActive.value = false
    crosshairIdx.value = -1
  }
}

// ========== 章 03：快照记录（按月分组） ==========
interface SnapRow {
  date: string
  change: number
  pct: number
  days: number
}
const snapshotRows = computed<SnapRow[]>(() =>
  snapshotChanges.value.map((ch) => ({
    date: ch.curr.date,
    change: ch.mvChange,
    pct: ch.mvChangePct,
    days: ch.days,
  })),
)

interface MonthBucket {
  key: string
  title: string
  rows: SnapRow[]
}
const monthBuckets = computed<MonthBucket[]>(() => {
  const buckets: MonthBucket[] = []
  for (const row of snapshotRows.value) {
    const key = row.date.slice(0, 7)
    let bucket = buckets[buckets.length - 1]
    if (!bucket || bucket.key !== key) {
      bucket = { key, title: `${key.slice(0, 4)} 年 ${Number(key.slice(5, 7))} 月`, rows: [] }
      buckets.push(bucket)
    }
    bucket.rows.push(row)
  }
  return buckets
})

interface GroupView {
  key: string
  title: string
  count: number
  total: string
  shown: RowView[]
  hidden: RowView[]
}
function toSnapRowView(row: SnapRow, spanMonths: boolean): RowView {
  const d = new Date(row.date)
  return {
    key: row.date,
    label: spanMonths ? `${d.getMonth() + 1}/${d.getDate()}` : `${d.getDate()} 日`,
    sub: `${formatQty(row.days, 0)} 天`,
    value: `${formatMoney(row.change, { sign: true })}（${formatPercent(row.pct)}）`,
    mark: rowMark(row.change),
    tone: rowTone(row.change),
  }
}
/** 一章 ≤3 组：最近的月份铺开，更早的合并成「更早」组 */
const displayGroups = computed<GroupView[]>(() => {
  const buckets = monthBuckets.value
  if (!buckets.length) return []
  const merged: { key: string; title: string; rows: SnapRow[]; span: boolean }[] = []
  if (buckets.length <= 3) {
    buckets.forEach((b) => merged.push({ key: b.key, title: b.title, rows: b.rows, span: false }))
  } else {
    buckets.slice(0, 2).forEach((b) => merged.push({ key: b.key, title: b.title, rows: b.rows, span: false }))
    merged.push({
      key: 'older',
      title: '更早',
      rows: buckets.slice(2).flatMap((b) => b.rows),
      span: true,
    })
  }
  return merged.map((g) => {
    const views = g.rows.map((r) => toSnapRowView(r, g.span))
    const net = g.rows.reduce((sum, r) => sum + r.change, 0)
    return {
      key: g.key,
      title: g.title,
      count: g.rows.length,
      total: formatMoney(net, { sign: true }),
      shown: views.slice(0, 5),
      hidden: views.slice(5),
    }
  })
})

// ========== 数据加载 ==========
async function loadData() {
  pageState.value = 'loading'
  errorMsg.value = ''
  crosshairActive.value = false
  crosshairIdx.value = -1
  try {
    const id = holdingId.value
    const results = await Promise.allSettled([getHoldingSeries(id, range.value), getAnnualizedReturn(id)])
    holdingSeries.value = results[0].status === 'fulfilled' ? results[0].value : null
    annualized.value = results[1].status === 'fulfilled' ? results[1].value : null

    if (!holdingSeries.value) {
      pageState.value = 'error'
      errorMsg.value = '加载持仓数据失败'
      return
    }
    pageState.value = 'ready'
    playHero()
  } catch (e: any) {
    console.error('加载持仓历史失败:', e)
    errorMsg.value = e.message || '加载失败'
    pageState.value = 'error'
  }
}

// 监听路由参数变化（同一路由不同 id 时 Vue 复用组件不会重建）
watch(holdingId, () => {
  if (pageState.value === 'ready' || pageState.value === 'error') {
    loadData()
  }
})

function selectRange(r: HistoryRange) {
  range.value = r
  loadData()
}
function selectTab(tab: 'value' | 'shares' | 'profit') {
  chartTab.value = tab
}

// ========== 生命周期 ==========
onMounted(() => {
  document.addEventListener('pointerdown', onDocPointer)
  loadData()
})
onUnmounted(() => {
  document.removeEventListener('pointerdown', onDocPointer)
  heroCancel?.()
})
</script>

<template>
  <div class="page">
    <AurosNavBar :title="holdingSeries?.holding?.name || '持仓历史'" :sub="holdingSeries?.holding?.code || ''" />

    <main class="body">
      <AurosState v-if="pageState === 'loading'" state="loading" :rows="4" />
      <AurosState
        v-else-if="pageState === 'error'"
        state="error"
        :text="errorMsg"
        action-label="重试"
        @action="loadData"
      />
      <AurosState v-else-if="!hasSeries" state="empty" text="暂无快照数据" />

      <template v-else>
        <!-- 01 VALUE 市值走势 -->
        <AurosChapter label="VALUE" title="市值走势" :meta="trendConclusion" :gap="false">
          <div class="seg" role="tablist" aria-label="时间区间">
            <button
              v-for="r in ranges"
              :key="r.key"
              type="button"
              class="seg__item"
              :class="{ 'is-active': range === r.key }"
              @click="selectRange(r.key)"
            >
              {{ r.label }}
            </button>
          </div>

          <div class="stat-wrap">
            <AurosStat :value="statValue" size="lg" pink :caption="statCaption" />
          </div>

          <div class="rail-wrap">
            <AurosRail
              :percent="rail.pct"
              :label-left="`区间低 ${formatMoney(rail.min)}`"
              :label-right="`区间高 ${formatMoney(rail.max)}`"
            />
          </div>

          <AurosGroup v-if="overviewRows.length" title="持有概况" :count="`${overviewRows.length} 项`">
            <AurosRow
              v-for="(row, i) in overviewRows"
              :key="row.key"
              class="anim"
              :style="{ '--d': `${i * 30}ms` }"
              :label="row.label"
              :sub="row.sub"
              :value="row.value"
              :mark="row.mark"
              :tone="row.tone"
            />
          </AurosGroup>
        </AurosChapter>

        <!-- 02 TREND 走势 -->
        <AurosChapter label="TREND" title="走势" meta="点按读数">
          <div class="tabs" role="tablist" aria-label="走势口径">
            <button
              v-for="m in metrics"
              :key="m.key"
              type="button"
              class="tabs__item"
              :class="{ 'is-active': chartTab === m.key }"
              @click="selectTab(m.key)"
            >
              {{ m.label }}
            </button>
          </div>

          <div
            ref="trendHit"
            class="trend-hit"
            @pointerdown="onTrendPointer"
            @pointermove="onTrendMove"
          >
            <AurosTrend
              :points="activeValues"
              :height="84"
              :zero="chartTab === 'profit'"
              :label="fmtDate(seriesPoints[0].date)"
              :caption="fmtDate(seriesPoints[seriesPoints.length - 1].date)"
            />
            <i
              v-if="crosshairActive && selRatio !== null"
              class="trend-hit__hair"
              :style="{ left: `${selRatio}%` }"
              aria-hidden="true"
            ></i>
          </div>

          <p v-if="!crosshairActive" class="hint">点击走势查看坐标</p>

          <AurosGroup
            v-if="crosshairActive && selectedPoint"
            :title="fmtFullDate(selectedPoint.date)"
            :count="`${crosshairIdx + 1} / ${seriesPoints.length}`"
          >
            <AurosRow
              v-for="(row, i) in readout"
              :key="row.key"
              class="anim"
              :style="{ '--d': `${i * 30}ms` }"
              :label="row.label"
              :sub="row.sub"
              :value="row.value"
              :mark="row.mark"
              :tone="row.tone"
            />
          </AurosGroup>
        </AurosChapter>

        <!-- 03 SNAPSHOTS 快照记录 -->
        <AurosChapter label="SNAPSHOTS" title="快照记录" meta="按月归档">
          <AurosGroup
            v-for="g in displayGroups"
            :key="g.key"
            :title="g.title"
            :count="`${g.count} 条`"
            :total="g.total"
          >
            <AurosRow
              v-for="(row, i) in g.shown"
              :key="row.key"
              class="anim"
              :style="{ '--d': `${i * 30}ms` }"
              :label="row.label"
              :sub="row.sub"
              :value="row.value"
              :mark="row.mark"
              :tone="row.tone"
            />
            <AurosDisclosure v-if="g.hidden.length" :count="g.hidden.length">
              <AurosRow
                v-for="(row, i) in g.hidden"
                :key="row.key"
                class="anim"
                :style="{ '--d': `${i * 30}ms` }"
                :label="row.label"
                :sub="row.sub"
                :value="row.value"
                :mark="row.mark"
                :tone="row.tone"
              />
            </AurosDisclosure>
          </AurosGroup>
        </AurosChapter>
      </template>
    </main>
  </div>
</template>

<style scoped>
.page {
  min-height: 100svh;
  display: flex;
  flex-direction: column;
}
.body {
  flex: 1;
  padding: 8px 16px calc(env(safe-area-inset-bottom, 0px) + 40px);
}

/* 时间区间胶囊 */
.seg {
  display: flex;
  gap: 4px;
  padding: 3px;
  border-radius: 6px;
  background: rgba(0, 55, 52, 0.55);
}
.seg__item {
  flex: 1;
  height: 34px;
  border: 0;
  border-radius: 6px;
  background: none;
  font-family: inherit;
  font-size: 13px;
  letter-spacing: 0.04em;
  color: rgba(187, 199, 198, 0.72);
  transition: background 200ms var(--au-ease), color 200ms var(--au-ease);
}
.seg__item.is-active {
  background: rgba(203, 255, 252, 0.14);
  color: var(--au-mist);
}

/* 章内间距 */
.stat-wrap {
  margin-top: 18px;
}
.rail-wrap {
  margin-top: 16px;
}
.rail-wrap + :deep(.group) {
  margin-top: 22px;
}

/* 走势口径 tab */
.tabs {
  display: flex;
  gap: 14px;
  margin-bottom: 12px;
}
.tabs__item {
  border: 0;
  background: none;
  padding: 0;
  font-family: inherit;
  font-size: 12px;
  letter-spacing: 0.1em;
  color: rgba(187, 199, 198, 0.55);
  transition: color 200ms var(--au-ease);
}
.tabs__item.is-active {
  color: var(--au-mist);
}

/* 走势（点选命中区） */
.trend-hit {
  position: relative;
  touch-action: pan-y;
  cursor: crosshair;
}
.trend-hit__hair {
  position: absolute;
  top: 0;
  bottom: 22px;
  width: 1px;
  margin-left: -0.5px;
  background: rgba(237, 255, 254, 0.24);
  pointer-events: none;
}
.trend-hit + :deep(.group) {
  margin-top: 14px;
}
.hint {
  margin: 12px 0 0;
  font-size: 10px;
  letter-spacing: 0.06em;
  color: rgba(187, 199, 198, 0.45);
}

/* 列表逐行揭示（与全站卡片页同一套步进） */
.anim {
  animation: rise 440ms var(--au-ease) var(--d, 0ms) both;
}
@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@media (prefers-reduced-motion: reduce) {
  .anim {
    animation: none;
  }
}
</style>
