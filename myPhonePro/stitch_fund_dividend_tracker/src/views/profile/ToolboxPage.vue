<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { getAuditDates, getAuditContent } from '@/api/audit'
import type { AuditContent } from '@/api/audit'

const router = useRouter()

// ———— 日期弹窗 ————
const showDatePicker = ref(false)
const availableDates = ref<string[]>([])
const loadingDates = ref(false)

// ———— 内容弹窗 ————
const showContent = ref(false)
const content = ref<AuditContent | null>(null)

// ———— 日期选择器 ————
async function openDatePicker() {
  showDatePicker.value = true
  if (availableDates.value.length === 0) {
    loadingDates.value = true
    try {
      availableDates.value = await getAuditDates()
    } catch (e) {
      console.error('获取审计日期列表失败:', e)
    } finally {
      loadingDates.value = false
    }
  }
}

async function selectDate(date: string) {
  showDatePicker.value = false
  try {
    content.value = await getAuditContent(date)
    showContent.value = true
  } catch (e) {
    console.error('获取审计内容失败:', e)
  }
}

function goBack() {
  router.back()
}

// 工具列表
const tools = [
  {
    id: 'audit',
    icon: 'verified',
    label: '数据审计报告',
    desc: '查看每日自动对账结果',
    action: openDatePicker,
  },
]
</script>

