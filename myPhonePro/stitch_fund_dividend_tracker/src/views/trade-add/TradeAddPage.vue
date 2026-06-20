<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { createTransaction } from '@/api/transaction'
import { listHoldings } from '@/api/holding'
import type { HoldingItem } from '@/api/holding'

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

// Labels that change based on type
const qtyLabel = ref('买入数量')
const priceLabel = ref('买入价格')
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
  const total = price.value || 0
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

function setType(type: 'buy' | 'sell' | 'bonus_share' | 'reinvest') {
  transactionType.value = type

  switch (type) {
    case 'buy':
      qtyLabel.value = '买入数量'
      priceLabel.value = '买入金额 (总价)'
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
      selectedHoldingId.value = holdings.value[0].id
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

  // 计算每份单价 = 总金额 / 数量（仅买入/卖出使用总价模式）
  let perSharePrice = price.value
  if ((transactionType.value === 'buy' || transactionType.value === 'sell') && quantity.value > 0) {
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
  <div class="min-h-screen bg-background">
    <!-- Custom Fixed Header -->
    <header class="fixed top-0 w-full z-50 bg-surface shadow-sm">
      <div class="flex items-center justify-between px-gutter h-14 max-w-[600px] mx-auto">
        <div class="flex items-center gap-2">
          <button
            class="w-10 h-10 flex items-center justify-center -ml-2 active:opacity-80 transition-opacity"
            @click="goBack"
          >
            <span class="material-symbols-outlined text-on-surface-variant">arrow_back</span>
          </button>
          <h1 class="font-headline-md text-headline-md text-on-surface font-bold">添加交易</h1>
        </div>
        <button @click="router.push('/')" class="w-10 h-10 flex items-center justify-center active:opacity-80 transition-opacity">
          <span class="material-symbols-outlined text-on-surface-variant">home</span>
        </button>
      </div>
    </header>

    <!-- Main Content -->
    <main class="pt-14 pb-32 px-gutter max-w-[600px] mx-auto">
      <!-- Transaction Type Segmented Control -->
      <section class="mb-xl mt-lg">
        <div class="bg-surface-container-high rounded-xl p-1 flex w-full">
          <button
            v-for="item in segmentItems"
            :key="item.type"
            class="type-btn flex-1 py-2 rounded-lg font-label-bold text-label-bold transition-all duration-200"
            type="button"
            :class="transactionType === item.type
              ? 'bg-surface-container-lowest text-primary shadow-sm'
              : 'text-on-surface-variant hover:bg-surface-container/50'"
            @click="setType(item.type)"
          >
            {{ item.label }}
          </button>
        </div>
      </section>

      <!-- Form Card -->
      <section class="bg-surface-container-lowest rounded-xl card-shadow p-md space-y-md">
        <!-- Transaction Date -->
        <div class="group">
          <label class="block font-caption text-caption text-on-surface-variant mb-1 ml-1">{{ dateLabel }}</label>
          <div class="relative flex items-center">
            <input
              v-model="date"
              class="w-full bg-surface-container-low border-none rounded-lg px-md py-3 font-body-md text-on-surface focus:ring-2 focus:ring-primary-container outline-none transition-all appearance-none"
              type="date"
              placeholder="选择日期"
            />
            <span class="material-symbols-outlined absolute right-md pointer-events-none text-on-surface-variant">calendar_month</span>
          </div>
        </div>

        <!-- Two Columns: Quantity + Price -->
        <div class="grid grid-cols-2 gap-md">
          <div class="group">
            <label class="block font-caption text-caption text-on-surface-variant mb-1 ml-1">{{ qtyLabel }}</label>
            <div class="relative">
              <input
                v-model.number="quantity"
                class="w-full bg-surface-container-low border-none rounded-lg px-md py-3 font-body-md text-on-surface focus:ring-2 focus:ring-primary-container outline-none transition-all"
                :placeholder="qtyPlaceholder"
                type="number"
                step="any"
                min="0"
              />
              <span class="absolute right-md top-1/2 -translate-y-1/2 font-caption text-on-surface-variant">{{ qtySuffix }}</span>
            </div>
          </div>
          <div class="group">
            <label class="block font-caption text-caption text-on-surface-variant mb-1 ml-1">{{ priceLabel }}</label>
            <div class="relative">
              <input
                v-model.number="price"
                class="w-full bg-surface-container-low border-none rounded-lg px-md py-3 font-body-md text-on-surface focus:ring-2 focus:ring-primary-container outline-none transition-all"
                :placeholder="pricePlaceholder"
                type="number"
                step="any"
                min="0"
              />
              <span class="absolute right-md top-1/2 -translate-y-1/2 font-caption text-on-surface-variant">{{ priceSuffix }}</span>
            </div>
          </div>
        </div>

        <!-- Transaction Fee (Optional) -->
        <div class="group">
          <label class="block font-caption text-caption text-on-surface-variant mb-1 ml-1">交易费用 (可选)</label>
          <div class="relative">
            <input
              v-model.number="fee"
              class="w-full bg-surface-container-low border-none rounded-lg px-md py-3 font-body-md text-on-surface focus:ring-2 focus:ring-primary-container outline-none transition-all"
              placeholder="0.00"
              type="number"
              step="any"
              min="0"
            />
            <span class="absolute right-md top-1/2 -translate-y-1/2 font-caption text-on-surface-variant">CNY</span>
          </div>
        </div>

        <!-- Estimated Impact -->
        <div class="pt-sm">
          <div class="flex justify-between items-center mb-2">
            <span class="font-caption text-on-surface-variant">预估持仓影响</span>
            <span class="font-label-bold text-primary">{{ estimatedImpact }}</span>
          </div>
          <div class="h-2 w-full bg-surface-container-high rounded-full overflow-hidden">
            <div
              class="h-full bg-primary-container rounded-full transition-all duration-500"
              :style="{ width: impactProgress + '%' }"
            ></div>
          </div>
        </div>
      </section>

      <!-- 装饰插图 -->
      <div class="mt-lg rounded-xl overflow-hidden h-32 relative bg-surface-container-high">
        <div class="absolute inset-0 bg-gradient-to-br from-primary-container/10 to-secondary-container/10"></div>
        <div class="absolute inset-0 flex items-center justify-center">
          <p class="text-on-surface-variant/60 font-label-bold text-center italic">&ldquo;每一笔投入，都是种下一颗未来的树&rdquo;</p>
        </div>
      </div>
    </main>

    <!-- Fixed Bottom Action Bar -->
    <div class="fixed bottom-0 w-full bg-surface px-gutter pb-8 pt-4 shadow-[0_-4px_12px_rgba(0,0,0,0.05)] z-50">
      <button
        id="submit-btn"
        class="w-full h-14 bg-primary-container hover:bg-primary-container/90 active:scale-95 transition-all text-on-primary-container font-headline-md text-headline-md rounded-xl flex items-center justify-center gap-2"
        @click="handleSubmit"
      >
        <span>确认添加</span>
        <span class="material-symbols-outlined">check_circle</span>
      </button>
    </div>
  </div>
</template>
