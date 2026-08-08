import { get } from './request'

/**
 * 获取有监控日志的日期列表
 */
export async function getMonitorDates(): Promise<MonitorDatesResp> {
  return get<string[]>('/monitor/dates')
}

/**
 * 获取指定日期的监控日志内容
 */
export async function getMonitorContent(date: string): Promise<MonitorContent> {
  return get<MonitorContent>('/monitor/content', { date })
}

// ==================== 类型定义 ====================

export type MonitorDatesResp = string[]

export interface MonitorLogEntry {
  taskName: string
  success: boolean
  durationMs: number
  detail: string
}

export interface MonitorContent {
  date: string
  totalCount: number
  failCount: number
  summary: string
  entries: MonitorLogEntry[]
}
