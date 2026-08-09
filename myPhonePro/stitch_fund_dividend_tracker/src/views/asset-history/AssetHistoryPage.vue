<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { listHoldings } from '@/api/holding'
import type { HoldingItem } from '@/api/holding'
import {
  getAssetOverview,
  getHoldingDiff,
  triggerSnapshot,
  type TotalAssetSeries,
  type HoldingDiff,
  type HistoryRange,
} from '@/api/assetHistory'

const router = useRouter()

// 页面状态
const loading = ref(true)
const errorMsg = ref('')
const range = ref<HistoryRange>('month')

// 总资产
const totalData = ref<TotalAssetSeries | null>(null)
const totalTab = ref<'value' | 'shares' | 'profit'>('value')

// 持仓卡片
const holdings = ref<HoldingItem[]>([])
const holdingDiffs = ref<Map<string, HoldingDiff>>(new Map())

// 手动快照
const snapshotting = ref(false)
const snapshotToast = ref('')

// ECharts
let totalChart: any = null
const totalChartRef = ref<HTMLElement | null>(null)
const crosshairActive = ref(false)
const crosshairIdx = ref(-1)

// 类别色
const catColors: Record<string, string> = {
  us_stock: '#3B82F6',
  gold: '#8A6B08',
  dividend: '#8A6B08',
  crypto: '#6366F1',
  cash: '#1A6B56',
}
const brandColor = '#1A6B56'

