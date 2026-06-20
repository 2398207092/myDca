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
}

export interface CreateEventReq {
  holdingId: string
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

export async function listEvents(params?: {
  holdingId?: string
  month?: string
  dateFrom?: string
  dateTo?: string
  type?: string
  status?: string
}): Promise<DividendEventItem[]> {
  return get<DividendEventItem[]>('/events', params as Record<string, string | undefined>)
}

export async function getEventsByDate(date: string): Promise<DividendEventItem[]> {
  return get<DividendEventItem[]>(`/events/date/${date}`)
}

export async function createEvent(req: CreateEventReq): Promise<DividendEventItem> {
  return post<DividendEventItem>('/events', req)
}

export async function markDistributed(id: string): Promise<DividendEventItem> {
  return put<DividendEventItem>(`/events/${id}/distribute`)
}

export async function cancelEvent(id: string): Promise<CancelEventResp> {
  return put<CancelEventResp>(`/events/${id}/cancel`)
}

export async function getDividendRecords(params?: {
  holdingId?: string
  year?: number
  status?: string
}): Promise<DividendEventItem[]> {
  return get<DividendEventItem[]>('/dividend-records', params as Record<string, string | number | undefined>)
}

export async function syncAllEvents(): Promise<{ totalCreated: number }> {
  return post<{ totalCreated: number }>('/events/sync-all')
}

export async function syncEventsByFund(fundCode: string): Promise<{ fundCode: string; created: number }> {
  return post<{ fundCode: string; created: number }>(`/events/sync/${fundCode}`)
}