<template>
  <div class="min-h-screen bg-page-bg">
    <!-- Header -->
    <header class="flex items-center justify-between px-gutter h-14 sticky top-0 z-50 bg-card-bg border-b border-border-light/40">
      <button
        class="w-10 h-10 flex items-center justify-center -ml-2 active:scale-90 transition-transform"
        @click="goBack"
      >
        <span class="material-symbols-outlined text-text-secondary">arrow_back</span>
      </button>
      <h1 class="font-body text-md font-medium text-text-primary">工具箱</h1>
      <div class="w-10" />
    </header>

    <!-- 工具列表 -->
    <main class="pt-4 pb-24 px-gutter">
      <section class="bg-card-bg rounded-xl overflow-hidden card-shadow border border-border-light/40">
        <div class="divide-y divide-border-light">
          <div
            v-for="tool in tools"
            :key="tool.id"
            class="flex items-center justify-between p-lg hover:bg-card-alt transition-colors cursor-pointer group active:scale-[0.99] active:transition-transform"
            @click="tool.action"
          >
            <div class="flex items-center gap-md">
              <span
                class="material-symbols-outlined text-brand"
                :class="{ 'fill-icon': tool.id === 'audit' }"
              >{{ tool.icon }}</span>
              <div>
                <p class="font-body text-sm font-medium text-text-primary">{{ tool.label }}</p>
                <p class="font-body text-xs text-text-tertiary mt-0.5">{{ tool.desc }}</p>
              </div>
            </div>
            <span class="material-symbols-outlined text-text-tertiary group-hover:translate-x-1 transition-transform">
              chevron_right
            </span>
          </div>

          <!-- 功能预留位 -->
          <div
            class="flex items-center justify-between p-lg opacity-40 cursor-not-allowed"
          >
            <div class="flex items-center gap-md">
              <span class="material-symbols-outlined text-text-secondary">construction</span>
              <div>
                <p class="font-body text-sm font-medium text-text-primary">更多功能</p>
                <p class="font-body text-xs text-text-tertiary mt-0.5">即将上线</p>
              </div>
            </div>
          </div>
        </div>
      </section>
    </main>

    <!-- ==================== 日期选择弹窗 ==================== -->
    <Teleport to="body">
      <div
        v-if="showDatePicker"
        class="fixed inset-0 z-[100] flex items-end justify-center bg-black/40"
        @click.self="showDatePicker = false"
      >
        <div class="bg-card-bg rounded-t-2xl w-full max-w-lg px-gutter pt-lg pb-8 animate-slide-up max-h-[70vh] flex flex-col">
          <div class="flex items-center justify-between mb-md shrink-0">
            <h3 class="font-body text-base font-medium text-text-primary">选择日期</h3>
            <button
              class="w-8 h-8 flex items-center justify-center text-text-tertiary hover:bg-card-alt rounded-lg transition-colors"
              @click="showDatePicker = false"
            >
              <span class="material-symbols-outlined">close</span>
            </button>
          </div>

          <!-- 加载中 -->
          <div v-if="loadingDates" class="flex items-center justify-center py-12">
            <span class="material-symbols-outlined text-brand animate-spin text-3xl">refresh</span>
          </div>

          <!-- 空状态 -->
          <div v-else-if="availableDates.length === 0" class="text-center py-12">
            <span class="material-symbols-outlined text-text-tertiary text-4xl mb-3">calendar_month</span>
            <p class="font-body text-sm text-text-tertiary">暂无审计日志</p>
            <p class="font-body text-xs text-text-tertiary mt-1 opacity-60">审计任务每天凌晨 3:00 自动运行</p>
          </div>

          <!-- 日期列表 -->
          <div v-else class="overflow-y-auto flex-1 -mx-gutter px-gutter space-y-1">
            <div
              v-for="date in availableDates"
              :key="date"
              class="flex items-center justify-between p-md rounded-xl hover:bg-card-alt cursor-pointer transition-colors active:scale-[0.98] active:transition-transform"
              @click="selectDate(date)"
            >
              <div class="flex items-center gap-md">
                <span class="material-symbols-outlined text-brand text-xl">calendar_today</span>
                <span class="font-body text-sm text-text-primary">{{ date }}</span>
              </div>
              <span class="material-symbols-outlined text-text-tertiary text-xl">chevron_right</span>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ==================== 审计内容弹窗 ==================== -->
    <Teleport to="body">
      <div
        v-if="showContent && content"
        class="fixed inset-0 z-[110] flex items-end justify-center bg-black/40"
        @click.self="showContent = false"
      >
        <div class="bg-card-bg rounded-t-2xl w-full max-w-lg px-gutter pt-lg pb-8 animate-slide-up max-h-[80vh] flex flex-col">
          <div class="flex items-center justify-between mb-md shrink-0">
            <div class="flex items-center gap-sm">
              <span class="material-symbols-outlined text-brand">verified</span>
              <h3 class="font-body text-base font-medium text-text-primary">数据对账报告</h3>
            </div>
            <button
              class="w-8 h-8 flex items-center justify-center text-text-tertiary hover:bg-card-alt rounded-lg transition-colors"
              @click="showContent = false"
            >
              <span class="material-symbols-outlined">close</span>
            </button>
          </div>

          <!-- 报告日期 -->
          <div class="text-center mb-md shrink-0">
            <p class="font-display text-lg font-semibold text-text-primary">{{ content.date }}</p>
          </div>

          <!-- 摘要 -->
          <div
            class="rounded-xl p-md mb-md shrink-0"
            :class="content.errorCount > 0
              ? 'bg-red-50 border border-red-200'
              : content.warningCount > 0
                ? 'bg-amber-50 border border-amber-200'
                : 'bg-green-50 border border-green-200'"
          >
            <p
              class="font-body text-sm font-medium text-center"
              :class="content.errorCount > 0
                ? 'text-red-600'
                : content.warningCount > 0
                  ? 'text-amber-600'
                  : 'text-green-600'"
            >
              {{ content.summary }}
            </p>
          </div>

          <!-- 条目列表 -->
          <div v-if="content.entries.length > 0" class="overflow-y-auto flex-1 -mx-gutter px-gutter space-y-2">
            <div
              v-for="(entry, i) in content.entries"
              :key="i"
              class="flex items-start gap-sm p-md rounded-xl"
              :class="entry.level === 'error'
                ? 'bg-red-50/50'
                : entry.level === 'warning'
                  ? 'bg-amber-50/50'
                  : 'bg-card-alt'"
            >
              <span v-if="entry.level === 'error'" class="material-symbols-outlined text-red-500 text-lg shrink-0 mt-0.5">error</span>
              <span v-else-if="entry.level === 'warning'" class="material-symbols-outlined text-amber-500 text-lg shrink-0 mt-0.5">warning</span>
              <span v-else class="material-symbols-outlined text-green-500 text-lg shrink-0 mt-0.5">check_circle</span>
              <p class="font-body text-sm leading-relaxed" :class="{
                'text-red-700': entry.level === 'error',
                'text-amber-700': entry.level === 'warning',
                'text-text-secondary': entry.level === 'info',
              }">
                {{ entry.message }}
              </p>
            </div>
          </div>

          <!-- 底部统计 -->
          <div class="mt-md pt-md border-t border-border-light shrink-0">
            <p class="font-body text-xs text-text-tertiary text-center">
              共检测 <span v-if="content.errorCount > 0" class="text-red-500 font-medium">{{ content.errorCount }} 个错误</span>
              <span v-if="content.errorCount > 0 && content.warningCount > 0">，</span>
              <span v-if="content.warningCount > 0" class="text-amber-500 font-medium">{{ content.warningCount }} 个警告</span>
              <span v-if="content.errorCount === 0 && content.warningCount === 0" class="text-green-500 font-medium">全部正常</span>
            </p>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
/* 从底部滑入动画 */
.animate-slide-up {
  animation: slideUp 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes slideUp {
  from {
    transform: translateY(100%);
    opacity: 0.5;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}
</style>
