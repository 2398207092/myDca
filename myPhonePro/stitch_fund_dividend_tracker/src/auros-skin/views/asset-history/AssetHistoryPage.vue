<script setup lang="ts">
/**
 * 资产历史（卡片体系 · 2026-11-11）
 * 一章一张卡（AurosChapter = .au-card）：01 HISTORY 资产走势 / 02 TREND 走势 / 03 SNAPSHOTS 快照记录。
 * 卡内只用 AurosStat / AurosRail / AurosTrend / AurosGroup / AurosRow / AurosDisclosure；
 * 金额/百分比/数量统一走 @/auros-skin/utils/format（¥+千分位+两位小数，不做万/亿压缩）。
 * 业务逻辑、接口与计算口径未变。
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { listHoldings } from '@/api/holding'
import type { HoldingItem } from '@/api/holding'
import {
  getAssetOverview,
  getHoldingDiff,
  getHoldingSeries,
  triggerSnapshot,
  type TotalAssetSeries,
  type HoldingDiff,
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

const router = useRouter()

// 页面状态
const loading = ref(true)
const errorMsg = ref('')
const range = ref<HistoryRange>('month')

// 总资产
const totalData = ref<TotalAssetSeries | null>(null)
const totalTab = ref<'value' | 'shares' | 'profit'>('value')

// 加密币持仓份额（按日期）：后端 totalShares 是基金份额与币数裸加，前端剔除
const cryptoSharesByDate = ref<Map<string, number>>(new Map())

function fundSharesOf(point: { date: string; totalShares: number }): number {
  return point.totalShares - (cryptoSharesByDate.value.get(point.date) ?? 0)
}
function cryptoSharesOf(point: { date: string }): number {
  return cryptoSharesByDate.value.get(point.date) ?? 0
}

// 持仓
const holdings = ref<HoldingItem[]>([])
const holdingDiffs = ref<Map<string, HoldingDiff>>(new Map())

// 手动快照
const snapshotting = ref(false)
const snapshotToast = ref('')

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
function signClass(v: number): 'is-up' | 'is-dn' | 'is-flat' {
  return v > 0 ? 'is-up' : v < 0 ? 'is-dn' : 'is-flat'
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

// ========== 章 01：读数头 ==========
const seriesPoints = computed(() => totalData.value?.series ?? [])
const hasSeries = computed(() => seriesPoints.value.length > 0)
const latestPoint = computed(() => seriesPoints.value[seriesPoints.value.length - 1] ?? null)

const heroDisp = ref(0)
let heroCancel: (() => void) | null = null
/** 主数给精确两位小数：金额准确是这个 App 的底线，不做万元压缩 */
const statValue = computed(() => formatMoney(Math.max(0, heroDisp.value)))
/** 区间变化放 caption：精确值只出现一次，变化也只出现一次（不出组件内置 delta 行） */
const statCaption = computed(() =>
  totalData.value
    ? `当前总资产 · 区间 ${formatMoney(totalData.value.totalChange, { sign: true })} · ${formatPercent(totalData.value.totalChangePercent)}`
    : '',
)
const trendConclusion = computed(() => {
  const v = totalData.value?.totalChange ?? 0
  return v > 0 ? '较期初上行' : v < 0 ? '较期初回落' : '较期初持平'
})
const rail = computed(() => {
  const vals = seriesPoints.value.map((p) => p.totalMarketValue)
  if (!vals.length) return { pct: 0, min: 0, max: 0 }
  const min = Math.min(...vals)
  const max = Math.max(...vals)
  const cur = vals[vals.length - 1]
  const pct = max === min ? 50 : Math.min(100, Math.max(0, ((cur - min) / (max - min)) * 100))
  return { pct, min, max }
})
function playHero() {
  heroCancel?.()
  const target = latestPoint.value?.totalMarketValue ?? 0
  heroDisp.value = 0
  heroCancel = animateValue(0, target, 800, (v) => (heroDisp.value = v))
}

// 持仓明细（组内 ≤5 行 + 折叠）
interface HoldingView {
  id: string
  label: string
  sub: string
  value: string
  mark: 'none' | 'lit' | 'dim'
  tone: 'default' | 'muted'
}
const holdingViews = computed<HoldingView[]>(() =>
  holdings.value.map((h) => {
    const diff = holdingDiffs.value.get(h.id)
    return {
      id: h.id,
      label: h.name,
      sub: h.code,
      value: diff ? formatMoney(diff.marketValueChange, { sign: true }) : '—',
      mark: rowMark(diff ? diff.marketValueChange : null),
      tone: rowTone(diff ? diff.marketValueChange : null),
    }
  }),
)
const shownHoldings = computed(() => holdingViews.value.slice(0, 5))
const restHoldings = computed(() => holdingViews.value.slice(5))

