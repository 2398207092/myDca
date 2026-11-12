<script setup lang="ts">
/**
 * 交易录入 / 编辑：
 * - 新建（买入 / 卖出）：选持仓 → 日期 / 份额 / 净值 / 手续费 → 确认层 → createTransaction；
 * - 编辑：预填 → 确认层 → updateTransaction，并提供删除（二次确认，写明金额/份额/日期）；
 * - 金额只做估算 = 份额 × 净值（买入 + 手续费 / 卖出 − 手续费），最终以后端计算为准；
 * - 资金动作必须二次确认：确认层重复份额、净值、净值日期、预计金额与资金账户。
 */
import { computed, ref, watch } from 'vue'
import {
  createTransaction,
  deleteTransaction,
  updateTransaction,
  type TransactionItem,
} from '@/api/transaction'
import { listHoldings, type HoldingItem } from '@/api/holding'
import { getAccounts, type AccountItem } from '@/api/account'
import SheetFrame from '@/shared/components/SheetFrame.vue'
import TextField from '@/shared/components/TextField.vue'
import SelectField from '@/shared/components/SelectField.vue'
import BtnKey from '@/shared/components/BtnKey.vue'
import DataRow from '@/shared/components/DataRow.vue'
import EmptyState from '@/shared/components/EmptyState.vue'
import ErrorState from '@/shared/components/ErrorState.vue'
import SkeletonRows from '@/shared/components/SkeletonRows.vue'
import { useAsyncData } from '@/shared/useAsync'
import { closeSheet, refreshData, toast } from '@/shared/ui'
import { fmtMoney, fmtNav, fmtShares, toNumber } from '@/shared/money'
import { parseDate, todayStr, weekdayZH } from '@/shared/date'
import { TX_TYPE_LABEL } from '@/shared/dict'

type Step = 'form' | 'confirm' | 'delete'

const props = withDefaults(
  defineProps<{
    mode?: 'create' | 'edit'
    type?: 'buy' | 'sell'
    holdingId?: string
    holdingName?: string
    transaction?: TransactionItem
  }>(),
  { mode: 'create', type: 'buy', holdingId: '', holdingName: '' },
)

const isEdit = computed(() => props.mode === 'edit')
const originalTx = computed<TransactionItem | null>(() => props.transaction ?? null)
const canEdit = computed(() => !isEdit.value || Boolean(originalTx.value))

interface PageData {
  holdings: HoldingItem[]
  accounts: AccountItem[]
}

const asyncData = useAsyncData<PageData>(async () => {
  const [holdings, accounts] = await Promise.all([listHoldings(), getAccounts()])
  return { holdings, accounts }
})

const holdings = computed(() => asyncData.data.value?.holdings ?? [])
const accounts = computed(() => asyncData.data.value?.accounts ?? [])
const holdingOptions = computed(() =>
  holdings.value.map((h) => ({ value: h.id, label: `${h.name}（${h.code}）` })),
)

const effectiveType = computed(() => (isEdit.value ? (originalTx.value?.type ?? props.type) : props.type))
const typeLabel = computed(() => TX_TYPE_LABEL[effectiveType.value] ?? effectiveType.value)
const canCashEstimate = computed(() => effectiveType.value === 'buy' || effectiveType.value === 'sell')

const sheetTitle = computed(() => {
  if (isEdit.value) return '编辑交易'
  return effectiveType.value === 'sell' ? '卖出' : '买入'
})

const form = ref({
  holdingId: props.holdingId || originalTx.value?.holdingId || '',
  date: originalTx.value?.date ?? todayStr(),
  quantity: originalTx.value ? String(originalTx.value.quantity ?? '') : '',
  price: originalTx.value ? String(originalTx.value.price ?? '') : '',
  fee: originalTx.value ? String(originalTx.value.fee ?? 0) : '',
})

// 只有一个持仓时直接选中，减少一次选择
watch(
  holdings,
  (list) => {
    if (!isEdit.value && !form.value.holdingId && list.length === 1) {
      form.value.holdingId = list[0].id
    }
  },
  { immediate: true },
)

