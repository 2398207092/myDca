<script setup lang="ts">
import { ref } from 'vue'
import { executeDcaPlan, type DcaExecutionResultVO } from '@/api/dca'

interface Props {
  planId: string
  holdingName: string
  amount: number
}

const props = defineProps<Props>()

const emit = defineEmits<{
  close: []
  executed: []
}>()

const state = ref<'confirm' | 'loading' | 'success' | 'error'>('confirm')
const result = ref<DcaExecutionResultVO | null>(null)
const errorMsg = ref('')

async function handleExecute() {
  state.value = 'loading'
  errorMsg.value = ''
  try {
    const res = await executeDcaPlan(props.planId)
    result.value = res
    state.value = 'success'
    emit('executed')
  } catch (e: any) {
    errorMsg.value = e.message || '执行失败，请重试'
    state.value = 'error'
  }
}

function handleRetry() {
  state.value = 'confirm'
  handleExecute()
}

function formatNumber(n: number, digits = 2): string {
  return n.toLocaleString('zh-CN', { minimumFractionDigits: digits, maximumFractionDigits: digits })
}
</script>

<template>
  <div class="bg-card-bg rounded-2xl px-xl py-lg shadow-overlay w-full max-w-sm mx-auto">

    <!-- Confirm state -->
    <template v-if="state === 'confirm'">
      <h3 class="font-body text-md font-medium text-text-primary mb-1">执行定投</h3>
      <p class="font-body text-sm text-text-secondary mb-lg">{{ holdingName }}</p>

      <div class="bg-card-alt rounded-xl px-md py-md mb-lg">
        <div class="flex justify-between items-center">
          <span class="font-body text-xs text-text-tertiary">金额</span>
          <span class="font-display text-lg font-semibold text-brand">¥{{ formatNumber(amount) }}</span>
        </div>
      </div>

      <button
        class="w-full h-12 rounded-xl bg-brand text-white font-body font-medium text-md transition-all active:scale-[0.98] flex items-center justify-center gap-sm"
        @click="handleExecute"
      >
        <span class="material-symbols-outlined">play_arrow</span>
        <span>确认执行</span>
      </button>
    </template>

    <!-- Loading state -->
    <template v-if="state === 'loading'">
      <div class="flex flex-col items-center justify-center py-lg">
        <span class="material-symbols-outlined animate-spin text-3xl text-brand mb-md">progress_activity</span>
        <p class="font-body text-sm text-text-secondary">正在执行定投...</p>
      </div>
    </template>

    <!-- Success state -->
    <template v-if="state === 'success' && result">
      <div class="flex flex-col items-center py-md">
        <div class="w-12 h-12 rounded-full bg-brand-light flex items-center justify-center mb-md">
          <span class="material-symbols-outlined text-2xl text-brand">check_circle</span>
        </div>
        <h3 class="font-body text-md font-medium text-text-primary mb-1">执行成功</h3>
        <p class="font-body text-sm text-text-secondary mb-lg">{{ holdingName }}</p>

        <div class="w-full bg-card-alt rounded-xl px-md py-md space-y-sm mb-lg">
          <div class="flex justify-between items-center">
            <span class="font-body text-xs text-text-tertiary">金额</span>
            <span class="font-body text-sm text-text-primary">¥{{ formatNumber(result.amount) }}</span>
          </div>
          <div class="flex justify-between items-center">
            <span class="font-body text-xs text-text-tertiary">成交份额</span>
            <span class="font-body text-sm text-text-primary">{{ formatNumber(result.quantity, 4) }} 份</span>
          </div>
          <div class="flex justify-between items-center">
            <span class="font-body text-xs text-text-tertiary">成交净值</span>
            <span class="font-body text-sm text-text-primary">¥{{ formatNumber(result.navPrice, 4) }}</span>
          </div>
          <div class="flex justify-between items-center">
            <span class="font-body text-xs text-text-tertiary">净值日期</span>
            <span class="font-body text-sm text-text-primary">{{ result.navDate }}</span>
          </div>
        </div>

        <button
          class="w-full h-12 rounded-xl bg-brand text-white font-body font-medium text-md transition-all active:scale-[0.98]"
          @click="emit('close')"
        >
          完成
        </button>
      </div>
    </template>

    <!-- Error state -->
    <template v-if="state === 'error'">
      <div class="flex flex-col items-center py-md">
        <div class="w-12 h-12 rounded-full bg-error/10 flex items-center justify-center mb-md">
          <span class="material-symbols-outlined text-2xl text-error">error_outline</span>
        </div>
        <h3 class="font-body text-md font-medium text-text-primary mb-1">执行失败</h3>
        <p class="font-body text-sm text-text-secondary mb-lg">{{ errorMsg }}</p>

        <div class="flex gap-md w-full">
          <button
            class="flex-1 h-12 rounded-xl bg-card-alt text-text-secondary font-body font-medium text-md transition-colors active:scale-[0.98]"
            @click="emit('close')"
          >
            关闭
          </button>
          <button
            class="flex-1 h-12 rounded-xl bg-brand text-white font-body font-medium text-md transition-colors active:scale-[0.98] flex items-center justify-center gap-sm"
            @click="handleRetry"
          >
            <span class="material-symbols-outlined">refresh</span>
            <span>重试</span>
          </button>
        </div>
      </div>
    </template>
  </div>
</template>
