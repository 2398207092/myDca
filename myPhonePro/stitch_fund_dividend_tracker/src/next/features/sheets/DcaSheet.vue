<script setup lang="ts">
/**
 * 定投计划 Sheet：新建 / 编辑。
 * - 扣款账户必填（后端 CreateDcaPlanReq.accountId 为 @NotBlank）；
 * - 标的不支持改（后端 UpdateDcaPlanReq 无 holdingId，换标的要删除后新建）；
 * - 编辑态支持「立即执行一次」（二次确认，重复已保存的金额与扣款账户）与「删除计划」（danger 二次确认）。
 * 金额一律走 shared/money，不自己拼 ¥。
 */
import { computed, reactive, ref, watch } from 'vue'
import {
  createDcaPlan,
  deleteDcaPlan,
  executeDcaPlan,
  updateDcaPlan,
  type DcaPlanVO,
} from '@/api/dca'
import { listHoldings, type HoldingItem } from '@/api/holding'
import { getAccounts, type AccountItem } from '@/api/account'
import SheetFrame from '@/next/shared/components/SheetFrame.vue'
import SelectField from '@/next/shared/components/SelectField.vue'
import TextField from '@/next/shared/components/TextField.vue'
import BtnKey from '@/next/shared/components/BtnKey.vue'
import StatusPill from '@/next/shared/components/StatusPill.vue'
import SkeletonRows from '@/next/shared/components/SkeletonRows.vue'
import ErrorState from '@/next/shared/components/ErrorState.vue'
import { useAsyncData, errorText } from '@/next/shared/useAsync'
import { closeSheet, refreshData, toast } from '@/next/shared/ui'
import { fmtMoney, fmtNumber } from '@/next/shared/money'
import { formatMonthDay } from '@/next/shared/date'
import { DCA_STATUS_LABEL, labelOf } from '@/next/shared/dict'
import { FREQUENCY_CHOICES, scheduleText, weekdayName } from '@/next/features/future/dcaText'

const props = defineProps<{ plan?: DcaPlanVO }>()

interface DcaFormData {
  holdings: HoldingItem[]
  accounts: AccountItem[]
}

const asyncData = useAsyncData<DcaFormData>(async () => {
  const [holdings, accounts] = await Promise.all([listHoldings(), getAccounts()])
  return { holdings, accounts }
})

const formReady = computed(() => Boolean(asyncData.data.value))
const holdings = computed(() => asyncData.data.value?.holdings ?? [])
const accounts = computed(() => asyncData.data.value?.accounts ?? [])

const plan = computed(() => props.plan ?? null)
const isEdit = computed(() => Boolean(plan.value))

/** 频率默认扣款日：每月 8 日 / 每周一（1） */
function defaultDayFor(frequency: string): string {
  if (frequency === 'monthly') return '8'
  if (frequency === 'daily') return ''
  return '1'
}

const holdingId = ref(props.plan?.holdingId ?? '')
const accountId = ref(props.plan?.accountId ?? '')
const amountText = ref(props.plan?.amount !== null && props.plan?.amount !== undefined ? String(props.plan.amount) : '')
const frequency = ref(props.plan?.frequency ?? 'monthly')
const dayText = ref(props.plan?.day !== null && props.plan?.day !== undefined ? String(props.plan.day) : defaultDayFor(frequency.value))

const errors = reactive({ holding: '', account: '', amount: '', day: '' })

// 用户开始修正时立即撤掉旧错误提示（错误靠近字段，不留在页面上误导）
watch(holdingId, () => { errors.holding = '' })
watch(accountId, () => { errors.account = '' })
watch(amountText, () => { errors.amount = '' })
watch(dayText, () => { errors.day = '' })

const selectedHolding = computed(() => holdings.value.find((h) => h.id === holdingId.value) ?? null)
const isCrypto = computed(() => selectedHolding.value?.type === 'crypto')
/** crypto 计划的每期金额单位是 USDT，其余是元 */
const currencyText = computed(() => (isCrypto.value ? 'USDT' : '元'))

