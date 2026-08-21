<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createTransaction } from '@/api/transaction'
import { listHoldings, getNavByDate } from '@/api/holding'
import type { HoldingItem, NavByDateResult } from '@/api/holding'

const route = useRoute()
const router = useRouter()

// Holdings data
const holdings = ref<HoldingItem[]>([])
const selectedHoldingId = ref('')
const formState = ref<'loading' | 'ready' | 'submitting'>('loading')
const error = ref('')
const success = ref(false)

// Form state
const transactionType = ref<'buy' | 'sell' | 'bonus_share' | 'reinvest'>('buy')
const date = ref(new Date().toISOString().split('T')[0])
const quantity = ref(0)
const price = ref(0)
const fee = ref(0)

// Buy auto-calc: 金额 → 自动算手续费/份额
const buyAmount = ref(0)
const navResult = ref<NavByDateResult | null>(null)
const navState = ref<'idle' | 'loading' | 'found' | 'notfound' | 'error'>('idle')

// Labels that change based on type
const qtyLabel = ref('买入数量')
const priceLabel = ref('买入金额 (总价)')
const dateLabel = ref('交易日期')
const qtySuffix = ref('份')
const priceSuffix = ref('CNY')

// Quantity placeholder based on type
const qtyPlaceholder = computed(() => {
  return transactionType.value === 'bonus_share' ? '0' : '0.00'
})

const pricePlaceholder = computed(() => {
  return transactionType.value === 'bonus_share' ? '0.0000' : '0.0000'
})

// Estimated impact (shares + total amount)
const estimatedImpact = computed(() => {
  const qty = quantity.value || 0
  const unit = price.value || 0
  const feeVal = fee.value || 0
  const total = qty * unit + feeVal
  const sign = transactionType.value === 'buy' || transactionType.value === 'bonus_share' || transactionType.value === 'reinvest' ? '+' : '-'
  const totalStr = total.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  return `${sign}${qty.toLocaleString()} 份 / ¥${totalStr}`
})

const impactProgress = computed(() => {
  const qty = quantity.value || 0
  // Cap at 10000 for progress visualization
  const max = 10000
  return Math.min((qty / max) * 100, 100)
})

const holdingItem = computed(() =>
  holdings.value.find(h => h.id === selectedHoldingId.value)
)

// 该持仓买入费率（后端返回百分数值，如 0.15 表示 0.15%）
const currentBuyFeeRate = computed(() => holdingItem.value?.buyFeeRate ?? 0.15)

// 按选中日期拉取本地净值，并基于买入金额自动计算手续费与份额
async function fetchNav() {
  if (transactionType.value !== 'buy') {
    navState.value = 'idle'
    navResult.value = null
    return
  }
  const h = holdingItem.value
  if (!h || !date.value || !h.code) return
  navState.value = 'loading'
  try {
    const r = await getNavByDate(h.code, date.value)
    if (r && r.unitNav && r.unitNav > 0) {
      navState.value = 'found'
      navResult.value = r
      price.value = r.unitNav
      applyBuyAutoCalc()
    } else {
      navState.value = 'notfound'
      navResult.value = null
    }
  } catch (e) {
    navState.value = 'error'
    navResult.value = null
  }
}

function applyBuyAutoCalc() {
  if (transactionType.value !== 'buy') return
  const nav = navResult.value?.unitNav
  const amount = buyAmount.value || 0
  const rate = currentBuyFeeRate.value
  if (nav && nav > 0 && amount > 0) {
    const feeVal = amount * rate / 100
    const shares = (amount - feeVal) / nav
    fee.value = Number(feeVal.toFixed(2))
    quantity.value = Number(shares.toFixed(4))
    price.value = nav
  }
}

// 标的、日期或交易类型变化时重新拉净值
watch([selectedHoldingId, date, transactionType], () => {
  buyAmount.value = 0
  fetchNav()
})
// 买入金额变化时自动算手续费与份额（后续仍可手动修改）
watch(buyAmount, () => applyBuyAutoCalc())

