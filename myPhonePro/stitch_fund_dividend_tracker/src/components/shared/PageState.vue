<script setup lang="ts">
import type { PageState } from '@/types'

interface Props {
  state: PageState
  message?: string
  onRetry?: () => void
}

const props = withDefaults(defineProps<Props>(), {
  message: '',
})

const messages: Record<PageState, string> = {
  loading: '加载中...',
  empty: '暂无数据',
  error: '加载失败，请重试',
  ready: '',
}

const getMessage = (state: PageState) => {
  return props.message || messages[state]
}
</script>

<template>
  <div v-if="state !== 'ready'" class="flex flex-col items-center justify-center py-20">
    <!-- Loading -->
    <div v-if="state === 'loading'" class="flex flex-col items-center gap-4">
      <div class="w-10 h-10 border-4 border-primary-container border-t-transparent rounded-full animate-spin" />
      <p class="text-on-surface-variant font-caption">{{ getMessage('loading') }}</p>
    </div>

    <!-- Empty -->
    <div v-else-if="state === 'empty'" class="flex flex-col items-center gap-4">
      <span class="material-symbols-outlined text-[48px] text-outline-variant">inbox</span>
      <p class="text-on-surface-variant font-caption">{{ getMessage('empty') }}</p>
    </div>

    <!-- Error -->
    <div v-else-if="state === 'error'" class="flex flex-col items-center gap-4">
      <span class="material-symbols-outlined text-[48px] text-error">error_outline</span>
      <p class="text-on-surface-variant font-caption">{{ getMessage('error') }}</p>
      <button
        v-if="onRetry"
        class="px-4 py-2 rounded-lg bg-primary-container text-white font-label-bold active:scale-95 transition-transform"
        @click="onRetry"
      >
        重试
      </button>
    </div>
  </div>
</template>
