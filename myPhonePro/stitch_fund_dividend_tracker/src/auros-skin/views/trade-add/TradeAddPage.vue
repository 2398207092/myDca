<script setup lang="ts">
/**
 * 添加交易（Auros 卡片化 · 2026-11-11）
 * 结构：类型切换卡 → 交易信息章卡 → 金额与份额章卡 → 影响预估章卡 → 底部固定主按钮
 * 业务逻辑与原页面逐行一致，只换外壳与呈现；金额/份额/净值统一走 utils/format。
 */
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createTransaction } from '@/api/transaction'
import { listHoldings, getNavByDate } from '@/api/holding'
import type { HoldingItem, NavByDateResult } from '@/api/holding'
import { formatMoney, formatPercent, formatPrice, formatQty } from '@/auros-skin/utils/format'
import AurosNavBar from '@/auros-skin/components/auros/AurosNavBar.vue'
import AurosCard from '@/auros-skin/components/auros/AurosCard.vue'
import AurosChapter from '@/auros-skin/components/auros/AurosChapter.vue'
import AurosField from '@/auros-skin/components/auros/AurosField.vue'
import AurosRow from '@/auros-skin/components/auros/AurosRow.vue'

const route = useRoute()
const router = useRouter()

const holdings = ref<HoldingItem[]>([])
const selectedHoldingId = ref('')
const formState = ref<'loading' | 'ready' | 'submitting'>('loading')
const error = ref('')
const success = ref(false)

function localToday(): string {
  const n = new Date()
  return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')}`
}

const transactionType = ref<'buy' | 'sell' | 'bonus_share' | 'reinvest'>('buy')
const date = ref(localToday())
const quantity = ref(0)
const price = ref(0)
const fee = ref(0)

const buyAmount = ref(0)
const navResult = ref<NavByDateResult | null>(null)
const navState = ref<'idle' | 'loading' | 'found' | 'notfound' | 'error'>('idle')

const qtyLabel = ref('买入数量')
const priceLabel = ref('买入金额 (总价)')
const dateLabel = ref('交易日期')

const isCryptoHolding = computed(() => holdingItem.value?.quoteCurrency === 'USDT')
const qtySuffix = computed(() => (isCryptoHolding.value ? '枚' : '份'))
const priceSuffix = computed(() => (isCryptoHolding.value ? 'USDT' : 'CNY'))
const currencySuffix = computed(() => (isCryptoHolding.value ? 'USDT' : 'CNY'))

const qtyPlaceholder = computed(() => (transactionType.value === 'bonus_share' ? '0' : '0.00'))
const pricePlaceholder = computed(() => '0.0000')

const estimatedImpact = computed(() => {
  const qty = quantity.value || 0
  const unit = price.value || 0
  const feeVal = fee.value || 0
  const total = qty * unit + feeVal
  const sign =
    transactionType.value === 'buy' ||
    transactionType.value === 'bonus_share' ||
    transactionType.value === 'reinvest'
      ? '+'
      : '-'
  const qtyUnit = isCryptoHolding.value ? '枚' : '份'
  const totalText = isCryptoHolding.value
    ? `${formatMoney(total, { symbol: false })} USDT`
    : formatMoney(total)
  return `${sign}${formatQty(qty, 2)} ${qtyUnit} / ${totalText}`
})

const impactProgress = computed(() => {
  const qty = quantity.value || 0
  return Math.min((qty / 10000) * 100, 100)
})

const holdingItem = computed(() => holdings.value.find((h) => h.id === selectedHoldingId.value))
const currentBuyFeeRate = computed(() => holdingItem.value?.buyFeeRate ?? 0.15)

async function fetchNav() {
  if (isCryptoHolding.value) {
    navState.value = 'idle'
    navResult.value = null
    return
  }
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
    const feeVal = (amount * rate) / 100
    const shares = (amount - feeVal) / nav
    fee.value = Number(feeVal.toFixed(2))
    quantity.value = Number(shares.toFixed(4))
    price.value = nav
  }
}

watch([selectedHoldingId, date, transactionType], () => {
  buyAmount.value = 0
  fetchNav()
})
watch(buyAmount, () => applyBuyAutoCalc())

function setType(type: 'buy' | 'sell' | 'bonus_share' | 'reinvest') {
  transactionType.value = type
  switch (type) {
    case 'buy':
      qtyLabel.value = '买入数量'
      priceLabel.value = '买入单价'
      dateLabel.value = '交易日期'
      break
    case 'sell':
      qtyLabel.value = '卖出数量'
      priceLabel.value = '卖出金额 (总价)'
      dateLabel.value = '交易日期'
      break
    case 'bonus_share':
      qtyLabel.value = '送股数量'
      priceLabel.value = '登记价格 (通常为0)'
      dateLabel.value = '除权日期'
      break
    case 'reinvest':
      qtyLabel.value = '复投数量'
      priceLabel.value = '复投单位净值'
      dateLabel.value = '分红再投日'
      break
  }
  try {
    if (navigator.vibrate) navigator.vibrate(10)
  } catch (_) {}
}

