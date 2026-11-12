<script setup lang="ts">
/**
 * 定投计划创建 / 编辑（Auros 弹层 · 2026-11-08）
 * 外壳：AurosSheet（底部抽屉）；字段：AurosField；分段与账户 chips 用发丝级控件。
 * 对外 props/emits 与旧版完全一致（父级仍用 v-if 控制挂载）；业务字段与校验逻辑一行未改。
 */
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { createDcaPlan, updateDcaPlan, type DcaPlanVO } from '@/api/dca'
import { getAccounts, type AccountItem } from '@/api/account'
import AurosSheet from '@/auros-skin/components/auros/AurosSheet.vue'
import AurosField from '@/auros-skin/components/auros/AurosField.vue'
import AurosRow from '@/auros-skin/components/auros/AurosRow.vue'
import AurosButton from '@/auros-skin/components/auros/AurosButton.vue'

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

/* ---------------- 抽屉开合（仅 UI 态） ---------------- */
const open = ref(false)
let closeTimer: ReturnType<typeof setTimeout> | null = null
onMounted(() => {
  open.value = true
})
onBeforeUnmount(() => {
  if (closeTimer) clearTimeout(closeTimer)
})

/** 等抽屉退出动效（约 320ms）后再通知父级卸载，避免弹层瞬间消失 */
function requestClose() {
  open.value = false
  if (closeTimer) clearTimeout(closeTimer)
  closeTimer = setTimeout(() => emit('close'), 320)
}

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

