<script setup lang="ts">
import type { PageState } from '@/types'

interface Props {
  state: PageState
  message?: string
  onRetry?: () => void
  /** 骨架屏样式：'spinner' 菊花（默认），'card' 卡片骨架 */
  skeleton?: 'spinner' | 'card'
}

const props = withDefaults(defineProps<Props>(), {
  message: '',
  skeleton: 'spinner',
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
  <Transition name="state-fade" mode="out-in">
    <div v-if="state !== 'ready'" :key="state" class="flex flex-col items-center justify-center py-20">
      <!-- Loading: 卡片骨架屏 -->
      <div v-if="state === 'loading' && skeleton === 'card'" class="w-full space-y-md">
        <div class="bg-card-bg rounded-xl p-md card-shadow border border-border-light/40 animate-pulse">
          <div class="h-3 w-20 bg-progress-bg rounded mb-md"></div>
          <div class="h-8 w-32 bg-progress-bg rounded mx-auto mb-md"></div>
          <div class="flex gap-2">
            <div class="flex-1 h-12 bg-card-alt rounded-lg"></div>
            <div class="flex-1 h-12 bg-card-alt rounded-lg"></div>
            <div class="flex-1 h-12 bg-card-alt rounded-lg"></div>
          </div>
        </div>
        <div class="bg-card-bg rounded-xl p-md card-shadow border border-border-light/40 animate-pulse space-y-sm">
          <div class="h-4 w-16 bg-progress-bg rounded"></div>
          <div class="h-16 bg-card-alt rounded-lg"></div>
          <div class="h-16 bg-card-alt rounded-lg"></div>
        </div>
      </div>

      <!-- Loading: 菊花 -->
      <div v-else-if="state === 'loading'" class="flex flex-col items-center gap-4">
        <div class="w-10 h-10 border-4 border-brand-light border-t-brand rounded-full animate-spin"></div>
        <p class="font-body text-sm text-text-tertiary">{{ getMessage('loading') }}</p>
      </div>

      <!-- Empty -->
      <div v-else-if="state === 'empty'" class="flex flex-col items-center gap-4">
        <div class="w-16 h-16 rounded-full bg-card-alt flex items-center justify-center animate-float">
          <span class="material-symbols-outlined text-[32px] text-text-tertiary">inbox</span>
        </div>
        <p class="font-body text-sm text-text-tertiary">{{ getMessage('empty') }}</p>
      </div>

      <!-- Error -->
      <div v-else-if="state === 'error'" class="flex flex-col items-center gap-4">
        <div class="w-16 h-16 rounded-full bg-error/10 flex items-center justify-center">
          <span class="material-symbols-outlined text-[32px] text-error">error_outline</span>
        </div>
        <p class="font-body text-sm font-medium text-text-primary">{{ getMessage('error') }}</p>
        <button
          v-if="onRetry"
          class="px-lg py-2 rounded-lg bg-brand text-white font-body text-sm font-medium active:scale-95 transition-transform"
          @click="onRetry"
        >
          重试
        </button>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
/* 状态切换淡入淡出，配合 reduced-motion 全局规则自动降级 */
.state-fade-enter-active,
.state-fade-leave-active {
  transition: opacity 0.2s ease;
}
.state-fade-enter-from,
.state-fade-leave-to {
  opacity: 0;
}

/* D2 空状态呼吸：图标极缓浮动（6s 循环 ±4px） */
.animate-float {
  animation: floatY 6s ease-in-out infinite;
}

@keyframes floatY {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-4px); }
}
</style>