const holdingOptions = computed(() =>
  holdings.value.map((h) => ({ value: h.id, label: `${h.name}（${h.code}）` })),
)

/**
 * 可选的扣款账户：后端会校验账户本位币与资产类型匹配
 * （USDT 账户只接加密币，CNY 账户只接人民币资产），这里提前过滤，避免用户提交后才报错。
 */
const eligibleAccounts = computed(() => {
  if (!selectedHolding.value) return accounts.value
  const wantUsdt = isCrypto.value
  return accounts.value.filter((a) => (a.baseCurrency === 'USDT') === wantUsdt)
})

const accountOptions = computed(() =>
  eligibleAccounts.value.map((a) => ({ value: a.id, label: `${a.name}（${a.baseCurrency}）` })),
)

/** 选中持仓后自动带出该持仓归属账户（不可用时退回第一个同本位币账户） */
watch([holdingId, () => asyncData.data.value], () => {
  const list = eligibleAccounts.value
  if (!accountId.value || !list.some((a) => a.id === accountId.value)) {
    const preferred = selectedHolding.value?.accountId
    const picked = preferred && list.some((a) => a.id === preferred) ? preferred : list[0]?.id
    accountId.value = picked ?? ''
  }
})

const QUICK_AMOUNTS = [500, 1000, 2000]

function applyQuickAmount(value: number): void {
  amountText.value = String(value)
  errors.amount = ''
}

function setFrequency(value: string): void {
  frequency.value = value
  errors.day = ''
  if (value === 'daily') {
    dayText.value = ''
    return
  }
  const current = Number(dayText.value)
  const max = value === 'monthly' ? 28 : 7
  if (!Number.isInteger(current) || current < 1 || current > max) dayText.value = defaultDayFor(value)
}

const showDay = computed(() => frequency.value !== 'daily')
const dayLabel = computed(() => (frequency.value === 'monthly' ? '扣款日（每月几号）' : '扣款日（周几）'))
const dayHint = computed(() => {
  if (!showDay.value) return ''
  if (frequency.value === 'monthly') return '1–28；当月不足该日按月末处理，遇非交易日顺延'
  const day = Number(dayText.value)
  const weekday = Number.isInteger(day) && day >= 1 && day <= 7 ? weekdayName(day) : ''
  return `1 = 周一 … 7 = 周日${weekday ? `（当前为${weekday}）` : ''}；遇非交易日顺延`
})

const schedulePreview = computed(() => {
  if (frequency.value === 'daily') return '每日（仅交易日执行）'
  const day = Number(dayText.value)
  if (!Number.isInteger(day)) return ''
  return scheduleText(frequency.value, day)
})

const planStatusText = computed(() => labelOf(DCA_STATUS_LABEL, plan.value?.status))
const planStatusTone = computed<'accent' | 'warn' | 'muted'>(() => {
  if (plan.value?.status === 'active') return 'accent'
  if (plan.value?.status === 'paused') return 'warn'
  return 'muted'
})

/** 已保存计划里的一次性执行口径（立即执行用的是已保存值，不是表单里未保存的修改） */
const planAmountText = computed(() => {
  const p = plan.value
  if (!p) return ''
  return p.tradingMarket === 'crypto' ? `${fmtNumber(p.amount, 2)} USDT` : fmtMoney(p.amount)
})
const planAccountText = computed(() => {
  const id = plan.value?.accountId
  if (!id) return '未关联（按持仓归属账户扣款）'
  return accounts.value.find((a) => a.id === id)?.name ?? '已关联扣款账户'
})

/** 表单与已保存计划是否有差异：立即执行前提醒「未保存的修改不生效」 */
const formDirty = computed(() => {
  const p = plan.value
  if (!p) return false
  if (holdingId.value !== p.holdingId) return true
  if (accountId.value !== (p.accountId ?? '')) return true
  if (Number(amountText.value) !== Number(p.amount)) return true
  if (frequency.value !== p.frequency) return true
  if (frequency.value !== 'daily' && Number(dayText.value) !== Number(p.day ?? -1)) return true
  return false
})

