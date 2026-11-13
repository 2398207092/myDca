<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getSchedulerTasks, runSchedulerTask } from '@/api/scheduler'
import type { SchedulerTask } from '@/api/scheduler'
import ToastNotification from '@/components/shared/ToastNotification.vue'
import PageStateComp from '@/components/shared/PageState.vue'

const router = useRouter()

const pageState = ref<'loading' | 'ready' | 'error'>('loading')
const tasks = ref<SchedulerTask[]>([])
const toastMsg = ref<string | null>(null)
const toastType = ref<'success' | 'error' | 'info'>('success')

// 确认弹窗
const confirmTask = ref<SchedulerTask | null>(null)
const runningId = ref('')

const taskIcons: Record<string, string> = {
  audit: 'verified',
  dividend_refresh: 'history_edu',
  dividend_distribute: 'savings',
  dca: 'repeat',
  nav: 'query_stats',
  snapshot: 'photo_camera',
}

function showToast(msg: string, type: 'success' | 'error' | 'info' = 'success') {
  toastMsg.value = msg
  toastType.value = type
}

async function loadTasks() {
  pageState.value = 'loading'
  try {
    tasks.value = await getSchedulerTasks()
    pageState.value = 'ready'
  } catch (e: any) {
    pageState.value = 'error'
    showToast(e.message || '加载失败', 'error')
  }
}

function openConfirm(task: SchedulerTask) {
  confirmTask.value = task
}

// 危险操作（真实资金/外部接口）的确认文案
function confirmText(task: SchedulerTask): string {
  switch (task.id) {
    case 'dca':
      return '将立即执行一次定投：会对到期的定投计划真实买入扣款，并联动刷新持仓。确定继续？'
    case 'nav':
      return '将立即抓取所有持仓基金的最新净值（调用外部接口），并刷新市值、成本、分红预测。确定继续？'
    case 'dividend_refresh':
      return '将立即抓取所有持仓基金的分红数据（调用外部接口），并同步分红事件到日历。确定继续？'
    case 'snapshot':
      return '将立即为所有持仓生成一份资产快照。确定继续？'
    default:
      return `立即执行一次「${task.name}」？`
  }
}

async function doRun() {
  if (!confirmTask.value) return
  const t = confirmTask.value
  runningId.value = t.id
  try {
    const r = await runSchedulerTask(t.id)
    showToast(
      r.success ? `「${r.taskName}」执行完成，耗时 ${(r.durationMs / 1000).toFixed(1)}s` : `「${r.taskName}」执行失败：${r.detail}`,
      r.success ? 'success' : 'error',
    )
    confirmTask.value = null
    await loadTasks()
  } catch (e: any) {
    showToast(e.message || '触发失败', 'error')
    confirmTask.value = null
  } finally {
    runningId.value = ''
  }
}

onMounted(loadTasks)
</script>

