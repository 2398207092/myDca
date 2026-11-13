<script setup lang="ts">
/**
 * 新建持仓：代码可先用「查询」从后端基金库带出名称/类型，也可以完全手填。
 * 注意后端口径：
 * - 基金/ETF：cost 传「每份成本」，创建后按「份额 × 每份成本 + 买入费率」从账户现金扣款；
 * - 加密币：cost 传「每枚成本（USDT）」按当日汇率折算成人民币，后端不建初始交易、不扣人民币现金。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { createHolding, searchHoldings, type HoldingSearchResult } from '@/api/holding'
import { getAccounts, type AccountItem } from '@/api/account'
import { listExchangeRates } from '@/api/exchangeRate'
import SheetFrame from '@/next/shared/components/SheetFrame.vue'
import TextField from '@/next/shared/components/TextField.vue'
import SelectField from '@/next/shared/components/SelectField.vue'
import BtnKey from '@/next/shared/components/BtnKey.vue'
import { closeSheet, refreshData, toast } from '@/next/shared/ui'
import { errorText } from '@/next/shared/useAsync'
import { fmtMoney, toNumber } from '@/next/shared/money'

const HOLDING_TYPES = [
  { value: 'fund', label: '基金' },
  { value: 'ETF', label: 'ETF' },
  { value: 'A股', label: 'A股' },
  { value: '港股', label: '港股' },
  { value: '美股', label: '美股' },
  { value: 'crypto', label: '加密资产' },
  { value: '自定义', label: '自定义' },
]

const CATEGORIES = [
  { value: '', label: '未分类' },
  { value: 'dividend', label: '红利' },
  { value: 'us_stock', label: '美股' },
  { value: 'gold', label: '黄金' },
  { value: 'crypto', label: '加密' },
]

const CURRENCIES = [
  { value: 'CNY', label: 'CNY 人民币' },
  { value: 'USDT', label: 'USDT 计价' },
]

const accounts = ref<AccountItem[]>([])
const accountsError = ref('')
const usdCny = ref<number | null>(null)
const ratesError = ref('')
const searching = ref(false)
const searchError = ref('')
const searchResults = ref<HoldingSearchResult[]>([])
const submitting = ref(false)
const submitError = ref('')
const errors = reactive<Record<string, string>>({})

const form = reactive({
  code: '',
  name: '',
  type: 'fund',
  shares: '',
  cost: '',
  assetCategory: '',
  accountId: '',
  quoteCurrency: 'CNY',
})

const isCrypto = computed(() => form.type === 'crypto')

/** 加密币只能挂 USDT 账户，人民币资产只能挂 CNY 账户（与后端校验同一条规则） */
const eligibleAccounts = computed(() =>
  accounts.value.filter((a) => (isCrypto.value ? a.baseCurrency === 'USDT' : a.baseCurrency !== 'USDT')),
)
const accountOptions = computed(() =>
  eligibleAccounts.value.map((a) => ({ value: a.id, label: `${a.name}（${a.baseCurrency}）` })),
)

function pickAccount(): void {
  if (eligibleAccounts.value.some((a) => a.id === form.accountId)) return
  const preferred = eligibleAccounts.value.find((a) => a.isDefault) ?? eligibleAccounts.value[0]
  form.accountId = preferred?.id ?? ''
}

watch(isCrypto, (crypto) => {
  form.quoteCurrency = crypto ? 'USDT' : 'CNY'
  pickAccount()
  errors.accountId = ''
})

onMounted(async () => {
  const [accountResult, rateResult] = await Promise.allSettled([getAccounts(), listExchangeRates()])
  if (accountResult.status === 'fulfilled') {
    accounts.value = accountResult.value
    pickAccount()
  } else {
    accountsError.value = errorText(accountResult.reason)
  }
  if (rateResult.status === 'fulfilled') {
    usdCny.value = rateResult.value.find((r) => r.pair === 'USD/CNY')?.rate ?? null
    if (!usdCny.value) ratesError.value = '未找到 USD/CNY 汇率，加密币成本无法折算'
  } else {
    ratesError.value = '汇率加载失败，加密币成本无法折算'
  }
})

