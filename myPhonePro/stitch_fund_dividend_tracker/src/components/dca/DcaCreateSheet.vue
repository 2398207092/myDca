<script setup lang="ts">
import { ref, computed } from 'vue'
import { createDcaPlan } from '@/api/dca'

interface Props {
  holdingId: string
  holdingName: string
  holdingCode: string
  tradingMarket: string
}

const props = defineProps<Props>()

const emit = defineEmits<{
  close: []
  created: []
}>()

const amount = ref<number>(50)
const frequency = ref<'daily' | 'weekly' | 'biweekly' | 'monthly'>('daily')
const day = ref<number | null>(null)
const submitting = ref(false)
const error = ref('')

const frequencyOptions: { value: string; label: string }[] = [
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

async function handleSubmit() {
  if (!amount.value || amount.value < 1) {
    error.value = '请输入有效的金额'
    return
  }

  if (frequency.value !== 'daily') {
    if (day.value === null || day.value === undefined || isNaN(day.value)) {
      error.value = '请选择或输入扣款日'
      return
    }
    if (day.value < 1 || day.value > 31) {
      error.value = '扣款日必须在 1~31 之间'
      return
    }
  }

  submitting.value = true
  error.value = ''

  try {
    await createDcaPlan({
      holdingId: props.holdingId,
      amount: amount.value,
      frequency: frequency.value,
      day: frequency.value === 'daily' ? null : day.value,
    })
    emit('created')
    emit('close')
  } catch (e: any) {
    error.value = e.message || '创建失败，请重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="bg-surface rounded-2xl px-xl py-lg shadow-2xl w-full max-w-sm mx-auto" style="max-height: 85vh; overflow-y: auto;">

    <h3 class="text-title-large font-title-large text-on-surface mb-1">创建定投计划</h3>
    <p class="text-body-medium font-body-medium text-on-surface-variant mb-lg">{{ holdingName }} ({{ holdingCode }})</p>

    <!-- Amount -->
    <label class="text-label-medium font-label-medium text-on-surface-variant mb-sm block">每期金额</label>
    <div class="relative mb-lg">
      <span class="absolute left-md top-1/2 -translate-y-1/2 text-on-surface-variant font-label-large">¥</span>
      <input
        v-model.number="amount"
        type="number"
        step="1"
        min="1"
        placeholder="50"
        class="w-full h-12 rounded-xl bg-surface-container-high pl-10 pr-md text-on-surface text-body-large font-body-large outline-none transition-colors focus:ring-2 focus:ring-primary"
      />
    </div>

    <!-- Frequency -->
    <label class="text-label-medium font-label-medium text-on-surface-variant mb-sm block">频率</label>
    <div class="flex gap-sm mb-lg">
      <button
        v-for="opt in frequencyOptions"
        :key="opt.value"
        type="button"
        class="flex-1 h-10 rounded-lg text-label-large font-label-large transition-all"
        :class="frequency === opt.value
          ? 'bg-primary-container text-on-primary-container shadow-sm'
          : 'bg-surface-container-high text-on-surface-variant hover:bg-surface-container-highest'"
        @click="frequency = opt.value as typeof frequency; if (opt.value === 'daily') day = null"
      >
        {{ opt.label }}
      </button>
    </div>

    <!-- Day selector (hidden when daily) -->
    <template v-if="showDaySelector">
      <label class="text-label-medium font-label-medium text-on-surface-variant mb-sm block">扣款日</label>

      <!-- Weekly / Biweekly: 7 days -->
      <div v-if="frequency === 'weekly' || frequency === 'biweekly'" class="flex gap-1.5 mb-lg">
        <button
          v-for="wd in weekDays"
          :key="wd.value"
          type="button"
          class="flex-1 min-w-[40px] h-9 rounded-lg text-label-large font-label-large transition-all"
          :class="day === wd.value
            ? 'bg-primary-container text-on-primary-container shadow-sm'
            : 'bg-surface-container-high text-on-surface-variant hover:bg-surface-container-highest'"
          @click="day = wd.value"
        >
          {{ wd.label }}
        </button>
      </div>

      <!-- Monthly: number input -->
      <div v-else class="relative mb-lg">
        <span class="absolute left-md top-1/2 -translate-y-1/2 text-on-surface-variant font-label-large">日</span>
        <input
          v-model.number="day"
          type="number"
          min="1"
          max="31"
          step="1"
          placeholder="15"
          class="w-full h-12 rounded-xl bg-surface-container-high pl-10 pr-md text-on-surface text-body-large font-body-large outline-none transition-colors focus:ring-2 focus:ring-primary"
          @keydown="(e: KeyboardEvent) => {
            if (['e', 'E', '+', '-', '.'].includes(e.key)) e.preventDefault()
          }"
        />
      </div>
    </template>

    <!-- Read-only info -->
    <div class="bg-surface-container-high rounded-xl px-md py-sm mb-lg space-y-sm">
      <div class="flex justify-between items-center">
        <span class="text-caption font-caption text-on-surface-variant">市场</span>
        <span class="text-body-small font-body-small text-on-surface">{{ marketLabel }}</span>
      </div>
      <div class="flex justify-between items-center">
        <span class="text-caption font-caption text-on-surface-variant">开始日期</span>
        <span class="text-body-small font-body-small text-on-surface">明天起第一个交易日</span>
      </div>
    </div>

    <!-- Error -->
    <p v-if="error" class="text-error text-body-small font-body-small mb-sm text-center">{{ error }}</p>

    <!-- Submit -->
    <button
      class="w-full h-12 rounded-xl bg-primary-container text-on-primary-container text-label-large font-label-large transition-all hover:brightness-95 active:scale-[0.98] flex items-center justify-center gap-sm disabled:opacity-50"
      :disabled="submitting"
      @click="handleSubmit"
    >
      <span v-if="submitting" class="material-symbols-outlined animate-spin text-lg">progress_activity</span>
      <span>{{ submitting ? '创建中...' : '确认创建' }}</span>
    </button>
  </div>
</template>