const selectedHolding = computed(
  () => holdings.value.find((h) => h.id === form.value.holdingId) ?? null,
)

const holdingDisplay = computed(() => {
  const h = selectedHolding.value
  if (h) return h.name
  if (form.value.holdingId && form.value.holdingId === props.holdingId && props.holdingName) return props.holdingName
  return form.value.holdingId ? '已选持仓' : '未选择持仓'
})
const holdingMeta = computed(() => selectedHolding.value?.code ?? '')

/** 资金账户：持仓归属账户；未归属时后端退回主现金 */
const fundingAccount = computed(
  () => accounts.value.find((a) => a.id === selectedHolding.value?.accountId) ?? null,
)
const isCryptoHolding = computed(() => selectedHolding.value?.type === 'crypto')
const accountLabel = computed(() => {
  if (isCryptoHolding.value) return fundingAccount.value?.name ?? '加密资产账户'
  return fundingAccount.value?.name ?? '主现金账户（持仓未归属账户，按后端规则退回主现金）'
})

/** 可卖份额：卖出不能超过持仓；编辑原卖单时要把这笔加回去 */
const availableShares = computed<number | null>(() => {
  const h = selectedHolding.value
  if (!h) return null
  const base = toNumber(h.shares)
  if (isEdit.value && originalTx.value?.type === 'sell') return base + toNumber(originalTx.value.quantity)
  return base
})

const preview = computed(() => {
  const quantity = Number(form.value.quantity)
  const price = Number(form.value.price)
  const feeNumber = form.value.fee.trim() === '' ? 0 : Number(form.value.fee)
  const fee = Number.isFinite(feeNumber) && feeNumber > 0 ? feeNumber : 0
  const ready = Number.isFinite(quantity) && quantity > 0 && Number.isFinite(price) && price > 0
  const gross = ready ? quantity * price : 0
  const cash = effectiveType.value === 'sell' ? gross - fee : gross + fee
  return { quantity: ready ? quantity : 0, price: ready ? price : 0, fee, gross, cash, ready }
})

const cashLabel = computed(() => {
  if (effectiveType.value === 'sell') return '预计到账'
  if (effectiveType.value === 'buy') return '预计扣款'
  return '现金影响'
})

const quantityHint = computed(() => {
  if (effectiveType.value === 'sell') {
    if (availableShares.value === null) return '卖出数量不能超过当前持有份额'
    return `当前可卖 ${fmtShares(availableShares.value)}`
  }
  return ''
})

const priceHint = computed(() => {
  const h = selectedHolding.value
  if (h && toNumber(h.latestPrice) > 0) {
    return `持仓最新净值 ${fmtNav(h.latestPrice)}（${h.priceDate || '日期未知'}），可修改`
  }
  return '单位净值，4 位小数；以成交确认的净值为准'
})

const errors = ref<{
  holding?: string
  date?: string
  quantity?: string
  price?: string
  fee?: string
}>({})
const attempted = ref(false)

function isValidDateString(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const parsed = parseDate(value)
  if (!parsed) return false
  return (
    parsed.getFullYear() === Number(value.slice(0, 4)) &&
    parsed.getMonth() + 1 === Number(value.slice(5, 7)) &&
    parsed.getDate() === Number(value.slice(8, 10))
  )
}

function validate(): boolean {
  const next: typeof errors.value = {}
  if (!form.value.holdingId) next.holding = '请选择持仓'
  if (!isValidDateString(form.value.date)) next.date = '日期格式应为 YYYY-MM-DD，例如 2026-08-14'

  const quantity = Number(form.value.quantity)
  if (!Number.isFinite(quantity) || quantity <= 0) {
    next.quantity = '份额必须大于 0'
  } else if (
    effectiveType.value === 'sell' &&
    availableShares.value !== null &&
    quantity > availableShares.value + 1e-9
  ) {
    next.quantity = `超过可卖份额（当前可卖 ${fmtShares(availableShares.value)}）`
  }

  const price = Number(form.value.price)
  if (!Number.isFinite(price) || price <= 0) next.price = '净值必须大于 0'

  const feeRaw = form.value.fee.trim()
  if (feeRaw !== '') {
    const fee = Number(feeRaw)
    if (!Number.isFinite(fee) || fee < 0) next.fee = '手续费不能为负数'
  }

  errors.value = next
  return Object.keys(next).length === 0
}

