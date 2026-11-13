<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ALL_METRICS, loadEnabledKeys, saveEnabledKeys } from '@/api/metrics'

const router = useRouter()

const enabledKeys = ref<string[]>(loadEnabledKeys())

const MAX_COUNT = 6
const isAtMax = computed(() => enabledKeys.value.length >= MAX_COUNT)

function isEnabled(key: string) {
  return enabledKeys.value.includes(key)
}

function canToggle(key: string) {
  return isEnabled(key) || !isAtMax.value
}

function toggle(key: string) {
  const idx = enabledKeys.value.indexOf(key)
  if (idx >= 0) {
    enabledKeys.value.splice(idx, 1)
  } else {
    if (isAtMax.value) return
    enabledKeys.value.push(key)
  }
  saveEnabledKeys(enabledKeys.value)
}

const previewMetrics = computed(() =>
  ALL_METRICS.filter((m) => enabledKeys.value.includes(m.key))
)

function goBack() {
  router.back()
}
</script>

<template>
  <div class="min-h-screen bg-page-bg flex flex-col">
    <!-- TopAppBar -->
    <header class="flex items-center justify-between px-gutter h-14 sticky top-0 z-50 bg-card-bg border-b border-border-light/40">
      <button @click="goBack" class="w-10 h-10 flex items-center justify-center -ml-2 active:opacity-80">
        <span class="material-symbols-outlined text-text-secondary">arrow_back</span>
      </button>
      <div class="flex-1 text-center">
        <h1 class="font-body text-md font-medium text-text-primary">指标设置</h1>
      </div>
      <button @click="router.push('/')" class="w-10 h-10 flex items-center justify-center active:opacity-80 transition-opacity">
        <span class="material-symbols-outlined text-text-secondary">home</span>
      </button>
    </header>

    <main class="flex-1 px-gutter pb-8 overflow-y-auto">
      <!-- ========== 实时预览 ========== -->
      <div class="mt-md bg-card-bg rounded-xl p-lg card-shadow border border-border-light/40">
        <div class="flex items-center justify-between mb-md">
          <h3 class="font-body text-sm font-medium text-text-primary">实时预览</h3>
          <span class="text-text-tertiary font-body text-xs">已选 {{ enabledKeys.length }}/{{ MAX_COUNT }}</span>
        </div>
        <div v-if="previewMetrics.length > 0" class="grid grid-cols-3 gap-sm">
          <div
            v-for="metric in previewMetrics"
            :key="metric.key"
            class="text-center py-3 px-1 rounded-lg bg-brand-light/60"
          >
            <span class="text-lg block mb-0.5">{{ metric.formatter === 'money' ? '💰' : metric.formatter === 'percent' ? '📈' : '🔢' }}</span>
            <span class="font-body text-xs font-medium text-brand">{{ metric.label }}</span>
          </div>
        </div>
        <div v-else class="flex flex-col items-center py-4 gap-1">
          <span class="text-2xl">📋</span>
          <p class="font-body text-xs text-text-tertiary">请选择要展示的指标</p>
        </div>
      </div>

      <!-- ========== 提示文字 ========== -->
      <p class="mt-md font-body text-sm text-text-tertiary">
        选择要在首页展示的指标，最多可选
        <span class="font-semibold text-brand">{{ MAX_COUNT }}</span> 项
      </p>

      <!-- 已达上限提示 -->
      <div
        v-if="isAtMax"
        class="mt-sm flex items-center gap-1 text-text-tertiary font-body text-xs"
      >
        <span>⚠️</span>
        <span>已达上限，取消某项后可替换</span>
      </div>

      <!-- ========== 指标列表 ========== -->
      <div class="mt-lg bg-card-bg rounded-xl card-shadow border border-border-light/40 overflow-hidden">
        <div
          v-for="(metric, idx) in ALL_METRICS"
          :key="metric.key"
          class="flex items-center justify-between px-md py-3 active:bg-card-alt/40 transition-colors duration-150 cursor-pointer"
          :class="idx < ALL_METRICS.length - 1 ? 'border-b border-border-light/40' : ''"
          @click="toggle(metric.key)"
        >
          <div class="flex items-center gap-3 min-w-0" :class="!canToggle(metric.key) ? 'opacity-40' : ''">
            <div
              class="w-8 h-8 rounded-full flex items-center justify-center text-xs font-semibold flex-shrink-0"
              :class="isEnabled(metric.key)
                ? 'bg-brand text-white shadow-elevated'
                : 'bg-card-alt text-text-tertiary'"
            >
              <span v-if="isEnabled(metric.key)">✓</span>
              <span v-else>{{ idx + 1 }}</span>
            </div>
            <div class="min-w-0">
              <p
                class="font-body text-sm truncate"
                :class="isEnabled(metric.key) ? 'font-medium text-text-primary' : 'text-text-tertiary'"
              >{{ metric.label }}</p>
              <p class="font-body text-xs text-text-tertiary">
                {{ metric.formatter === 'money' ? '金额' : metric.formatter === 'percent' ? '百分比' : '数值' }}
              </p>
            </div>
          </div>
          <button
            @click.stop="toggle(metric.key)"
            :disabled="!canToggle(metric.key)"
            class="relative flex-shrink-0 w-[44px] h-[24px] rounded-full transition-colors duration-200"
            :class="[
              isEnabled(metric.key) ? 'bg-brand' : 'bg-border-light',
              !canToggle(metric.key) ? 'cursor-not-allowed' : 'cursor-pointer'
            ]"
          >
            <span
              class="absolute top-[2px] w-[20px] h-[20px] bg-white rounded-full shadow-card transition-all duration-200"
              :class="isEnabled(metric.key) ? 'left-[22px]' : 'left-[2px]'"
            />
          </button>
        </div>
      </div>

      <!-- ========== 底部操作 ========== -->
      <button
        @click="goBack"
        class="mt-lg w-full h-12 bg-brand text-white rounded-xl font-body text-sm font-medium flex items-center justify-center gap-2 active:scale-[0.98] transition-all duration-200 shadow-card"
      >
        <span class="text-lg">✓</span>
        <span>完成设置（已选 {{ enabledKeys.length }}/{{ MAX_COUNT }}）</span>
      </button>
    </main>
  </div>
</template>
