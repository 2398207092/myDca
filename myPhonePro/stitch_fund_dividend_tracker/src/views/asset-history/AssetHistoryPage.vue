<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { listHoldings } from '@/api/holding'
import type { HoldingItem } from '@/api/holding'
import {
  getAssetOverview,
  getHoldingSeries,
  getHoldingDiff,
  getAnnualizedReturn,
  triggerSnapshot,
  type TotalAssetSeries,
  type HoldingSeries,
  type HoldingDiff,
  type AnnualizedReturn,
  type HistoryRange,
} from '@/api/assetHistory'

// 后端就绪后改为 false
const DEV_MOCK = false

const router = useRouter()

// 页面状态
const loading = ref(true)
const errorMsg = ref('')
const range = ref<HistoryRange>('month')
const holdings = ref<HoldingItem[]>([])
const selectedIdx = ref(0)

// 总资产
const totalData = ref<TotalAssetSeries | null>(null)
const totalTab = ref<'value' | 'shares' | 'profit'>('value')

// 单持仓
const holdingSeries = ref<HoldingSeries | null>(null)
const holdingDiff = ref<HoldingDiff | null>(null)
const annualized = ref<AnnualizedReturn | null>(null)
const holdingTab = ref<'value' | 'shares'>('value')
const holdingLoading = ref(false)

// 手动记录快照
const snapshotting = ref(false)
const snapshotToast = ref('')

// ECharts 实例
let totalChart: any = null
let holdingChart: any = null
const totalChartRef = ref<HTMLElement | null>(null)
const holdingChartRef = ref<HTMLElement | null>(null)

// 类别色
const catColors: Record<string, string> = {
  us_stock: '#3B82F6',
  gold: '#F59E0B',
  dividend: '#EAB308',
  crypto: '#6366F1',
  cash: '#34A853',
}
const brandColor = '#1A6B56'

function holdingColor(cat?: string): string {
  return catColors[cat || ''] || '#9CA3AF'
}

// 格式化
function fmtMoney(v: number | null | undefined): string {
  if (v == null) return '¥0'
  if (Math.abs(v) >= 1_0000_0000) return `¥${(v / 1_0000_0000).toFixed(2)}亿`
  if (Math.abs(v) >= 1_0000) return `¥${(v / 1_0000).toFixed(2)}万`
  return `¥${v.toLocaleString()}`
}
function fmtChange(v: number): string {
  return v >= 0 ? `+${v.toFixed(2)}` : v.toFixed(2)
}
function fmtPct(v: number): string {
  return v >= 0 ? `+${v.toFixed(2)}%` : `${v.toFixed(2)}%`
}
function fmtDate(dateStr: string): string {
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

// 当前选中的持仓
const currentHolding = computed(() => holdings.value[selectedIdx.value] || null)

// vs上期实际间隔天数（current.date - previous.date，无 previous 时为 null）
const diffDays = computed(() => {
  const diff = holdingDiff.value
  if (!diff || !diff.current || !diff.previous) return null
  const cur = new Date(diff.current.date).getTime()
  const prev = new Date(diff.previous.date).getTime()
  return Math.round((cur - prev) / (1000 * 60 * 60 * 24))
})

// 总资产当前值
const totalCurrentValue = computed(() => {
  if (!totalData.value || totalData.value.series.length === 0) return ''
  const last = totalData.value.series[totalData.value.series.length - 1]
  if (totalTab.value === 'value') return fmtMoney(last.totalMarketValue)
  if (totalTab.value === 'shares') return `${last.totalShares.toLocaleString()} 份`
  return fmtChange(last.totalProfitLoss)
})

// ========== ECharts 构建 ==========
// 根据数据点数量动态计算 X 轴标签间隔，避免标签密集重叠
function computeLabelInterval(count: number): number {
  if (count <= 7) return 0
  if (count <= 15) return 1
  if (count <= 30) return 2
  return Math.floor(count / 10)
}

function buildOption(data: number[], xLabels: string[], color: string) {
  return {
    grid: { left: 44, right: 8, top: 12, bottom: 24 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(28,27,26,0.92)',
      borderColor: 'transparent',
      textStyle: { color: '#fff', fontSize: 11 },
      formatter: (params: any) => {
        const p = params[0]
        return `<span style="color:rgba(255,255,255,0.5);font-size:10px">${p.axisValue}</span><br/><b style="font-size:13px">${p.data.toLocaleString()}</b>`
      },
    },
    xAxis: {
      type: 'category',
      data: xLabels,
      axisLine: { lineStyle: { color: '#E8E7E5' } },
      axisTick: { show: true, length: 4, lineStyle: { color: '#C8C7C5' } },
      axisLabel: { color: '#A09E9B', fontSize: 9, interval: computeLabelInterval(xLabels.length) },
    },
    yAxis: {
      type: 'value',
      show: true,
      scale: true,
      axisLine: { show: false },
      axisTick: { show: true, length: 4, lineStyle: { color: '#C8C7C5' } },
      splitLine: { show: true, lineStyle: { color: '#F0EFED', type: 'dashed', width: 1 } },
      axisLabel: {
        color: '#A09E9B',
        fontSize: 9,
        formatter: (v: number) => {
          if (Math.abs(v) >= 10000) return (v / 10000).toFixed(1) + 'w'
          return v.toLocaleString()
        },
      },
    },
    series: [{
      data,
      type: 'line',
      smooth: false,
      symbol: 'circle',
      symbolSize: 5,
      showSymbol: false,
      lineStyle: { color, width: 2 },
      itemStyle: { color, borderColor: '#fff', borderWidth: 2 },
      emphasis: { focus: 'series', scale: 1.4 },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: color + '33' },
            { offset: 1, color: color + '00' },
          ],
        },
      },
    }],
  }
}