watch(
  form,
  () => {
    if (attempted.value) validate()
  },
  { deep: true },
)

const step = ref<Step>('form')
const submitting = ref(false)
const deleting = ref(false)
const submitError = ref('')
const deleteError = ref('')

function dateText(value: string): string {
  const weekday = weekdayZH(value)
  return weekday ? `${value} ${weekday}` : value
}

function goConfirm(): void {
  attempted.value = true
  if (!validate()) {
    toast('请先修正表单里的错误', 'error')
    return
  }
  submitError.value = ''
  step.value = 'confirm'
}

const confirmLabel = computed(() => {
  if (isEdit.value) return '确认修改'
  return effectiveType.value === 'sell' ? '确认卖出' : '确认买入'
})

async function submit(): Promise<void> {
  if (submitting.value) return
  submitting.value = true
  submitError.value = ''
  const quantity = preview.value.quantity
  const price = preview.value.price
  const fee = preview.value.fee
  try {
    if (isEdit.value) {
      if (!originalTx.value) throw new Error('缺少交易数据')
      await updateTransaction(originalTx.value.id, {
        date: form.value.date,
        quantity,
        price,
        fee,
      })
      toast(`已修改${typeLabel.value}，份额与现金按后端重算`)
    } else {
      const created = await createTransaction({
        holdingId: form.value.holdingId,
        type: effectiveType.value,
        date: form.value.date,
        quantity,
        price,
        fee,
      })
      toast(`已记录${typeLabel.value} ${fmtShares(created.quantity)}，后端金额 ${fmtMoney(created.total)}`)
    }
    refreshData()
    closeSheet()
  } catch (e) {
    submitError.value = e instanceof Error ? e.message : '提交失败'
    toast(submitError.value, 'error')
  } finally {
    submitting.value = false
  }
}

function goDelete(): void {
  deleteError.value = ''
  step.value = 'delete'
}

async function remove(): Promise<void> {
  if (deleting.value || !originalTx.value) return
  deleting.value = true
  deleteError.value = ''
  try {
    await deleteTransaction(originalTx.value.id)
    toast(`已删除这笔${typeLabel.value}`)
    refreshData()
    closeSheet()
  } catch (e) {
    deleteError.value = e instanceof Error ? e.message : '删除失败'
    toast(deleteError.value, 'error')
  } finally {
    deleting.value = false
  }
}

const noHoldings = computed(
  () => !isEdit.value && Boolean(asyncData.data.value) && holdings.value.length === 0,
)
</script>

