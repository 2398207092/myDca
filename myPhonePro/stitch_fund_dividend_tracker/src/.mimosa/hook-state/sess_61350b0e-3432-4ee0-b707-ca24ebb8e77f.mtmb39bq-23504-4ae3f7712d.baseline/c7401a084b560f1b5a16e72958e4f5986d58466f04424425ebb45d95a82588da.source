import { get, post } from './request'

/**
 * 获取定时任务总览（仅管理员）
 */
export async function getSchedulerTasks(): Promise<SchedulerTask[]> {
  return get<SchedulerTask[]>('/scheduler/tasks')
}

/**
 * 手动触发一次定时任务（仅管理员，同一任务 5 分钟节流）
 */
export async function runSchedulerTask(id: string): Promise<SchedulerRunResult> {
  return post<SchedulerRunResult>(`/scheduler/tasks/${id}/run`)
}

// ==================== 类型定义 ====================

export interface SchedulerTask {
  id: string
  name: string
  cron: string
  description: string
  nextRunAt: string
  /** null = 暂无执行记录 */
  lastSuccess: boolean | null
  lastRunAt: string | null
  lastDurationMs: number | null
  lastDetail: string | null
}

export interface SchedulerRunResult {
  taskId: string
  taskName: string
  success: boolean
  durationMs: number
  detail: string
}
