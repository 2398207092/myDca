<script setup lang="ts">
/**
 * 记分红：创建一笔 payout 分红事件，固定记为「待确认」。
 * 口径：待确认不改变现金与份额；到账/复投在过去面处理（后端到账时才动现金或自动复投）。
 */
import { computed, ref, watch } from 'vue'
import { createEvent } from '@/api/event'
import { listHoldings, type HoldingItem } from '@/api/holding'
import SheetFrame from '@/shared/components/SheetFrame.vue'
import TextField from '@/shared/components/TextField.vue'
import SelectField from '@/shared/components/SelectField.vue'
import BtnKey from '@/shared/components/BtnKey.vue'
import DataRow from '@/shared/components/DataRow.vue'
import StatusPill from '@/shared/components/StatusPill.vue'
import EmptyState from '@/shared/components/EmptyState.vue'
import ErrorState from '@/shared/components/ErrorState.vue'
import SkeletonRows from '@/shared/components/SkeletonRows.vue'
import { useAsyncData } from '@/shared/useAsync'
import { closeSheet, refreshData, toast } from '@/shared/ui'
import { fmtMoney, toNumber } from '@/shared/money'
import { parseDate, todayStr, weekdayZH } from '@/shared/date'

const props = withDefaults(
  defineProps<{
    holdingId?: string
    holdingName?: string
  }>(),
  { holdingId: '', holdingName: '' },
)

const asyncData = useAsyncData<HoldingItem[]>(() => listHoldings())
const holdings = computed(() => asyncData.data.value ?? [])

const holdingOptions = computed(() =>
  holdings.value.map((h) => ({ value: h.id, label: `${h.name}（${h.code}）` })),
)

const form = ref({
  holdingId: props.holdingId,
  date: todayStr(),
  amount: '',
  description: '',
})

const errors = ref<{ holding?: string; date?: string; amount?: string }>({})
const attempted = ref(false)
const submitting = ref(false)
const submitError = ref('')

const selectedHolding = computed(() => holdings.value.find((h) => h.id === form.value.holdingId) ?? null)
const selectedLabel = computed(() => {
  const h = selectedHolding.value
  if (h) return `${h.name}（${h.code}）`
  if (form.value.holdingId && form.value.holdingId === props.holdingId && props.holdingName) return props.holdingName
  return '未选择持仓'
})

const amountValue = computed(() => toNumber(form.value.amount))
const amountValid = computed(() => Number.isFinite(Number(form.value.amount)) && amountValue.value > 0)

// 只有一个持仓时直接选中，减少一次选择
watch(
  holdings,
  (list) => {
    if (!form.value.holdingId && list.length === 1) form.value.holdingId = list[0].id
  },
  { immediate: true },
)

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
  if (!form.value.holdingId) next.holding = '请选择分红对应的持仓'
  if (!isValidDateString(form.value.date)) next.date = '日期格式应为 YYYY-MM-DD，例如 2026-08-14'
  const amount = Number(form.value.amount)
  if (!Number.isFinite(amount) || amount <= 0) next.amount = '分红金额必须大于 0'
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

async function submit(): Promise<void> {
  if (submitting.value) return
  attempted.value = true
  if (!validate()) {
    toast('请先修正表单里的错误', 'error')
    return
  }
  submitting.value = true
  submitError.value = ''
  try {
    await createEvent({
      holdingId: form.value.holdingId,
      type: 'payout',
      date: form.value.date,
      amount: Number(form.value.amount),
      description: form.value.description.trim() || undefined,
    })
    toast(`已记为待确认，可在过去面标记到账`)
    refreshData()
    closeSheet()
  } catch (e) {
    submitError.value = e instanceof Error ? e.message : '记录分红失败'
    toast(submitError.value, 'error')
  } finally {
    submitting.value = false
  }
}

const emptyHoldings = computed(() => !asyncData.loading.value && !asyncData.error.value && holdings.value.length === 0)
</script>

<template>
  <SheetFrame
    title="记分红"
    subtitle="先记为待确认；到账或复投在过去面处理，待确认不改变现金与份额"
    @close="closeSheet"
  >
    <EmptyState
      v-if="emptyHoldings"
      title="还没有持仓"
      description="分红要挂在某个持仓下，先添加持仓（或现金账户）再来记分红。"
    >
      <BtnKey variant="secondary" @click="closeSheet">返回</BtnKey>
    </EmptyState>

    <SkeletonRows v-else-if="asyncData.loading.value && !asyncData.data.value" :rows="4" />
    <ErrorState
      v-else-if="asyncData.error.value && !asyncData.data.value"
      :message="asyncData.error.value"
      @retry="asyncData.load()"
    />

    <template v-else>
      <SelectField
        v-model="form.holdingId"
        label="持仓"
        :options="holdingOptions"
        placeholder="选择分红对应的持仓"
        :error="errors.holding"
      />
      <TextField
        v-model="form.date"
        label="派息日期"
        inputmode="numeric"
        placeholder="YYYY-MM-DD"
        :maxlength="10"
        :error="errors.date"
        hint="分红实际派息日期，格式 YYYY-MM-DD"
      />
      <TextField
        v-model="form.amount"
        label="分红金额"
        inputmode="decimal"
        placeholder="0.00"
        suffix="元"
        :maxlength="18"
        :error="errors.amount"
        hint="本次实际收到的现金分红（税前/税后按你自己的记录口径填）"
      />
      <TextField
        v-model="form.description"
        label="说明（可选）"
        placeholder="如：2026 年中期分红"
        :maxlength="64"
        hint="会显示在过去面的事件行里"
      />

      <div class="preview">
        <div class="preview-head">
          <span class="label">记录后状态</span>
          <StatusPill tone="warn">待确认</StatusPill>
        </div>
        <DataRow label="持仓" :value="selectedLabel" />
        <DataRow
          label="派息日期"
          :value="errors.date ? form.date : `${form.date} ${weekdayZH(form.date)}`"
        />
        <DataRow label="分红金额" :value="amountValid ? fmtMoney(amountValue) : '—'" />
        <p v-if="submitError" class="submit-error" role="alert">⚠ {{ submitError }}</p>
        <p class="note micro">
          待确认不改变现金与份额。到账后在过去面点该事件「标记到账」；若该持仓开启分红再投，到账时会按最新净值自动复投。
        </p>
      </div>
    </template>

    <template #footer>
      <BtnKey variant="secondary" block :disabled="submitting" @click="closeSheet">取消</BtnKey>
      <BtnKey
        variant="primary"
        block
        :disabled="submitting || emptyHoldings || Boolean(asyncData.loading.value)"
        @click="submit"
      >
        {{ submitting ? '提交中…' : '记为待确认' }}
      </BtnKey>
    </template>
  </SheetFrame>
</template>

<style scoped>
.preview {
  margin-top: var(--space-5);
  border-top: 1px solid var(--hairline);
  padding-top: var(--space-3);
}
.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  min-height: 32px;
}
.submit-error {
  margin-top: var(--space-2);
  font-size: var(--text-label);
  color: var(--danger);
  line-height: 1.5;
}
.note {
  margin-top: var(--space-3);
  line-height: 1.6;
}
</style>