function ensureTotalChart() {
  // 图表容器不存在（v-if=false / loading 中）时，销毁旧实例避免内存泄漏
  if (!totalChartRef.value) {
    if (totalChart) { totalChart.dispose(); totalChart = null }
    return
  }
  if (!(window as any).echarts) return
  // v-if 重建 DOM 时，旧实例绑定的 DOM 已脱离文档，需 dispose 后重建
  if (totalChart && totalChart.getDom() !== totalChartRef.value) {
    totalChart.dispose()
    totalChart = null
  }
  if (!totalChart) {
    totalChart = (window as any).echarts.init(totalChartRef.value)
  }
}

function ensureHoldingChart() {
  if (!holdingChartRef.value) {
    if (holdingChart) { holdingChart.dispose(); holdingChart = null }
    return
  }
  if (!(window as any).echarts) return
  if (holdingChart && holdingChart.getDom() !== holdingChartRef.value) {
    holdingChart.dispose()
    holdingChart = null
  }
  if (!holdingChart) {
    holdingChart = (window as any).echarts.init(holdingChartRef.value)
  }
}

function renderTotalChart() {
  ensureTotalChart()
  if (!totalChart || !totalData.value) return
  const series = totalData.value.series
  if (!series || series.length === 0) {
    // 空数据时销毁图表实例，模板用 v-if 显示"暂无数据"占位
    // 注意：不能用 setOption 传空数组，会导致 ECharts 内部状态损坏
    totalChart.dispose()
    totalChart = null
    return
  }
  const labels = series.map(p => fmtDate(p.date))
  let data: number[]
  if (totalTab.value === 'value') data = series.map(p => p.totalMarketValue)
  else if (totalTab.value === 'shares') data = series.map(p => p.totalShares)
  else data = series.map(p => p.totalProfitLoss)
  totalChart.setOption(buildOption(data, labels, brandColor), true)
}

function renderHoldingChart() {
  ensureHoldingChart()
  if (!holdingChart || !holdingSeries.value) return
  const series = holdingSeries.value.series
  if (!series || series.length === 0) {
    holdingChart.dispose()
    holdingChart = null
    return
  }
  const labels = series.map(p => fmtDate(p.date))
  let data: number[]
  const color = holdingColor(holdingSeries.value.holding.assetCategory)
  if (holdingTab.value === 'value') data = series.map(p => p.marketValue)
  else data = series.map(p => p.shares)
  holdingChart.setOption(buildOption(data, labels, color), true)
}

// ========== 数据加载 ==========
async function loadTotalData() {
  if (DEV_MOCK) {
    totalData.value = mockTotalData()
    return
  }
  totalData.value = await getAssetOverview(range.value)
}

