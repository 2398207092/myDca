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
  return holding.value.shares.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
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
  <div class="min-h-screen bg-page-bg">
    <!-- 自定义固定顶栏 -->
    <header class="fixed top-0 w-full z-50 bg-card-bg border-b border-border-light/40">
      <div class="flex items-center justify-between px-gutter h-14 w-full max-w-[600px] mx-auto">
        <button
          class="w-10 h-10 flex items-center justify-center -ml-2 active:opacity-80 transition-opacity"
          @click="goBack"
        >
          <span class="material-symbols-outlined text-text-secondary">arrow_back</span>
        </button>
        <h1 class="font-body text-md font-medium text-text-primary">持仓详情</h1>
        <button
          class="w-10 h-10 flex items-center justify-center active:opacity-80 transition-opacity"
          @click="router.push('/')"
        >
          <span class="material-symbols-outlined text-text-secondary">home</span>
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
      <div v-if="holding" class="flex items-baseline justify-between px-sm">
        <div class="flex items-baseline gap-2 flex-wrap">
          <h2 class="font-body text-lg font-medium text-text-primary">{{ holding.name }}</h2>
          <div class="flex items-center gap-2 font-body text-xs text-text-tertiary">
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

      <!-- Hero 主卡：年分红 + 累计分红 + 息率 -->
      <section class="bg-card-bg rounded-xl p-lg card-shadow border border-border-light/40 relative overflow-hidden">
        <div class="absolute -right-8 -top-8 w-40 h-40 rounded-full bg-brand-light/40 pointer-events-none"></div>
        <div class="relative z-10">
          <!-- 预测年分红 和 累计已获分红 并排 -->
          <div class="grid grid-cols-2 gap-md">
            <div>
              <p class="font-body text-xs text-text-tertiary mb-1">预测年分红</p>
              <div class="flex items-baseline gap-1">
                <span class="font-display text-3xl font-semibold text-text-primary leading-none">{{ annualDividend }}</span>
                <span class="font-body text-sm text-text-tertiary">元</span>
              </div>
            </div>
            <div>
              <p class="font-body text-xs text-text-tertiary mb-1">累计已获分红</p>
              <div class="flex items-baseline gap-1">
                <span class="font-display text-xl font-semibold text-text-primary leading-none mt-[5px]">{{ totalDividendFormatted }}</span>
                <span class="font-body text-sm text-text-tertiary mt-[5px]">元</span>
              </div>
            </div>
          </div>
          <!-- 息率双列：内联在数字下方 -->
          <div v-if="hasDividends" class="flex items-center gap-3 mt-md pt-md border-t border-border-light/40">
            <div class="flex items-center gap-1">
              <span class="font-body text-[10px] text-text-tertiary">成本息率</span>
              <span class="font-display text-xs font-semibold text-brand">{{ holding.dividendRate }}%</span>
            </div>
            <span class="w-px h-3 bg-border-light"></span>
            <div class="flex items-center gap-1">
              <span class="font-body text-[10px] text-text-tertiary">股价息率</span>
              <span class="font-display text-xs font-semibold text-brand">{{ holding.priceDividendRate }}%</span>
            </div>
          </div>
        </div>
      </section>

      <!-- Hero 副卡：市值 + 成本 + 份额等辅助数据 -->
      <section class="bg-card-bg rounded-xl p-md card-shadow border border-border-light/40">
        <div class="grid grid-cols-[1fr_auto_1fr] gap-0">
          <div class="pr-md">
            <p class="font-body text-xs text-text-tertiary">总市值</p>
            <p class="font-display text-md font-semibold text-text-primary mt-0.5 whitespace-nowrap">
              ¥{{ marketValueFormatted }}<span class="font-body text-xs text-text-tertiary ml-0.5">元</span>
            </p>
          </div>
          <div class="w-px bg-border-light/60"></div>
          <div class="pl-md min-w-0">
            <p class="font-body text-xs text-text-tertiary">{{ priceDateFormatted }} 收盘价</p>
            <p class="font-display text-sm font-semibold text-text-primary truncate mt-[6px]">
              ¥{{ latestPriceFormatted }} × {{ sharesFormatted }}份
            </p>
          </div>
        </div>
        <div class="my-md h-px bg-border-light/60"></div>
        <div class="grid grid-cols-[1fr_auto_1fr] gap-0">
          <div class="pr-md">
            <p class="font-body text-xs text-text-tertiary">持仓数量</p>
            <p class="font-display text-md font-semibold text-text-primary mt-0.5">
              {{ sharesFormatted }}<span class="font-body text-xs text-text-tertiary ml-0.5">份</span>
            </p>
          </div>
          <div class="w-px bg-border-light/60"></div>
          <div class="pl-md">
            <p class="font-body text-xs text-text-tertiary">当前成本</p>
            <p class="font-display text-md font-semibold text-text-primary mt-0.5">
              ¥{{ costPerShareFormatted }}<span class="font-body text-xs text-text-tertiary ml-0.5">/份</span>
            </p>
          </div>
        </div>
      </section>

      <!-- 分红回本进度 + 分红预测合并 -->
      <section v-if="hasDividends" class="bg-card-bg rounded-xl p-md card-shadow border border-border-light/40 space-y-md">
        <div class="flex justify-between items-center">
          <h3 class="font-body text-sm font-medium text-text-primary">分红回本进度</h3>
          <span class="font-display text-md font-semibold text-brand">{{ holding.dividendRecoveryRate }}%</span>
        </div>
        <!-- 进度条 -->
        <div class="relative w-full h-3 bg-progress-bg rounded-full overflow-hidden">
          <div
            class="absolute top-0 left-0 h-full bg-brand transition-all duration-1000 ease-out rounded-full"
            :style="{ width: progressWidth + '%' }"
          ></div>
        </div>
        <!-- 回本网格 -->
        <div class="grid grid-cols-2 gap-x-md gap-y-sm">
          <div class="flex justify-between items-center border-b border-border-light/40 pb-2">
            <span class="font-body text-xs text-text-tertiary">净投入</span>
            <span class="font-body text-sm text-text-primary">¥{{ netInvestmentFormatted }}</span>
          </div>
          <div class="flex justify-between items-center border-b border-border-light/40 pb-2">
            <span class="font-body text-xs text-text-tertiary">已收回</span>
            <span class="font-body text-sm font-medium text-brand">¥{{ totalDividendFormatted }}</span>
          </div>
          <div class="flex justify-between items-center border-b border-border-light/40 pb-2">
            <span class="font-body text-xs text-text-tertiary">剩余待回收</span>
            <span class="font-body text-sm text-text-primary">¥{{ remainingRecovery }}</span>
          </div>
          <div class="flex justify-between items-center border-b border-border-light/40 pb-2">
            <span class="font-body text-xs text-text-tertiary">预计回本</span>
            <span class="font-body text-sm text-text-primary">{{ holding.estimatedRecoveryYears }} 年</span>
          </div>
        </div>
        <p class="font-body text-[10px] text-center text-text-tertiary">
          *基于当前持仓市值及预测年度派息计算
        </p>

        <!-- 内嵌分红预测 -->
        <div class="pt-md border-t border-border-light/40">
          <div class="flex justify-between items-center mb-sm">
            <h4 class="font-body text-xs font-medium text-text-primary">分红预测</h4>
            <div class="flex bg-card-alt p-0.5 rounded-lg gap-0.5">
              <button
                class="text-[9px] px-2 py-1 rounded transition-all duration-200 font-body"
                :class="forecastTab === '12m'
                  ? 'bg-brand text-white'
                  : 'text-text-tertiary hover:text-text-primary'"
                @click="switchForecastTab('12m')"
              >
                近12月
              </button>
              <button
                class="text-[9px] px-2 py-1 rounded transition-all duration-200 font-body"
                :class="forecastTab === '5y'
                  ? 'bg-brand text-white'
                  : 'text-text-tertiary hover:text-text-primary'"
                @click="switchForecastTab('5y')"
              >
                未来5年
              </button>
            </div>
          </div>
          <!-- SVG 小图表 -->
          <div class="relative h-20 w-full">
            <svg class="w-full h-full" preserveAspectRatio="none" viewBox="0 0 300 60">
              <defs>
                <linearGradient id="chartGradient" x1="0%" x2="0%" y1="0%" y2="100%">
                  <stop offset="0%" style="stop-color:#1A6B56;stop-opacity:0.12" />
                  <stop offset="100%" style="stop-color:#1A6B56;stop-opacity:0" />
                </linearGradient>
              </defs>
              <path :d="areaPath" fill="url(#chartGradient)" />
              <path :d="linePath" fill="none" stroke="#1A6B56" stroke-linecap="round" stroke-width="2" />
              <circle
                v-for="(pt, idx) in chartPoints"
                :key="idx"
                :cx="pt.x"
                :cy="pt.y"
                r="2.5"
                fill="#1A6B56"
              />
            </svg>
            <!-- 最后一点标签 -->
            <div v-if="chartPoints.length >= 1" class="absolute -top-1 right-0">
              <span class="bg-brand text-white text-[9px] px-1.5 py-0.5 rounded shadow-card whitespace-nowrap">
                ¥{{ chartPoints[chartPoints.length - 1].value.toLocaleString() }}
              </span>
            </div>
          </div>
          <div class="flex items-center gap-1 mt-1">
            <span class="text-brand text-xs">📈</span>
            <span class="font-body text-[10px] text-text-tertiary">预计未来五年分红总额将增长{{ trendPercentage }}%</span>
          </div>
        </div>
      </section>

      <!-- 定投计划 -->
      <section class="bg-card-bg rounded-xl p-md card-shadow border border-border-light/40 space-y-md">
        <div class="flex justify-between items-center">
          <h3 class="font-body text-sm font-medium text-text-primary">
            定投计划
          </h3>
        </div>
        <template v-if="dcaPlans.length > 0">
          <div
            v-for="plan in dcaPlans"
            :key="plan.id"
            class="bg-card-alt/60 rounded-lg p-md space-y-sm cursor-pointer hover:bg-card-alt transition-colors"
            @click="goToDcaPlanDetail(plan.id)"
          >
            <div class="flex justify-between items-center">
              <span class="font-body text-sm font-medium text-text-primary">
                {{ plan.frequency === 'daily' ? '每日' : plan.frequency === 'weekly' ? '每周' : plan.frequency === 'biweekly' ? '双周' : '每月' }} ¥{{ plan.amount }}
              </span>
              <span
                class="flex items-center gap-[2px] text-[10px] px-2 py-0.5 rounded-full whitespace-nowrap font-body"
                :class="plan.status === 'active' ? 'bg-brand-light text-brand' : plan.status === 'paused' ? 'bg-amber-50 text-amber-700' : 'bg-card-alt text-text-tertiary'"
              >
                <span class="material-symbols-outlined text-[10px]">{{ plan.status === 'active' ? 'play_arrow' : plan.status === 'paused' ? 'pause' : 'stop' }}</span>
                {{ plan.status === 'active' ? '活跃中' : plan.status === 'paused' ? '已暂停' : '已终止' }}
              </span>
            </div>
            <div class="flex justify-between font-body text-xs text-text-tertiary">
              <span>已执行 {{ plan.totalExecutions }} 期</span>
              <span>累计 ¥{{ plan.totalInvested.toLocaleString() }}</span>
            </div>
            <div class="flex gap-sm mt-sm">
              <button
                class="flex-1 h-8 rounded-lg bg-brand text-white text-[11px] font-medium transition-colors active:scale-95"
                @click.stop="openExecuteSheet(plan.id)"
              >
                执行一期
              </button>
              <button
                class="flex-1 h-8 rounded-lg bg-card-alt text-text-secondary text-[11px] font-medium transition-colors active:scale-95"
                @click.stop="goToDcaPlanDetail(plan.id)"
              >
                查看详情
              </button>
            </div>
          </div>
        </template>
        <button
          v-else
          class="w-full py-lg rounded-xl border-2 border-dashed border-border-light text-text-tertiary font-body text-sm hover:border-brand/50 hover:text-brand transition-colors active:scale-[0.98] flex items-center justify-center gap-2"
          @click="showCreateSheet = true"
        >
          <span class="material-symbols-outlined text-lg">add</span>
          设置定投计划
        </button>
      </section>

      <!-- 操作按钮：横排标签样式 -->
      <div class="grid grid-cols-4 gap-sm">
        <button
          class="flex flex-col items-center justify-center gap-1 bg-card-bg rounded-xl py-md card-shadow border border-border-light/40 active:scale-[0.95] transition-all duration-150"
          @click="goToTradeDetail"
        >
          <span class="material-symbols-outlined text-brand text-lg">receipt_long</span>
          <span class="font-body text-[10px] font-medium text-text-primary">交易明细</span>
        </button>
        <button
          class="flex flex-col items-center justify-center gap-1 bg-card-bg rounded-xl py-md card-shadow border border-border-light/40 active:scale-[0.95] transition-all duration-150"
          @click="showDividendHistory"
        >
          <span class="material-symbols-outlined text-brand text-lg">history_edu</span>
          <span class="font-body text-[10px] font-medium text-text-primary">分红记录</span>
        </button>
        <button
          class="flex flex-col items-center justify-center gap-1 bg-card-bg rounded-xl py-md card-shadow border border-border-light/40 active:scale-[0.95] transition-all duration-150"
          @click="showEditHolding"
        >
          <span class="material-symbols-outlined text-brand text-lg">edit_square</span>
          <span class="font-body text-[10px] font-medium text-text-primary">编辑持仓</span>
        </button>
        <button
          class="flex flex-col items-center justify-center gap-1 bg-card-bg rounded-xl py-md card-shadow border border-border-light/40 active:scale-[0.95] transition-all duration-150"
          @click="confirmDelete"
        >
          <span class="material-symbols-outlined text-error text-lg">delete</span>
          <span class="font-body text-[10px] font-medium text-error">删除持仓</span>
        </button>
      </div>

      <!-- 底部 slogan 分隔线 -->
      <div class="flex items-center gap-2 pt-sm pb-lg">
        <span class="flex-1 h-px bg-border-light/60"></span>
        <span class="font-body text-xs text-text-tertiary">🌱 正如照料花园，财富也需耐心培育</span>
        <span class="flex-1 h-px bg-border-light/60"></span>
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
             class="fixed bottom-0 left-0 right-0 z-[110] bg-card-bg rounded-t-2xl px-gutter py-lg shadow-elevated max-w-[600px] mx-auto"
             :style="{ maxHeight: '80vh', overflowY: 'auto' }">
          <!-- Drag handle -->
          <div class="w-10 h-1 bg-border-light rounded-full mx-auto mb-lg"></div>

          <h3 class="font-body text-md font-medium text-text-primary mb-md">编辑持仓</h3>

          <!-- Cost Algorithm -->
           <label class="font-body text-xs text-text-tertiary mb-sm block">成本算法</label>
           <div class="flex gap-sm mb-lg">
             <button v-for="opt in (['diluted', 'diluted_only', 'weighted_avg'] as const)" :key="opt"
                     class="flex-1 h-10 rounded-lg font-body text-sm transition-all"
                     :class="editAlgorithm === opt
                       ? 'bg-brand text-white shadow-card'
                       : 'bg-card-alt text-text-secondary hover:bg-card-alt/80'"
                     @click="editAlgorithm = opt">
               {{ opt === 'diluted' ? '分红摊薄' : opt === 'diluted_only' ? '摊薄成本' : '加权平均' }}
             </button>
           </div>

          <!-- Shares -->
          <label class="font-body text-xs text-text-tertiary mb-sm block">持有份额</label>
          <input v-model.number="editShares" type="number" step="0.01" min="0"
                 class="w-full h-11 rounded-xl bg-card-alt px-md text-text-primary font-body text-sm outline-none mb-lg transition-colors focus:ring-2 focus:ring-brand" />

          <!-- Cost -->
          <label class="font-body text-xs text-text-tertiary mb-sm block">总成本 (¥)</label>
          <input v-model.number="editCost" type="number" step="0.01" min="0"
                 class="w-full h-11 rounded-xl bg-card-alt px-md text-text-primary font-body text-sm outline-none mb-lg transition-colors focus:ring-2 focus:ring-brand" />

          <!-- Market Value -->
          <label class="font-body text-xs text-text-tertiary mb-sm block">总市值 (¥)</label>
          <input v-model.number="editMarketValue" type="number" step="0.01" min="0"
                 class="w-full h-11 rounded-xl bg-card-alt px-md text-text-primary font-body text-sm outline-none mb-lg transition-colors focus:ring-2 focus:ring-brand" />

          <!-- Asset Category -->
          <label class="font-body text-xs text-text-tertiary mb-sm block">资产分类</label>
          <div class="flex gap-sm mb-lg">
            <button @click="editCategory=''" :class="editCategory==='' ? 'bg-brand text-white shadow-card' : 'bg-card-alt text-text-secondary hover:bg-card-alt/80'" class="flex-1 h-10 rounded-lg font-body text-sm transition-all">
              不分类
            </button>
            <button @click="editCategory='us_stock'" :class="editCategory==='us_stock' ? 'bg-brand text-white shadow-card' : 'bg-card-alt text-text-secondary hover:bg-card-alt/80'" class="flex-1 h-10 rounded-lg font-body text-sm transition-all">
              📈 美股
            </button>
            <button @click="editCategory='gold'" :class="editCategory==='gold' ? 'bg-brand text-white shadow-card' : 'bg-card-alt text-text-secondary hover:bg-card-alt/80'" class="flex-1 h-10 rounded-lg font-body text-sm transition-all">
              🥇 黄金
            </button>
            <button @click="editCategory='dividend'" :class="editCategory==='dividend' ? 'bg-brand text-white shadow-card' : 'bg-card-alt text-text-secondary hover:bg-card-alt/80'" class="flex-1 h-10 rounded-lg font-body text-sm transition-all">
              📋 红利
            </button>
          </div>

          <!-- Actions -->
          <div class="flex gap-md mt-xl">
            <button class="flex-1 h-12 rounded-xl bg-card-alt text-text-secondary font-body text-sm font-medium transition-colors active:scale-[0.98]"
                    @click="showEditSheet = false">
              取消
            </button>
            <button class="flex-1 h-12 rounded-xl bg-brand text-white font-body text-sm font-medium transition-colors active:scale-[0.98] flex items-center justify-center gap-sm disabled:opacity-50"
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
          <div class="bg-card-bg rounded-2xl px-xl py-lg mx-gutter max-w-sm w-full shadow-elevated">
            <div class="flex flex-col items-center text-center">
              <div class="w-12 h-12 rounded-full bg-error/10 flex items-center justify-center mb-md">
                <span class="material-symbols-outlined text-2xl text-error">delete_forever</span>
              </div>
              <h3 class="font-body text-md font-medium text-text-primary mb-sm">确认删除</h3>
              <p class="font-body text-sm text-text-tertiary mb-xl">删除后数据将不可恢复，确定要继续吗？</p>
              <div class="flex gap-md w-full">
                <button class="flex-1 h-12 rounded-xl bg-card-alt text-text-secondary font-body text-sm font-medium transition-colors active:scale-[0.98]"
                        @click="showDeleteConfirm = false">
                  取消
                </button>
                <button class="flex-1 h-12 rounded-xl bg-error text-white font-body text-sm font-medium transition-colors active:scale-[0.98] flex items-center justify-center gap-sm"
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