function holdingColor(cat?: string): string {
  return catColors[cat || ''] || '#6F6F6E'
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
function fmtFullDate(dateStr: string): string {
  const d = new Date(dateStr)
  return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()}`
}

// 选中坐标点
const selectedPoint = computed(() => {
  if (crosshairIdx.value < 0 || !totalData.value) return null
  const s = totalData.value.series
  if (crosshairIdx.value >= s.length) return null
  return s[crosshairIdx.value]
})

// ========== ECharts 构建 ==========
function computeLabelInterval(count: number): number {
  if (count <= 7) return 0
  if (count <= 15) return 1
  if (count <= 30) return 2
  return Math.floor(count / 10)
}

function buildOption(data: number[], xLabels: string[], color: string) {
  const vals = data.filter(v => v != null)
  const dataMin = vals.length > 0 ? Math.min(...vals) : 0
  const dataMax = vals.length > 0 ? Math.max(...vals) : 1

  // Y 轴范围：市值/份额从 0 开始，收益模式 0 居中
  let yMin: number, yMax: number
  if (totalTab.value === 'profit') {
    const absMax = Math.max(Math.abs(dataMin), Math.abs(dataMax), 1)
    const pad = absMax * 0.15
    yMin = -(absMax + pad)
    yMax = absMax + pad
  } else {
    yMin = 0
    yMax = dataMax + dataMax * 0.1
  }

  return {
    grid: { left: 48, right: 12, top: 12, bottom: 28 },
    tooltip: { show: false },
    xAxis: {
      type: 'category',
      data: xLabels,
      axisLine: { lineStyle: { color: '#E8E7E5' } },
      axisTick: { show: true, length: 4, lineStyle: { color: '#C8C7C5' } },
      axisLabel: { color: '#6F6F6E', fontSize: 9, interval: computeLabelInterval(xLabels.length) },
    },
    yAxis: {
      type: 'value',
      min: yMin,
      max: yMax,
      axisLine: { show: false },
      axisTick: { show: true, length: 4, lineStyle: { color: '#C8C7C5' } },
      splitLine: { show: true, lineStyle: { color: '#F0EFED', type: 'dashed', width: 1 } },
      axisLabel: {
        color: '#6F6F6E',
        fontSize: 9,
        formatter: (v: number) => {
          if (totalTab.value === 'profit') {
            if (Math.abs(v) >= 10000) return (v / 10000).toFixed(1) + 'w'
            return v.toLocaleString()
          }
          if (Math.abs(v) >= 10000) return (v / 10000).toFixed(1) + 'w'
          return v.toLocaleString()
        },
      },
    },
    series: [{
      data: data.map((v, i) => ({
        value: v,
        itemStyle: i === crosshairIdx.value
          ? { color, borderColor: '#fff', borderWidth: 3 }
          : { color, borderColor: '#fff', borderWidth: 1.5 },
      })),
      type: 'line',
      smooth: false,
      symbol: 'circle',
      symbolSize: (_val: any, params: any) => params.dataIndex === crosshairIdx.value ? 8 : 4,
      showSymbol: true,
      lineStyle: { color, width: 2 },
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
  if (!totalChartRef.value) { totalChart?.dispose(); totalChart = null; return }
  if (!(window as any).echarts) return
  if (totalChart && totalChart.getDom() !== totalChartRef.value) { totalChart.dispose(); totalChart = null }
  if (!totalChart) totalChart = (window as any).echarts.init(totalChartRef.value)
}

function renderTotalChart() {
  ensureTotalChart()
  if (!totalChart || !totalData.value) return
  const series = totalData.value.series
  if (!series || series.length === 0) { totalChart.dispose(); totalChart = null; return }

  const labels = series.map(p => fmtDate(p.date))
  let data: number[]
  if (totalTab.value === 'value') data = series.map(p => p.totalMarketValue)
  else if (totalTab.value === 'shares') data = series.map(p => p.totalShares)
  else data = series.map(p => p.totalProfitLoss)

  totalChart.setOption(buildOption(data, labels, brandColor), true)
  bindCrosshairEvents()
}

function bindCrosshairEvents() {
  if (!totalChart) return
  const zr = totalChart.getZr()
  zr.off('mousedown')
  zr.off('mousemove')
  zr.off('mouseup')

  zr.on('mousedown', (e: any) => updateCrosshair(e))
  zr.on('mousemove', (e: any) => {
    if (crosshairActive.value) updateCrosshair(e)
  })
  zr.on('mouseup', () => { /* keep selection */ })
  document.addEventListener('click', onDocClick)
}

function onDocClick(e: MouseEvent) {
  if (totalChartRef.value && !totalChartRef.value.contains(e.target as Node)) {
    crosshairActive.value = false
    crosshairIdx.value = -1
    renderTotalChart()
    document.removeEventListener('click', onDocClick)
  }
}

function updateCrosshair(e: any) {
  if (!totalChart || !totalData.value) return
  const pointInGrid = totalChart.convertFromPixel({ seriesIndex: 0 }, [e.offsetX || 0, e.offsetY || 0])
  if (!pointInGrid) return

  const s = totalData.value.series
  const idx = Math.round(pointInGrid[0])
  if (idx < 0 || idx >= s.length) return

  crosshairActive.value = true
  crosshairIdx.value = idx
  renderTotalChart()
}

// ========== 数据加载 ==========
async function loadTotalData() {
  totalData.value = await getAssetOverview(range.value)
}

async function loadHoldingDiffs(holdingList: HoldingItem[]) {
  const diffs = new Map<string, HoldingDiff>()
  const results = await Promise.allSettled(
    holdingList.map(h => getHoldingDiff(h.id))
  )
  holdingList.forEach((h, i) => {
    const r = results[i]
    // 仅当 current 和 previous 都存在（至少两次快照）时才纳入
    if (r.status === 'fulfilled' && r.value?.current && r.value?.previous) {
      diffs.set(h.id, r.value)
    }
  })
  holdingDiffs.value = diffs
}

async function loadAll() {
  loading.value = true
  errorMsg.value = ''
  crosshairActive.value = false
  crosshairIdx.value = -1
  totalChart?.dispose()
  totalChart = null
  try {
    const holdingList = await listHoldings()
    holdings.value = holdingList
    await Promise.all([
      loadTotalData(),
      loadHoldingDiffs(holdingList),
    ])
  } catch (e: any) {
    console.error('加载数据失败:', e)
    errorMsg.value = e.message || '加载失败'
  } finally {
    loading.value = false
    await nextTick()
    renderTotalChart()
  }
}

function selectRange(r: HistoryRange) {
  range.value = r
  loadAll()
}

function selectTotalTab(tab: 'value' | 'shares' | 'profit') {
  totalTab.value = tab
  renderTotalChart()
}

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

function goToHoldingHistory(id: string) {
  router.push({ name: 'holding-history', params: { id } })
}

function goBack() { router.back() }
function goHome() { router.push({ name: 'home' }) }

function getChangeClass(v: number): string {
  return v >= 0 ? 'text-pos' : 'text-neg'
}

// ========== 生命周期 ==========
onMounted(async () => {
  window.addEventListener('resize', handleResize)
  await loadAll()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  document.removeEventListener('click', onDocClick)
  totalChart?.dispose()
})

function handleResize() { totalChart?.resize() }
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
    <main v-else class="flex-1 px-gutter pt-md pb-28 space-y-md">

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

      <!-- ========== 总资产图表 ========== -->
      <section v-if="totalData && totalData.series.length > 0" class="bg-card-bg rounded-xl p-lg card-shadow border border-border-light/40">
        <!-- Tab -->
        <div class="flex gap-1 bg-card-alt rounded-md p-1 mb-3 w-fit">
          <button v-for="t in (['value','shares','profit'] as const)" :key="t"
            class="px-3 py-1.5 rounded-md text-xs font-medium transition-all whitespace-nowrap"
            :class="totalTab === t ? 'bg-card-bg text-brand shadow-card' : 'text-text-secondary'"
            @click="selectTotalTab(t)">
            {{ t === 'value' ? '市值' : t === 'shares' ? '份额' : '收益' }}
          </button>
        </div>

        <!-- 图表 -->
        <div ref="totalChartRef" class="w-full" style="height: 200px; touch-action: pan-y;"></div>

        <!-- 选中坐标详情 -->
        <Transition name="crosshair-info">
          <div v-if="crosshairActive && selectedPoint" class="mt-3 p-3 rounded-lg bg-card-alt/80 border border-border-light/40">
            <div class="flex items-center justify-between mb-2">
              <span class="font-body text-xs font-medium text-text-primary">{{ fmtFullDate(selectedPoint.date) }}</span>
              <span class="font-body text-[10px] text-text-tertiary bg-card-bg px-2 py-0.5 rounded-full">选中</span>
            </div>
            <div class="grid grid-cols-2 gap-x-3 gap-y-1.5">
              <div>
                <p class="text-[10px] text-text-tertiary">总市值</p>
                <p class="text-sm font-bold text-text-primary font-display">{{ fmtMoney(selectedPoint.totalMarketValue) }}</p>
              </div>
              <div>
                <p class="text-[10px] text-text-tertiary">总份额</p>
                <p class="text-sm font-bold text-text-primary font-display">{{ selectedPoint.totalShares.toLocaleString() }} 份</p>
              </div>
              <div>
                <p class="text-[10px] text-text-tertiary">盈亏</p>
                <p class="text-sm font-bold font-display" :class="getChangeClass(selectedPoint.totalProfitLoss)">{{ fmtChange(selectedPoint.totalProfitLoss) }}</p>
              </div>
              <div>
                <p class="text-[10px] text-text-tertiary">收益率</p>
                <p class="text-sm font-bold font-display" :class="getChangeClass(selectedPoint.totalProfitLossPct)">{{ fmtPct(selectedPoint.totalProfitLossPct) }}</p>
              </div>
            </div>
          </div>
        </Transition>

        <p v-if="!crosshairActive" class="mt-3 text-center font-body text-[11px] text-text-tertiary/50">点击图表查看坐标详情</p>

        <!-- 摘要 -->
        <div class="flex items-center gap-4 pt-3 border-t border-border-light mt-2">
          <div class="flex items-center gap-1">
            <span class="text-[10px] text-text-tertiary">区间变动</span>
            <span class="text-xs font-bold text-brand font-display">{{ fmtChange(totalData.totalChange) }}</span>
          </div>
          <div class="flex items-center gap-1">
            <span class="text-[10px] text-text-tertiary">涨幅</span>
            <span class="text-xs font-bold text-brand font-display">{{ fmtPct(totalData.totalChangePercent) }}</span>
          </div>
          <div class="flex items-center gap-1">
            <span class="text-[10px] text-text-tertiary">记录数</span>
            <span class="text-xs font-bold text-text-primary font-display">{{ totalData.series.length }} 条</span>
          </div>
        </div>
      </section>

      <!-- 总资产空数据 -->
      <section v-else class="bg-card-bg rounded-xl p-lg card-shadow border border-border-light/40 flex items-center justify-center" style="height: 200px;">
        <p class="font-body text-sm text-text-tertiary">暂无快照数据，点击右上角 📸 记录一次</p>
      </section>

      <!-- ========== 分隔线 + 持仓分析标题 ========== -->
      <div v-if="holdings.length > 0" class="flex items-center gap-3 py-1">
        <div class="flex-1 h-px bg-border-light"></div>
        <span class="text-xs text-text-tertiary whitespace-nowrap">持仓分析</span>
        <div class="flex-1 h-px bg-border-light"></div>
      </div>

      <!-- ========== 持仓竖向卡片 ========== -->
      <section v-if="holdings.length > 0" class="space-y-sm">
        <div
          v-for="h in holdings"
          :key="h.id"
          class="bg-card-bg rounded-xl p-md card-shadow border border-border-light/40 cursor-pointer interactive-card active:scale-[0.98] transition-all relative overflow-hidden"
          @click="goToHoldingHistory(h.id)"
        >
          <!-- 顶部渐变线 -->
          <div class="absolute top-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-brand/20 to-transparent pointer-events-none"></div>
          <!-- 右上角树叶水印 -->
          <span class="material-symbols-outlined absolute -top-3 -right-3 text-5xl text-brand/[0.04] pointer-events-none select-none">eco</span>

          <!-- 第一行：圆点 + 名称 + 代码 + 箭头 -->
          <div class="flex items-center gap-md">
            <span class="w-2.5 h-2.5 rounded-full shrink-0" :style="{ backgroundColor: holdingColor(h.assetCategory) }"></span>
            <div class="flex-1 min-w-0">
              <p class="font-body text-sm font-medium text-text-primary truncate">{{ h.name }}</p>
              <p class="font-body text-[11px] text-text-tertiary">{{ h.code }}</p>
            </div>
            <span class="material-symbols-outlined text-text-tertiary text-sm shrink-0">chevron_right</span>
          </div>

          <!-- 第二行：vs 上期变化数据（如果有 diff） -->
          <template v-if="holdingDiffs.has(h.id)">
            <div class="mt-sm grid grid-cols-2 gap-x-3 gap-y-1.5 rounded-lg bg-card-alt/50 p-sm">
              <div>
                <p class="text-[10px] text-text-tertiary">市值变化</p>
                <p class="text-sm font-bold font-display" :class="getChangeClass(holdingDiffs.get(h.id)!.marketValueChange)">
                  {{ fmtChange(holdingDiffs.get(h.id)!.marketValueChange) }}
                </p>
                <p class="text-[10px]" :class="getChangeClass(holdingDiffs.get(h.id)!.marketValueChangePct)">
                  {{ fmtPct(holdingDiffs.get(h.id)!.marketValueChangePct) }}
                </p>
              </div>
              <div>
                <p class="text-[10px] text-text-tertiary">份额变化</p>
                <p class="text-sm font-bold font-display" :class="getChangeClass(holdingDiffs.get(h.id)!.sharesChange)">
                  {{ fmtChange(holdingDiffs.get(h.id)!.sharesChange) }} 份
                </p>
                <p class="text-[10px]" :class="getChangeClass(holdingDiffs.get(h.id)!.sharesChangePct)">
                  {{ fmtPct(holdingDiffs.get(h.id)!.sharesChangePct) }}
                </p>
              </div>
            </div>
            <!-- 日期范围 -->
            <div class="mt-2 flex items-center gap-1.5 text-[10px] text-text-tertiary/60">
              <span>{{ fmtFullDate(holdingDiffs.get(h.id)!.previous.date) }}</span>
              <span class="material-symbols-outlined text-[10px]">arrow_forward</span>
              <span>{{ fmtFullDate(holdingDiffs.get(h.id)!.current.date) }}</span>
            </div>
          </template>

          <!-- 无 diff 数据 -->
          <div v-else class="mt-sm rounded-lg bg-card-alt/50 p-sm">
            <p class="font-body text-xs text-text-tertiary text-center">暂无变化数据</p>
          </div>
        </div>
      </section>

      <!-- 无持仓 -->
      <section v-else class="flex flex-col items-center justify-center py-12 gap-sm">
        <span class="text-4xl">📦</span>
        <p class="font-body text-sm text-text-tertiary">暂无持仓数据</p>
      </section>

    </main>
  </div>
</template>

<style scoped>
.crosshair-info-enter-active,
.crosshair-info-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.crosshair-info-enter-from,
.crosshair-info-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
