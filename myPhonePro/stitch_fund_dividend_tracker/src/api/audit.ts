import { get } from './request'
import type { AuditDatesResp, AuditContentResp } from '@/types/audit'

/**
 * 获取有审计日志的日期列表
 */
export async function getAuditDates(): Promise<AuditDatesResp> {
  return get<string[]>('/audit/dates')
}

/**
 * 获取指定日期的审计日志内容
 */
export async function getAuditContent(date: string): Promise<AuditContentResp> {
  return get<AuditContent>('/audit/content', { date })
}

// ==================== 类型定义 ====================

export type AuditDatesResp = string[]

export interface AuditEntry {
  level: 'error' | 'warning' | 'info'
  message: string
}

export interface AuditContent {
  date: string
  hasContent: boolean
  errorCount: number
  warningCount: number
  summary: string
  entries: AuditEntry[]
}

export type AuditContentResp = AuditContent
