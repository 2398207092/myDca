<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getHolding, getForecast, deleteHolding, updateHolding } from '@/api/holding'
import { listTransactions } from '@/api/transaction'
import { listDcaPlans } from '@/api/dca'
import type { HoldingItem, ForecastData, UpdateHoldingReq } from '@/api/holding'
import type { TransactionItem } from '@/api/transaction'
import type { DcaPlanVO } from '@/api/dca'
import PageStateComp from '@/components/shared/PageState.vue'
import DcaCreateSheet from '@/components/dca/DcaCreateSheet.vue'
import DcaExecuteSheet from '@/components/dca/DcaExecuteSheet.vue'

const route = useRoute()
const router = useRouter()

// 页面状态
const pageState = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')

// 标签切换
const forecastTab = ref<'12m' | '5y'>('12m')

// 进度条动画
const progressWidth = ref(0)

// API 数据
const holding = ref<HoldingItem | null>(null)
const forecast = ref<ForecastData | null>(null)
const transactions = ref<TransactionItem[]>([])

// DCA 定投
const dcaPlans = ref<DcaPlanVO[]>([])
const showCreateSheet = ref(false)
const showExecuteSheet = ref(false)
const selectedPlanId = ref('')

async function loadDcaPlans() {
  const id = route.params.id as string
  try {
    dcaPlans.value = await listDcaPlans(id)
  } catch (e) {
    console.error('加载定投计划失败:', e)
  }
}

function handleDcaCreated() {
  showCreateSheet.value = false
  loadDcaPlans()
}

function openExecuteSheet(planId: string) {
  selectedPlanId.value = planId
  showExecuteSheet.value = true
}

function handleDcaExecuted() {
  showExecuteSheet.value = false
  loadData()
}

function goToDcaPlanDetail(planId: string) {
  router.push({ name: 'dca-plan-detail', params: { id: planId } })
}

// === 异步加载数据 ===
async function loadData() {
  pageState.value = 'loading'
  const id = route.params.id as string
  try {
    const [h, f, t] = await Promise.all([
      getHolding(id),
      getForecast(id, '12m'),
      listTransactions(id),
    ])
    // 加载定投计划（单独，避免失败影响主数据）
    loadDcaPlans()
    holding.value = h
    forecast.value = f
    transactions.value = t
    pageState.value = 'ready'
    // 延迟触发进度条动画
    setTimeout(() => {
      progressWidth.value = h.dividendRecoveryRate
    }, 100)
  } catch (e) {
    console.error('加载持仓详情失败:', e)
    pageState.value = 'error'
  }
}

// 监听路由参数变化，重新加载数据（解决 KeepAlive 缓存导致不同持仓间切换不刷新的问题）
watch(() => route.params.id, (newId) => {
  if (newId && route.name === 'holding-detail') loadData()
})

async function switchForecastTab(tab: '12m' | '5y') {
  forecastTab.value = tab
  const id = route.params.id as string
  try {
    forecast.value = await getForecast(id, tab)
  } catch (e) {
    console.error('加载预测数据失败:', e)
  }
}

// === 格式化展示值 ===
const annualDividend = computed(() => holding.value?.predictedDividend?.toLocaleString() ?? '0')
const marketValueFormatted = computed(() => holding.value?.marketValue?.toLocaleString() ?? '0')
const totalDividendFormatted = computed(() => holding.value?.totalDividendReceived?.toLocaleString() ?? '0')
const netInvestmentFormatted = computed(() => holding.value?.netInvestment?.toLocaleString() ?? '0')
const remainingRecovery = computed(() => {
  if (!holding.value) return '0'
  return (holding.value.netInvestment - holding.value.totalDividendReceived).toLocaleString()
})

// 是否有分红（用于控制分红相关 UI 的显隐）
const hasDividends = computed(() => {
  return holding.value != null && (holding.value.predictedDividend > 0 || holding.value.totalDividendReceived > 0)
})

// === 当前成本格式化（直接使用后端返回的 costPerShare）===
const costPerShareFormatted = computed(() => {
  if (!holding.value) return '0.0000'
  return holding.value.costPerShare.toLocaleString('zh-CN', { minimumFractionDigits: 4, maximumFractionDigits: 4 })
})

// === 最新价格格式化 ===
const latestPriceFormatted = computed(() => {
  if (!holding.value || !holding.value.latestPrice) return '--'
  return holding.value.latestPrice.toLocaleString('zh-CN', { minimumFractionDigits: 4, maximumFractionDigits: 4 })
})

