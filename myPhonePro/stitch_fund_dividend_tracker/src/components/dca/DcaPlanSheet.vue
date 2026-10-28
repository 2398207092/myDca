<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { createDcaPlan, updateDcaPlan, type DcaPlanVO } from '@/api/dca'
import { getAccounts, type AccountItem } from '@/api/account'

interface Props {
  holdingId: string
  holdingName: string
  holdingCode: string
  tradingMarket: string
  /** 传入计划则为「编辑」模式；不传（或传 null）为「创建」模式 */
  plan?: DcaPlanVO | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  close: []
  /** 创建成功或编辑保存成功后触发 */
  saved: []
}>()

const isEdit = computed(() => !!props.plan?.id)
/** 加密币：金额按 USDT 计，365 天均可投 */
const isCrypto = computed(() => props.tradingMarket === 'crypto')

type Frequency = 'daily' | 'weekly' | 'biweekly' | 'monthly'

const amount = ref<number>(50)
const frequency = ref<Frequency>('daily')
const day = ref<number | null>(null)
const submitting = ref(false)
const error = ref('')

/** 扣款账户 */
const accounts = ref<AccountItem[]>([])
const accountId = ref<string>('')
/** 本位币匹配：加密币只扣 USDT 账户，基金/黄金只扣 CNY 账户（与后端 matchesAssetType 同一条规则） */
const matchingAccounts = computed(() =>
  accounts.value.filter((a) => isCrypto.value ? a.baseCurrency === 'USDT' : a.baseCurrency !== 'USDT')
)
// 账户列表异步到达后，若还没选则默认选中第一个匹配账户（账户为必选）
watch(matchingAccounts, (list) => {
  if (!accountId.value && list.length) accountId.value = list[0].id
}, { immediate: true })
onMounted(async () => {
  try {
    accounts.value = await getAccounts()
  } catch {
    /* 账户拉取失败时提交会被必选校验拦住 */
  }
})

const frequencyOptions: { value: Frequency; label: string }[] = [
  { value: 'daily', label: '每日' },
  { value: 'weekly', label: '每周' },
  { value: 'biweekly', label: '双周' },
  { value: 'monthly', label: '每月' },
]

const showDaySelector = computed(() => frequency.value !== 'daily')

const marketLabel = computed(() => {
  switch (props.tradingMarket) {
    case 'us': return '美股'
    case 'china': return '中国A股'
    case 'crypto': return '数字货币'
    default: return props.tradingMarket
  }
})

const weekDays = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' },
]

/** 用当前计划值预填（编辑），或重置为默认值（创建） */
function initFromPlan() {
  const p = props.plan
  if (p && p.id) {
    amount.value = p.amount
    frequency.value = (p.frequency as Frequency) || 'daily'
    day.value = p.day ?? null
    accountId.value = p.accountId || ''
  } else {
    amount.value = 50
    frequency.value = 'daily'
    day.value = null
    accountId.value = matchingAccounts.value[0]?.id || ''
  }
  error.value = ''
}

initFromPlan()
watch(() => props.plan?.id, initFromPlan)

function pickFrequency(v: Frequency) {
  frequency.value = v
  if (v === 'daily') day.value = null
}