<template>
  <div class="min-h-screen bg-page-bg">
    <!-- Toast -->
    <ToastNotification :message="toastMsg" :type="toastType" @close="toastMsg = null" />

    <!-- Header -->
    <header class="flex items-center justify-between px-gutter h-14 sticky top-0 z-50 bg-card-bg border-b border-border-light/40">
      <button @click="router.back()" class="w-10 h-10 flex items-center justify-center -ml-2 active:opacity-80">
        <span class="material-symbols-outlined text-text-secondary">arrow_back</span>
      </button>
      <div class="flex-1 text-center">
        <h1 class="font-body text-md font-medium text-text-primary">定时任务</h1>
      </div>
      <button @click="router.push('/profile')" class="w-10 h-10 flex items-center justify-center active:opacity-80 transition-opacity">
        <span class="material-symbols-outlined text-text-secondary">home</span>
      </button>
    </header>

    <PageStateComp v-if="pageState !== 'ready'" :state="pageState" @retry="loadTasks" />

    <main v-if="pageState === 'ready'" class="px-gutter pt-3 pb-32 space-y-md max-w-[600px] mx-auto">
      <!-- 说明 -->
      <div class="flex items-start gap-2 bg-brand-light/40 rounded-lg px-md py-sm">
        <span class="material-symbols-outlined text-brand text-[16px] mt-[1px]">info</span>
        <p class="font-body text-xs text-text-secondary leading-relaxed">
          定时任务每日自动运行；也可手动「立即执行」。同一任务 5 分钟内只能手动触发一次。执行结果记录在「每日监控日志」。
        </p>
      </div>

      <!-- 任务列表 -->
      <section
        v-for="task in tasks"
        :key="task.id"
        class="bg-card-bg rounded-xl p-lg card-shadow border border-border-light/40"
      >
        <div class="flex items-center justify-between mb-sm">
          <div class="flex items-center gap-md min-w-0">
            <span class="w-9 h-9 rounded-lg bg-brand-light/60 flex items-center justify-center shrink-0">
              <span class="material-symbols-outlined text-brand text-[20px]">{{ taskIcons[task.id] || 'schedule' }}</span>
            </span>
            <div class="min-w-0">
              <p class="font-body text-sm font-medium text-text-primary">{{ task.name }}</p>
              <p class="font-body text-[11px] text-text-tertiary mt-0.5 break-all">{{ task.cron }}</p>
            </div>
          </div>
          <button
            class="shrink-0 ml-2 h-8 px-md rounded-lg bg-brand text-white text-[11px] font-medium transition-all active:scale-95 flex items-center gap-1 disabled:opacity-50 disabled:active:scale-100"
            :disabled="runningId === task.id"
            @click="openConfirm(task)"
          >
            <span v-if="runningId === task.id" class="material-symbols-outlined text-[14px] animate-spin">progress_activity</span>
            <span v-else class="material-symbols-outlined text-[14px]">play_arrow</span>
            <span>{{ runningId === task.id ? '执行中' : '立即执行' }}</span>
          </button>
        </div>

        <p class="font-body text-xs text-text-secondary mb-md">{{ task.description }}</p>

        <div class="grid grid-cols-2 gap-x-md gap-y-1.5 pt-sm border-t border-border-light/40">
          <div class="flex items-center justify-between">
            <span class="font-body text-[11px] text-text-tertiary">下次执行</span>
            <span class="font-body text-xs text-text-primary">{{ task.nextRunAt || '--' }}</span>
          </div>
          <div class="flex items-center justify-between">
            <span class="font-body text-[11px] text-text-tertiary">最近状态</span>
            <span class="flex items-center gap-1">
              <span
                v-if="task.lastSuccess === null"
                class="font-body text-xs text-text-tertiary"
              >暂无记录</span>
              <template v-else>
                <span
                  class="font-body text-xs font-medium"
                  :class="task.lastSuccess ? 'text-success' : 'text-error'"
                >{{ task.lastSuccess ? '成功' : '失败' }}</span>
                <span class="font-body text-[11px] text-text-tertiary">{{ task.lastRunAt ? task.lastRunAt.slice(5, 16) : '' }}</span>
              </template>
            </span>
          </div>
        </div>
      </section>
    </main>

    <!-- 确认执行弹窗 -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="confirmTask" class="fixed inset-0 z-modal-backdrop bg-black/40" @click="confirmTask = null"></div>
      </Transition>
      <Transition name="scale-up">
        <div v-if="confirmTask" class="fixed inset-0 z-modal flex items-center justify-center px-gutter" @click.self="confirmTask = null">
          <div class="bg-card-bg rounded-2xl px-xl py-lg mx-gutter max-w-sm w-full shadow-elevated">
            <div class="flex flex-col items-center text-center">
              <div class="w-12 h-12 rounded-full bg-brand-light/60 flex items-center justify-center mb-md">
                <span class="material-symbols-outlined text-brand text-2xl">{{ taskIcons[confirmTask.id] || 'schedule' }}</span>
              </div>
              <h3 class="font-body text-md font-medium text-text-primary mb-sm">立即执行「{{ confirmTask.name }}」</h3>
              <p class="font-body text-sm text-text-tertiary leading-relaxed mb-xl">{{ confirmText(confirmTask) }}</p>
              <div class="flex gap-md w-full">
                <button
                  class="flex-1 h-12 rounded-xl bg-card-alt text-text-secondary font-body text-sm font-medium transition-colors active:scale-[0.98]"
                  @click="confirmTask = null"
                >取消</button>
                <button
                  class="flex-1 h-12 rounded-xl bg-brand text-white font-body text-sm font-medium transition-colors active:scale-[0.98]"
                  @click="doRun"
                >确认执行</button>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.fade-enter-active { transition: opacity 0.2s ease; }
.fade-leave-active { transition: opacity 0.15s ease; }
.fade-enter-from,
.fade-leave-to { opacity: 0; }

.scale-up-enter-active { transition: transform 0.2s ease, opacity 0.2s ease; }
.scale-up-leave-active { transition: transform 0.15s ease, opacity 0.15s ease; }
.scale-up-enter-from { transform: scale(0.9); opacity: 0; }
.scale-up-leave-to { transform: scale(0.9); opacity: 0; }
</style>