// === 净值日期格式化（MM-DD）===
const priceDateFormatted = computed(() => {
  if (!holding.value?.priceDate) return '--'
  const date = new Date(holding.value.priceDate)
  return `${date.getMonth() + 1}月${date.getDate()}日`
})

const sharesFormatted = computed(() => {
  if (!holding.value) return '0'
  return holding.value.shares.toLocaleString('zh-CN', { maximumFractionDigits: 4 })
})

// === 类型映射（币种 + 资产类别）===
const typeMap: Record<string, { currency: string; currencyColor: string; typeLabel: string; typeColor: string }> = {
  fund:       { currency: '人民币', currencyColor: 'bg-[#ff7a45]', typeLabel: '基金', typeColor: 'bg-[#2f95dc]' },
  ETF:        { currency: '人民币', currencyColor: 'bg-[#ff7a45]', typeLabel: '基金', typeColor: 'bg-[#2f95dc]' },
  cny_asset:  { currency: '人民币', currencyColor: 'bg-[#ff7a45]', typeLabel: '资产', typeColor: 'bg-[#52c41a]' },
  A股:        { currency: '人民币', currencyColor: 'bg-[#ff7a45]', typeLabel: '股票', typeColor: 'bg-[#722ed1]' },
  港股:       { currency: '港币',   currencyColor: 'bg-[#eb2f96]', typeLabel: '股票', typeColor: 'bg-[#722ed1]' },
  美股:       { currency: '美元',   currencyColor: 'bg-[#13c2c2]', typeLabel: '股票', typeColor: 'bg-[#722ed1]' },
  自定义:     { currency: '人民币', currencyColor: 'bg-[#ff7a45]', typeLabel: '自定义', typeColor: 'bg-[#86909c]' },
}

const typeInfo = computed(() => typeMap[String(holding.value?.type)] ?? typeMap['自定义'])
const currencyLabel = computed(() => typeInfo.value.currency)
const currencyColorClass = computed(() => typeInfo.value.currencyColor)
const typeLabel = computed(() => typeInfo.value.typeLabel)
const typeColorClass = computed(() => typeInfo.value.typeColor)

// === 分红预测图表数据 ===
const forecastSeries = computed<{ label: string; value: number }[]>(() => {
  return forecast.value?.series ?? []
})

const trendPercentage = computed(() => forecast.value?.trendPercentage ?? 0)

// SVG 图表坐标计算
const chartPoints = computed(() => {
  const data = forecastSeries.value
  const n = data.length
  const w = 300
  const h = 80
  const padding = 10
  const maxVal = Math.max(...data.map(d => d.value))

  return data.map((d, i) => ({
    x: padding + (w - 2 * padding) * (i / Math.max(n - 1, 1)),
    y: h - ((d.value / maxVal) * (h - 20)) - 10,
    value: d.value,
    label: d.label,
  }))
})

const areaPath = computed(() => {
  const points = chartPoints.value
  if (points.length < 2) return ''
  const top = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ')
  return `${top} L${points[points.length - 1].x.toFixed(1)},80 L${points[0].x.toFixed(1)},80 Z`
})

const linePath = computed(() => {
  const points = chartPoints.value
  if (points.length < 2) return ''
  return points.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ')
})

// === 操作 ===
function goBack() {
  router.back()
}

function goToTradeDetail() {
  router.push({ name: 'transaction-list', params: { id: route.params.id } })
}

function showDividendHistory() {
  router.push({ name: 'dividend-history', params: { id: route.params.id } })
}

function showEditHolding() {
  if (!holding.value) return
  editShares.value = holding.value.shares
  editCost.value = holding.value.cost
  editMarketValue.value = holding.value.marketValue
  editAlgorithm.value = (holding.value.costAlgorithm as 'diluted' | 'diluted_only' | 'weighted_avg') || 'diluted'
  editCategory.value = holding.value.assetCategory || ''
  showEditSheet.value = true
}

const showEditSheet = ref(false)
const editShares = ref(0)
const editCost = ref(0)
const editMarketValue = ref(0)
const editAlgorithm = ref<'diluted' | 'diluted_only' | 'weighted_avg'>('diluted')
const editCategory = ref('')
const editSaving = ref(false)