<template>
  <SheetFrame
    :title="sheetTitle"
    :subtitle="isEdit ? '修改后份额与现金由后端重算' : '金额实时估算，最终以后端计算为准'"
    @close="closeSheet"
  >
    <EmptyState
      v-if="!canEdit"
      title="缺少交易数据"
      description="请从「过去」面的交易行重新打开编辑。"
    >
      <BtnKey variant="secondary" @click="closeSheet">关闭</BtnKey>
    </EmptyState>

    <SkeletonRows v-else-if="asyncData.loading.value && !asyncData.data.value" :rows="4" />
    <ErrorState
      v-else-if="asyncData.error.value && !asyncData.data.value"
      :message="asyncData.error.value"
      @retry="asyncData.load()"
    />

    <template v-else>
      <!-- 第一步：表单 -->
      <template v-if="step === 'form'">
        <EmptyState
          v-if="noHoldings"
          compact
          title="还没有持仓"
          description="先添加持仓，再来记录买入或卖出。"
        >
          <BtnKey variant="secondary" @click="closeSheet">关闭</BtnKey>
        </EmptyState>

        <template v-else>
          <template v-if="isEdit">
            <DataRow label="交易类型" :value="typeLabel" />
            <DataRow label="持仓" :value="holdingDisplay" :meta="holdingMeta" />
          </template>
          <SelectField
            v-else
            v-model="form.holdingId"
            label="持仓"
            :options="holdingOptions"
            placeholder="选择持仓"
            :error="errors.holding"
          />

          <p v-if="selectedHolding" class="field-note micro" :class="{ 'field-note--tight': !isEdit }">
            当前持有 {{ fmtShares(selectedHolding.shares) }} · 最新净值 {{ fmtNav(selectedHolding.latestPrice) }}（{{
              selectedHolding.priceDate || '—'
            }}）
          </p>

          <TextField
            v-model="form.date"
            label="日期（净值日期）"
            inputmode="numeric"
            placeholder="YYYY-MM-DD"
            :maxlength="10"
            :error="errors.date"
            hint="格式 YYYY-MM-DD，默认今天"
          />
          <TextField
            v-model="form.quantity"
            label="份额"
            inputmode="decimal"
            placeholder="0.00"
            suffix="份"
            :maxlength="20"
            :error="errors.quantity"
            :hint="quantityHint"
          />
          <TextField
            v-model="form.price"
            :label="effectiveType === 'sell' ? '卖出净值' : '买入净值'"
            inputmode="decimal"
            placeholder="0.0000"
            :maxlength="20"
            :error="errors.price"
            :hint="priceHint"
          />
          <TextField
            v-model="form.fee"
            label="手续费（可选）"
            inputmode="decimal"
            placeholder="0.00"
            suffix="元"
            :maxlength="18"
            :error="errors.fee"
            hint="不填按 0 计算"
          />

          <div class="preview">
            <DataRow label="成交金额（份额 × 净值）" :value="preview.ready ? fmtMoney(preview.gross) : '—'" />
            <DataRow label="手续费" :value="fmtMoney(preview.fee)" />
            <p class="note micro">
              估算 = 份额 × 净值{{
                canCashEstimate ? (effectiveType === 'sell' ? ' − 手续费（到账）' : ' + 手续费（扣款）') : '（该类型不动现金）'
              }}；最终以后端计算为准。
            </p>
          </div>

          <div v-if="isEdit" class="danger-zone">
            <BtnKey variant="danger" block @click="goDelete">删除这笔交易</BtnKey>
            <p class="note micro">删除会回退持仓份额、成本与现金，不可恢复。</p>
          </div>
        </template>
      </template>

      <!-- 第二步：确认 -->
      <template v-else-if="step === 'confirm'">
        <p class="confirm-lead">请确认{{ isEdit ? '修改' : `这笔${typeLabel}` }}：</p>
        <DataRow label="交易类型" :value="typeLabel" />
        <DataRow label="持仓" :value="holdingDisplay" :meta="holdingMeta" />
        <DataRow label="日期（净值日期）" :value="dateText(form.date)" />
        <DataRow label="份额" :value="fmtShares(preview.quantity)" />
        <DataRow label="净值" :value="fmtNav(preview.price)" />
        <DataRow label="手续费" :value="fmtMoney(preview.fee)" />
        <DataRow label="成交金额" :value="fmtMoney(preview.gross)" />
        <div class="total-row">
          <span class="label">{{ cashLabel }}</span>
          <span class="num total-amount">{{ canCashEstimate ? fmtMoney(preview.cash) : '不动现金' }}</span>
        </div>
        <div class="money-note">
          <p class="micro">
            资金账户：{{ accountLabel }}{{ isCryptoHolding ? '（加密资产不联动人民币现金）' : '' }}
          </p>
          <p class="micro">金额为估算，最终以后端计算为准。</p>
        </div>
        <p v-if="submitError" class="submit-error" role="alert">⚠ {{ submitError }}</p>
      </template>

      <!-- 第三步：删除确认 -->
      <template v-else>
        <template v-if="originalTx">
          <p class="confirm-lead danger-text">⚠ 删除这笔{{ typeLabel }}？</p>
          <DataRow label="交易类型" :value="typeLabel" />
          <DataRow label="持仓" :value="holdingDisplay" :meta="holdingMeta" />
          <DataRow label="日期" :value="dateText(originalTx.date)" />
          <DataRow label="份额" :value="fmtShares(originalTx.quantity)" />
          <DataRow label="净值" :value="fmtNav(originalTx.price)" />
          <DataRow label="手续费" :value="fmtMoney(originalTx.fee)" />
          <DataRow label="记录金额" :value="fmtMoney(originalTx.total)" />
          <p class="warn-line micro">⚠ 删除后不可恢复：持仓份额、成本与现金会按这笔交易回退。</p>
          <p v-if="originalTx.source === 'dca' || originalTx.dcaPlanId" class="warn-line micro">
            该笔来自定投执行，删除会同时回退定投计划的累计期数与累计投入。
          </p>
          <p v-if="deleteError" class="submit-error" role="alert">⚠ {{ deleteError }}</p>
        </template>
      </template>
    </template>

    <template #footer>
      <div class="footer">
        <div v-if="step === 'form' && canEdit" class="footer-summary">
          <span class="label">{{ cashLabel }}</span>
          <span class="num footer-amount">
            {{ canCashEstimate ? (preview.ready ? fmtMoney(preview.cash) : '—') : '不动现金' }}
          </span>
        </div>
        <div class="footer-actions">
          <BtnKey v-if="!canEdit" variant="secondary" block @click="closeSheet">关闭</BtnKey>
          <template v-else-if="step === 'form'">
            <BtnKey variant="secondary" block @click="closeSheet">取消</BtnKey>
            <BtnKey variant="primary" block :disabled="submitting || noHoldings" @click="goConfirm">
              下一步
            </BtnKey>
          </template>
          <template v-else-if="step === 'confirm'">
            <BtnKey variant="secondary" block :disabled="submitting" @click="step = 'form'">返回修改</BtnKey>
            <BtnKey variant="primary" block :disabled="submitting" @click="submit">
              {{ submitting ? '提交中…' : confirmLabel }}
            </BtnKey>
          </template>
          <template v-else>
            <BtnKey variant="secondary" block :disabled="deleting" @click="step = 'form'">返回</BtnKey>
            <BtnKey variant="danger" block :disabled="deleting" @click="remove">
              {{ deleting ? '删除中…' : '确认删除' }}
            </BtnKey>
          </template>
        </div>
      </div>
    </template>
  </SheetFrame>