async function loadHoldingData() {
  if (!currentHolding.value) return
  holdingLoading.value = true
  try {
    if (DEV_MOCK) {
      const idx = selectedIdx.value
      holdingSeries.value = mockHoldingSeries(idx)
      holdingDiff.value = mockHoldingDiff(idx)
      annualized.value = mockAnnualized(idx)
    } else {
      const id = currentHolding.value.id
      const results = await Promise.allSettled([
        getHoldingSeries(id, range.value),
        getHoldingDiff(id),
        getAnnualizedReturn(id),
      ])
      holdingSeries.value = results[0].status === 'fulfilled' ? results[0].value : null
      holdingDiff.value = results[1].status === 'fulfilled' ? results[1].value : null
      annualized.value = results[2].status === 'fulfilled' ? results[2].value : null
      // 记录失败的请求（不影响其他数据展示）
      const failures = results.filter((r): r is PromiseRejectedResult => r.status === 'rejected')
      if (failures.length > 0) {
        console.warn('部分持仓数据加载失败:', failures.map(f => f.reason))
      }
    }
  } catch (e: any) {
    console.error('加载持仓数据失败:', e)
    holdingSeries.value = null
    holdingDiff.value = null
    annualized.value = null
  } finally {
    holdingLoading.value = false
    await nextTick()
    renderHoldingChart()
  }
}

async function loadAll() {
  loading.value = true
  errorMsg.value = ''
  try {
    if (DEV_MOCK) {
      holdings.value = mockHoldings()
    } else {
      holdings.value = await listHoldings()
    }
    await loadTotalData()
    await loadHoldingData()
  } catch (e: any) {
    console.error('加载数据失败:', e)
    errorMsg.value = e.message || '加载失败'
  } finally {
    loading.value = false
    // 必须在 loading=false（Content v-if 渲染出图表容器）后再 nextTick + render
    await nextTick()
    renderTotalChart()
    renderHoldingChart()
  }
}

// 切换 range
function selectRange(r: HistoryRange) {
  range.value = r
  loadAll()
}

// 手动记录快照
async function handleSnapshot() {
  if (snapshotting.value) return
  snapshotting.value = true
  try {
    await triggerSnapshot()
    snapshotToast.value = '已记录 ✓'
    await loadAll()
  } catch (e: any) {
    snapshotToast.value = '失败，重试'
    console.error('记录快照失败:', e)
  } finally {
    snapshotting.value = false
    setTimeout(() => { snapshotToast.value = '' }, 2000)
  }
}

// 切换总资产 Tab
function selectTotalTab(tab: 'value' | 'shares' | 'profit') {
  totalTab.value = tab
  renderTotalChart()
}

// 切换单持仓 Tab
function selectHoldingTab(tab: 'value' | 'shares') {
  holdingTab.value = tab
  renderHoldingChart()
}

// 切换持仓
function selectHolding(idx: number) {
  selectedIdx.value = idx
  loadHoldingData()
}

// 导航
function goBack() { router.back() }
function goHome() { router.push({ name: 'home' }) }

// ========== 生命周期 ==========
onMounted(async () => {
  window.addEventListener('resize', handleResize)
  await loadAll()
  // loadAll 内部已调用 render，render 会 lazy init chart
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  totalChart?.dispose()
  holdingChart?.dispose()
})

function handleResize() {
  totalChart?.resize()
  holdingChart?.resize()
}