function validate(): boolean {
  errors.holding = holdingId.value ? '' : '请选择定投持仓'
  errors.account = accountId.value ? '' : '请选择扣款账户（必填）'

  const amount = Number(amountText.value)
  if (!amountText.value.trim()) errors.amount = '请输入每期金额'
  else if (!Number.isFinite(amount) || amount <= 0) errors.amount = '金额必须大于 0'
  else if (amount < 1) errors.amount = '每期金额不能小于 1'
  else errors.amount = ''

  errors.day = ''
  if (frequency.value !== 'daily') {
    const day = Number(dayText.value)
    if (!dayText.value.trim()) errors.day = '请输入扣款日'
    else if (!Number.isInteger(day) || day < 1) errors.day = '扣款日必须为整数'
    else if (frequency.value === 'monthly' && day > 28) errors.day = '每月扣款日需在 1–28 之间'
    else if (frequency.value !== 'monthly' && day > 7) errors.day = '周几需在 1–7 之间'
  }
  return !errors.holding && !errors.account && !errors.amount && !errors.day
}

const submitting = ref(false)

async function save(): Promise<void> {
  if (submitting.value) return
  if (!validate()) return
  const saved = plan.value
  const payload = {
    accountId: accountId.value,
    amount: Number(amountText.value),
    frequency: frequency.value,
    day: frequency.value === 'daily' ? null : Number(dayText.value),
  }
  submitting.value = true
  try {
    if (saved) await updateDcaPlan(saved.id, payload)
    else await createDcaPlan({ holdingId: holdingId.value, ...payload })
    toast(saved ? '已更新定投计划' : '已创建定投计划')
    refreshData()
    closeSheet()
  } catch (e) {
    toast(errorText(e), 'error')
  } finally {
    submitting.value = false
  }
}

const confirmKind = ref<'execute' | 'delete' | null>(null)
const acting = ref(false)

function openConfirm(kind: 'execute' | 'delete'): void {
  if (acting.value || submitting.value) return
  confirmKind.value = kind
}

async function doExecute(): Promise<void> {
  const saved = plan.value
  if (!saved || acting.value) return
  acting.value = true
  try {
    const result = await executeDcaPlan(saved.id)
    const warned = Boolean(result.deductionInfo?.startsWith('⚠'))
    toast(
      warned
        ? (result.deductionInfo ?? '执行完成，但扣款异常，请核对扣款账户')
        : '已执行本期定投，已写入一笔买入交易',
      warned ? 'error' : 'ok',
    )
    refreshData()
    closeSheet()
  } catch (e) {
    toast(errorText(e), 'error')
  } finally {
    acting.value = false
    confirmKind.value = null
  }
}

async function doDelete(): Promise<void> {
  const saved = plan.value
  if (!saved || acting.value) return
  acting.value = true
  try {
    await deleteDcaPlan(saved.id)
    toast('已删除定投计划')
    refreshData()
    closeSheet()
  } catch (e) {
    toast(errorText(e), 'error')
  } finally {
    acting.value = false
    confirmKind.value = null
  }
}
</script>

