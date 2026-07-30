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

export async function markDistributed(id: string): Promise<DividendEventItem> {
  return put<DividendEventItem>(`/events/${id}/distribute`)
}

export async function convertEventToReinvest(id: string): Promise<DividendEventItem> {
  return post<DividendEventItem>(`/events/${id}/convert-to-reinvest`)
}

export async function syncAllEvents(): Promise<{ totalCreated: number }> {
  return post<{ totalCreated: number }>('/events/sync-all')
}
