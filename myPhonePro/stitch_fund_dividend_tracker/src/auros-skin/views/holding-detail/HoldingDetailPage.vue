<script setup lang="ts">
/**
 * 持仓详情（卡片化 v4 · 2026-11-11）
 * 章 01 POSITION 持仓：章卡（AurosStat 市值 + 盈亏 delta）+ 持有组
 * 章 02 INCOME 分红与回本：章卡（AurosRail 回本刻度 + 金额组 + 息率/复投组）
 * 章 03 FORECAST 分红预测：章卡（区间切换 + AurosTrend + 趋势判断行）
 * 章 04 ACTIONS 操作：章卡（明细 / 定投 / 管理 三组纯行）
 * 只改结构、样式与显示格式：接口、金额计算口径、校验、弹层契约均未动；
 * 数字统一走 @/auros-skin/utils/format（钱 ¥+千分位+两位、涨跌两位、占比一位、份额两位、单价四位）。
 */
import { ref, computed, onMounted, onActivated, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getHolding, getForecast, deleteHolding, updateHolding, updateDividendReinvest } from '@/api/holding'
import { refreshCryptoPrices } from '@/api/crypto'
import { listTransactions } from '@/api/transaction'
import { listDcaPlans } from '@/api/dca'
import { getAccounts, type AccountItem } from '@/api/account'
import type { HoldingItem, ForecastData, UpdateHoldingReq } from '@/api/holding'
import type { TransactionItem } from '@/api/transaction'
import type { DcaPlanVO } from '@/api/dca'
import AurosNavBar from '@/auros-skin/components/auros/AurosNavBar.vue'
import AurosState from '@/auros-skin/components/auros/AurosState.vue'
import AurosChapter from '@/auros-skin/components/auros/AurosChapter.vue'
import AurosStat from '@/auros-skin/components/auros/AurosStat.vue'
import AurosGroup from '@/auros-skin/components/auros/AurosGroup.vue'
import AurosRow from '@/auros-skin/components/auros/AurosRow.vue'
import AurosRail from '@/auros-skin/components/auros/AurosRail.vue'
import AurosTrend from '@/auros-skin/components/auros/AurosTrend.vue'
import AurosDisclosure from '@/auros-skin/components/auros/AurosDisclosure.vue'
import AurosSheet from '@/auros-skin/components/auros/AurosSheet.vue'
import AurosField from '@/auros-skin/components/auros/AurosField.vue'
import AurosButton from '@/auros-skin/components/auros/AurosButton.vue'
import ToastNotification from '@/auros-skin/components/shared/ToastNotification.vue'
import DcaPlanSheet from '@/auros-skin/components/dca/DcaPlanSheet.vue'
import DcaExecuteSheet from '@/auros-skin/components/dca/DcaExecuteSheet.vue'
import { formatMoney, formatPercent, formatQty, formatPrice } from '@/auros-skin/utils/format'

const route = useRoute()
const router = useRouter()

// 页面状态
const pageState = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')

// 标签切换
const forecastTab = ref<'12m' | '5y'>('12m')

// API 数据
const holding = ref<HoldingItem | null>(null)

// 加密币价格刷新
const refreshing = ref(false)
const toastMsg = ref<string | null>(null)
const toastType = ref<'success' | 'error' | 'info'>('success')
function showToast(msg: string, type: 'success' | 'error' | 'info' = 'success') {
  toastMsg.value = msg
  toastType.value = type
}

async function refreshPrice() {
  if (refreshing.value) return
  refreshing.value = true
  // 兜底提示：数据源超时上限 4s/源，超过 10s 说明外网异常，先告知用户别干等
  const slowHint = setTimeout(() => showToast('数据源响应较慢，正在重试…', 'info'), 10000)
  try {
    const resp = await refreshCryptoPrices()
    if (resp.stale) {
      showToast(resp.message, 'error')
    } else if (resp.updatedHoldings === 0) {
      // 接口成功但没有可刷新的持仓（例如该币种暂无价格源），必须明确告知
      showToast('该币种暂不支持自动定价', 'info')
    } else {
      showToast(`已更新 ${resp.updatedHoldings} 个持仓`, 'success')
    }
    await loadData()
  } catch (e: any) {
    showToast(e?.message || '刷新失败', 'error')
  } finally {
    clearTimeout(slowHint)
    refreshing.value = false
  }
}
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

