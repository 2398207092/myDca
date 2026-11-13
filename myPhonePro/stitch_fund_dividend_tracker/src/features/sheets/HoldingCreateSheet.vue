<script setup lang="ts">
/**
 * 新建持仓：代码可先用「查询」从后端基金库带出名称/类型，也可以完全手填。
 * 注意后端口径：cost 传的是「每份成本」，创建后会按「份额 × 每份成本 + 买入费率」从账户现金扣款。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { createHolding, searchHoldings, type HoldingSearchResult } from '@/api/holding'
import { getAccounts, type AccountItem } from '@/api/account'
import SheetFrame from '@/shared/components/SheetFrame.vue'
import TextField from '@/shared/components/TextField.vue'
import SelectField from '@/shared/components/SelectField.vue'
import BtnKey from '@/shared/components/BtnKey.vue'
import { closeSheet, refreshData, toast } from '@/shared/ui'
import { errorText } from '@/shared/useAsync'
import { fmtMoney, toNumber } from '@/shared/money'

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

const accountOptions = computed(() =>
  accounts.value.map((a) => ({ value: a.id, label: `${a.name}（${a.baseCurrency}）` })),
)

onMounted(async () => {
  try {
    accounts.value = await getAccounts()
    const def = accounts.value.find((a) => a.isDefault) ?? accounts.value[0]
    if (def) form.accountId = def.id
  } catch (e) {
    accountsError.value = errorText(e)
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
  if (!(toNumber(form.shares) > 0)) errors.shares = '持有份额必须大于 0'
  if (!(toNumber(form.cost) > 0)) errors.cost = '每份成本必须大于 0'
  if (!form.accountId) errors.accountId = '请选择归属账户'
  return Object.keys(errors).length === 0
}

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
      cost: toNumber(form.cost),
      assetCategory: form.assetCategory || undefined,
      quoteCurrency: form.quoteCurrency,
      accountId: form.accountId,
    })
    toast('持仓已创建，已按成本从账户现金扣减')
    refreshData()
    closeSheet()
  } catch (e) {
    submitError.value = errorText(e)
  } finally {
    submitting.value = false
  }
}

const costPreview = computed(() => toNumber(form.shares) * toNumber(form.cost))
</script>

<template>
  <SheetFrame
    title="新建持仓"
    subtitle="成本填「每份成本」；创建后后端会按成本 + 买入费率从账户现金扣款"
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
      label="当前份额"
      inputmode="decimal"
      placeholder="例如 10000"
      suffix="份"
      :error="errors.shares"
    />
    <TextField
      v-model="form.cost"
      label="每份成本"
      inputmode="decimal"
      placeholder="例如 1.08"
      suffix="元/份"
      :error="errors.cost"
      hint="不是总成本；总成本 = 份额 × 每份成本"
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
    <SelectField v-model="form.quoteCurrency" label="报价币种" :options="CURRENCIES" placeholder="CNY" />
    <p class="note micro">
      创建后会以「当前份额 + 每份成本」建仓，并从归属账户现金扣减
      <b class="num">{{ fmtMoney(costPreview) }}</b>
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