<template>
  <SheetFrame
    :title="isEdit ? '编辑定投计划' : '新建定投计划'"
    :subtitle="
      isEdit
        ? '金额、频率、扣款日与扣款账户可改；标的不支持更换'
        : '创建后按交易日历自动执行，扣款账户必填'
    "
    @close="closeSheet"
  >
    <SkeletonRows v-if="asyncData.loading.value && !asyncData.data.value" :rows="4" />
    <ErrorState
      v-else-if="asyncData.error.value && !asyncData.data.value"
      :message="asyncData.error.value"
      @retry="asyncData.load()"
    />
    <template v-else-if="formReady">
      <!-- 编辑态：已保存计划的现状（立即执行以此为准） -->
      <div v-if="plan" class="plan-head">
        <div class="head-line">
          <b class="plan-name">{{ plan.holdingName || plan.holdingCode }}</b>
          <StatusPill :tone="planStatusTone">{{ planStatusText }}</StatusPill>
        </div>
        <p class="micro">
          累计已投 <b class="num head-amount">{{ fmtMoney(plan.totalInvested) }}</b> · 已投 {{ plan.totalExecutions ?? 0 }} 期
          <span v-if="plan.lastExecutedAt"> · 最近 {{ formatMonthDay(String(plan.lastExecutedAt).slice(0, 10)) }}</span>
        </p>
      </div>

      <SelectField
        v-model="holdingId"
        label="定投持仓"
        :options="holdingOptions"
        :disabled="isEdit"
        :error="errors.holding"
      />
      <p v-if="!holdingOptions.length" class="field-note micro">
        还没有持仓，请先在「记一笔 → 买入」添加持仓。
      </p>
      <p v-else-if="isEdit" class="field-note micro">标的不支持修改；要换标的请删除本计划后新建。</p>

      <SelectField
        v-model="accountId"
        label="扣款账户（必填）"
        :options="accountOptions"
        :error="errors.account"
      />
      <p v-if="selectedHolding && !eligibleAccounts.length" class="field-note micro">
        该持仓类型下没有可用扣款账户（需 {{ isCrypto ? 'USDT' : 'CNY' }} 本位币），请先在「设置 → 账户」创建。
      </p>
      <p v-else class="field-note micro">
        执行时从该账户扣{{ isCrypto ? ' USDT' : '人民币现金' }}；账户本位币与持仓类型不匹配会被后端拒绝。
      </p>

      <TextField
        v-model="amountText"
        label="每期金额"
        type="text"
        inputmode="decimal"
        :suffix="currencyText"
        :error="errors.amount"
        placeholder="例如 1000"
      />
      <div class="quick-row">
        <button
          v-for="value in QUICK_AMOUNTS"
          :key="value"
          class="quick-key"
          type="button"
          @click="applyQuickAmount(value)"
        >
          +{{ value }}
        </button>
      </div>

      <div class="field-block">
        <p class="field-label">频率</p>
        <div class="chips" role="group" aria-label="定投频率">
          <button
            v-for="item in FREQUENCY_CHOICES"
            :key="item.value"
            class="chip"
            :class="{ 'chip--on': frequency === item.value }"
            type="button"
            :aria-pressed="frequency === item.value"
            @click="setFrequency(item.value)"
          >
            <span v-if="frequency === item.value" class="chip-mark" aria-hidden="true">✓</span>
            {{ item.label }}
          </button>
        </div>
      </div>

      <TextField
        v-if="showDay"
        v-model="dayText"
        :label="dayLabel"
        type="text"
        inputmode="numeric"
        :maxlength="2"
        :hint="dayHint"
        :error="errors.day"
        placeholder="1"
      />

      <p v-if="schedulePreview" class="schedule-preview micro">
        当前设置：{{ schedulePreview }}（实际执行日由后端按交易日历计算）
      </p>

      <p v-if="plan && plan.status !== 'active'" class="status-hint micro">
        {{
          plan.status === 'paused'
            ? '计划已暂停：回未来面打开开关恢复后，才能「立即执行一次」。'
            : '计划已结束：不能执行；如需继续定投请删除后重建。'
        }}
      </p>

      <div class="sheet-pad" />
    </template>

    <template #footer>
      <div v-if="formReady" class="footer">
        <div v-if="confirmKind" class="confirm" role="alert" aria-live="assertive">
          <template v-if="confirmKind === 'execute'">
            <p class="confirm-title">立即执行一期定投？</p>
            <p class="confirm-text">
              买入 <b>{{ plan?.holdingName }}</b>，金额 <b class="num confirm-amount">{{ planAmountText }}</b>，从「<b>{{ planAccountText }}</b>」扣款。
            </p>
            <p v-if="formDirty" class="confirm-warn micro">表单里尚未保存的修改不会用于本次执行。</p>
            <p class="micro">按最新净值/实时价成交，执行后写入一笔买入交易；今日已执行过会被后端拒绝。</p>
            <div class="confirm-acts">
              <BtnKey variant="secondary" :disabled="acting" @click="confirmKind = null">取消</BtnKey>
              <BtnKey variant="primary" :disabled="acting" @click="doExecute">
                {{ acting ? '执行中…' : '确认执行' }}
              </BtnKey>
            </div>
          </template>
          <template v-else>
            <p class="confirm-title">删除这个定投计划？</p>
            <p class="confirm-text">
              「<b>{{ plan?.holdingName }}</b>」删除后不可恢复；已生成的交易记录会保留。
            </p>
            <div class="confirm-acts">
              <BtnKey variant="secondary" :disabled="acting" @click="confirmKind = null">取消</BtnKey>
              <BtnKey variant="danger" :disabled="acting" @click="doDelete">
                {{ acting ? '删除中…' : '确认删除' }}
              </BtnKey>
            </div>
          </template>
        </div>
        <template v-else>
          <BtnKey variant="primary" block :disabled="submitting" @click="save">
            {{ submitting ? '保存中…' : isEdit ? '保存修改' : '创建定投计划' }}
          </BtnKey>
          <div v-if="isEdit" class="footer-row">
            <BtnKey
              variant="secondary"
              :disabled="plan?.status !== 'active' || acting || submitting"
              @click="openConfirm('execute')"
            >
              立即执行一次
            </BtnKey>
            <BtnKey variant="danger" :disabled="acting || submitting" @click="openConfirm('delete')">
              删除计划
            </BtnKey>
          </div>
        </template>
      </div>
    </template>
  </SheetFrame>
