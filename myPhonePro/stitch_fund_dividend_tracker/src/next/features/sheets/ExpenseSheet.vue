<script setup lang="ts">
/**
 * 生活支出项 Sheet：管理「分红覆盖生活支出」的分母。
 * 列出已有支出（名称 / 月金额 / 删除），并可直接添加。
 * 删除是破坏性动作：二次确认 + 图标 + 动词；跨面数据用 refreshData 通知未来面重算覆盖进度。
 */
import { computed, reactive, ref } from 'vue'
import {
  createExpense,
  deleteExpense,
  listExpenses,
  type LiveExpenseItem,
} from '@/api/expense'
import SheetFrame from '@/next/shared/components/SheetFrame.vue'
import SectionLabel from '@/next/shared/components/SectionLabel.vue'
import SeamDivider from '@/next/shared/components/SeamDivider.vue'
import TextField from '@/next/shared/components/TextField.vue'
import BtnKey from '@/next/shared/components/BtnKey.vue'
import IconKey from '@/next/shared/components/IconKey.vue'
import EmptyState from '@/next/shared/components/EmptyState.vue'
import ErrorState from '@/next/shared/components/ErrorState.vue'
import SkeletonRows from '@/next/shared/components/SkeletonRows.vue'
import { useAsyncData, errorText } from '@/next/shared/useAsync'
import { closeSheet, refreshData, toast } from '@/next/shared/ui'
import { fmtMoney, toNumber } from '@/next/shared/money'

const asyncData = useAsyncData<LiveExpenseItem[]>(async () => listExpenses())

const expenses = computed(() => asyncData.data.value ?? [])
const monthlyTotal = computed(() =>
  expenses.value.reduce((sum, item) => sum + toNumber(item.monthlyAmount), 0),
)

/** 后端 icon 是字符串枚举（seed 用 home/utensils）；这里给预设 + 中文标签，不引图标库 */
const ICON_CHOICES = [
  { value: 'home', label: '居住' },
  { value: 'utensils', label: '餐饮' },
  { value: 'car', label: '交通' },
  { value: 'heart', label: '医疗' },
  { value: 'book', label: '教育' },
  { value: 'repeat', label: '订阅' },
] as const

function iconLabel(icon: string | null | undefined): string {
  if (!icon) return '其他'
  return ICON_CHOICES.find((item) => item.value === icon)?.label ?? '其他'
}

function iconBadge(icon: string | null | undefined): string {
  return iconLabel(icon).slice(0, 1)
}

const name = ref('')
const amountText = ref('')
const icon = ref<string>('home')
const errors = reactive({ name: '', amount: '' })
const submitting = ref(false)

function validate(): boolean {
  errors.name = name.value.trim() ? '' : '请输入支出名称'
  const amount = Number(amountText.value)
  if (!amountText.value.trim()) errors.amount = '请输入每月金额'
  else if (!Number.isFinite(amount) || amount <= 0) errors.amount = '每月金额必须大于 0'
  else errors.amount = ''
  return !errors.name && !errors.amount
}

async function addExpense(): Promise<void> {
  if (submitting.value || !validate()) return
  submitting.value = true
  try {
    await createExpense({
      name: name.value.trim(),
      icon: icon.value,
      monthlyAmount: Number(amountText.value),
    })
    toast('已添加支出项')
    refreshData()
    name.value = ''
    amountText.value = ''
    icon.value = 'home'
    errors.name = ''
    errors.amount = ''
  } catch (e) {
    toast(errorText(e), 'error')
  } finally {
    submitting.value = false
  }
}

const confirmId = ref<string | null>(null)
const deletingId = ref<string | null>(null)

async function removeExpense(item: LiveExpenseItem): Promise<void> {
  if (deletingId.value) return
  deletingId.value = item.id
  try {
    await deleteExpense(item.id)
    toast(`已删除「${item.name}」`)
    refreshData()
    confirmId.value = null
  } catch (e) {
    toast(errorText(e), 'error')
  } finally {
    deletingId.value = null
  }
}
</script>

