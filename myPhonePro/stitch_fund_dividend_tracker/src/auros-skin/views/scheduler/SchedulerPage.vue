<script setup lang="ts">
/**
 * 定时任务（四层信息骨架 · 2026-11-08 v3）
 * 章 01 STATUS 运行状态：主角数（失败数）+ 一组行（上次运行 / 下次运行 / 任务数）
 * 章 02 TASKS 任务：每个任务一行（名称 + cron 副行 + 状态点 + 结果字），≤5 行 + AurosDisclosure
 * 业务逻辑与接口不变，只重排信息骨架与呈现。
 */
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getSchedulerTasks, runSchedulerTask } from '@/api/scheduler'
import type { SchedulerTask } from '@/api/scheduler'
import ToastNotification from '@/auros-skin/components/shared/ToastNotification.vue'
import AurosNavBar from '@/auros-skin/components/auros/AurosNavBar.vue'
import AurosState from '@/auros-skin/components/auros/AurosState.vue'
import AurosChapter from '@/auros-skin/components/auros/AurosChapter.vue'
import AurosStat from '@/auros-skin/components/auros/AurosStat.vue'
import AurosGroup from '@/auros-skin/components/auros/AurosGroup.vue'
import AurosRow from '@/auros-skin/components/auros/AurosRow.vue'
import AurosDisclosure from '@/auros-skin/components/auros/AurosDisclosure.vue'
import AurosSheet from '@/auros-skin/components/auros/AurosSheet.vue'
import AurosButton from '@/auros-skin/components/auros/AurosButton.vue'

const router = useRouter()

const pageState = ref<'loading' | 'ready' | 'error'>('loading')
const tasks = ref<SchedulerTask[]>([])
const toastMsg = ref<string | null>(null)
const toastType = ref<'success' | 'error' | 'info'>('success')

// 确认弹窗
const confirmTask = ref<SchedulerTask | null>(null)
const confirmVisible = computed({
  get: () => !!confirmTask.value,
  set: (v: boolean) => {
    if (!v) confirmTask.value = null
  },
})
const runningId = ref('')

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

/* ---------------- 章 01 读数派生（纯 UI 聚合） ---------------- */
const failureCount = computed(() => tasks.value.filter((t) => t.lastSuccess === false).length)
const noRecord = computed(() => tasks.value.length > 0 && tasks.value.every((t) => t.lastSuccess === null))

/** 最近一次运行时间（所有任务里最新的 lastRunAt） */
const latestRunAt = computed(() => {
  const list = tasks.value
    .map((t) => t.lastRunAt)
    .filter((v): v is string => !!v)
    .sort()
  return list.length ? list[list.length - 1] : ''
})

/** 最近的下次运行时间（所有任务里最早的 nextRunAt） */
const earliestNextRunAt = computed(() => {
  const list = tasks.value
    .map((t) => t.nextRunAt)
    .filter(Boolean)
    .sort()
  return list.length ? list[0] : ''
})

const statusCaption = computed(() => {
  if (failureCount.value > 0) return '最近有任务失败'
  if (noRecord.value) return '等待首次运行'
  return '运行正常'
})

function shortTime(v: string | null | undefined): string {
  return v ? v.slice(5, 16) : '--'
}

function statusWord(t: SchedulerTask): string {
  if (t.lastSuccess === null) return '暂无记录'
  return t.lastSuccess ? '成功' : '失败'
}

function markForTask(t: SchedulerTask): 'lit' | 'pink' | 'dim' {
  if (t.lastSuccess === null) return 'dim'
  return t.lastSuccess ? 'lit' : 'pink'
}

/* ---------------- 章 02 密度预算：前 5 行 + 折叠 ---------------- */
const shownTasks = computed(() => tasks.value.slice(0, 5))
const restTasks = computed(() => tasks.value.slice(5))
</script>