</template>

<style scoped>
.plan-head {
  padding: var(--space-2) 0 var(--space-4);
  border-bottom: 1px solid var(--hairline);
  margin-bottom: var(--space-4);
}
.head-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-bottom: var(--space-1);
}
.plan-name {
  font-size: var(--text-heading);
  font-weight: 600;
  color: var(--ink);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.head-amount {
  font-size: var(--text-num-sm);
  font-weight: 500;
  color: var(--ink);
}
.field-note {
  color: var(--ink-3);
  line-height: 1.5;
  margin: calc(var(--space-2) * -1) 0 var(--space-4);
}
.quick-row {
  display: flex;
  gap: var(--space-2);
  margin: calc(var(--space-2) * -1) 0 var(--space-4);
}
.quick-key {
  flex: 1;
  min-height: 44px;
  border-radius: var(--radius-key);
  border: 1px solid var(--edge);
  background: var(--canvas);
  box-shadow: var(--el-key);
  font-size: var(--text-label);
  font-weight: 600;
  color: var(--ink-2);
}
.quick-key:active {
  box-shadow: var(--press);
  color: var(--accent);
}
.quick-key:focus-visible,
.chip:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
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
  grid-template-columns: repeat(4, 1fr);
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
.schedule-preview {
  color: var(--ink-2);
  line-height: 1.5;
  margin-top: calc(var(--space-2) * -1);
  margin-bottom: var(--space-4);
}
.status-hint {
  color: var(--warn);
  line-height: 1.5;
  margin-bottom: var(--space-3);
}
.sheet-pad {
  height: var(--space-2);
}
.footer {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  width: 100%;
}
.footer-row {
  display: flex;
  gap: var(--space-2);
}
.footer-row :deep(.btn) {
  flex: 1;
}
.confirm {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-3);
  border-radius: var(--radius-key);
  background: var(--surface-1);
  border: 1px solid var(--edge);
  margin-bottom: var(--space-1);
}
.confirm-title {
  font-size: var(--text-body-sm);
  font-weight: 600;
  color: var(--ink);
}
.confirm-text {
  font-size: var(--text-label);
  color: var(--ink-2);
  line-height: 1.55;
}
.confirm-text b {
  color: var(--ink);
  font-weight: 600;
}
.confirm-amount {
  font-size: var(--text-num-sm);
  font-weight: 600;
  color: var(--ink);
  white-space: nowrap;
}
.confirm-warn {
  color: var(--warn);
  line-height: 1.5;
}
.confirm-acts {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-1);
}
.confirm-acts :deep(.btn) {
  flex: 1;
}
</style>