async function doSearch(): Promise<void> {
  const keyword = form.code.trim()
  if (!keyword) {
    searchError.value = '先输入基金代码'
    return
  }
  searching.value = true
  searchError.value = ''
  searchResults.value = []
  try {
    const list = await searchHoldings(keyword)
    searchResults.value = list.slice(0, 6)
    if (!list.length) searchError.value = '没有匹配的基金/ETF，可以直接手填名称'
  } catch (e) {
    searchError.value = errorText(e)
  } finally {
    searching.value = false
  }
}

function pickResult(item: HoldingSearchResult): void {
  form.code = item.code
  form.name = item.name
  if (HOLDING_TYPES.some((t) => t.value === item.type)) form.type = item.type
  searchResults.value = []
  searchError.value = ''
}

function validate(): boolean {
  for (const key of Object.keys(errors)) delete errors[key]
  if (!form.code.trim()) errors.code = '基金代码不能为空'
  if (!form.name.trim()) errors.name = '名称不能为空'
  if (!(toNumber(form.shares) > 0)) errors.shares = isCrypto.value ? '持有数量必须大于 0' : '持有份额必须大于 0'
  if (!(toNumber(form.cost) > 0)) errors.cost = isCrypto.value ? '每枚成本必须大于 0' : '每份成本必须大于 0'
  if (isCrypto.value && !usdCny.value) errors.cost = 'USD/CNY 汇率不可用，暂时不能创建加密币持仓'
  if (!form.accountId) errors.accountId = '请选择归属账户'
  return Object.keys(errors).length === 0
}

/** 加密币成本按录入时汇率折算成人民币；后端只存人民币成本 */
const costCny = computed(() => {
  const shares = toNumber(form.shares)
  const cost = toNumber(form.cost)
  if (isCrypto.value) return usdCny.value ? shares * cost * usdCny.value : 0
  return shares * cost
})

async function submit(): Promise<void> {
  if (submitting.value || !validate()) return
  submitting.value = true
  submitError.value = ''
  try {
    await createHolding({
      name: form.name.trim(),
      code: form.code.trim(),
      type: form.type,
      shares: toNumber(form.shares),
      cost: Math.round(costCny.value * 100) / 100,
      assetCategory: form.assetCategory || undefined,
      quoteCurrency: isCrypto.value ? 'USDT' : form.quoteCurrency,
      // 加密币没有初始买入交易，必须标估算成本；否则夜间净值刷新会把成本重算成 0
      costEstimated: isCrypto.value ? true : undefined,
      accountId: form.accountId,
    })
    toast(
      isCrypto.value
        ? `已创建加密持仓，成本 ${fmtMoney(costCny.value)}（按 1 USDT≈${usdCny.value?.toFixed(4)} CNY 折算）`
        : '持仓已创建，已按成本从账户现金扣减',
    )
    refreshData()
    closeSheet()
  } catch (e) {
    submitError.value = errorText(e)
  } finally {
    submitting.value = false
  }
}

const costLabel = computed(() => (isCrypto.value ? '每枚成本（USDT）' : '每份成本'))
const costSuffix = computed(() => (isCrypto.value ? 'USDT/枚' : '元/份'))
const costHint = computed(() =>
  isCrypto.value
    ? '按 1 USDT 的汇率折算成人民币保存；加密币不生成初始买入交易，也不扣人民币现金'
    : '不是总成本；总成本 = 份额 × 每份成本',
)
</script>