function setType(type: 'buy' | 'sell' | 'bonus_share' | 'reinvest') {
  transactionType.value = type

  switch (type) {
    case 'buy':
      qtyLabel.value = '买入数量'
      priceLabel.value = '买入单价'
      dateLabel.value = '交易日期'
      qtySuffix.value = '份'
      priceSuffix.value = 'CNY'
      break
    case 'sell':
      qtyLabel.value = '卖出数量'
      priceLabel.value = '卖出金额 (总价)'
      dateLabel.value = '交易日期'
      qtySuffix.value = '份'
      priceSuffix.value = 'CNY'
      break
    case 'bonus_share':
      qtyLabel.value = '送股数量'
      priceLabel.value = '登记价格 (通常为0)'
      dateLabel.value = '除权日期'
      qtySuffix.value = '份'
      priceSuffix.value = 'CNY'
      break
    case 'reinvest':
      qtyLabel.value = '复投数量'
      priceLabel.value = '复投单位净值'
      dateLabel.value = '分红再投日'
      qtySuffix.value = '份'
      priceSuffix.value = 'CNY'
      break
  }

  // Haptic feedback
  try {
    if (navigator.vibrate) {
      navigator.vibrate(10)
    }
  } catch (_) {}
}

const segmentItems: { type: 'buy' | 'sell' | 'bonus_share' | 'reinvest'; label: string }[] = [
  { type: 'buy', label: '买入' },
  { type: 'sell', label: '卖出' },
  { type: 'bonus_share', label: '送股' },
  { type: 'reinvest', label: '分红复投' },
]

function goBack() {
  router.back()
}

async function loadHoldings() {
  try {
    holdings.value = await listHoldings()
    formState.value = 'ready'
    if (holdings.value.length > 0) {
      // 优先使用路由传入的 holdingId
      const presetId = route.query.holdingId as string
      if (presetId && holdings.value.some(h => h.id === presetId)) {
        selectedHoldingId.value = presetId
      } else {
        selectedHoldingId.value = holdings.value[0].id
      }
    }
  } catch (e) {
    formState.value = 'ready'
  }
}

loadHoldings()

async function handleSubmit() {
  if (!selectedHoldingId.value || quantity.value <= 0) {
    error.value = '请填写完整的交易信息'
    return
  }
  // 送股价格为0，不验证价格
  if (transactionType.value !== 'bonus_share' && price.value <= 0) {
    error.value = '请填写完整的交易信息'
    return
  }

  // 计算提交单价：卖出仍用"总价 → 单价"；买入已直接使用当日净值单价，不再折算
  let perSharePrice = price.value
  if (transactionType.value === 'sell' && quantity.value > 0) {
    perSharePrice = price.value / quantity.value
  }

  formState.value = 'submitting'
  error.value = ''
  try {
    await createTransaction({
      holdingId: selectedHoldingId.value,
      type: transactionType.value,
      date: date.value,
      quantity: quantity.value,
      price: perSharePrice,
      fee: fee.value || 0,
    })
    success.value = true
    setTimeout(() => {
      router.replace({ name: 'transaction-list', params: { id: selectedHoldingId.value } })
    }, 1500)
  } catch (e: any) {
    error.value = e.message || '提交失败'
  } finally {
    formState.value = 'ready'
  }
}
</script>