</template>

<style scoped>
.field-note {
  margin: var(--space-2) 0 var(--space-4);
  line-height: 1.5;
}
.field-note--tight {
  margin-top: calc(var(--space-2) * -1);
}
.footer {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.footer-summary {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 0 var(--space-1);
}
.footer-amount {
  font-size: var(--text-readout-md);
  font-weight: 600;
}
.footer-actions {
  display: flex;
  gap: var(--space-2);
}
.preview {
  margin-top: var(--space-5);
  border-top: 1px solid var(--hairline);
  padding-top: var(--space-3);
}
.total-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) 0;
}
.total-amount {
  font-size: var(--text-readout-md);
  font-weight: 600;
}
.confirm-lead {
  font-size: var(--text-body);
  font-weight: 600;
  padding: var(--space-2) 0 var(--space-3);
}
.danger-text {
  color: var(--danger);
}
.money-note {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  margin-top: var(--space-2);
}
.warn-line {
  margin-top: var(--space-3);
  color: var(--warn);
  line-height: 1.6;
}
.submit-error {
  margin-top: var(--space-2);
  font-size: var(--text-label);
  color: var(--danger);
  line-height: 1.5;
}
.note {
  margin-top: var(--space-2);
  line-height: 1.6;
}
.danger-zone {
  margin-top: var(--space-6);
  padding-top: var(--space-4);
  border-top: 1px solid var(--hairline);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
</style>