// ========== Mock 数据（后端就绪后删除） ==========
function mockHoldings(): HoldingItem[] {
  return [
    { id: 'm1', name: '博时红利低波动', code: '021550', assetCategory: 'dividend', marketValue: 10960, shares: 10000, deleted: false } as any,
    { id: 'm2', name: '华夏纳斯达克', code: '000834', assetCategory: 'us_stock', marketValue: 46500, shares: 52000, deleted: false } as any,
    { id: 'm3', name: '黄金 ETF', code: '518880', assetCategory: 'gold', marketValue: 22380, shares: 4000, deleted: false } as any,
  ]
}
function mockTotalData(): TotalAssetSeries {
  const dates = ['2026-07-05', '2026-07-10', '2026-07-15', '2026-07-20', '2026-07-25', '2026-07-30']
  return {
    series: dates.map(d => ({
      date: d,
      totalMarketValue: [178000, 182000, 180500, 186500, 184200, 186500][dates.indexOf(d)],
      totalShares: [142000, 145000, 147500, 150000, 150000, 150000][dates.indexOf(d)],
      totalCostBasis: [140000, 140000, 140000, 140000, 140000, 140000][dates.indexOf(d)],
      totalProfitLoss: [38000, 42000, 40500, 46500, 44200, 46500][dates.indexOf(d)],
      totalProfitLossPct: [27.14, 30.00, 28.93, 33.21, 31.57, 33.21][dates.indexOf(d)],
    })),
    totalChange: 8500,
    totalChangePercent: 4.78,
  }
}
function mockHoldingSeries(idx: number): HoldingSeries {
  const h = mockHoldings()[idx]
  const dates = ['2026-07-05', '2026-07-10', '2026-07-15', '2026-07-20', '2026-07-25', '2026-07-30']
  const dataMap = [
    { mv: [9800, 10200, 10500, 10960, 10800, 10960], sh: [8500, 9000, 9500, 10000, 10000, 10000], cb: [8500, 8800, 9200, 9800, 9800, 9800] },
    { mv: [45000, 46000, 45500, 46800, 46000, 46500], sh: [50000, 51000, 51500, 52000, 52000, 52000], cb: [42000, 43000, 43500, 44000, 44000, 44000] },
    { mv: [20000, 20500, 21000, 21800, 21500, 22380], sh: [4000, 4000, 4000, 4000, 4000, 4000], cb: [19500, 19500, 19500, 19500, 19500, 19500] },
  ]
  const d = dataMap[idx]
  return {
    holding: { id: h.id, name: h.name, code: h.code || '', assetCategory: h.assetCategory || '' },
    series: dates.map((date, i) => ({
      date,
      marketValue: d.mv[i],
      shares: d.sh[i],
      costBasis: d.cb[i],
      profitLoss: d.mv[i] - d.cb[i],
      profitLossPct: ((d.mv[i] - d.cb[i]) / d.cb[i]) * 100,
      pctOfTotal: (d.mv[i] / 186500) * 100,
    })),
  }
}
function mockHoldingDiff(idx: number): HoldingDiff {
  const h = mockHoldings()[idx]
  const s = mockHoldingSeries(idx).series
  const cur = s[s.length - 1]
  const prev = s[s.length - 2]
  return {
    holdingId: h.id,
    current: { date: cur.date, marketValue: cur.marketValue, shares: cur.shares },
    previous: { date: prev.date, marketValue: prev.marketValue, shares: prev.shares },
    marketValueChange: cur.marketValue - prev.marketValue,
    marketValueChangePct: ((cur.marketValue - prev.marketValue) / prev.marketValue) * 100,
    sharesChange: cur.shares - prev.shares,
    sharesChangePct: ((cur.shares - prev.shares) / prev.shares) * 100,
    pctOfTotalChange: cur.pctOfTotal - prev.pctOfTotal,
  }
}
function mockAnnualized(idx: number): AnnualizedReturn {
  const h = mockHoldings()[idx]
  const s = mockHoldingSeries(idx).series
  const last = s[s.length - 1]
  const returns = [12.34, 8.12, 5.67]
  return {
    holdingId: h.id,
    annualizedReturn: returns[idx],
    totalInvested: last.costBasis,
    totalWithdrawn: 0,
    currentValue: last.marketValue,
    holdingDays: 180,
    firstTransactionDate: '2026-01-15',
    irr: returns[idx] / 100,
  }
}
</script>

