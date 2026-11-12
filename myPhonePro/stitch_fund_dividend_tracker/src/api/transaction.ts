import { get, post, put, del } from './request'

export interface TransactionItem {
  id: string
  holdingId: string
  type: string
  date: string
  quantity: number
  price: number
  fee: number
  total: number
  source?: string
  dcaPlanId?: string
}

export interface CreateTransactionReq {
  holdingId: string
  type: string
  date: string
  quantity: number
  price: number
  fee?: number
}

export interface UpdateTransactionReq {
  type?: string
  date?: string
  quantity?: number
  price?: number
  fee?: number
}

export interface TransactionQuery {
  holdingId?: string
  type?: string
  dateFrom?: string
  dateTo?: string
}

export async function listTransactions(params?: TransactionQuery | string): Promise<TransactionItem[]> {
  // 兼容旧调用：传字符串视为 holdingId
  const query = typeof params === 'string' ? { holdingId: params } : params
  return get<TransactionItem[]>('/transactions', query as Record<string, string | undefined>)
}

/**
 * 按日期区间取交易。
 * 注意（2026-11-13 实测）：后端 /transactions 的 dateFrom/dateTo 参数没有生效，
 * 返回的是全量；这里在客户端按 date 兜底过滤，避免「今日事件」出现历史交易。
 */
export async function listTransactionsInRange(
  dateFrom: string,
  dateTo: string,
  params: Omit<TransactionQuery, 'dateFrom' | 'dateTo'> = {},
): Promise<TransactionItem[]> {
  const list = await listTransactions(params)
  return list.filter((t) => t.date >= dateFrom && t.date <= dateTo)
}

export async function createTransaction(req: CreateTransactionReq): Promise<TransactionItem> {
  return post<TransactionItem>('/transactions', req)
}

export async function updateTransaction(id: string, req: UpdateTransactionReq): Promise<TransactionItem> {
  return put<TransactionItem>(`/transactions/${id}`, req)
}

export async function deleteTransaction(id: string): Promise<{ success: boolean }> {
  return del<{ success: boolean }>(`/transactions/${id}`)
}
