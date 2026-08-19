<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getHoldingSeries,
  getAnnualizedReturn,
  type HoldingSeries,
  type AnnualizedReturn,
  type HoldingPoint,
  type HistoryRange,
} from '@/api/assetHistory'
import PageStateComp from '@/components/shared/PageState.vue'

const route = useRoute()
const router = useRouter()
const holdingId = computed(() => route.params.id as string)

// 页面状态
const pageState = ref<'loading' | 'ready' | 'error'>('loading')
const errorMsg = ref('')
const range = ref<HistoryRange>('month')

// 数据
const holdingSeries = ref<HoldingSeries | null>(null)
const annualized = ref<AnnualizedReturn | null>(null)
const chartTab = ref<'value' | 'shares' | 'profit'>('value')

// 图表
let chart: any = null
const chartRef = ref<HTMLElement | null>(null)
const crosshairActive = ref(false)
const crosshairIdx = ref(-1)
const crosshairX = ref(0)

// 格式化
function fmtMoney(v: number | null | undefined): string {
  if (v == null) return '¥0'
  if (Math.abs(v) >= 1_0000_0000) return `¥${(v / 1_0000_0000).toFixed(2)}亿`
  if (Math.abs(v) >= 1_0000) return `¥${(v / 1_0000).toFixed(2)}万`
  return `¥${v.toLocaleString()}`
}
function fmtDate(dateStr: string): string {
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}
function fmtFullDate(dateStr: string): string {
  const d = new Date(dateStr)
  return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()}`
}
function fmtChange(v: number): string {
  return v >= 0 ? `+${v.toFixed(2)}` : v.toFixed(2)
}
function fmtPct(v: number): string {
  return v >= 0 ? `+${v.toFixed(2)}%` : `${v.toFixed(2)}%`
}

// 类别色
const catColors: Record<string, string> = {
  us_stock: '#3B82F6',
  gold: '#8A6B08',
  dividend: '#8A6B08',
  crypto: '#6366F1',
  cash: '#1A6B56',
}
const brandColor = '#1A6B56'

function chartColor(): string {
  if (!holdingSeries.value) return brandColor
  return catColors[holdingSeries.value.holding.assetCategory] || brandColor
}

// ========== 逐次快照变化计算 ==========
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

// 选中坐标点
const selectedPoint = computed(() => {
  if (crosshairIdx.value < 0 || !holdingSeries.value) return null
  const s = holdingSeries.value.series
  if (crosshairIdx.value >= s.length) return null
  return s[crosshairIdx.value]
})

// ========== ECharts ==========
function computeLabelInterval(count: number): number {
  if (count <= 7) return 0
  if (count <= 15) return 1
  if (count <= 30) return 2
  return Math.floor(count / 10)
}

function buildOption(data: number[], xLabels: string[], color: string, min: number, max: number) {
  return {
    grid: { left: 48, right: 12, top: 12, bottom: 28 },
    tooltip: { show: false }, // 用自定义浮层替代
    xAxis: {
      type: 'category',
      data: xLabels,
      axisLine: { lineStyle: { color: '#E8E7E5' } },
      axisTick: { show: true, length: 4, lineStyle: { color: '#C8C7C5' } },
      axisLabel: { color: '#6F6F6E', fontSize: 9, interval: computeLabelInterval(xLabels.length) },
    },
    yAxis: {
      type: 'value',
      min,
      max: max > 0 ? max : undefined,
      axisLine: { show: false },
      axisTick: { show: true, length: 4, lineStyle: { color: '#C8C7C5' } },
      splitLine: { show: true, lineStyle: { color: '#F0EFED', type: 'dashed', width: 1 } },
      axisLabel: {
        color: '#6F6F6E',
        fontSize: 9,
        formatter: (v: number) => {
          if (Math.abs(v) >= 10000) return (v / 10000).toFixed(1) + 'w'
          return v.toLocaleString()
        },
      },
    },
    series: [{
      data: data.map((v, i) => ({
        value: v,
        itemStyle: i === crosshairIdx.value ? { color, borderColor: '#fff', borderWidth: 3 } : { color, borderColor: '#fff', borderWidth: 1.5 },
      })),
      type: 'line',
      smooth: false,
      symbol: 'circle',
      symbolSize: (val: any, params: any) => params.dataIndex === crosshairIdx.value ? 8 : 4,
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

function renderChart() {
  if (!chartRef.value) { chart?.dispose(); chart = null; return }
  if (!(window as any).echarts) return
  if (chart && chart.getDom() !== chartRef.value) { chart.dispose(); chart = null }
  if (!chart) chart = (window as any).echarts.init(chartRef.value)

  const s = holdingSeries.value?.series
  if (!s || s.length === 0) { chart.dispose(); chart = null; return }

  const labels = s.map(p => fmtDate(p.date))
  let data: number[]
  if (chartTab.value === 'value') data = s.map(p => p.marketValue)
  else if (chartTab.value === 'shares') data = s.map(p => p.shares)
  else data = s.map(p => p.profitLoss)

  const vals = data.filter(v => v != null)
  const dataMin = vals.length > 0 ? Math.min(...vals) : 0
  const dataMax = vals.length > 0 ? Math.max(...vals) : 1
  const padding = (dataMax - dataMin) * 0.15 || dataMax * 0.1 || 1
  // 收益模式：Y 轴 0 居中（正负对称）
  const isProfit = chartTab.value === 'profit'
  const yMin = isProfit ? Math.min(0, dataMin - padding) : Math.max(0, dataMin - padding)
  const yMax = isProfit ? Math.max(Math.abs(dataMin), Math.abs(dataMax)) + padding : dataMax + padding

  const color = chartColor()
  chart.setOption(buildOption(data, labels, color, yMin, yMax), true)

  // 绑定触摸事件实现十字准星
  bindCrosshairEvents()
}

function bindCrosshairEvents() {
  if (!chart) return
  const zr = chart.getZr()
  zr.off('mousedown')
  zr.off('mousemove')
  zr.off('mouseup')

  zr.on('mousedown', (e: any) => updateCrosshair(e))
  zr.on('mousemove', (e: any) => {
    if (crosshairActive.value) updateCrosshair(e)
  })
  zr.on('mouseup', () => { /* 保持选中 */ })

  // 点击图表外区域取消选中
  document.addEventListener('click', onDocClick)
}

function onDocClick(e: MouseEvent) {
  if (chartRef.value && !chartRef.value.contains(e.target as Node)) {
    crosshairActive.value = false
    crosshairIdx.value = -1
    renderChart()
    document.removeEventListener('click', onDocClick)
  }
}

function updateCrosshair(e: any) {
  if (!chart || !holdingSeries.value) return
  const pointInGrid = chart.convertFromPixel({ seriesIndex: 0 }, [e.offsetX || 0, e.offsetY || 0])
  if (!pointInGrid) return

  const s = holdingSeries.value.series
  const idx = Math.round(pointInGrid[0])
  if (idx < 0 || idx >= s.length) return

  crosshairActive.value = true
  crosshairIdx.value = idx
  renderChart()
}

function ensureChart() {
  if (!chartRef.value) return
  nextTick(() => renderChart())
}

// ========== 数据加载 ==========
async function loadData() {
  pageState.value = 'loading'
  errorMsg.value = ''
  crosshairActive.value = false
  crosshairIdx.value = -1
  chart?.dispose()
  chart = null
  try {
    const id = holdingId.value
    const results = await Promise.allSettled([
      getHoldingSeries(id, range.value),
      getAnnualizedReturn(id),
    ])
    holdingSeries.value = results[0].status === 'fulfilled' ? results[0].value : null
    annualized.value = results[1].status === 'fulfilled' ? results[1].value : null

    if (!holdingSeries.value) {
      pageState.value = 'error'
      errorMsg.value = '加载持仓数据失败'
      return
    }
    pageState.value = 'ready'
    await nextTick()
    renderChart()
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
  renderChart()
}

function goBack() { router.back() }

// 导航：点柱状切换
function getChangeClass(v: number): string {
  return v >= 0 ? 'text-pos' : 'text-neg'
}

onMounted(() => loadData())
onUnmounted(() => {
  document.removeEventListener('click', onDocClick)
  chart?.dispose()
})

// 窗口缩放
function handleResize() { chart?.resize() }
onMounted(() => window.addEventListener('resize', handleResize))
onUnmounted(() => window.removeEventListener('resize', handleResize))
</script>

<template>
  <div class="min-h-screen bg-page-bg flex flex-col">
    <!-- Header -->
    <header class="flex items-center justify-between px-gutter h-14 sticky top-0 z-50 bg-card-bg border-b border-border-light/40">
      <button @click="goBack" class="w-10 h-10 flex items-center justify-center -ml-2 active:opacity-80">
        <span class="material-symbols-outlined text-text-secondary">arrow_back</span>
      </button>
      <h1 class="font-display text-md font-semibold text-text-primary truncate max-w-[60%]">
        {{ holdingSeries?.holding?.name || '持仓历史' }}
      </h1>
      <div class="w-10" /><!-- 占位保持居中 -->
    </header>

    <!-- Loading -->
    <main v-if="pageState === 'loading'" class="flex-1 flex flex-col items-center justify-center py-32">
      <span class="material-symbols-outlined animate-spin text-text-tertiary text-3xl">progress_activity</span>
      <p class="font-body text-sm text-text-tertiary mt-md">加载中...</p>
    </main>

    <!-- Error -->
    <main v-else-if="pageState === 'error'" class="flex-1 flex flex-col items-center justify-center py-32">
      <span class="material-symbols-outlined text-error text-3xl mb-md">error</span>
      <p class="font-body text-sm font-medium text-text-primary mb-1">加载失败</p>
      <p class="font-body text-xs text-text-tertiary mb-lg">{{ errorMsg }}</p>
      <button class="px-lg py-2 bg-brand text-white rounded-lg font-body text-sm font-medium active:scale-[0.98] transition-transform" @click="loadData">重试</button>
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
      </section>

      <!-- 折线图 -->
      <section v-if="holdingSeries && holdingSeries.series.length > 0" class="bg-card-bg rounded-xl p-lg card-shadow border border-border-light/40">
        <!-- Tab -->
        <div class="flex gap-1 bg-card-alt rounded-md p-1 mb-3 w-fit">
          <button v-for="t in (['value','shares','profit'] as const)" :key="t"
            class="px-3 py-1.5 rounded-md text-xs font-medium transition-all whitespace-nowrap"
            :class="chartTab === t ? 'bg-card-bg text-brand shadow-card' : 'text-text-secondary'"
            @click="selectTab(t)">
            {{ t === 'value' ? '市值走势' : t === 'shares' ? '份额走势' : '收益走势' }}
          </button>
        </div>

        <!-- 图表 + 选中坐标详情 -->
        <div ref="chartRef" class="w-full" style="height: 200px; touch-action: pan-y;"></div>

        <!-- 选中坐标详情浮层 -->
        <Transition name="crosshair-info">
          <div v-if="crosshairActive && selectedPoint" class="mt-3 p-3 rounded-lg bg-card-alt/80 border border-border-light/40">
            <div class="flex items-center justify-between mb-2">
              <span class="font-body text-xs font-medium text-text-primary">{{ fmtFullDate(selectedPoint.date) }}</span>
              <span class="font-body text-[10px] text-text-tertiary bg-card-bg px-2 py-0.5 rounded-full">选中</span>
            </div>
            <div class="grid grid-cols-2 gap-x-3 gap-y-1.5">
              <div>
                <p class="text-[10px] text-text-tertiary">市值</p>
                <p class="text-sm font-bold text-text-primary font-display">{{ fmtMoney(selectedPoint.marketValue) }}</p>
              </div>
              <div>
                <p class="text-[10px] text-text-tertiary">份额</p>
                <p class="text-sm font-bold text-text-primary font-display">{{ selectedPoint.shares.toLocaleString() }} 份</p>
              </div>
              <div>
                <p class="text-[10px] text-text-tertiary">盈亏</p>
                <p class="text-sm font-bold font-display" :class="getChangeClass(selectedPoint.profitLoss)">{{ fmtChange(selectedPoint.profitLoss) }}</p>
              </div>
              <div>
                <p class="text-[10px] text-text-tertiary">收益率</p>
                <p class="text-sm font-bold font-display" :class="getChangeClass(selectedPoint.profitLossPct)">{{ fmtPct(selectedPoint.profitLossPct) }}</p>
              </div>
            </div>
          </div>
        </Transition>

        <!-- 提示（未选中时） -->
        <p v-if="!crosshairActive" class="mt-3 text-center font-body text-[11px] text-text-tertiary/50">点击图表查看坐标详情</p>
      </section>

      <!-- 空数据 -->
      <section v-else class="bg-card-bg rounded-xl p-lg card-shadow border border-border-light/40 flex items-center justify-center" style="height: 200px;">
        <p class="font-body text-sm text-text-tertiary">暂无快照数据</p>
      </section>

      <!-- 年化收益率 -->
      <section v-if="annualized" class="bg-card-bg rounded-xl p-lg card-shadow border border-border-light/40">
        <h2 class="font-body text-sm font-medium text-text-primary mb-3 flex items-center gap-2">
          <span class="material-symbols-outlined text-brand text-sm">trending_up</span>
          年化收益率
        </h2>
        <div v-if="annualized.annualizedReturn != null" class="space-y-3">
          <div class="flex items-center gap-4">
            <div class="flex items-baseline gap-0.5">
              <span class="text-[28px] leading-none font-extrabold font-display"
                :class="annualized.annualizedReturn >= 0 ? 'text-pos' : 'text-neg'">
                {{ annualized.annualizedReturn.toFixed(2) }}
              </span>
              <span class="text-text-secondary text-sm">%</span>
            </div>
          </div>
          <div class="grid grid-cols-2 gap-x-3 gap-y-2">
            <div>
              <p class="text-[10px] text-text-tertiary">总投入</p>
              <p class="text-xs font-bold text-text-primary font-display">{{ fmtMoney(annualized.totalInvested) }}</p>
            </div>
            <div>
              <p class="text-[10px] text-text-tertiary">当前市值</p>
              <p class="text-xs font-bold text-text-primary font-display">{{ fmtMoney(annualized.currentValue) }}</p>
            </div>
            <div>
              <p class="text-[10px] text-text-tertiary">持有天数</p>
              <p class="text-xs font-bold text-text-primary font-display">{{ annualized.holdingDays }} 天</p>
            </div>
            <div>
              <p class="text-[10px] text-text-tertiary">首次买入</p>
              <p class="text-xs font-bold text-text-primary font-display">{{ fmtFullDate(annualized.firstTransactionDate) }}</p>
            </div>
          </div>
        </div>
        <div v-else class="py-4 text-center">
          <p class="text-xs text-text-tertiary">
            {{ annualized.status === 'abnormal'
              ? '收益波动异常或暂不可信，未计算年化收益率'
              : '数据不足，暂无法计算年化收益率' }}
          </p>
        </div>
      </section>

      <!-- 快照变化记录 -->
      <section class="bg-card-bg rounded-xl p-lg card-shadow border border-border-light/40">
        <h2 class="font-body text-sm font-medium text-text-primary mb-3 flex items-center gap-2">
          <span class="material-symbols-outlined text-brand text-sm">history</span>
          快照变化记录
        </h2>

        <div v-if="snapshotChanges.length > 0" class="space-y-2">
          <div v-for="ch in snapshotChanges" :key="ch.curr.date"
            class="rounded-lg p-3 border border-border-light/30"
            :class="ch.idx === (holdingSeries?.series?.length ?? 1) - 1 ? 'bg-brand-light/40' : 'bg-card-alt/40'">
            <!-- 日期范围 -->
            <div class="flex items-center justify-between mb-2">
              <div class="flex items-center gap-1.5">
                <span class="font-body text-xs text-text-tertiary">{{ fmtFullDate(ch.prev.date) }}</span>
                <span class="material-symbols-outlined text-text-tertiary text-xs">arrow_forward</span>
                <span class="font-body text-xs text-text-primary font-medium">{{ fmtFullDate(ch.curr.date) }}</span>
              </div>
              <span class="font-body text-[10px] text-text-tertiary bg-card-bg px-2 py-0.5 rounded-full">
                {{ ch.days }} 天
              </span>
            </div>

            <!-- 变化数据 -->
            <div class="grid grid-cols-3 gap-x-2 gap-y-1">
              <div>
                <p class="text-[10px] text-text-tertiary">市值变化</p>
                <p class="text-xs font-bold font-display" :class="getChangeClass(ch.mvChange)">
                  {{ fmtChange(ch.mvChange) }}
                </p>
                <p class="text-[10px]" :class="getChangeClass(ch.mvChangePct)">{{ fmtPct(ch.mvChangePct) }}</p>
              </div>
              <div>
                <p class="text-[10px] text-text-tertiary">份额变化</p>
                <p class="text-xs font-bold font-display" :class="getChangeClass(ch.sharesChange)">
                  {{ fmtChange(ch.sharesChange) }} 份
                </p>
                <p class="text-[10px]" :class="getChangeClass(ch.sharesChangePct)">{{ fmtPct(ch.sharesChangePct) }}</p>
              </div>
              <div>
                <p class="text-[10px] text-text-tertiary">上期→本期</p>
                <p class="text-xs font-medium font-display text-text-primary">{{ fmtMoney(ch.prev.marketValue) }}</p>
                <p class="text-[10px] text-text-tertiary">→ {{ fmtMoney(ch.curr.marketValue) }}</p>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="flex flex-col items-center py-8">
          <span class="text-3xl mb-2">📭</span>
          <p class="font-body text-sm text-text-tertiary">需要至少两次快照才能比较变化</p>
        </div>
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