<template>
  <div class="min-h-screen bg-page-bg">
    <!-- Header — 统一 -->
    <header class="flex items-center justify-between px-gutter h-14 sticky top-0 z-50 bg-card-bg border-b border-border-light/40">
      <button @click="goBack" class="w-10 h-10 flex items-center justify-center -ml-2 active:opacity-80">
        <span class="material-symbols-outlined text-text-secondary">arrow_back</span>
      </button>
      <div class="flex-1 text-center">
        <h1 class="font-body text-md font-medium text-text-primary">添加交易</h1>
      </div>
      <button @click="router.push('/')" class="w-10 h-10 flex items-center justify-center active:opacity-80 transition-opacity">
        <span class="material-symbols-outlined text-text-secondary">home</span>
      </button>
    </header>

    <!-- Main Content -->
    <main class="px-gutter pb-32 space-y-md pt-md">
      <!-- Transaction Type Segmented Control -->
      <section>
        <div class="bg-card-alt rounded-xl p-[3px] flex w-full">
          <button
            v-for="item in segmentItems"
            :key="item.type"
            class="flex-1 py-2 rounded-lg font-body font-medium text-sm transition-all duration-200"
            type="button"
            :class="transactionType === item.type
              ? 'bg-card-bg text-text-primary shadow-sm'
              : 'text-text-secondary hover:bg-card-alt/50'"
            @click="setType(item.type)"
          >
            {{ item.label }}
          </button>
        </div>
      </section>

      <!-- Form Card -->
      <section class="bg-card-bg rounded-xl card-shadow border border-border-light/40 p-lg space-y-md">
        <!-- Holding Selector -->
        <div class="group">
          <label class="block font-body text-xs text-text-tertiary mb-1 ml-1">标的</label>
          <div class="relative">
            <select v-model="selectedHoldingId"
                    class="w-full bg-card-alt border-none rounded-lg px-md py-3 font-body text-sm text-text-primary focus:ring-2 focus:ring-brand outline-none transition-all appearance-none pr-md">
              <option v-for="h in holdings" :key="h.id" :value="h.id">{{ h.name }}（{{ h.code }}）</option>
            </select>
            <span class="material-symbols-outlined absolute right-md top-1/2 -translate-y-1/2 pointer-events-none text-text-tertiary">expand_more</span>
          </div>
        </div>

        <!-- Transaction Date -->
        <div class="group">
          <label class="block font-body text-xs text-text-tertiary mb-1 ml-1">{{ dateLabel }}</label>
          <div class="relative flex items-center">
            <input
              v-model="date"
              class="w-full bg-card-alt border-none rounded-lg px-md py-3 font-body text-sm text-text-primary focus:ring-2 focus:ring-brand outline-none transition-all appearance-none"
              type="date"
              placeholder="选择日期"
            />
            <span class="material-symbols-outlined absolute right-md pointer-events-none text-text-tertiary">calendar_month</span>
          </div>
        </div>

        <!-- Buy-only: 当日净值状态 -->
        <div v-if="transactionType === 'buy'"
             class="flex items-center gap-2 px-md py-2 rounded-lg text-xs font-body"
             :class="navState === 'found' ? 'bg-brand-light/60 text-brand' : navState === 'loading' ? 'bg-card-alt text-text-secondary' : navState === 'notfound' || navState === 'error' ? 'bg-error/10 text-error' : 'bg-card-alt text-text-tertiary'">
          <span class="material-symbols-outlined text-[14px]">
            {{ navState === 'loading' ? 'progress_activity' : navState === 'found' ? 'check_circle' : navState === 'notfound' ? 'search_off' : navState === 'error' ? 'error' : 'query_stats' }}
          </span>
          <span v-if="navState === 'loading'">正在读取 {{ navState === 'loading' ? '当日净值' : '' }}...</span>
          <span v-else-if="navState === 'notfound'">该日期暂无净值记录，请手动填写单价与数量</span>
          <span v-else-if="navState === 'error'">净值读取失败，请手动填写</span>
          <span v-else-if="navState === 'found' && navResult">
            当日净值 ¥{{ navResult.unitNav.toFixed(4) }}（{{ navResult.navDate }}）
            <span class="ml-1 opacity-70">费率 {{ currentBuyFeeRate }}%</span>
          </span>
          <span v-else>选择日期后自动读取本地净值</span>
        </div>

        <!-- Buy-only: 买入金额（自动算手续费/份额） -->
        <div v-if="transactionType === 'buy'" class="group">
          <label class="block font-body text-xs text-text-tertiary mb-1 ml-1">买入金额</label>
          <div class="relative">
            <input
              v-model.number="buyAmount"
              class="w-full bg-card-alt border-none rounded-lg px-md py-3 font-body text-sm text-text-primary focus:ring-2 focus:ring-brand outline-none transition-all"
              placeholder="输入金额后自动计算手续费与份额"
              type="number"
              step="any"
              min="0"
            />
            <span class="absolute right-md top-1/2 -translate-y-1/2 font-body text-xs text-text-tertiary">CNY</span>
          </div>
        </div>

        <!-- Two Columns: Quantity + Price -->
        <div class="grid grid-cols-2 gap-md">
          <div class="group">
            <label class="block font-body text-xs text-text-tertiary mb-1 ml-1">{{ qtyLabel }}</label>
            <div class="relative">
              <input
                v-model.number="quantity"
                class="w-full bg-card-alt border-none rounded-lg px-md py-3 font-body text-sm text-text-primary focus:ring-2 focus:ring-brand outline-none transition-all"
                :placeholder="qtyPlaceholder"
                type="number"
                step="any"
                min="0"
              />
              <span class="absolute right-md top-1/2 -translate-y-1/2 font-body text-xs text-text-tertiary">{{ qtySuffix }}</span>
            </div>
          </div>
          <div class="group">
            <label class="block font-body text-xs text-text-tertiary mb-1 ml-1">{{ priceLabel }}</label>
            <div class="relative">
              <input
                v-model.number="price"
                class="w-full bg-card-alt border-none rounded-lg px-md py-3 font-body text-sm text-text-primary focus:ring-2 focus:ring-brand outline-none transition-all"
                :placeholder="pricePlaceholder"
                type="number"
                step="any"
                min="0"
              />
              <span class="absolute right-md top-1/2 -translate-y-1/2 font-body text-xs text-text-tertiary">{{ priceSuffix }}</span>
            </div>
          </div>
        </div>

        <!-- Transaction Fee (Optional) -->
        <div class="group">
          <label class="block font-body text-xs text-text-tertiary mb-1 ml-1">交易费用 (可选)</label>
          <div class="relative">
            <input
              v-model.number="fee"
              class="w-full bg-card-alt border-none rounded-lg px-md py-3 font-body text-sm text-text-primary focus:ring-2 focus:ring-brand outline-none transition-all"
              placeholder="0.00"
              type="number"
              step="any"
              min="0"
            />
            <span class="absolute right-md top-1/2 -translate-y-1/2 font-body text-xs text-text-tertiary">CNY</span>
          </div>
        </div>

        <!-- Estimated Impact -->
        <div class="pt-sm">
          <div class="flex justify-between items-center mb-2">
            <span class="font-body text-xs text-text-tertiary">预估持仓影响</span>
            <span class="font-body text-sm font-medium text-brand">{{ estimatedImpact }}</span>
          </div>
          <div class="h-2 w-full bg-progress-bg rounded-full overflow-hidden">
            <div
              class="h-full bg-brand-light rounded-full transition-all duration-500"
              :style="{ width: impactProgress + '%' }"
            ></div>
          </div>
        </div>
      </section>

      <!-- 装饰插图 -->
      <div class="rounded-xl overflow-hidden h-32 relative bg-card-alt">
        <div class="absolute inset-0 bg-gradient-to-br from-brand-light/20 to-card-alt"></div>
        <div class="absolute inset-0 flex items-center justify-center">
          <p class="text-text-tertiary/60 font-body text-xs font-medium text-center italic">&ldquo;每一笔投入，都是种下一颗未来的树&rdquo;</p>
        </div>
      </div>
    </main>

    <!-- Fixed Bottom Action Bar -->
    <div class="fixed bottom-0 w-full bg-card-bg px-gutter pb-8 pt-4 shadow-overlay z-50">
      <button
        id="submit-btn"
        class="w-full h-[52px] bg-brand hover:bg-brand/90 active:scale-95 transition-all text-white font-body font-medium text-md rounded-xl flex items-center justify-center gap-2"
        @click="handleSubmit"
      >
        <span>确认添加</span>
        <span class="material-symbols-outlined">check_circle</span>
      </button>
    </div>
  </div>
</template>