const segmentItems: { type: 'buy' | 'sell' | 'bonus_share' | 'reinvest'; label: string }[] = [
  { type: 'buy', label: '买入' },
  { type: 'sell', label: '卖出' },
  { type: 'bonus_share', label: '送股' },
  { type: 'reinvest', label: '复投' },
]

async function loadHoldings() {
  try {
    holdings.value = await listHoldings()
    formState.value = 'ready'
    if (holdings.value.length > 0) {
      const presetId = route.query.holdingId as string
      if (presetId && holdings.value.some((h) => h.id === presetId)) {
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
  if (formState.value === 'submitting') return
  if (!selectedHoldingId.value || quantity.value <= 0) {
    error.value = '请填写完整的交易信息'
    return
  }
  if (transactionType.value !== 'bonus_share' && price.value <= 0) {
    error.value = '请填写完整的交易信息'
    return
  }

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
    formState.value = 'ready'
  }
}
</script>

<template>
  <div class="page">
    <AurosNavBar title="添加交易">
      <template #right>
        <button type="button" class="nav-text" @click="router.push('/')">取消</button>
      </template>
    </AurosNavBar>

    <main class="body">
      <!-- 类型切换：无标题表单组 = 一张卡 -->
      <AurosCard>
        <div class="seg">
          <button
            v-for="item in segmentItems"
            :key="item.type"
            type="button"
            class="seg__item"
            :class="{ 'is-active': transactionType === item.type }"
            @click="setType(item.type)"
          >
            {{ item.label }}
          </button>
        </div>
      </AurosCard>

      <!-- 交易信息 -->
      <AurosChapter label="TRADE" title="交易信息" :meta="holdings.length ? `${holdings.length} 只可选` : '暂无持仓'">
        <div class="form">
          <label class="field">
            <span class="field__label">标的</span>
            <span class="field__box">
              <select v-model="selectedHoldingId" class="field__select">
                <option v-for="h in holdings" :key="h.id" :value="h.id">{{ h.name }}（{{ h.code }}）</option>
              </select>
              <svg class="field__chev" viewBox="0 0 12 12" width="10" height="10" fill="none">
                <path d="M2.6 4.6 6 8l3.4-3.4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </span>
          </label>

          <label class="field">
            <span class="field__label">{{ dateLabel }}</span>
            <span class="field__box">
              <input v-model="date" class="field__input is-date" type="date" />
            </span>
          </label>

          <!-- 加密币提示 -->
          <p v-if="isCryptoHolding" class="hint">
            <i class="hint__dot is-warn"></i>
            加密币交易：请手动输入 USDT 单价；不会自动扣减交易所余额
          </p>

          <!-- 净值状态 -->
          <p
            v-if="transactionType === 'buy' && !isCryptoHolding && formState === 'ready'"
            class="hint"
            :class="{ 'is-on': navState === 'found', 'is-off': navState === 'notfound' || navState === 'error' }"
          >
            <i class="hint__dot" :class="{ 'is-on': navState === 'found', 'is-warn': navState === 'loading' }"></i>
            <template v-if="navState === 'loading'">正在读取当日净值…</template>
            <template v-else-if="navState === 'notfound'">该日期暂无净值记录，请手动填写单价与数量</template>
            <template v-else-if="navState === 'error'">净值读取失败，请手动填写</template>
            <template v-else-if="navState === 'found' && navResult">
              当日净值 {{ formatPrice(navResult.unitNav, 4) }}（{{ navResult.navDate }}）· 费率
              {{ formatPercent(currentBuyFeeRate, 2, false) }}
            </template>
            <template v-else>选择日期后自动读取本地净值</template>
          </p>
        </div>
      </AurosChapter>

      <!-- 金额与份额 -->
      <AurosChapter label="AMOUNT" title="金额与份额">
        <div class="form">
          <AurosField
            v-if="transactionType === 'buy'"
            v-model="buyAmount"
            label="买入金额"
            :suffix="currencySuffix"
            inputmode="decimal"
            type="number"
            placeholder="输入金额后自动算手续费与份额"
            mono
          />

          <div class="grid2">
            <AurosField v-model="quantity" :label="qtyLabel" :suffix="qtySuffix" type="number" inputmode="decimal" :placeholder="qtyPlaceholder" mono />
            <AurosField v-model="price" :label="priceLabel" :suffix="priceSuffix" type="number" inputmode="decimal" :placeholder="pricePlaceholder" mono />
          </div>

          <AurosField v-model="fee" label="交易费用（可选）" :suffix="currencySuffix" type="number" inputmode="decimal" placeholder="0.00" mono />
        </div>
      </AurosChapter>

      <!-- 影响预估 -->
      <AurosChapter label="IMPACT" title="影响预估">
        <AurosRow label="预估持仓影响" :value="estimatedImpact" />
        <div class="impact__bar"><i :style="{ width: impactProgress + '%' }"></i></div>
        <p v-if="error" class="error">{{ error }}</p>
        <p v-if="success" class="ok">已记录 · 正在返回交易列表</p>
      </AurosChapter>
    </main>

    <footer class="bar">
      <button type="button" class="submit" :disabled="formState === 'submitting'" @click="handleSubmit">
        {{ formState === 'submitting' ? '提交中…' : '确认添加' }}
      </button>
    </footer>
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
  padding: 8px 16px calc(env(safe-area-inset-bottom, 0px) + 96px);
}
.nav-text {
  border: 0;
  background: none;
  padding: 0;
  font-family: inherit;
  font-size: 13px;
  color: var(--au-silver);
}

/* 卡内表单：字段之间 14px，不需要发丝线 */
.form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.grid2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.grid2 > * {
  min-width: 0;
}

/* ---------- 类型切换 ---------- */
.seg {
  display: flex;
  gap: 4px;
  padding: 3px;
  border-radius: 6px;
  background: rgba(1, 29, 28, 0.5);
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
  transition: background 200ms var(--au-ease), color 200ms var(--au-ease);
}
.seg__item.is-active {
  background: rgba(203, 255, 252, 0.14);
  color: var(--au-mist);
}

/* ---------- 自写字段（标的 / 日期）：与 AurosField 同一口井 ---------- */
.field {
  display: block;
}
.field__label {
  display: block;
  margin-bottom: 7px;
  font-size: 10px;
  letter-spacing: 0.16em;
  color: var(--au-silver);
}
.field__box {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 46px;
  padding: 0 12px;
  border-radius: 6px;
  background: rgba(1, 29, 28, 0.55);
  transition: background 200ms var(--au-ease);
}
.field__box::before {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 1px;
  background: rgba(237, 255, 254, 0.12);
  transition: background 200ms var(--au-ease);
}
.field:focus-within .field__box {
  background: rgba(0, 67, 63, 0.8);
}
.field:focus-within .field__box::before {
  background: rgba(203, 255, 252, 0.75);
}
.field__select,
.field__input {
  flex: 1;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  border: 0;
  background: none;
  outline: none;
  color: var(--au-mist);
  font-family: inherit;
  font-size: 16px;
  font-weight: 500;
  appearance: none;
  -webkit-appearance: none;
}
.field__select option {
  background: var(--au-abyss);
  color: var(--au-mist);
}
.field__input.is-date {
  color-scheme: dark;
  font-weight: 400;
}
.field__chev {
  flex: none;
  color: var(--au-silver);
}

/* AurosField 在抬升卡上改用下沉井，避免与卡面同色 */
.form :deep(.field__box) {
  background: rgba(1, 29, 28, 0.55);
}
.form :deep(.field:focus-within .field__box) {
  background: rgba(0, 67, 63, 0.8);
}

/* ---------- 提示（放在所属卡内） ---------- */
.hint {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin: 0;
  font-size: 10px;
  line-height: 1.6;
  letter-spacing: 0.03em;
  color: rgba(187, 199, 198, 0.72);
}
.hint__dot {
  flex: none;
  width: 4px;
  height: 4px;
  margin-top: 6px;
  border-radius: 50%;
  background: rgba(187, 199, 198, 0.4);
}
.hint.is-on {
  color: var(--au-lit);
}
.hint.is-on .hint__dot,
.hint__dot.is-on {
  background: var(--au-lit);
  box-shadow: 0 0 8px rgba(203, 255, 252, 0.8);
}
.hint.is-off {
  color: rgba(253, 233, 255, 0.85);
}
.hint__dot.is-warn {
  background: var(--au-pink);
  animation: au-breathe 2s ease-in-out infinite;
}

/* ---------- 影响预估 ---------- */
.impact__bar {
  position: relative;
  height: 3px;
  margin-top: 12px;
  border-radius: 2px;
  background: rgba(237, 255, 254, 0.1);
}
.impact__bar i {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  border-radius: 2px;
  background: linear-gradient(90deg, rgba(203, 255, 252, 0.5), var(--au-lit));
  transition: width 400ms var(--au-ease);
}
.error {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: rgba(253, 233, 255, 0.92);
}
.ok {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--au-lit);
}

/* ---------- 底部固定提交条（全页唯一渐变） ---------- */
.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 12px 16px calc(env(safe-area-inset-bottom, 0px) + 16px);
  background: linear-gradient(180deg, rgba(1, 38, 36, 0) 0%, rgba(1, 29, 28, 0.92) 38%);
}
.submit {
  width: 100%;
  height: 46px;
  border: 0;
  border-radius: 6px;
  background: var(--au-aurora);
  color: var(--au-ink);
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.14em;
  text-indent: 0.14em;
  transition: transform 160ms var(--au-ease), filter 200ms var(--au-ease);
}
.submit:active:not(:disabled) {
  transform: scale(0.98);
  filter: brightness(0.96);
}
.submit:disabled {
  opacity: 0.5;
}

@media (prefers-reduced-motion: reduce) {
  .hint__dot.is-warn {
    animation: none;
  }
  .seg__item,
  .field__box,
  .field__box::before,
  .impact__bar i,
  .submit {
    transition: none;
  }
}
</style>