<template>
  <div class="min-h-screen bg-page-bg flex flex-col">
    <!-- Header -->
    <header class="flex items-center justify-between px-gutter h-14 sticky top-0 z-50 bg-card-bg border-b border-border-light/40">
      <button @click="goBack" class="w-10 h-10 flex items-center justify-center -ml-2 active:opacity-80">
        <span class="material-symbols-outlined text-text-secondary">arrow_back</span>
      </button>
      <h1 class="font-display text-md font-semibold text-text-primary">资产历史记录</h1>
      <button @click="goHome" class="w-10 h-10 flex items-center justify-center active:opacity-80">
        <span class="material-symbols-outlined text-text-tertiary">home</span>
      </button>
    </header>

    <!-- Loading -->
    <main v-if="loading" class="flex-1 flex flex-col items-center justify-center py-32">
      <span class="material-symbols-outlined animate-spin text-text-tertiary text-3xl">progress_activity</span>
      <p class="font-body text-sm text-text-tertiary mt-md">加载中...</p>
    </main>

    <!-- Error -->
    <main v-else-if="errorMsg" class="flex-1 flex flex-col items-center justify-center py-32">
      <span class="material-symbols-outlined text-error text-3xl mb-md">error</span>
      <p class="font-body text-sm font-medium text-text-primary mb-1">加载失败</p>
      <p class="font-body text-xs text-text-tertiary mb-lg">{{ errorMsg }}</p>
      <button class="px-lg py-2 bg-brand text-white rounded-lg font-body text-sm font-medium active:scale-[0.98] transition-transform" @click="loadAll">重试</button>
    </main>

    <!-- Content -->
    <main v-else class="flex-1 px-gutter pt-md pb-28 space-y-sm">

      <!-- Range Selector -->
      <section class="flex gap-2">
        <button v-for="r in (['month','quarter','all'] as const)" :key="r"
          class="flex-1 py-2 rounded-lg text-xs font-medium transition-colors"
          :class="range === r ? 'bg-brand text-white' : 'bg-card-bg text-text-secondary border border-border-light'"
          @click="selectRange(r)">
          {{ r === 'month' ? '近 30 天' : r === 'quarter' ? '近 90 天' : '全部' }}
        </button>
        <!-- 手动记录快照 -->
        <button
          class="shrink-0 w-10 flex items-center justify-center rounded-lg text-text-secondary bg-card-bg border border-border-light active:scale-[0.96] transition-all"
          :class="snapshotting ? 'opacity-60' : ''"
          :disabled="snapshotting"
          @click="handleSnapshot">
          <span v-if="snapshotting" class="material-symbols-outlined animate-spin text-base">progress_activity</span>
          <span v-else-if="snapshotToast" class="text-xs font-medium text-brand">{{ snapshotToast }}</span>
          <span v-else class="material-symbols-outlined text-base">add_a_photo</span>
        </button>
      </section>

      <!-- 总资产图表 -->
      <section v-if="totalData" class="bg-card-bg rounded-lg p-lg card-shadow border border-border-light/40">
        <!-- Tab + 当前值 -->
        <div class="flex items-center justify-between mb-3">
          <div class="flex gap-1 bg-card-alt rounded-md p-1">
            <button v-for="t in (['value','shares','profit'] as const)" :key="t"
              class="px-3 py-1.5 rounded-md text-xs font-medium transition-all whitespace-nowrap"
              :class="totalTab === t ? 'bg-card-bg text-brand shadow-card' : 'text-text-secondary'"
              @click="selectTotalTab(t)">
              {{ t === 'value' ? '市值' : t === 'shares' ? '份额' : '收益' }}
            </button>
          </div>
          <span class="text-sm font-bold whitespace-nowrap text-brand font-display">{{ totalCurrentValue }}</span>
        </div>
        <!-- 图表 -->
        <div v-if="totalData.series.length > 0" ref="totalChartRef" style="width: 100%; height: 180px;"></div>
        <div v-else class="flex items-center justify-center text-text-tertiary text-xs" style="height: 180px;">暂无快照数据</div>
        <!-- 摘要 -->
        <div class="flex items-center gap-4 pt-3 border-t border-border-light mt-2">
          <div class="flex items-center gap-1">
            <span class="text-[10px] text-text-tertiary">区间变动</span>
            <span class="text-xs font-bold whitespace-nowrap text-brand font-display">{{ fmtChange(totalData.totalChange) }}</span>
          </div>
          <div class="flex items-center gap-1">
            <span class="text-[10px] text-text-tertiary">涨幅</span>
            <span class="text-xs font-bold whitespace-nowrap text-brand font-display">{{ fmtPct(totalData.totalChangePercent) }}</span>
          </div>
          <div class="flex items-center gap-1">
            <span class="text-[10px] text-text-tertiary">记录数</span>
            <span class="text-xs font-bold text-text-primary whitespace-nowrap font-display">{{ totalData.series.length }} 条</span>
          </div>
        </div>
      </section>

      <!-- 分隔线 -->
      <div v-if="holdings.length > 0" class="flex items-center gap-3 py-1">
        <div class="flex-1 h-px bg-border-light"></div>
        <span class="text-xs text-text-tertiary">单持仓分析</span>
        <div class="flex-1 h-px bg-border-light"></div>
      </div>

      <!-- 持仓选择器 -->
      <section v-if="holdings.length > 0" class="flex gap-2 overflow-x-auto pb-1 hide-scrollbar">
        <button v-for="(h, idx) in holdings" :key="h.id"
          class="shrink-0 px-3 py-2 rounded-lg text-xs font-medium transition-all whitespace-nowrap flex items-center gap-1.5"
          :class="selectedIdx === idx ? 'bg-brand-light text-brand' : 'bg-card-alt text-text-secondary'"
          @click="selectHolding(idx)">
          <span class="w-2 h-2 rounded-full shrink-0" :style="{ backgroundColor: holdingColor(h.assetCategory) }"></span>
          {{ h.name }}
        </button>
      </section>

      <!-- 单持仓图表 -->
      <section v-if="holdingSeries" class="bg-card-bg rounded-lg p-lg card-shadow border border-border-light/40">
        <div class="flex items-center justify-between mb-3">
          <div class="flex gap-1 bg-card-alt rounded-md p-1">
            <button v-for="t in (['value','shares'] as const)" :key="t"
              class="px-3 py-1.5 rounded-md text-xs font-medium transition-all whitespace-nowrap"
              :class="holdingTab === t ? 'bg-card-bg text-brand shadow-card' : 'text-text-secondary'"
              @click="selectHoldingTab(t)">
              {{ t === 'value' ? '市值走势' : '份额走势' }}
            </button>
          </div>
          <span v-if="holdingSeries.series.length > 0" class="text-sm font-bold whitespace-nowrap text-brand font-display">
            {{ holdingTab === 'value'
              ? fmtMoney(holdingSeries.series[holdingSeries.series.length - 1]?.marketValue)
              : `${holdingSeries.series[holdingSeries.series.length - 1]?.shares.toLocaleString()} 份` }}
          </span>
        </div>
        <div v-if="holdingSeries.series.length > 0" ref="holdingChartRef" style="width: 100%; height: 160px;"></div>
        <div v-else class="flex items-center justify-center text-text-tertiary text-xs" style="height: 160px;">暂无快照数据</div>
      </section>

      <!-- 持仓加载中 -->
      <section v-else-if="holdingLoading" class="bg-card-bg rounded-lg p-lg card-shadow border border-border-light/40 flex justify-center py-12">
        <span class="material-symbols-outlined animate-spin text-text-tertiary">progress_activity</span>
      </section>

      <!-- vs 上期 + 年化收益率 -->
      <section v-if="holdingDiff && annualized" class="bg-card-bg rounded-lg p-lg card-shadow border border-border-light/40 space-y-lg">
        <!-- vs 上期 -->
        <div>
          <h2 class="text-sm font-bold text-text-primary mb-3 font-display">
            {{ diffDays === null ? 'vs 上期' : `较上期（${diffDays} 天）` }}
          </h2>
          <div class="grid grid-cols-2 gap-3">
            <div class="rounded-lg p-3" :class="holdingDiff.marketValueChange >= 0 ? 'bg-brand-light' : 'bg-error/10'">
              <p class="text-[11px] text-text-secondary mb-1">市值变化</p>
              <p class="text-lg font-extrabold whitespace-nowrap font-display" :class="holdingDiff.marketValueChange >= 0 ? 'text-brand' : 'text-error'">
                {{ fmtChange(holdingDiff.marketValueChange) }}
              </p>
              <p class="text-[10px] text-text-secondary mt-1">{{ fmtPct(holdingDiff.marketValueChangePct) }}</p>
            </div>
            <div class="rounded-lg p-3 bg-card-alt">
              <p class="text-[11px] text-text-secondary mb-1">份额变化</p>
              <p class="text-lg font-extrabold whitespace-nowrap font-display text-text-primary">
                {{ fmtChange(holdingDiff.sharesChange) }} 份
              </p>
              <p class="text-[10px] text-text-secondary mt-1">{{ fmtPct(holdingDiff.sharesChangePct) }}</p>
            </div>
          </div>
          <div class="mt-3 grid grid-cols-2 gap-3 text-xs">
            <div>
              <template v-if="holdingDiff.previous">
                <p class="text-text-tertiary mb-1">上期 ({{ fmtDate(holdingDiff.previous.date) }})</p>
                <p class="text-text-primary font-medium whitespace-nowrap">{{ fmtMoney(holdingDiff.previous.marketValue) }} / {{ holdingDiff.previous.shares.toLocaleString() }} 份</p>
              </template>
              <template v-else>
                <p class="text-text-tertiary mb-1">上期</p>
                <p class="text-text-secondary font-medium whitespace-nowrap">暂无数据</p>
              </template>
            </div>
            <div>
              <template v-if="holdingDiff.current">
                <p class="text-text-tertiary mb-1">本期 ({{ fmtDate(holdingDiff.current.date) }})</p>
                <p class="text-text-primary font-medium whitespace-nowrap">{{ fmtMoney(holdingDiff.current.marketValue) }} / {{ holdingDiff.current.shares.toLocaleString() }} 份</p>
              </template>
              <template v-else>
                <p class="text-text-tertiary mb-1">本期</p>
                <p class="text-text-secondary font-medium whitespace-nowrap">暂无数据</p>
              </template>
            </div>
          </div>
        </div>

        <div class="h-px bg-border-light"></div>

        <!-- 年化收益率 -->
        <div>
          <h2 class="text-sm font-bold text-text-primary mb-3 font-display">年化收益率</h2>
          <div v-if="annualized.annualizedReturn != null" class="flex items-center gap-4">
            <div class="flex items-baseline gap-0.5">
              <span class="text-[28px] leading-none font-extrabold whitespace-nowrap font-display"
                :class="annualized.annualizedReturn >= 0 ? 'text-brand' : 'text-error'">
                {{ annualized.annualizedReturn.toFixed(2) }}
              </span>
              <span class="text-text-secondary text-sm">%</span>
            </div>
            <div class="flex-1 grid grid-cols-2 gap-x-3 gap-y-1.5">
              <div>
                <p class="text-[10px] text-text-tertiary">总投入</p>
                <p class="text-xs font-bold text-text-primary whitespace-nowrap font-display">{{ fmtMoney(annualized.totalInvested) }}</p>
              </div>
              <div>
                <p class="text-[10px] text-text-tertiary">当前市值</p>
                <p class="text-xs font-bold text-text-primary whitespace-nowrap font-display">{{ fmtMoney(annualized.currentValue) }}</p>
              </div>
              <div>
                <p class="text-[10px] text-text-tertiary">持有天数</p>
                <p class="text-xs font-bold text-text-primary whitespace-nowrap font-display">{{ annualized.holdingDays }} 天</p>
              </div>
              <div>
                <p class="text-[10px] text-text-tertiary">首次买入</p>
                <p class="text-xs font-bold text-text-primary whitespace-nowrap font-display">{{ fmtDate(annualized.firstTransactionDate) }}</p>
              </div>
            </div>
          </div>
          <div v-else class="py-4 text-center">
            <p class="text-xs text-text-tertiary">数据不足，无法计算年化收益率</p>
          </div>
        </div>
      </section>

      <!-- 快照记录列表 -->
      <section v-if="holdingSeries" class="bg-card-bg rounded-lg p-lg card-shadow border border-border-light/40">
        <h2 class="text-sm font-bold text-text-primary mb-3 font-display">快照记录</h2>
        <div class="space-y-2">
          <div v-for="(p, idx) in [...holdingSeries.series].reverse().slice(0, 6)" :key="p.date"
            class="flex items-center justify-between py-2 px-3 rounded-lg"
            :class="idx === 0 ? 'bg-card-alt' : ''">
            <div>
              <p class="text-xs font-medium text-text-primary">{{ p.date }}</p>
              <p class="text-[10px] text-text-secondary mt-0.5">
                {{ fmtMoney(p.marketValue) }} · {{ p.shares.toLocaleString() }} 份 · {{ fmtPct(p.profitLossPct) }}
              </p>
            </div>
            <span v-if="idx === 0" class="text-[10px] px-2 py-0.5 rounded-full bg-brand-light text-brand">最新</span>
          </div>
        </div>
      </section>

    </main>
  </div>
</template>
