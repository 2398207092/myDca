import { get, post, put } from './request'

export interface DividendEventItem {
  id: string
  holdingId: string
  holdingName: string
  type: string
  date: string
  amount: number
  status: string
  description: string
  participated: boolean
  converted: boolean
}

export interface CreateEventReq {
  holdingId: string
  /** registration / ex_dividend / payout / announcement */
  type: string
  date: string
  amount?: number
  description?: string
}

export interface CancelEventResp {
  id: string
  status: string
  updatedAt: string
}

export interface EventQuery {
  holdingId?: string
  /** YYYY-MM */
  month?: string
  dateFrom?: string
  dateTo?: string
  type?: string
  status?: string
}

export async function listEvents(params?: EventQuery): Promise<DividendEventItem[]> {
  return get<DividendEventItem[]>('/events', params as Record<string, string | undefined>)
}

/** 按日期取分红事件（今天/某天） */
export async function listEventsByDate(date: string): Promise<DividendEventItem[]> {
  return get<DividendEventItem[]>(`/events/date/${date}`)
}

export async function createEvent(req: CreateEventReq): Promise<DividendEventItem> {
  return post<DividendEventItem>('/events', req)
}

export async function markDistributed(id: string): Promise<DividendEventItem> {
  return put<DividendEventItem>(`/events/${id}/distribute`)
}

export async function convertEventToReinvest(id: string): Promise<DividendEventItem> {
  return post<DividendEventItem>(`/events/${id}/convert-to-reinvest`)
}

export async function cancelEvent(id: string): Promise<CancelEventResp> {
  return put<CancelEventResp>(`/events/${id}/cancel`)
}

export async function syncAllEvents(): Promise<{ totalCreated: number }> {
  return post<{ totalCreated: number }>('/events/sync-all')
}

export async function syncEventsByFund(fundCode: string): Promise<{ fundCode: string; created: number }> {
  return post<{ fundCode: string; created: number }>(`/events/sync/${fundCode}`)
}
