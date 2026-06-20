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
  <div class="min-h-screen bg-surface flex flex-col">
    <!-- TopAppBar -->
    <header class="flex items-center justify-between px-gutter h-14 sticky top-0 z-50 bg-surface shadow-sm">
      <button @click="goBack" class="w-10 h-10 flex items-center justify-center -ml-2 active:opacity-80">
        <span class="material-symbols-outlined text-on-surface-variant">arrow_back</span>
      </button>
      <div class="flex-1 text-center">
        <h1 class="font-headline-lg text-headline-lg font-bold text-on-surface">指标设置</h1>
      </div>
      <button @click="router.push('/')" class="w-10 h-10 flex items-center justify-center active:opacity-80 transition-opacity">
          <span class="material-symbols-outlined text-on-surface-variant">home</span>
        </button>
    </header>

    <main class="flex-1 px-gutter pb-8 overflow-y-auto">
      <!-- ========== 实时预览 ========== -->
      <div class="mt-md bg-surface-container-lowest rounded-xl p-lg card-shadow">
        <div class="flex items-center justify-between mb-md">
          <h3 class="font-label-bold text-label-bold text-on-surface">实时预览</h3>
          <span class="text-on-surface-variant font-caption text-caption">已选 {{ enabledKeys.length }}/{{ MAX_COUNT }}</span>
        </div>
        <div v-if="previewMetrics.length > 0" class="grid grid-cols-3 gap-sm">
          <div
            v-for="metric in previewMetrics"
            :key="metric.key"
            class="text-center py-2 px-1 rounded-lg bg-primary-fixed/20"
          >
            <span class="font-caption text-caption text-on-primary-fixed">{{ metric.label }}</span>
          </div>
        </div>
        <div v-else class="text-center py-4 text-outline-variant font-caption text-caption">
          请选择要展示的指标
        </div>
      </div>

      <!-- ========== 提示文字 ========== -->
      <p class="mt-md text-on-surface-variant font-body-md text-body-md">
        选择要在首页展示的指标，最多可选
        <span class="font-semibold text-primary">{{ MAX_COUNT }}</span> 项
      </p>

      <!-- 已达上限提示 -->
      <div
        v-if="isAtMax"
        class="mt-sm flex items-center gap-1.5 text-primary font-caption text-caption"
      >
        <span class="material-symbols-outlined text-[14px]">info</span>
        <span>已达上限，取消某项后可替换</span>
      </div>

      <!-- ========== 指标列表 ========== -->
      <div class="mt-lg bg-surface-container-lowest rounded-xl overflow-hidden">
        <div
          v-for="(metric, idx) in ALL_METRICS"
          :key="metric.key"
          class="flex items-center justify-between px-md py-3"
          :class="idx < ALL_METRICS.length - 1 ? 'border-b border-surface-variant/50' : ''"
        >
          <div class="flex items-center gap-3" :class="!canToggle(metric.key) ? 'opacity-40' : ''">
            <div
              class="w-8 h-8 rounded-full flex items-center justify-center text-sm font-semibold"
              :class="isEnabled(metric.key)
                ? 'bg-primary-container text-white'
                : 'bg-surface-container text-on-surface-variant'"
            >
              <span v-if="isEnabled(metric.key)">✓</span>
              <span v-else>{{ idx + 1 }}</span>
            </div>
            <div>
              <p
                class="font-label-bold text-label-bold"
                :class="isEnabled(metric.key) ? 'text-on-surface' : 'text-on-surface-variant'"
              >{{ metric.label }}</p>
              <p class="font-caption text-caption text-outline-variant">
                {{ metric.formatter === 'money' ? '金额' : metric.formatter === 'percent' ? '百分比' : '数值' }}
              </p>
            </div>
          </div>
          <button
            @click="toggle(metric.key)"
            :disabled="!canToggle(metric.key)"
            class="relative flex-shrink-0 w-[44px] h-[24px] rounded-full transition-colors duration-200"
            :class="[
              isEnabled(metric.key) ? 'bg-primary-container' : 'bg-outline-variant',
              !canToggle(metric.key) ? 'cursor-not-allowed' : 'cursor-pointer'
            ]"
          >
            <span
              class="absolute top-[2px] w-[20px] h-[20px] bg-white rounded-full shadow-sm transition-all duration-200"
              :class="isEnabled(metric.key) ? 'left-[22px]' : 'left-[2px]'"
            />
          </button>
        </div>
      </div>

      <!-- ========== 底部操作 ========== -->
      <div class="my-lg flex items-center gap-3">
        <span class="flex-1 h-px bg-outline-variant/40" />
        <span class="text-outline-variant font-caption text-caption">已选 {{ enabledKeys.length }} 项</span>
        <span class="flex-1 h-px bg-outline-variant/40" />
      </div>

      <button
        @click="goBack"
        class="w-full h-12 bg-primary-container text-white rounded-2xl font-headline-sm flex items-center justify-center gap-2 active:scale-[0.98] transition-transform"
      >
        <span>完成设置</span>
      </button>
    </main>
  </div>
</template>