// ========== 章 02：走势 ==========
function seriesLen(): number {
  return seriesPoints.value.length
}
const activeValues = computed<number[]>(() => {
  const s = seriesPoints.value
  if (totalTab.value === 'value') return s.map((p) => p.totalMarketValue)
  if (totalTab.value === 'shares') return s.map((p) => fundSharesOf(p))
  return s.map((p) => p.totalProfitLoss)
})
const selectedPoint = computed(() => {
  if (crosshairIdx.value < 0 || !totalData.value) return null
  return totalData.value.series[crosshairIdx.value] ?? null
})
const selRatio = computed(() => {
  const n = seriesLen()
  if (n < 2 || crosshairIdx.value < 0) return null
  return (crosshairIdx.value / (n - 1)) * 100
})

interface RowView {
  key: string
  label: string
  sub?: string
  value: string
  mark: 'none' | 'lit' | 'dim'
  tone: 'default' | 'muted'
}
const readout = computed<RowView[]>(() => {
  const p = selectedPoint.value
  if (!p) return []
  const crypto = cryptoSharesOf(p)
  return [
    { key: 'mv', label: '市值', value: formatMoney(p.totalMarketValue), mark: 'none', tone: 'default' },
    {
      key: 'shares',
      label: '基金总份额',
      value: `${formatQty(fundSharesOf(p))} 份`,
      sub: crypto > 0 ? `加密币 ${formatQty(crypto)} 枚不计入份额` : '',
      mark: 'none',
      tone: 'default',
    },
    {
      key: 'pnl',
      label: '盈亏',
      value: formatMoney(p.totalProfitLoss, { sign: true }),
      mark: rowMark(p.totalProfitLoss),
      tone: rowTone(p.totalProfitLoss),
    },
    {
      key: 'pct',
      label: '收益率',
      value: formatPercent(p.totalProfitLossPct),
      mark: rowMark(p.totalProfitLossPct),
      tone: rowTone(p.totalProfitLossPct),
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
  change: number | null
  pct: number | null
}
const snapshotRows = computed<SnapRow[]>(() => {
  const s = seriesPoints.value
  const rows: SnapRow[] = s.map((p, i) => {
    const prev = i > 0 ? s[i - 1] : null
    const change = prev ? p.totalMarketValue - prev.totalMarketValue : null
    const pct =
      prev && prev.totalMarketValue !== 0 ? ((change as number) / Math.abs(prev.totalMarketValue)) * 100 : null
    return { date: p.date, change, pct }
  })
  return rows.reverse()
})

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
    value:
      row.change == null
        ? '—'
        : `${formatMoney(row.change, { sign: true })}（${formatPercent(row.pct ?? 0)}）`,
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
    const net = g.rows.reduce((sum, r) => sum + (r.change ?? 0), 0)
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
async function loadTotalData() {
  totalData.value = await getAssetOverview(range.value)
}
async function loadHoldingDiffs(holdingList: HoldingItem[]) {
  const diffs = new Map<string, HoldingDiff>()
  const results = await Promise.allSettled(holdingList.map((h) => getHoldingDiff(h.id)))
  holdingList.forEach((h, i) => {
    const r = results[i]
    if (r.status === 'fulfilled' && r.value?.current && r.value?.previous) {
      diffs.set(h.id, r.value)
    }
  })
  holdingDiffs.value = diffs
}
async function loadCryptoShares(holdingList: HoldingItem[]) {
  const cryptoIds = holdingList.filter((h) => h.quoteCurrency === 'USDT').map((h) => h.id)
  if (cryptoIds.length === 0) {
    cryptoSharesByDate.value = new Map()
    return
  }
  const results = await Promise.allSettled(cryptoIds.map((id) => getHoldingSeries(id, range.value)))
  const byDate = new Map<string, number>()
  results.forEach((r) => {
    if (r.status === 'fulfilled') {
      r.value.series.forEach((p) => {
        byDate.set(p.date, (byDate.get(p.date) ?? 0) + (p.shares || 0))
      })
    }
  })
  cryptoSharesByDate.value = byDate
}

async function loadAll() {
  loading.value = true
  errorMsg.value = ''
  crosshairActive.value = false
  crosshairIdx.value = -1
  try {
    const holdingList = await listHoldings()
    holdings.value = holdingList
    await Promise.all([loadTotalData(), loadHoldingDiffs(holdingList), loadCryptoShares(holdingList)])
  } catch (e: any) {
    console.error('加载数据失败:', e)
    errorMsg.value = e.message || '加载失败'
  } finally {
    loading.value = false
    playHero()
  }
}

function selectRange(r: HistoryRange) {
  range.value = r
  loadAll()
}
function selectTotalTab(tab: 'value' | 'shares' | 'profit') {
  totalTab.value = tab
}
async function handleSnapshot() {
  if (snapshotting.value) return
  snapshotting.value = true
  try {
    await triggerSnapshot()
    snapshotToast.value = '已记录'
    await loadAll()
  } catch (e: any) {
    snapshotToast.value = '失败，重试'
    console.error('记录快照失败:', e)
  } finally {
    snapshotting.value = false
    setTimeout(() => {
      snapshotToast.value = ''
    }, 2000)
  }
}
function goToHoldingHistory(id: string) {
  router.push({ name: 'holding-history', params: { id } })
}

// ========== 生命周期 ==========
onMounted(async () => {
  document.addEventListener('pointerdown', onDocPointer)
  await loadAll()
})
onUnmounted(() => {
  document.removeEventListener('pointerdown', onDocPointer)
  heroCancel?.()
})
</script>

<template>
  <div class="page">
    <AurosNavBar title="资产历史记录">
      <template #right>
        <button
          type="button"
          class="snap"
          :disabled="snapshotting"
          :aria-label="snapshotting ? '正在记录快照' : '记录快照'"
          @click="handleSnapshot"
        >
          <svg v-if="!snapshotting && !snapshotToast" viewBox="0 0 16 16" width="15" height="15" fill="none" aria-hidden="true">
            <rect x="2" y="4.4" width="12" height="9" rx="1.6" stroke="currentColor" stroke-width="1.2" />
            <circle cx="8" cy="8.9" r="2.3" stroke="currentColor" stroke-width="1.2" />
            <path d="M5.7 4.4 6.5 2.8h3l.8 1.6" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round" />
          </svg>
          <svg v-else-if="snapshotting" class="snap__spin" viewBox="0 0 12 12" width="12" height="12" fill="none" aria-hidden="true">
            <circle cx="6" cy="6" r="4.2" stroke="currentColor" stroke-width="1.2" stroke-dasharray="8 18" stroke-linecap="round" />
          </svg>
          <span v-else class="snap__t">{{ snapshotToast }}</span>
        </button>
      </template>
    </AurosNavBar>

    <main class="body">
      <AurosState v-if="loading" state="loading" :rows="4" />
      <AurosState v-else-if="errorMsg" state="error" :text="errorMsg" action-label="重试" @action="loadAll" />
      <AurosState
        v-else-if="!hasSeries"
        state="empty"
        text="还没有快照。记录一次，这里才有曲线。"
        action-label="记录快照"
        @action="handleSnapshot"
      />

      <template v-else>
        <!-- 01 HISTORY 资产走势 -->
        <AurosChapter label="HISTORY" title="资产走势" :meta="trendConclusion" :gap="false">
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

          <AurosGroup title="持仓明细" :count="`${holdings.length} 只`">
            <AurosRow
              v-for="(h, i) in shownHoldings"
              :key="h.id"
              class="anim"
              :style="{ '--d': `${i * 28}ms` }"
              :label="h.label"
              :sub="h.sub"
              :value="h.value"
              :mark="h.mark"
              :tone="h.tone"
              clickable
              chevron
              @click="goToHoldingHistory(h.id)"
            />
          </AurosGroup>
          <AurosDisclosure v-if="restHoldings.length" :count="restHoldings.length">
            <AurosRow
              v-for="(h, i) in restHoldings"
              :key="h.id"
              class="anim"
              :style="{ '--d': `${i * 28}ms` }"
              :label="h.label"
              :sub="h.sub"
              :value="h.value"
              :mark="h.mark"
              :tone="h.tone"
              clickable
              chevron
              @click="goToHoldingHistory(h.id)"
            />
          </AurosDisclosure>
        </AurosChapter>

        <!-- 02 TREND 走势 -->
        <AurosChapter label="TREND" title="走势" meta="点按读数">
          <div class="tabs" role="tablist" aria-label="走势口径">
            <button
              v-for="m in metrics"
              :key="m.key"
              type="button"
              class="tabs__item"
              :class="{ 'is-active': totalTab === m.key }"
              @click="selectTotalTab(m.key)"
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
              :zero="totalTab === 'profit'"
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

/* 顶栏动作：记录快照 */
.snap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 28px;
  padding: 0 8px;
  border: 0;
  border-radius: 6px;
  background: rgba(237, 255, 254, 0.06);
  color: var(--au-silver);
  font-family: inherit;
  transition: color 200ms var(--au-ease), background 200ms var(--au-ease);
}
.snap:active:not(:disabled) {
  color: var(--au-mist);
  background: rgba(237, 255, 254, 0.1);
}
.snap:disabled {
  opacity: 0.5;
}
.snap__t {
  font-size: 10px;
  letter-spacing: 0.08em;
  color: var(--au-mist);
}
.snap__spin {
  animation: spin 900ms linear infinite;
  transform-origin: 50% 50%;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
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
:deep(.group) + :deep(.disc) {
  margin-top: 2px;
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