<template>
  <div class="page">
    <ToastNotification :message="toastMsg" :type="toastType" @close="toastMsg = null" />

    <AurosNavBar title="定时任务">
      <template #right>
        <button type="button" class="nav-icon" aria-label="返回首页" @click="router.push('/profile')">
          <svg viewBox="0 0 16 16" width="14" height="14" fill="none">
            <path
              d="M2.8 7.4 8 2.9l5.2 4.5M4.1 6.8v6.3h7.8V6.8"
              stroke="currentColor"
              stroke-width="1.2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </button>
      </template>
    </AurosNavBar>

    <main class="body">
      <AurosState
        v-if="pageState !== 'ready'"
        :state="pageState === 'loading' ? 'loading' : 'error'"
        :rows="5"
        :text="pageState === 'error' ? '定时任务列表没有取到' : ''"
        :action-label="pageState === 'error' ? '重试' : ''"
        @action="loadTasks"
      />

      <template v-else>
        <!-- 章 01 STATUS 运行状态 -->
        <AurosChapter label="STATUS" title="运行状态" :gap="false" meta="每日自动运行">
          <AurosStat
            :value="String(failureCount)"
            unit="项失败"
            :caption="statusCaption"
            :pink="failureCount > 0"
            size="lg"
          />
          <AurosGroup class="status-group" title="运行读数">
            <AurosRow label="上次运行" :value="shortTime(latestRunAt)" />
            <AurosRow label="下次运行" :value="shortTime(earliestNextRunAt)" />
            <AurosRow label="任务数" :value="`${tasks.length} 项`" />
          </AurosGroup>
        </AurosChapter>

        <!-- 章 02 TASKS 任务（每任务一行，≤5 行 + 折叠） -->
        <AurosChapter label="TASKS" title="任务" meta="点一行手动执行">
          <template v-if="tasks.length">
            <AurosRow
              v-for="(task, i) in shownTasks"
              :key="task.id"
              class="anim"
              :style="{ '--d': `${i * 36}ms` }"
              :label="task.name"
              :sub="task.cron"
              :value="runningId === task.id ? '执行中' : statusWord(task)"
              :mark="markForTask(task)"
              :tone="task.lastSuccess === null ? 'muted' : 'default'"
              :clickable="runningId !== task.id"
              chevron
              @click="openConfirm(task)"
            />
            <AurosDisclosure v-if="restTasks.length" :count="restTasks.length">
              <AurosRow
                v-for="task in restTasks"
                :key="task.id"
                :label="task.name"
                :sub="task.cron"
                :value="runningId === task.id ? '执行中' : statusWord(task)"
                :mark="markForTask(task)"
                :tone="task.lastSuccess === null ? 'muted' : 'default'"
                :clickable="runningId !== task.id"
                chevron
                @click="openConfirm(task)"
              />
            </AurosDisclosure>
          </template>
          <p v-else class="blank">暂无任务</p>
        </AurosChapter>
      </template>
    </main>

    <!-- 确认执行（AurosSheet） -->
    <AurosSheet v-model="confirmVisible" title="立即执行">
      <p class="sheet__task">{{ confirmTask?.name }}</p>
      <p class="sheet__text">{{ confirmTask ? confirmText(confirmTask) : '' }}</p>
      <div class="sheet__actions">
        <AurosButton variant="ghost" block @click="confirmTask = null">取消</AurosButton>
        <AurosButton variant="primary" block @click="doRun">确认执行</AurosButton>
      </div>
    </AurosSheet>
  </div>
</template>

<style scoped>
.page {
  min-height: 100svh;
  display: flex;
  flex-direction: column;
}
.body {
  flex: 1;
  padding: 8px 16px calc(env(safe-area-inset-bottom, 0px) + 40px);
}
.nav-icon {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: rgba(237, 255, 254, 0.06);
  color: var(--au-silver);
}

/* 章内间距与行入场 */
.status-group {
  margin-top: 18px;
}
.blank {
  margin: 0;
  padding: 14px 0;
  font-size: 13px;
  letter-spacing: 0.02em;
  color: rgba(187, 199, 198, 0.6);
}
.anim {
  animation: rise 440ms var(--au-ease) var(--d, 0ms) both;
}

/* 确认弹层 */
.sheet__task {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.04em;
  color: var(--au-mist);
}
.sheet__text {
  margin: 12px 0 26px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--au-silver);
}
.sheet__actions {
  display: flex;
  gap: 10px;
  padding-bottom: 6px;
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@media (prefers-reduced-motion: reduce) {
  .anim {
    animation: none;
  }
}
</style>