async function handleSubmit() {
  if (!amount.value || amount.value < 1) {
    error.value = '请输入有效的金额'
    return
  }
  if (!accountId.value) {
    error.value = '请选择扣款账户（资产必须归属账户，否则现金账会失真）'
    return
  }

  if (frequency.value !== 'daily') {
    if (day.value === null || day.value === undefined || isNaN(day.value)) {
      error.value = frequency.value === 'monthly' ? '请输入每月扣款日' : '请选择扣款日'
      return
    }
    if (frequency.value === 'monthly' && (day.value < 1 || day.value > 31)) {
      error.value = '每月扣款日需在 1~31 之间'
      return
    }
  }

  submitting.value = true
  error.value = ''

  const payload = {
    amount: amount.value,
    frequency: frequency.value,
    day: frequency.value === 'daily' ? null : day.value,
    accountId: accountId.value || '',
  }

  try {
    if (isEdit.value && props.plan) {
      await updateDcaPlan(props.plan.id, payload)
    } else {
      await createDcaPlan({ holdingId: props.holdingId, ...payload })
    }
    emit('saved')
    emit('close')
  } catch (e: any) {
    error.value = e.message || (isEdit.value ? '保存失败，请重试' : '创建失败，请重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="bg-card-bg rounded-2xl px-xl py-lg shadow-overlay w-full max-w-sm mx-auto" style="max-height: 85vh; overflow-y: auto;">

    <h3 class="font-body text-md font-medium text-text-primary mb-1">
      {{ isEdit ? '编辑定投计划' : '创建定投计划' }}
    </h3>
    <p class="font-body text-sm text-text-secondary mb-lg">{{ holdingName }} ({{ holdingCode }})</p>

    <!-- Amount：单位放右侧后缀，避免与数字重叠 -->
    <label class="font-body text-xs text-text-tertiary mb-sm block">
      {{ isCrypto ? '每期金额 (USDT)' : '每期金额 (元)' }}
    </label>
    <div class="relative mb-lg">
      <input
        v-model.number="amount"
        type="number"
        step="1"
        min="1"
        placeholder="50"
        class="w-full h-12 rounded-xl bg-card-alt px-md pr-16 text-text-primary font-body text-sm outline-none transition-colors focus:ring-2 focus:ring-brand"
      />
      <span class="absolute right-md top-1/2 -translate-y-1/2 text-text-tertiary font-body text-xs">{{ isCrypto ? 'USDT' : '元' }}</span>
      <p v-if="isCrypto" class="font-body text-[11px] text-text-tertiary mt-2 leading-relaxed">
        按 USDT 计量，执行时按当时价格折算成币数并换算人民币计入总资产
      </p>
    </div>

    <!-- Frequency -->
    <label class="font-body text-xs text-text-tertiary mb-sm block">频率</label>
    <div class="flex gap-sm mb-lg">
      <button
        v-for="opt in frequencyOptions"
        :key="opt.value"
        type="button"
        class="flex-1 h-10 rounded-lg font-body font-medium text-sm transition-all"
        :class="frequency === opt.value
          ? 'bg-brand-light text-brand shadow-sm'
          : 'bg-card-alt text-text-secondary hover:bg-card-alt/80'"
        @click="pickFrequency(opt.value)"
      >
        {{ opt.label }}
      </button>
    </div>

    <!-- Day selector (hidden when daily) -->
    <template v-if="showDaySelector">
      <label class="font-body text-xs text-text-tertiary mb-sm block">
        {{ frequency === 'monthly' ? '每月扣款日' : '扣款日' }}
      </label>

      <!-- Weekly / Biweekly: 7 days -->
      <div v-if="frequency === 'weekly' || frequency === 'biweekly'" class="flex gap-1.5 mb-lg">
        <button
          v-for="wd in weekDays"
          :key="wd.value"
          type="button"
          class="flex-1 min-w-[40px] h-9 rounded-lg font-body font-medium text-sm transition-all"
          :class="day === wd.value
            ? 'bg-brand-light text-brand shadow-sm'
            : 'bg-card-alt text-text-secondary hover:bg-card-alt/80'"
          @click="day = wd.value"
        >
          {{ wd.label }}
        </button>
      </div>

      <!-- Monthly: number input -->
      <div v-else class="relative mb-lg">
        <span class="absolute left-md top-1/2 -translate-y-1/2 text-text-tertiary font-body text-md">日</span>
        <input
          v-model.number="day"
          type="number"
          min="1"
          max="31"
          step="1"
          placeholder="15"
          class="w-full h-12 rounded-xl bg-card-alt pl-10 pr-md text-text-primary font-body text-sm outline-none transition-colors focus:ring-2 focus:ring-brand"
          @keydown="(e: KeyboardEvent) => {
            if (['e', 'E', '+', '-', '.'].includes(e.key)) e.preventDefault()
          }"
        />
      </div>
    </template>

    <!-- 扣款账户：按钮组与全站风格一致；必须关联（不关联=不扣款=现金虚高，已被禁止） -->
    <label class="font-body text-xs text-text-tertiary mb-sm block">扣款账户（必选）</label>
    <div class="flex gap-2 overflow-x-auto no-scrollbar mb-lg">
      <button
        v-for="a in matchingAccounts"
        :key="a.id"
        type="button"
        class="px-3 py-2 rounded-lg border font-body text-xs whitespace-nowrap transition-colors shrink-0"
        :class="accountId === a.id
          ? 'border-brand bg-brand-light/40 text-brand'
          : 'border-border-light/40 bg-card-alt text-text-secondary'"
        @click="accountId = a.id"
      >
        {{ a.name }}{{ a.isDefault ? ' ·默认' : '' }}
      </button>
    </div>
    <p class="font-body text-[11px] text-text-tertiary mb-lg -mt-2">
      {{ isCrypto ? '执行时从该账户的 USDT 余额里扣' : '执行时从该账户的现金余额里扣' }}；余额不足时仍会记账（余额可能为负）
    </p>

    <!-- Read-only info -->
    <div class="bg-card-alt rounded-xl px-md py-sm mb-lg space-y-sm">
      <div class="flex justify-between items-center">
        <span class="font-body text-xs text-text-tertiary">市场</span>
        <span class="font-body text-sm text-text-primary">
          {{ isCrypto ? '加密币 · 365 天均可投' : marketLabel }}
        </span>
      </div>
      <div class="flex justify-between items-center">
        <span class="font-body text-xs text-text-tertiary">开始日期</span>
        <span class="font-body text-sm text-text-primary">
          {{ isEdit && plan ? plan.startedAt : (isCrypto ? '明天起（含周末）' : '明天起第一个交易日') }}
        </span>
      </div>
    </div>

    <!-- 编辑模式提示：标的不可更换 -->
    <p v-if="isEdit" class="font-body text-[11px] text-text-tertiary text-center mb-md leading-relaxed">
      换标的请删除本计划后重新创建，避免累计投入与份额统计串账
    </p>

    <!-- Error -->
    <p v-if="error" class="text-error font-body text-xs mb-sm text-center">{{ error }}</p>

    <!-- Submit -->
    <button
      class="w-full h-12 rounded-xl bg-brand text-white font-body font-medium text-md transition-all active:scale-[0.98] flex items-center justify-center gap-sm disabled:opacity-50"
      :disabled="submitting"
      @click="handleSubmit"
    >
      <span v-if="submitting" class="material-symbols-outlined animate-spin text-lg">progress_activity</span>
      <span>{{ submitting ? (isEdit ? '保存中...' : '创建中...') : (isEdit ? '保存修改' : '确认创建') }}</span>
    </button>
  </div>
</template>