async function saveEditHolding() {
  if (!holding.value) return
  editSaving.value = true
  try {
    const req: UpdateHoldingReq = {
      shares: editShares.value,
      cost: editCost.value,
      marketValue: editMarketValue.value,
      costAlgorithm: editAlgorithm.value,
      assetCategory: editCategory.value || undefined,
    }
    await updateHolding(route.params.id as string, req)
    showEditSheet.value = false
    await loadData()
  } catch (e) {
    console.error('保存失败:', e)
  } finally {
    editSaving.value = false
  }
}

const showDeleteConfirm = ref(false)

async function confirmDelete() {
  showDeleteConfirm.value = true
}

async function doDelete() {
  try {
    await deleteHolding(route.params.id as string)
    router.push('/')
  } catch (e) {
    console.error('删除失败:', e)
  } finally {
    showDeleteConfirm.value = false
  }
}

function handleRetry() {
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="min-h-screen bg-background">
    <!-- 自定义固定顶栏 -->
    <header class="fixed top-0 w-full z-50 bg-surface shadow-sm">
      <div class="flex items-center justify-between px-gutter h-14 w-full max-w-[600px] mx-auto">
        <button
          class="w-10 h-10 flex items-center justify-center -ml-2 active:opacity-80 transition-opacity"
          @click="goBack"
        >
          <span class="material-symbols-outlined text-on-surface-variant">arrow_back</span>
        </button>
        <h1 class="font-headline-md text-headline-md text-on-surface">持仓详情</h1>
        <button
          class="w-10 h-10 flex items-center justify-center active:opacity-80 transition-opacity"
          @click="router.push('/')"
        >
          <span class="material-symbols-outlined text-on-surface-variant">home</span>
        </button>
      </div>
    </header>

    <!-- 页面状态 -->
    <PageStateComp v-if="pageState !== 'ready'" :state="pageState" @retry="handleRetry" />

    <!-- 主内容 -->
    <main
      v-if="pageState === 'ready' && holding"
      class="pt-20 pb-8 px-gutter space-y-md max-w-[600px] mx-auto"
    >
      <!-- 持仓名称 + 类型注释 -->
      <div v-if="holding" class="flex items-baseline justify-between px-lg">
        <div class="flex items-baseline gap-2 flex-wrap">
          <h2 class="font-headline-md text-headline-md text-on-surface font-bold">{{ holding.name }}</h2>
          <div class="flex items-center gap-2 text-caption font-caption text-on-surface-variant">
            <span class="flex items-center gap-0.5">
              <span class="inline-block w-[6px] h-[6px] rounded-full" :class="currencyColorClass"></span>
              {{ currencyLabel }}
            </span>
            <span class="flex items-center gap-0.5">
              <span class="inline-block w-[6px] h-[6px] rounded-full" :class="typeColorClass"></span>
              {{ typeLabel }}
            </span>
          </div>
        </div>
      </div>

      <!-- Hero 数据卡片 -->
      <section class="bg-surface-container-lowest rounded-xl p-lg card-shadow relative overflow-hidden border border-outline-variant/10">
        <div class="absolute -right-4 -top-4 opacity-5 pointer-events-none">
          <span class="material-symbols-outlined text-[120px]">potted_plant</span>
        </div>
        <!-- 预测年分红 和 累计已获分红 并排 -->
        <div class="grid grid-cols-2 gap-md mb-md">
          <div>
            <p class="text-caption font-caption text-on-surface-variant flex items-center gap-1 mb-1">
              预测年分红
              <span class="material-symbols-outlined text-[14px]">info</span>
            </p>
            <div class="flex items-baseline gap-1">
              <span class="text-primary font-headline-lg text-[32px] leading-none">{{ annualDividend }}</span>
              <span class="text-primary font-label-bold text-label-bold">元</span>
            </div>
          </div>
          <div>
            <p class="text-caption font-caption text-on-surface-variant mb-1">累计已获分红</p>
            <div class="flex items-baseline gap-1">
              <span class="text-primary font-headline-lg text-[32px] leading-none">{{ totalDividendFormatted }}</span>
              <span class="text-primary font-label-bold text-label-bold">元</span>
            </div>
          </div>
        </div>
        <!-- 总市值 -->
        <div class="grid grid-cols-2 gap-md pt-md border-t border-surface-variant">
          <div>
            <p class="text-caption font-caption text-on-surface-variant">总市值</p>
            <p class="text-headline-md font-headline-md text-on-surface">
              ¥{{ marketValueFormatted }}<span class="text-[12px] ml-1">元</span>
            </p>
          </div>
          <div class="opacity-60">
            <p class="text-caption font-caption text-on-surface-variant">{{ priceDateFormatted }} 收盘价</p>
            <p class="text-body-sm font-body-sm text-on-surface-variant">
              ¥{{ latestPriceFormatted }} × {{ sharesFormatted }}份
            </p>
          </div>
        </div>
        <!-- 持仓数量 和 当前成本 -->
        <div class="grid grid-cols-2 gap-md pt-md border-t border-surface-variant">
          <div>
            <p class="text-caption font-caption text-on-surface-variant">持仓数量</p>
            <p class="text-headline-md font-headline-md text-on-surface">
              {{ sharesFormatted }}<span class="text-[12px] ml-1">份</span>
            </p>
          </div>
          <div>
            <p class="text-caption font-caption text-on-surface-variant">当前成本</p>
            <p class="text-headline-md font-headline-md text-on-surface">
              ¥{{ costPerShareFormatted }}<span class="text-[12px] ml-1">/份</span>
            </p>
          </div>
        </div>
        <div v-if="hasDividends" class="flex items-center justify-between mt-lg bg-surface p-sm rounded-lg">
          <div class="text-center flex-1">
            <p class="text-[10px] uppercase tracking-wider text-on-surface-variant mb-1">成本息率</p>
            <p class="font-label-bold text-label-bold text-on-surface">{{ holding.dividendRate }}%</p>
          </div>
          <div class="h-8 w-[1px] bg-outline-variant/30"></div>
          <div class="text-center flex-1">
            <p class="text-[10px] uppercase tracking-wider text-on-surface-variant mb-1">股价息率</p>
            <p class="font-label-bold text-label-bold text-on-surface">{{ holding.priceDividendRate }}%</p>
          </div>
        </div>
      </section>

      <!-- 分红回本进度 -->
      <section v-if="hasDividends" class="bg-surface-container-lowest rounded-xl p-md card-shadow space-y-md">
        <div class="flex justify-between items-center">
          <h3 class="font-label-bold text-label-bold text-on-surface">分红回本进度</h3>
          <span class="text-primary font-label-bold text-label-bold">{{ holding.dividendRecoveryRate }}%</span>
        </div>
        <!-- 进度条 -->
        <div class="relative w-full h-3 bg-surface-container rounded-full overflow-hidden">
          <div
            class="absolute top-0 left-0 h-full bg-primary-container transition-all duration-1000 ease-out rounded-full"
            :style="{ width: progressWidth + '%' }"
          ></div>
        </div>
        <!-- 回本网格 -->
        <div class="grid grid-cols-2 gap-x-md gap-y-sm">
          <div class="flex justify-between items-center border-b border-surface-variant pb-2">
            <span class="text-caption font-caption text-on-surface-variant">净投入</span>
            <span class="text-body-sm font-body-sm">¥{{ netInvestmentFormatted }}</span>
          </div>
          <div class="flex justify-between items-center border-b border-surface-variant pb-2">
            <span class="text-caption font-caption text-on-surface-variant">已收回</span>
            <span class="text-body-sm font-body-sm text-primary">¥{{ totalDividendFormatted }}</span>
          </div>
          <div class="flex justify-between items-center border-b border-surface-variant pb-2">
            <span class="text-caption font-caption text-on-surface-variant">剩余待回收</span>
            <span class="text-body-sm font-body-sm">¥{{ remainingRecovery }}</span>
          </div>
          <div class="flex justify-between items-center border-b border-surface-variant pb-2">
            <span class="text-caption font-caption text-on-surface-variant">预计回本</span>
            <span class="text-body-sm font-body-sm">{{ holding.estimatedRecoveryYears }} 年</span>
          </div>
        </div>
        <p class="text-[10px] text-center text-on-tertiary-container pt-1">
          *基于当前持仓市值及预测年度派息计算
        </p>
      </section>

      <!-- 分红预测图表 -->
      <section v-if="hasDividends" class="bg-surface-container-lowest rounded-xl p-md card-shadow space-y-md">
        <div class="flex justify-between items-center">
          <h3 class="font-headline-md text-headline-md text-on-surface">分红预测</h3>
          <div class="flex bg-surface-container p-1 rounded-lg gap-1">
            <button
              class="text-[10px] px-2 py-1 rounded transition-all duration-200"
              :class="forecastTab === '12m'
                ? 'bg-primary-container/20 text-primary font-label-bold'
                : 'text-on-surface-variant'"
              @click="switchForecastTab('12m')"
            >
              近12月
            </button>
            <button
              class="text-[10px] px-2 py-1 rounded transition-all duration-200"
              :class="forecastTab === '5y'
                ? 'bg-primary-container/20 text-primary font-label-bold'
                : 'text-on-surface-variant'"
              @click="switchForecastTab('5y')"
            >
              未来5年
            </button>
          </div>
        </div>
        <div class="relative h-32 w-full mt-md">
          <!-- SVG 图表 -->
          <svg class="w-full h-full" preserveAspectRatio="none" viewBox="0 0 300 100">
            <defs>
              <linearGradient id="chartGradient" x1="0%" x2="0%" y1="0%" y2="100%">
                <stop offset="0%" style="stop-color:#ff7a45;stop-opacity:0.2" />
                <stop offset="100%" style="stop-color:#ff7a45;stop-opacity:0" />
              </linearGradient>
            </defs>
            <!-- 面积填充 -->
            <path :d="areaPath" fill="url(#chartGradient)" />
            <!-- 折线 -->
            <path :d="linePath" fill="none" stroke="#ff7a45" stroke-linecap="round" stroke-width="3" />
            <!-- 数据点 -->
            <circle
              v-for="(pt, idx) in chartPoints"
              :key="idx"
              :cx="pt.x"
              :cy="pt.y"
              r="4"
              fill="#ff7a45"
            />
          </svg>
          <!-- 数据标签（最后两个点） -->
          <div
            v-if="chartPoints.length >= 2"
            class="absolute top-8"
            :style="{ left: `calc(${((chartPoints.length - 2) / Math.max(chartPoints.length - 1, 1)) * 70 + 15}%)` }"
          >
            <div class="bg-primary text-on-primary text-[10px] px-1.5 py-0.5 rounded shadow-sm whitespace-nowrap">
              ¥{{ chartPoints[chartPoints.length - 2].value.toLocaleString() }}
            </div>
          </div>
          <div
            v-if="chartPoints.length >= 1"
            class="absolute top-2 right-0"
          >
            <div class="bg-primary text-on-primary text-[10px] px-1.5 py-0.5 rounded shadow-sm whitespace-nowrap">
              ¥{{ chartPoints[chartPoints.length - 1].value.toLocaleString() }}
            </div>
          </div>
        </div>
        <div class="flex items-center gap-2 pt-2">
          <span class="material-symbols-outlined text-primary text-[18px]">trending_up</span>
          <p class="text-on-surface-variant font-body-sm text-body-sm">
            预计未来五年分红总额将增长{{ trendPercentage }}%
          </p>
        </div>
      </section>

      <!-- 操作网格 -->
      <section class="grid grid-cols-2 gap-md">
        <button
          class="flex flex-col items-center justify-center bg-surface-container-lowest p-lg rounded-xl card-shadow hover:bg-surface transition-colors active:scale-95 duration-150 group"
          @click="goToTradeDetail"
        >
          <div class="w-12 h-12 rounded-full bg-primary-container/10 flex items-center justify-center mb-2 group-hover:bg-primary-container/20 transition-colors">
            <span class="material-symbols-outlined text-primary">receipt_long</span>
          </div>
          <span class="text-label-bold font-label-bold text-on-surface">交易明细</span>
        </button>
        <button
          class="flex flex-col items-center justify-center bg-surface-container-lowest p-lg rounded-xl card-shadow hover:bg-surface transition-colors active:scale-95 duration-150 group"
          @click="showDividendHistory"
        >
          <div class="w-12 h-12 rounded-full bg-primary-container/10 flex items-center justify-center mb-2 group-hover:bg-primary-container/20 transition-colors">
            <span class="material-symbols-outlined text-primary">history_edu</span>
          </div>
          <span class="text-label-bold font-label-bold text-on-surface">分红记录</span>
        </button>
        <button
          class="flex flex-col items-center justify-center bg-surface-container-lowest p-lg rounded-xl card-shadow hover:bg-surface transition-colors active:scale-95 duration-150 group"
          @click="showEditHolding"
        >
          <div class="w-12 h-12 rounded-full bg-secondary-container flex items-center justify-center mb-2">
            <span class="material-symbols-outlined text-secondary">edit_square</span>
          </div>
          <span class="text-label-bold font-label-bold text-on-surface">编辑持仓</span>
        </button>
        <button
          class="flex flex-col items-center justify-center bg-surface-container-lowest p-lg rounded-xl card-shadow hover:bg-surface transition-colors active:scale-95 duration-150 group"
          @click="confirmDelete"
        >
          <div class="w-12 h-12 rounded-full bg-error-container/20 flex items-center justify-center mb-2">
            <span class="material-symbols-outlined text-error">delete</span>
          </div>
          <span class="text-label-bold font-label-bold text-error">删除持仓</span>
        </button>
      </section>

      <!-- 定投计划 -->
      <section class="bg-surface-container-lowest rounded-xl p-md card-shadow space-y-md">
        <div class="flex justify-between items-center">
          <h3 class="font-label-bold text-label-bold text-on-surface">
            定投计划
          </h3>
        </div>
        <template v-if="dcaPlans.length > 0">
          <div
            v-for="plan in dcaPlans"
            :key="plan.id"
            class="bg-surface-container rounded-lg p-md space-y-sm cursor-pointer hover:bg-surface-container-high transition-colors"
            @click="goToDcaPlanDetail(plan.id)"
          >
            <div class="flex justify-between items-center">
              <span class="text-label-medium font-label-medium text-on-surface">
                {{ plan.frequency === 'daily' ? '每日' : plan.frequency === 'weekly' ? '每周' : plan.frequency === 'biweekly' ? '双周' : '每月' }} ¥{{ plan.amount }}
              </span>
              <span
                class="flex items-center gap-[2px] text-[10px] px-2 py-0.5 rounded-full whitespace-nowrap"
                :class="plan.status === 'active' ? 'bg-green-500/10 text-green-400' : plan.status === 'paused' ? 'bg-yellow-500/10 text-yellow-400' : 'bg-gray-500/10 text-gray-400'"
              >
                <span class="material-symbols-outlined text-[10px]">{{ plan.status === 'active' ? 'play_arrow' : plan.status === 'paused' ? 'pause' : 'stop' }}</span>
                {{ plan.status === 'active' ? '活跃中' : plan.status === 'paused' ? '已暂停' : '已终止' }}
              </span>
            </div>
            <div class="flex justify-between text-caption font-caption text-on-surface-variant">
              <span>已执行 {{ plan.totalExecutions }} 期</span>
              <span>累计 ¥{{ plan.totalInvested.toLocaleString() }}</span>
            </div>
            <div class="flex gap-sm mt-sm">
              <button
                class="flex-1 h-8 rounded-lg bg-primary/10 text-primary text-[11px] font-label-bold transition-colors hover:bg-primary/20 active:scale-95"
                @click.stop="openExecuteSheet(plan.id)"
              >
                执行一期
              </button>
              <button
                class="flex-1 h-8 rounded-lg bg-surface-container-high text-on-surface-variant text-[11px] font-label-bold transition-colors hover:bg-surface-container-highest active:scale-95"
                @click.stop="goToDcaPlanDetail(plan.id)"
              >
                查看详情
              </button>
            </div>
          </div>
        </template>
        <button
          v-else
          class="w-full py-lg rounded-xl border-2 border-dashed border-outline-variant/30 text-on-surface-variant text-label-medium font-label-medium hover:border-primary/50 hover:text-primary transition-colors active:scale-[0.98] flex items-center justify-center gap-2"
          @click="showCreateSheet = true"
        >
          <span class="material-symbols-outlined text-lg">add</span>
          设置定投计划
        </button>
      </section>

      <!-- 底部装饰 -->
      <div class="relative w-full rounded-2xl overflow-hidden py-md">
        <div class="relative z-10 p-md flex flex-col h-full">
          <h4 class="text-on-surface font-headline-md text-headline-md">稳健增长</h4>
          <p class="text-on-surface-variant text-caption">正如照料花园，财富也需耐心培育</p>
        </div>
      </div>
    </main>

    <!-- 无底部导航（二级页面） -->

    <!-- Edit Holding Bottom Sheet -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showEditSheet" class="fixed inset-0 z-[100] bg-black/40" @click="showEditSheet = false"></div>
      </Transition>

      <Transition name="slide-up">
        <div v-if="showEditSheet"
             class="fixed bottom-0 left-0 right-0 z-[110] bg-surface rounded-t-2xl px-gutter py-lg shadow-2xl max-w-[600px] mx-auto"
             :style="{ maxHeight: '80vh', overflowY: 'auto' }">
          <!-- Drag handle -->
          <div class="w-10 h-1 bg-on-surface-variant/20 rounded-full mx-auto mb-lg"></div>

          <h3 class="text-title-large font-title-large text-on-surface mb-md">编辑持仓</h3>

          <!-- Cost Algorithm -->
           <label class="text-label-medium font-label-medium text-on-surface-variant mb-sm block">成本算法</label>
           <div class="flex gap-sm mb-lg">
             <button v-for="opt in (['diluted', 'diluted_only', 'weighted_avg'] as const)" :key="opt"
                     class="flex-1 h-10 rounded-lg text-label-large font-label-large transition-all"
                     :class="editAlgorithm === opt
                       ? 'bg-primary-container text-on-primary-container shadow-sm'
                       : 'bg-surface-container-high text-on-surface-variant hover:bg-surface-container-highest'"
                     @click="editAlgorithm = opt">
               {{ opt === 'diluted' ? '分红摊薄' : opt === 'diluted_only' ? '摊薄成本' : '加权平均' }}
             </button>
           </div>

          <!-- Shares -->
          <label class="text-label-medium font-label-medium text-on-surface-variant mb-sm block">持有份额</label>
          <input v-model.number="editShares" type="number" step="0.01" min="0"
                 class="w-full h-11 rounded-xl bg-surface-container-high px-md text-on-surface text-body-large font-body-large outline-none mb-lg transition-colors focus:ring-2 focus:ring-primary" />

          <!-- Cost -->
          <label class="text-label-medium font-label-medium text-on-surface-variant mb-sm block">总成本 (¥)</label>
          <input v-model.number="editCost" type="number" step="0.01" min="0"
                 class="w-full h-11 rounded-xl bg-surface-container-high px-md text-on-surface text-body-large font-body-large outline-none mb-lg transition-colors focus:ring-2 focus:ring-primary" />

          <!-- Market Value -->
          <label class="text-label-medium font-label-medium text-on-surface-variant mb-sm block">总市值 (¥)</label>
          <input v-model.number="editMarketValue" type="number" step="0.01" min="0"
                 class="w-full h-11 rounded-xl bg-surface-container-high px-md text-on-surface text-body-large font-body-large outline-none mb-lg transition-colors focus:ring-2 focus:ring-primary" />

          <!-- Asset Category -->
          <label class="text-label-medium font-label-medium text-on-surface-variant mb-sm block">资产分类</label>
          <div class="flex gap-sm mb-lg">
            <button @click="editCategory=''" :class="editCategory==='' ? 'bg-primary-container text-on-primary-container shadow-sm' : 'bg-surface-container-high text-on-surface-variant'" class="flex-1 h-10 rounded-lg text-label-large font-label-large transition-all hover:bg-surface-container-highest">
              不分类
            </button>
            <button @click="editCategory='us_stock'" :class="editCategory==='us_stock' ? 'bg-primary-container text-on-primary-container shadow-sm' : 'bg-surface-container-high text-on-surface-variant'" class="flex-1 h-10 rounded-lg text-label-large font-label-large transition-all hover:bg-surface-container-highest">
              📈 美股
            </button>
            <button @click="editCategory='gold'" :class="editCategory==='gold' ? 'bg-primary-container text-on-primary-container shadow-sm' : 'bg-surface-container-high text-on-surface-variant'" class="flex-1 h-10 rounded-lg text-label-large font-label-large transition-all hover:bg-surface-container-highest">
              🥇 黄金
            </button>
            <button @click="editCategory='dividend'" :class="editCategory==='dividend' ? 'bg-primary-container text-on-primary-container shadow-sm' : 'bg-surface-container-high text-on-surface-variant'" class="flex-1 h-10 rounded-lg text-label-large font-label-large transition-all hover:bg-surface-container-highest">
              📋 红利
            </button>
          </div>

          <!-- Actions -->
          <div class="flex gap-md mt-xl">
            <button class="flex-1 h-12 rounded-xl bg-surface-container-high text-on-surface-variant text-label-large font-label-large transition-colors hover:bg-surface-container-highest active:scale-[0.98]"
                    @click="showEditSheet = false">
              取消
            </button>
            <button class="flex-1 h-12 rounded-xl bg-primary text-on-primary text-label-large font-label-large transition-colors hover:brightness-95 active:scale-[0.98] flex items-center justify-center gap-sm disabled:opacity-50"
                    :disabled="editSaving"
                    @click="saveEditHolding">
              <span v-if="editSaving" class="material-symbols-outlined animate-spin text-lg">progress_activity</span>
              <span>{{ editSaving ? '保存中...' : '保存' }}</span>
            </button>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- Delete Confirm Dialog -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showDeleteConfirm" class="fixed inset-0 z-[100] bg-black/40" @click="showDeleteConfirm = false"></div>
      </Transition>

      <Transition name="scale-up">
        <div v-if="showDeleteConfirm"
             class="fixed inset-0 z-[110] flex items-center justify-center"
             @click.self="showDeleteConfirm = false">
          <div class="bg-surface rounded-2xl px-xl py-lg mx-gutter max-w-sm w-full shadow-2xl">
            <div class="flex flex-col items-center text-center">
              <div class="w-12 h-12 rounded-full bg-error/10 flex items-center justify-center mb-md">
                <span class="material-symbols-outlined text-2xl text-error">delete_forever</span>
              </div>
              <h3 class="text-title-large font-title-large text-on-surface mb-sm">确认删除</h3>
              <p class="text-body-medium font-body-medium text-on-surface-variant mb-xl">删除后数据将不可恢复，确定要继续吗？</p>
              <div class="flex gap-md w-full">
                <button class="flex-1 h-12 rounded-xl bg-surface-container-high text-on-surface-variant text-label-large font-label-large transition-colors hover:bg-surface-container-highest active:scale-[0.98]"
                        @click="showDeleteConfirm = false">
                  取消
                </button>
                <button class="flex-1 h-12 rounded-xl bg-error text-on-error text-label-large font-label-large transition-colors hover:brightness-95 active:scale-[0.98] flex items-center justify-center gap-sm"
                        @click="doDelete">
                  <span class="material-symbols-outlined">delete</span>
                  <span>删除</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- DCA Create Sheet -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showCreateSheet" class="fixed inset-0 z-[100] bg-black/40" @click="showCreateSheet = false"></div>
      </Transition>

      <Transition name="scale-up">
        <div v-if="showCreateSheet" class="fixed inset-0 z-[110] flex items-center justify-center px-gutter" @click.self="showCreateSheet = false">
          <DcaCreateSheet
            v-if="holding"
            :holding-id="holding.id"
            :holding-name="holding.name"
            :holding-code="holding.code"
            :trading-market="holding.type === '美股' ? 'us' : 'china'"
            @close="showCreateSheet = false"
            @created="handleDcaCreated"
          />
        </div>
      </Transition>
    </Teleport>

    <!-- DCA Execute Sheet -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showExecuteSheet" class="fixed inset-0 z-[100] bg-black/40" @click="showExecuteSheet = false"></div>
      </Transition>

      <Transition name="scale-up">
        <div v-if="showExecuteSheet" class="fixed inset-0 z-[110] flex items-center justify-center px-gutter" @click.self="showExecuteSheet = false">
          <DcaExecuteSheet
            :plan-id="selectedPlanId"
            :holding-name="dcaPlans.find(p => p.id === selectedPlanId)?.holdingName || ''"
            :amount="dcaPlans.find(p => p.id === selectedPlanId)?.amount || 0"
            @close="showExecuteSheet = false"
            @executed="handleDcaExecuted"
          />
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.fade-enter-active { transition: opacity 0.2s ease; }
.fade-leave-active { transition: opacity 0.15s ease; }
.fade-enter-from,
.fade-leave-to { opacity: 0; }

.slide-up-enter-active { transition: transform 0.3s cubic-bezier(0.32, 0.72, 0, 1); }
.slide-up-leave-active { transition: transform 0.2s ease; }
.slide-up-enter-from { transform: translateY(100%); }
.slide-up-leave-to { transform: translateY(100%); }

.scale-up-enter-active { transition: transform 0.2s ease, opacity 0.2s ease; }
.scale-up-leave-active { transition: transform 0.15s ease, opacity 0.15s ease; }
.scale-up-enter-from { transform: scale(0.9); opacity: 0; }
.scale-up-leave-to { transform: scale(0.9); opacity: 0; }

.slide-up-enter-active { transition: transform 0.25s ease-out; }
.slide-up-leave-active { transition: transform 0.2s ease-in; }
.slide-up-enter-from { transform: translateY(100%); }
.slide-up-leave-to { transform: translateY(100%); }
</style>