/* AurosField 只发字符串：这里还原为数字（等价于旧 v-model.number） */
function setAmount(v: string) {
  amount.value = v === '' ? 0 : Number(v)
}
function setDay(v: string) {
  day.value = v === '' ? null : Number(v)
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
    requestClose()
  } catch (e: any) {
    error.value = e.message || (isEdit.value ? '保存失败，请重试' : '创建失败，请重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AurosSheet
    v-model="open"
    :title="isEdit ? '编辑定投计划' : '创建定投计划'"
    @close="requestClose"
  >
    <p class="sub">{{ holdingName }}<i>·</i>{{ holdingCode }}</p>

    <AurosField
      class="fld"
      :model-value="amount"
      label="每期金额"
      :suffix="isCrypto ? 'USDT' : '元'"
      type="number"
      inputmode="decimal"
      placeholder="50"
      mono
      @update:model-value="setAmount"
    />
    <p v-if="isCrypto" class="hint">
      按 USDT 计量，执行时按当时价格折算成币数并换算人民币计入总资产
    </p>

    <p class="cap">频率</p>
    <div class="seg">
      <button
        v-for="opt in frequencyOptions"
        :key="opt.value"
        type="button"
        class="seg__item"
        :class="{ 'is-active': frequency === opt.value }"
        @click="pickFrequency(opt.value)"
      >
        {{ opt.label }}
      </button>
    </div>

    <template v-if="showDaySelector">
      <template v-if="frequency === 'weekly' || frequency === 'biweekly'">
        <p class="cap">扣款日</p>
        <div class="days">
          <button
            v-for="wd in weekDays"
            :key="wd.value"
            type="button"
            class="days__item"
            :class="{ 'is-active': day === wd.value }"
            @click="day = wd.value"
          >
            {{ wd.label }}
          </button>
        </div>
      </template>
      <AurosField
        v-else
        class="fld"
        :model-value="day ?? ''"
        label="每月扣款日"
        suffix="日"
        type="number"
        inputmode="numeric"
        placeholder="15"
        mono
        @update:model-value="setDay"
      />
    </template>

    <p class="cap">扣款账户（必选）</p>
    <div class="accounts">
      <button
        v-for="a in matchingAccounts"
        :key="a.id"
        type="button"
        class="accounts__item"
        :class="{ 'is-active': accountId === a.id }"
        @click="accountId = a.id"
      >
        {{ a.name }}<i v-if="a.isDefault"> ·默认</i>
      </button>
    </div>
    <p class="hint">
      {{ isCrypto ? '执行时从该账户的 USDT 余额里扣' : '执行时从该账户的现金余额里扣' }}；余额不足时仍会记账（余额可能为负）
    </p>

    <div class="readout">
      <AurosRow label="市场" :value="isCrypto ? '加密币 · 365 天均可投' : marketLabel" />
      <AurosRow
        label="开始日期"
        :value="isEdit && plan ? plan.startedAt : (isCrypto ? '明天起（含周末）' : '明天起第一个交易日')"
      />
    </div>

    <p v-if="isEdit" class="hint hint--center">
      换标的请删除本计划后重新创建，避免累计投入与份额统计串账
    </p>

    <p v-if="error" class="err">{{ error }}</p>

    <AurosButton class="submit" block :disabled="submitting" @click="handleSubmit">
      <span v-if="submitting" class="spin" aria-hidden="true"></span>
      <span>{{ submitting ? (isEdit ? '保存中...' : '创建中...') : (isEdit ? '保存修改' : '确认创建') }}</span>
    </AurosButton>
  </AurosSheet>
</template>

<style scoped>
.sub {
  margin: 0 0 18px;
  font-size: 13px;
  letter-spacing: 0.02em;
  color: var(--au-silver);
}
.sub i {
  font-style: normal;
  margin: 0 6px;
  color: rgba(187, 199, 198, 0.4);
}

.hint {
  margin: 8px 0 0;
  font-size: 10px;
  line-height: 1.6;
  letter-spacing: 0.03em;
  color: rgba(187, 199, 198, 0.64);
}
.hint--center {
  margin: 16px 0 0;
  text-align: center;
}
.cap {
  margin: 18px 0 8px;
  font-size: 10px;
  letter-spacing: 0.16em;
  color: var(--au-silver);
}
.fld {
  display: block;
  margin-bottom: 4px;
}

/* 分段：胶囊选中用一档磷光薄片 */
.seg {
  display: flex;
  gap: 4px;
  padding: 3px;
  border-radius: 8px;
  background: rgba(0, 55, 52, 0.55);
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
  transition:
    background 200ms var(--au-ease),
    color 200ms var(--au-ease);
}
.seg__item.is-active {
  background: rgba(203, 255, 252, 0.14);
  color: var(--au-mist);
}

/* 周内扣款日 */
.days {
  display: flex;
  gap: 5px;
}
.days__item {
  flex: 1;
  min-width: 0;
  height: 34px;
  border: 0;
  border-radius: 6px;
  background: rgba(0, 55, 52, 0.55);
  font-family: inherit;
  font-size: 12px;
  letter-spacing: 0;
  color: rgba(187, 199, 198, 0.75);
  transition:
    background 200ms var(--au-ease),
    color 200ms var(--au-ease);
}
.days__item.is-active {
  background: rgba(203, 255, 252, 0.14);
  color: var(--au-mist);
}

/* 扣款账户 */
.accounts {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  scrollbar-width: none;
  -webkit-overflow-scrolling: touch;
}
.accounts::-webkit-scrollbar {
  display: none;
}
.accounts__item {
  flex: none;
  height: 34px;
  padding: 0 12px;
  border: 0;
  border-radius: 6px;
  background: rgba(0, 55, 52, 0.55);
  font-family: inherit;
  font-size: 12px;
  letter-spacing: 0.02em;
  color: rgba(187, 199, 198, 0.8);
  white-space: nowrap;
  transition:
    background 200ms var(--au-ease),
    color 200ms var(--au-ease);
}
.accounts__item.is-active {
  background: rgba(203, 255, 252, 0.14);
  color: var(--au-mist);
}
.accounts__item i {
  font-style: normal;
  color: rgba(203, 255, 252, 0.66);
}

/* 只读读数：sheet 内行列表（不套卡） */
.readout {
  margin-top: 18px;
}

.err {
  margin: 14px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: rgba(253, 233, 255, 0.92);
}
.submit {
  margin-top: 20px;
}

/* 提交中的细环 */
.spin {
  width: 12px;
  height: 12px;
  border: 1.2px solid rgba(22, 33, 31, 0.28);
  border-top-color: var(--au-ink);
  border-radius: 50%;
  animation: au-spin 700ms linear infinite;
}
@keyframes au-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .spin {
    animation: none;
  }
}
</style>