<template>
  <SheetFrame title="生活支出项" subtitle="每月固定支出，用来计算「预测分红覆盖生活支出」的进度" @close="closeSheet">
    <SkeletonRows v-if="asyncData.loading.value && !asyncData.data.value" :rows="3" />
    <ErrorState
      v-else-if="asyncData.error.value && !asyncData.data.value"
      :message="asyncData.error.value"
      @retry="asyncData.load()"
    />
    <template v-else-if="asyncData.data.value">
      <SectionLabel text="现有支出">
        <template #action>
          <span class="total"><span class="micro">合计</span> <b class="num total-amount">{{ fmtMoney(monthlyTotal) }}</b><span class="micro">/ 月</span></span>
        </template>
      </SectionLabel>

      <template v-if="expenses.length">
        <div v-for="item in expenses" :key="item.id" class="expense-block">
          <div class="expense-row">
            <span class="badge" aria-hidden="true">{{ iconBadge(item.icon) }}</span>
            <div class="expense-main">
              <b class="expense-name">{{ item.name }}</b>
              <span class="micro">{{ iconLabel(item.icon) }} · 月支出</span>
            </div>
            <span class="expense-amount num">{{ fmtMoney(item.monthlyAmount) }}</span>
            <IconKey icon="trash" :label="`删除「${item.name}」`" @click="confirmId = item.id" />
          </div>
          <div v-if="confirmId === item.id" class="confirm" role="alert" aria-live="assertive">
            <p class="confirm-title">删除「{{ item.name }}」？</p>
            <p class="confirm-text micro">删除后不再计入年支出与覆盖进度，不可恢复。</p>
            <div class="confirm-acts">
              <BtnKey variant="secondary" :disabled="deletingId === item.id" @click="confirmId = null">取消</BtnKey>
              <BtnKey variant="danger" :disabled="deletingId === item.id" @click="removeExpense(item)">
                {{ deletingId === item.id ? '删除中…' : '确认删除' }}
              </BtnKey>
            </div>
          </div>
        </div>
      </template>
      <EmptyState
        v-else
        compact
        title="还没有支出项"
        description="添加房租、餐饮等月度支出后，未来面会显示预测分红的覆盖进度。"
      />

      <SeamDivider />

      <SectionLabel text="添加支出项" />
      <TextField
        v-model="name"
        label="名称"
        placeholder="例如 房租"
        :maxlength="20"
        :error="errors.name"
      />
      <TextField
        v-model="amountText"
        label="每月金额"
        type="text"
        inputmode="decimal"
        suffix="元 / 月"
        placeholder="例如 3000"
        :error="errors.amount"
      />

      <div class="field-block">
        <p class="field-label">分类图标</p>
        <div class="chips" role="group" aria-label="支出分类">
          <button
            v-for="choice in ICON_CHOICES"
            :key="choice.value"
            class="chip"
            :class="{ 'chip--on': icon === choice.value }"
            type="button"
            :aria-pressed="icon === choice.value"
            @click="icon = choice.value"
          >
            <span v-if="icon === choice.value" class="chip-mark" aria-hidden="true">✓</span>
            {{ choice.label }}
          </button>
        </div>
      </div>

      <BtnKey variant="primary" block :disabled="submitting" @click="addExpense">
        {{ submitting ? '添加中…' : '添加支出项' }}
      </BtnKey>

      <p class="note micro">年支出 = 月金额 × 12；覆盖进度只使用「预测分红」，不是已到账分红。</p>
      <div class="sheet-pad" />
    </template>
  </SheetFrame>
</template>

<style scoped>
.total {
  display: inline-flex;
  align-items: baseline;
  gap: var(--space-1);
}
.total-amount {
  font-size: var(--text-num-sm);
  font-weight: 500;
  color: var(--ink);
  white-space: nowrap;
}
.expense-block {
  border-bottom: 1px solid var(--hairline);
}
.expense-block:last-of-type {
  border-bottom: 0;
}
.expense-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-height: 56px;
  padding: var(--space-2) 0;
}
.badge {
  width: 32px;
  height: 32px;
  flex: none;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: var(--text-label);
  font-weight: 600;
  color: var(--ink-2);
  background: var(--surface-inset);
  box-shadow: var(--in-slot);
}
.expense-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.expense-name {
  font-size: var(--text-body-sm);
  font-weight: 600;
  color: var(--ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.expense-amount {
  font-size: var(--text-num-sm);
  font-weight: 500;
  color: var(--ink);
  white-space: nowrap;
}
.expense-row :deep(.icon-key) {
  color: var(--danger);
  flex: none;
}
.confirm {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-3);
  margin-bottom: var(--space-3);
  border-radius: var(--radius-key);
  background: var(--surface-1);
  border: 1px solid rgba(166, 27, 27, 0.28);
}
.confirm-title {
  font-size: var(--text-body-sm);
  font-weight: 600;
  color: var(--ink);
}
.confirm-text {
  color: var(--ink-2);
  line-height: 1.5;
}
.confirm-acts {
  display: flex;
  gap: var(--space-2);
}
.confirm-acts :deep(.btn) {
  flex: 1;
}
.field-block {
  margin-bottom: var(--space-4);
}
.field-label {
  font-size: var(--text-label);
  font-weight: 500;
  color: var(--ink-2);
  margin-bottom: var(--space-2);
}
.chips {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-2);
}
.chip {
  min-height: 44px;
  border-radius: var(--radius-key);
  border: 1px solid var(--edge);
  background: var(--canvas);
  box-shadow: var(--el-key);
  font-size: var(--text-label);
  font-weight: 500;
  color: var(--ink-2);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 0 4px;
}
.chip--on {
  box-shadow: var(--press);
  color: var(--accent);
  font-weight: 600;
  border-color: rgba(46, 75, 198, 0.35);
}
.chip-mark {
  font-size: 11px;
}
.chip:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.note {
  color: var(--ink-3);
  line-height: 1.5;
  margin-top: var(--space-3);
}
.sheet-pad {
  height: var(--space-2);
}
</style>