<template>
  <SheetFrame
    title="新建持仓"
    :subtitle="isCrypto ? '加密币按 USDT 成本录入，折算人民币保存；不扣现金' : '成本填「每份成本」；创建后后端会按成本 + 买入费率从账户现金扣款'"
    @close="closeSheet"
  >
    <TextField
      v-model="form.code"
      label="基金 / ETF 代码"
      placeholder="例如 515180"
      :maxlength="16"
      :error="errors.code"
    />
    <div class="query-row">
      <button class="query-key" type="button" :disabled="searching" @click="doSearch">
        {{ searching ? '查询中…' : '按代码查询名称' }}
      </button>
    </div>
    <p v-if="searchError" class="search-error micro" role="alert">{{ searchError }}</p>
    <div v-if="searchResults.length" class="search-list">
      <button
        v-for="item in searchResults"
        :key="item.code"
        class="search-item"
        type="button"
        @click="pickResult(item)"
      >
        <span class="search-name">{{ item.name }}</span>
        <span class="micro">{{ item.code }} · {{ item.type }}<template v-if="item.netWorth"> · 最新净值 {{ item.netWorth }}</template></span>
      </button>
    </div>

    <TextField v-model="form.name" label="持有名称" placeholder="例如 中证红利ETF" :error="errors.name" />
    <SelectField v-model="form.type" label="类型" :options="HOLDING_TYPES" placeholder="选择类型" />
    <TextField
      v-model="form.shares"
      :label="isCrypto ? '当前数量' : '当前份额'"
      inputmode="decimal"
      :placeholder="isCrypto ? '例如 0.01139738' : '例如 10000'"
      :suffix="isCrypto ? '枚' : '份'"
      :error="errors.shares"
    />
    <TextField
      v-model="form.cost"
      :label="costLabel"
      inputmode="decimal"
      :placeholder="isCrypto ? '例如 80000' : '例如 1.08'"
      :suffix="costSuffix"
      :error="errors.cost"
      :hint="costHint"
    />
    <SelectField v-model="form.assetCategory" label="资产分类" :options="CATEGORIES" placeholder="未分类" />
    <SelectField
      v-model="form.accountId"
      label="归属账户"
      :options="accountOptions"
      placeholder="选择账户"
      :error="errors.accountId"
    />
    <p v-if="accountsError" class="search-error micro">账户加载失败：{{ accountsError }}</p>
    <p v-if="isCrypto && !eligibleAccounts.length" class="search-error micro">
      还没有 USDT 本位币账户；加密币只能挂在交易所等 USDT 账户下，请先在「新建账户」创建。
    </p>
    <SelectField
      v-model="form.quoteCurrency"
      label="报价币种"
      :options="CURRENCIES"
      placeholder="CNY"
      :disabled="isCrypto"
    />
    <p v-if="ratesError && isCrypto" class="search-error micro">{{ ratesError }}</p>
    <p v-if="isCrypto" class="note micro">
      加密币按「数量 × 每枚成本（USDT）」记录，保存时按 1 USDT ≈
      <b class="num">{{ usdCny?.toFixed(4) ?? '—' }}</b>
      CNY 折算为 <b class="num">{{ fmtMoney(costCny) }}</b> 人民币成本；
      <b>不生成初始买入交易，也不扣人民币现金</b>，价格由行情任务刷新。
    </p>
    <p v-else class="note micro">
      创建后会以「当前份额 + 每份成本」建仓，并从归属账户现金扣减
      <b class="num">{{ fmtMoney(costCny) }}</b>
      （另加买入费率）；现金不足会创建失败。
    </p>
    <p v-if="submitError" class="submit-error" role="alert">{{ submitError }}</p>

    <template #footer>
      <BtnKey variant="secondary" block :disabled="submitting" @click="closeSheet">取消</BtnKey>
      <BtnKey variant="primary" block :disabled="submitting" @click="submit">
        {{ submitting ? '创建中…' : '创建持仓' }}
      </BtnKey>
    </template>
  </SheetFrame>
</template>

<style scoped>
.query-row {
  display: flex;
  justify-content: flex-end;
  margin: calc(var(--space-2) * -1) 0 var(--space-3);
}
.query-key {
  min-height: 44px;
  padding: 0 var(--space-3);
  border-radius: var(--radius-key);
  border: 1px solid var(--edge);
  background: var(--canvas);
  box-shadow: var(--el-key);
  color: var(--accent);
  font-size: var(--text-micro);
  font-weight: 600;
}
.query-key:active {
  box-shadow: var(--press);
}
.search-error {
  color: var(--warn);
  margin-bottom: var(--space-3);
  line-height: 1.5;
}
.search-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}
.search-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  min-height: 56px;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-key);
  border: 1px solid var(--edge);
  background: var(--canvas);
  box-shadow: var(--el-key);
  text-align: left;
}
.search-item:active {
  box-shadow: var(--press);
}
.search-name {
  font-size: var(--text-body-sm);
  font-weight: 600;
  color: var(--ink);
}
.note {
  margin-top: var(--space-2);
  line-height: 1.6;
}
.submit-error {
  margin-top: var(--space-2);
  font-size: var(--text-label);
  color: var(--danger);
  line-height: 1.5;
}
</style>