function handleDcaSaved() {
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
    // 账户列表（编辑弹层的归属账户用；失败不阻断）
    getAccounts().then((list: AccountItem[]) => { accounts.value = list }).catch(() => {})
    holding.value = h
    forecast.value = f
    transactions.value = t
    pageState.value = 'ready'
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

// === 展示格式化（只影响显示，不参与计算）===

/** 是否有可用价格（加密币看 USDT 原价，基金看净值） */
const hasPrice = computed(() => {
  if (!holding.value) return false
  return isCrypto.value ? holding.value.quotePrice != null : holding.value.latestPrice != null
})

/** 总市值：加密币没拿到价格时显示 --（避免把「按成本」的数字当成实时市值） */
const marketValueText = computed(() => {
  if (!holding.value) return '--'
  if (isCrypto.value && holding.value.quotePrice == null) return '--'
  return formatMoney(holding.value.marketValue)
})

/** 隐含汇率 = 人民币价 ÷ USDT 价，用于让「数量 × 汇率 ≈ 市值」这行算术成立 */
const impliedRate = computed(() => {
  const lp = holding.value?.latestPrice
  const qp = holding.value?.quotePrice
  if (!lp || !qp) return null
  return Number((lp / qp).toFixed(4))
})

/** 浮动盈亏 */
const profit = computed(() => {
  const h = holding.value
  if (!h || h.marketValue == null || h.cost == null || h.cost <= 0) return null
  if (isCrypto.value && !hasPrice.value) return null
  const amount = h.marketValue - h.cost
  return { amount, rate: (amount / h.cost) * 100 }
})

/** 成本折算成 USDT，让用户能对上录入时的 81000 */
const costInUsdt = computed(() => {
  const cps = holding.value?.costPerShare
  const rate = impliedRate.value
  if (!cps || cps <= 0 || !rate) return null
  return cps / rate
})

// 是否有分红（用于控制分红相关 UI 的显隐）
const hasDividends = computed(() => {
  return holding.value != null && (holding.value.predictedDividend > 0 || holding.value.totalDividendReceived > 0)
})

/** 息率：后端返回 -1 时显示"已收回"；占比口径 → 一位小数、不带正号 */
function rateText(rate: number | null | undefined): string {
  if (rate == null) return '--'
  if (rate === -1) return '已收回'
  return formatPercent(rate, 1, false)
}
const dividendRateText = computed(() => rateText(holding.value?.dividendRate))
const priceDividendRateText = computed(() => rateText(holding.value?.priceDividendRate))

// 成本展示：<=0 显示"成本已收回"；单价口径 → 四位小数 + 行尾单位
const costPerShareText = computed(() => {
  const v = holding.value?.costPerShare
  if (v == null || v <= 0) return '成本已收回'
  return `${formatPrice(v, 4)} 元/${isCrypto.value ? '枚' : '份'}`
})

// === 最新价格 ===
// 加密币用 USDT 原价展示（quotePrice），基金/ETF 用人民币净值
const isCrypto = computed(() => holding.value?.quoteCurrency === 'USDT')

const latestPriceText = computed(() => {
  if (!holding.value || !hasPrice.value) return '--'
  if (isCrypto.value) return `${formatPrice(holding.value.quotePrice, 4)} USDT`
  return `${formatPrice(holding.value.latestPrice, 4)} 元`
})

// 价格新鲜度：加密币实时刷新，基金按日
const priceAge = computed(() => {
  const s = holding.value?.priceUpdatedAt
  if (!s) return ''
  const diff = Date.now() - new Date(s).getTime()
  if (diff < 0) return ''
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min} 分钟前`
  const hours = Math.floor(min / 60)
  if (hours < 24) return `${hours} 小时前`
  return `${Math.floor(hours / 24)} 天前`
})

// === 净值日期格式化（MM-DD）===
const priceDateFormatted = computed(() => {
  if (!holding.value?.priceDate) return '--'
  const date = new Date(holding.value.priceDate)
  return `${date.getMonth() + 1}月${date.getDate()}日`
})

const latestPriceSub = computed(() => {
  if (!hasPrice.value) return '暂无价格'
  if (isCrypto.value) return priceAge.value ? `价格 ${priceAge.value}` : '实时价格'
  return `${priceDateFormatted.value} 收盘价`
})

// === 类型映射（币种 + 资产类别）===
const typeMap: Record<string, { currency: string; typeLabel: string }> = {
  fund:       { currency: '人民币', typeLabel: '基金' },
  ETF:        { currency: '人民币', typeLabel: '基金' },
  cny_asset:  { currency: '人民币', typeLabel: '资产' },
  A股:        { currency: '人民币', typeLabel: '股票' },
  港股:       { currency: '港币',   typeLabel: '股票' },
  美股:       { currency: '美元',   typeLabel: '股票' },
  自定义:     { currency: '人民币', typeLabel: '自定义' },
  crypto:     { currency: 'USDT',   typeLabel: '加密币' },
}

const typeInfo = computed(() => typeMap[String(holding.value?.type)] ?? typeMap['自定义'])
const currencyLabel = computed(() => typeInfo.value.currency)
const typeLabel = computed(() => typeInfo.value.typeLabel)

// === 分红预测数据（走势交给 AurosTrend，只取数值序列与端点标签）===
const forecastSeries = computed<{ label: string; value: number }[]>(() => {
  return forecast.value?.series ?? []
})

const trendPercentage = computed(() => forecast.value?.trendPercentage ?? 0)

// === DCA 行文案（显示层）===
const FREQ_LABEL: Record<string, string> = {
  daily: '每日',
  weekly: '每周',
  biweekly: '双周',
  monthly: '每月',
}

function dcaFreqText(plan: DcaPlanVO): string {
  const freq = FREQ_LABEL[plan.frequency] ?? '每月'
  return isCrypto.value
    ? `${freq} ${formatMoney(plan.amount, { symbol: false })} USDT`
    : `${freq} ${formatMoney(plan.amount)}`
}

function dcaSubText(plan: DcaPlanVO): string {
  const status = plan.status === 'active' ? '活跃中' : plan.status === 'paused' ? '已暂停' : '已终止'
  const invested = isCrypto.value
    ? `${formatMoney(plan.totalInvested, { symbol: false })} USDT`
    : formatMoney(plan.totalInvested)
  return `${status} · 已执行 ${plan.totalExecutions} 期 · 累计 ${invested}`
}

// === 操作 ===
function goToTradeDetail() {
  router.push({ name: 'transaction-list', params: { id: route.params.id } })
}

function showDividendHistory() {
  router.push({ name: 'dividend-history', params: { id: route.params.id } })
}

const reinvestSaving = ref(false)

async function toggleDividendReinvest() {
  if (!holding.value || reinvestSaving.value) return
  reinvestSaving.value = true
  try {
    const newVal = !holding.value.dividendReinvest
    const updated = await updateDividendReinvest(route.params.id as string, newVal)
    if (holding.value) holding.value.dividendReinvest = updated.dividendReinvest
  } catch (e: any) {
    console.error('更新复投设置失败', e)
    showToast(e?.message || '更新复投设置失败', 'error')
  } finally {
    reinvestSaving.value = false
  }
}

function showEditHolding() {
  if (!holding.value) return
  editName.value = holding.value.name
  editShares.value = holding.value.shares
  editCostPerShare.value = holding.value.costPerShare
  editAlgorithm.value = (holding.value.costAlgorithm as 'diluted' | 'diluted_only' | 'weighted_avg') || 'diluted'
  editCategory.value = holding.value.assetCategory || ''
  editBuyFeeRate.value = holding.value.buyFeeRate ?? 0.15
  editSellFeeRate.value = holding.value.sellFeeRate ?? 0
  editAccountId.value = holding.value.accountId || ''
  showEditSheet.value = true
}

const showEditSheet = ref(false)
const editName = ref('')
const editShares = ref(0)
const editCostPerShare = ref(0)
const editAlgorithm = ref<'diluted' | 'diluted_only' | 'weighted_avg'>('diluted')
const editCategory = ref('')
const editBuyFeeRate = ref(0.15)
const editSellFeeRate = ref(0)
const editAccountId = ref('')
const accounts = ref<AccountItem[]>([])

/** 持仓归属账户名（编辑弹层与详情展示共用 accounts 数据） */
const holdingAccountName = computed(() => {
  const id = holding.value?.accountId
  if (!id) return ''
  return accounts.value.find((a: any) => a.id === id)?.name || ''
})

const editSaving = ref(false)

/** 编辑弹层的账户下拉按本位币过滤（与后端校验同一条规则） */
const editAvailableAccounts = computed(() =>
  accounts.value.filter((a: any) =>
    isCrypto.value ? a.baseCurrency === 'USDT' : a.baseCurrency !== 'USDT'
  )
)

// 打开编辑时若未归属，默认选中第一个匹配账户（资产必须归属账户）
watch([editAvailableAccounts, showEditSheet], ([list, show]) => {
  if (show && !editAccountId.value && list.length) editAccountId.value = list[0].id
}, { immediate: true })

async function saveEditHolding() {
  if (!holding.value) return
  editSaving.value = true
  try {
    const req: UpdateHoldingReq = {
      name: editName.value || undefined,
      shares: editShares.value,
      costPerShare: editCostPerShare.value,
      costAlgorithm: editAlgorithm.value,
      assetCategory: editCategory.value || undefined,
      buyFeeRate: editBuyFeeRate.value,
      sellFeeRate: editSellFeeRate.value,
      accountId: editAccountId.value,
    }
    await updateHolding(route.params.id as string, req)
    showEditSheet.value = false
    await loadData()
  } catch (e: any) {
    console.error('保存失败:', e)
    showToast(e?.message || '保存失败，请重试', 'error')
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
  } catch (e: any) {
    console.error('删除失败:', e)
    showToast(e?.message || '删除持仓失败，请重试', 'error')
  } finally {
    showDeleteConfirm.value = false
  }
}

function handleRetry() {
  loadData()
}

onMounted(loadData)

// KeepAlive 缓存重新激活时刷新数据
onActivated(() => {
  loadData()
})
</script>

<template>
  <div class="page">
    <AurosNavBar :title="holding?.name || '持仓详情'" :sub="holding?.code || ''">
      <template #right>
        <button type="button" class="nav-btn" aria-label="返回首页" @click="router.push('/')">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none">
            <path d="M4.2 10.4 12 3.8l7.8 6.6" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" />
            <path d="M6.6 9.2v10.6h10.8V9.2" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
      </template>
    </AurosNavBar>

    <main class="body">
      <AurosState v-if="pageState === 'loading'" state="loading" :rows="6" />
      <AurosState
        v-else-if="pageState === 'error'"
        state="error"
        text="持仓详情没有取到"
        action-label="重试"
        @action="handleRetry"
      />
      <AurosState
        v-else-if="pageState === 'empty'"
        state="empty"
        text="这只持仓不存在或已被删除"
        action-label="回首页"
        @action="router.push('/')"
      />

      <template v-else-if="holding">
        <!-- 01 POSITION：章卡（主数 + 持有组） -->
        <AurosChapter
          label="POSITION"
          title="持仓"
          :meta="`${typeLabel} · ${currencyLabel}`"
          :gap="false"
        >
          <AurosStat
            :value="marketValueText"
            size="lg"
            :delta="profit ? formatPercent(profit.rate, 2) : ''"
            :delta-tone="profit ? (profit.amount >= 0 ? 'up' : 'down') : 'flat'"
            :caption="marketValueText === '--'
              ? '暂无价格，暂按成本记'
              : (isCrypto && impliedRate
                ? `1 USDT ≈ ${formatPrice(impliedRate, 4)} CNY · 盈亏 ${profit ? formatMoney(profit.amount, { sign: true }) : '--'}`
                : `总市值 · 盈亏 ${profit ? formatMoney(profit.amount, { sign: true }) : '--'}`)"
          />

          <AurosGroup class="g" title="持有" :count="isCrypto ? '5 项' : '4 项'">
            <AurosRow label="持仓数量" :value="`${formatQty(holding.shares, isCrypto ? 4 : 2)} ${isCrypto ? '枚' : '份'}`" />
            <AurosRow
              :label="holding.costEstimated ? '当前成本（估算）' : '当前成本'"
              :value="costPerShareText"
              :sub="isCrypto && costInUsdt != null ? `≈ ${formatPrice(costInUsdt, 4)} USDT/枚` : ''"
            />
            <AurosRow
              label="最新价格"
              :value="latestPriceText"
              :sub="latestPriceSub"
            />
            <AurosRow
              v-if="isCrypto"
              label="刷新价格"
              :value="refreshing ? '刷新中' : '刷新'"
              clickable
              @click="refreshPrice"
            />
            <AurosRow
              label="归属账户"
              :value="holdingAccountName || '未归属'"
              :tone="holdingAccountName ? 'default' : 'muted'"
            />
          </AurosGroup>
        </AurosChapter>

        <!-- 02 INCOME：章卡（回本刻度 pink + 金额组 + 息率/复投组） -->
        <AurosChapter v-if="hasDividends" label="INCOME" title="分红与回本">
          <AurosRail
            :percent="holding.dividendRecoveryRate"
            tone="pink"
            :ticks="11"
            :label-left="`已回本 ${formatPercent(holding.dividendRecoveryRate, 1, false)}`"
            :label-right="`预计 ${formatQty(holding.estimatedRecoveryYears, 2)} 年`"
          />

          <AurosGroup class="g" title="金额">
            <AurosRow label="预测年分红" :value="formatMoney(holding.predictedDividend)" mark="lit" />
            <AurosRow label="累计已获分红" :value="formatMoney(holding.totalDividendReceived)" />
            <AurosRow label="净投入" :value="formatMoney(holding.netInvestment)" />
            <AurosRow label="剩余待回收" :value="formatMoney(holding.netInvestment - holding.totalDividendReceived)" />
          </AurosGroup>

          <AurosGroup title="息率与复投">
            <AurosRow label="成本息率" :value="dividendRateText" />
            <AurosRow label="股价息率" :value="priceDividendRateText" />
            <AurosRow
              v-if="holding.reinvestRecoveryYears && holding.reinvestRecoveryYears < 999"
              label="复投预计"
              :value="`~${formatQty(holding.reinvestRecoveryYears, 2)} 年`"
              :sub="holding.estimatedRecoveryYears - holding.reinvestRecoveryYears > 0
                ? `节约 ${formatQty(holding.estimatedRecoveryYears - holding.reinvestRecoveryYears, 2)} 年`
                : ''"
            />
            <AurosRow label="分红复投" sub="开启后，分红到账时自动买入该标的份额">
              <button
                type="button"
                class="switch"
                :class="{ 'is-on': holding.dividendReinvest }"
                :disabled="reinvestSaving"
                :aria-pressed="!!holding.dividendReinvest"
                aria-label="分红复投"
                @click="toggleDividendReinvest"
              >
                <i class="switch__knob"></i>
              </button>
            </AurosRow>
          </AurosGroup>
        </AurosChapter>

        <!-- 03 FORECAST：章卡（区间切换 + 迷你走势 + 趋势判断行） -->
        <AurosChapter v-if="hasDividends" label="FORECAST" title="分红预测">
          <div class="seg" role="tablist" aria-label="预测区间">
            <button
              type="button"
              class="seg__item"
              :class="{ 'is-active': forecastTab === '12m' }"
              :aria-pressed="forecastTab === '12m'"
              @click="switchForecastTab('12m')"
            >
              近12月
            </button>
            <button
              type="button"
              class="seg__item"
              :class="{ 'is-active': forecastTab === '5y' }"
              :aria-pressed="forecastTab === '5y'"
              @click="switchForecastTab('5y')"
            >
              未来5年
            </button>
          </div>

          <div v-if="forecastSeries.length >= 2" class="shape">
            <AurosTrend
              :points="forecastSeries.map(p => p.value)"
              :height="64"
              :label="forecastSeries[0]?.label ?? ''"
              :caption="forecastSeries[forecastSeries.length - 1]?.label ?? ''"
            />
          </div>

          <AurosGroup class="g" title="趋势判断">
            <AurosRow
              :label="`预计${forecastTab === '5y' ? '五年' : '十二月'}分红总额`"
              :value="formatPercent(trendPercentage, 2)"
              mark="dim"
            />
          </AurosGroup>
        </AurosChapter>

        <!-- 04 ACTIONS：章卡（明细 / 定投 / 管理 三组纯行） -->
        <AurosChapter label="ACTIONS" title="操作">
          <AurosGroup title="明细" :count="isCrypto ? '1 项' : '2 项'">
            <AurosRow label="交易明细" chevron clickable @click="goToTradeDetail" />
            <AurosRow v-if="!isCrypto" label="分红记录" chevron clickable @click="showDividendHistory" />
          </AurosGroup>

          <AurosGroup title="定投计划" :count="dcaPlans.length ? `${dcaPlans.length} 项` : ''">
            <template v-if="dcaPlans.length">
              <AurosRow
                v-for="plan in dcaPlans.slice(0, 5)"
                :key="plan.id"
                class="row-link"
                :label="dcaFreqText(plan)"
                :sub="dcaSubText(plan)"
                :mark="plan.status === 'active' ? 'lit' : 'dim'"
                chevron
                @click="goToDcaPlanDetail(plan.id)"
              >
                <button type="button" class="mini-btn" @click.stop="openExecuteSheet(plan.id)">执行一期</button>
              </AurosRow>
              <AurosDisclosure v-if="dcaPlans.length > 5" :count="dcaPlans.length - 5">
                <AurosRow
                  v-for="plan in dcaPlans.slice(5)"
                  :key="plan.id"
                  class="row-link"
                  :label="dcaFreqText(plan)"
                  :sub="dcaSubText(plan)"
                  :mark="plan.status === 'active' ? 'lit' : 'dim'"
                  chevron
                  @click="goToDcaPlanDetail(plan.id)"
                >
                  <button type="button" class="mini-btn" @click.stop="openExecuteSheet(plan.id)">执行一期</button>
                </AurosRow>
              </AurosDisclosure>
            </template>
            <AurosRow v-else label="设置定投计划" sub="还没有定投计划" chevron clickable @click="showCreateSheet = true" />
          </AurosGroup>

          <AurosGroup title="管理" count="2 项">
            <AurosRow label="编辑持仓" chevron clickable @click="showEditHolding" />
            <AurosRow label="删除持仓" chevron clickable tone="muted" @click="confirmDelete" />
          </AurosGroup>
        </AurosChapter>
      </template>
    </main>

    <!-- 编辑持仓 -->
    <AurosSheet v-model="showEditSheet" title="编辑持仓">
      <div class="sheet-form">
        <AurosField v-model="editName" label="名称" placeholder="持仓名称" />

        <div class="field">
          <p class="field__label">成本算法</p>
          <div class="seg seg--equal">
            <button
              v-for="opt in (['diluted', 'diluted_only', 'weighted_avg'] as const)"
              :key="opt"
              type="button"
              class="seg__item"
              :class="{ 'is-active': editAlgorithm === opt }"
              @click="editAlgorithm = opt"
            >
              {{ opt === 'diluted' ? '分红摊薄' : opt === 'diluted_only' ? '摊薄成本' : '加权平均' }}
            </button>
          </div>
        </div>

        <div class="grid2">
          <AurosField v-model="editShares" label="持有份额" type="number" inputmode="decimal" mono />
          <AurosField v-model="editCostPerShare" label="每份成本" suffix="CNY" type="number" inputmode="decimal" mono />
        </div>

        <div class="grid2">
          <AurosField v-model="editBuyFeeRate" label="买入费率" suffix="%" type="number" inputmode="decimal" mono />
          <AurosField v-model="editSellFeeRate" label="卖出费率" suffix="%" type="number" inputmode="decimal" mono />
        </div>

        <div class="field">
          <p class="field__label">资产分类</p>
          <div class="chips">
            <button
              type="button"
              class="chip"
              :class="{ 'is-active': editCategory === '' }"
              @click="editCategory = ''"
            >
              不分类
            </button>
            <button
              type="button"
              class="chip"
              :class="{ 'is-active': editCategory === 'us_stock' }"
              @click="editCategory = 'us_stock'"
            >
              <svg class="chip__icon" viewBox="0 0 24 24" width="12" height="12" fill="none" aria-hidden="true">
                <path d="M4 16.6 9.6 11l3.6 3.6L20 7.2" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" />
                <path d="M14.8 7.2H20v5.2" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
              美股
            </button>
            <button
              type="button"
              class="chip"
              :class="{ 'is-active': editCategory === 'gold' }"
              @click="editCategory = 'gold'"
            >
              <svg class="chip__icon" viewBox="0 0 24 24" width="12" height="12" fill="none" aria-hidden="true">
                <circle cx="12" cy="9.4" r="4.6" stroke="currentColor" stroke-width="1.2" />
                <path d="m8.4 13.6-1.2 7 4.8-2.4 4.8 2.4-1.2-7" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round" />
              </svg>
              黄金
            </button>
            <button
              type="button"
              class="chip"
              :class="{ 'is-active': editCategory === 'dividend' }"
              @click="editCategory = 'dividend'"
            >
              <svg class="chip__icon" viewBox="0 0 24 24" width="12" height="12" fill="none" aria-hidden="true">
                <rect x="3.6" y="7" width="16.8" height="10" rx="1.6" stroke="currentColor" stroke-width="1.2" />
                <circle cx="12" cy="12" r="2.4" stroke="currentColor" stroke-width="1.2" />
              </svg>
              红利
            </button>
            <button
              type="button"
              class="chip"
              :class="{ 'is-active': editCategory === 'crypto' }"
              @click="editCategory = 'crypto'"
            >
              <svg class="chip__icon" viewBox="0 0 24 24" width="12" height="12" fill="none" aria-hidden="true">
                <path d="m12 3.6 7 4v8.8l-7 4-7-4V7.6z" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round" />
                <circle cx="12" cy="12" r="2.5" stroke="currentColor" stroke-width="1.2" />
              </svg>
              加密
            </button>
          </div>
        </div>

        <div class="field">
          <p class="field__label">归属账户</p>
          <div class="chips">
            <button
              v-for="acc in editAvailableAccounts"
              :key="acc.id"
              type="button"
              class="chip"
              :class="{ 'is-active': editAccountId === acc.id }"
              @click="editAccountId = editAccountId === acc.id ? '' : acc.id"
            >
              {{ acc.name }}{{ acc.isDefault ? ' ·默认' : '' }}
            </button>
          </div>
        </div>

        <div class="sheet-actions">
          <AurosButton variant="ghost" block @click="showEditSheet = false">取消</AurosButton>
          <AurosButton block :disabled="editSaving" @click="saveEditHolding">
            {{ editSaving ? '保存中…' : '保存' }}
          </AurosButton>
        </div>
      </div>
    </AurosSheet>

    <!-- 删除持仓 -->
    <AurosSheet v-model="showDeleteConfirm" title="确认删除">
      <p class="sheet-text">删除后数据将不可恢复，确定要继续吗？</p>
      <div class="sheet-actions">
        <AurosButton variant="ghost" block @click="showDeleteConfirm = false">取消</AurosButton>
        <button type="button" class="btn-danger" @click="doDelete">删除</button>
      </div>
    </AurosSheet>

    <!-- 新建定投（DcaPlanSheet 自带 AurosSheet 外壳，父级只负责 v-if 挂载与接事件） -->
    <DcaPlanSheet
      v-if="showCreateSheet && holding"
      :holding-id="holding.id"
      :holding-name="holding.name"
      :holding-code="holding.code"
      :trading-market="holding.type === '美股' ? 'us' : (holding.type === 'crypto' ? 'crypto' : 'china')"
      @close="showCreateSheet = false"
      @saved="handleDcaSaved"
    />

    <!-- 执行定投（DcaExecuteSheet 自带 AurosSheet 外壳） -->
    <DcaExecuteSheet
      v-if="showExecuteSheet"
      :plan-id="selectedPlanId"
      :holding-name="dcaPlans.find(p => p.id === selectedPlanId)?.holdingName || ''"
      :amount="dcaPlans.find(p => p.id === selectedPlanId)?.amount || 0"
      :trading-market="dcaPlans.find(p => p.id === selectedPlanId)?.tradingMarket || 'fund'"
      @close="showExecuteSheet = false"
      @executed="handleDcaExecuted"
    />

    <ToastNotification :message="toastMsg" :type="toastType" @close="toastMsg = null" />
  </div>
</template>

<style scoped>
.page {
  min-height: 100svh;
  display: flex;
  flex-direction: column;
}
/* 卡片化：页面只提供 16px 边距；章卡由 AurosChapter 自带（Kelp / 16 / 18-16 内边距） */
.body {
  flex: 1;
  padding: 8px 16px calc(env(safe-area-inset-bottom, 0px) + 40px);
  display: flex;
  flex-direction: column;
}

/* 卡内间距：卡头之下 14px 起，形状之后 18px */
.g {
  margin-top: 18px;
}
.shape {
  margin-top: 14px;
}

.nav-btn {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: rgba(237, 255, 254, 0.06);
  color: var(--au-silver);
  transition: color 160ms var(--au-ease);
}
.nav-btn:active {
  color: var(--au-mist);
}

/* ---------- 区间切换（卡内控件，不是形状） ---------- */
.seg {
  display: flex;
  gap: 4px;
  padding: 3px;
  border-radius: 6px;
  background: rgba(0, 55, 52, 0.55);
  margin-bottom: 14px;
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
  color: rgba(187, 199, 198, 0.75);
  transition:
    background 200ms var(--au-ease),
    color 200ms var(--au-ease);
}
.seg__item.is-active {
  background: rgba(203, 255, 252, 0.14);
  color: var(--au-mist);
}
.seg--equal {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.seg--equal .seg__item {
  height: 34px;
}

/* ---------- 复投开关（放进 AurosRow 右侧槽） ---------- */
.switch {
  flex: none;
  position: relative;
  width: 40px;
  height: 22px;
  padding: 0;
  border: 0;
  border-radius: 16px;
  background: rgba(237, 255, 254, 0.1);
  transition: background 220ms var(--au-ease);
}
.switch__knob {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--au-silver);
  transition:
    transform 240ms var(--au-ease),
    background 240ms var(--au-ease);
}
.switch.is-on {
  background: rgba(203, 255, 252, 0.2);
}
.switch.is-on .switch__knob {
  transform: translateX(18px);
  background: var(--au-lit);
  box-shadow: 0 0 10px rgba(203, 255, 252, 0.8);
}
.switch:disabled {
  opacity: 0.5;
}

/* ---------- 行内小动作（定投「执行一期」） ---------- */
.row-link {
  cursor: pointer;
}
.row-link:active {
  opacity: 0.7;
}
.mini-btn {
  flex: none;
  height: 30px;
  padding: 0 10px;
  border: 0;
  border-radius: 6px;
  background: rgba(203, 255, 252, 0.14);
  color: var(--au-lit);
  font-family: inherit;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.04em;
  transition: transform 160ms var(--au-ease);
}
.mini-btn:active {
  transform: scale(0.96);
}

/* ---------- 弹层表单 ---------- */
.sheet-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 2px 0 4px;
}
.field__label {
  margin: 0 0 7px;
  font-size: 10px;
  letter-spacing: 0.16em;
  color: var(--au-silver);
}
.grid2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.grid2 > * {
  min-width: 0;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 34px;
  padding: 0 12px;
  border: 0;
  border-radius: 6px;
  background: rgba(237, 255, 254, 0.05);
  font-family: inherit;
  font-size: 13px;
  letter-spacing: 0.02em;
  color: rgba(187, 199, 198, 0.75);
  transition:
    background 200ms var(--au-ease),
    color 200ms var(--au-ease);
}
.chip.is-active {
  background: rgba(203, 255, 252, 0.14);
  color: var(--au-mist);
}
.chip__icon {
  flex: none;
  color: currentColor;
}
.sheet-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  letter-spacing: 0.02em;
  color: var(--au-silver);
}
.sheet-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--au-hair);
}
.btn-danger {
  height: 44px;
  border: 0;
  border-radius: 6px;
  background: rgba(253, 233, 255, 0.12);
  color: rgba(253, 233, 255, 0.92);
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.12em;
  text-indent: 0.12em;
  transition:
    transform 160ms var(--au-ease),
    background 200ms var(--au-ease);
}
.btn-danger:active {
  transform: scale(0.97);
  background: rgba(253, 233, 255, 0.18);
}

@media (prefers-reduced-motion: reduce) {
  .switch__knob,
  .mini-btn,
  .seg__item {
    transition: none;
  }
}

/* 320 窄屏：卡内宽度只有 256px，主数收到 26 位，避免横向溢出 */
@media (max-width: 340px) {
  .body :deep(.stat__num.is-lg) {
    font-size: 26px;
    letter-spacing: -0.02em;
  }
  .body :deep(.stat__delta) {
    font-size: 12px;
  }
  .body :deep(.stat__line) {
    gap: 6px;
  }
}
</style>
