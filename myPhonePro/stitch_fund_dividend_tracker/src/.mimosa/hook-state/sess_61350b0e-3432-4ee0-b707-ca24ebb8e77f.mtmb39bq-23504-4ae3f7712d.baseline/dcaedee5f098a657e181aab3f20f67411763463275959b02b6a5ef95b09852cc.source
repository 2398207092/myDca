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

export async function listTransactions(holdingId?: string): Promise<TransactionItem[]> {
  return get<TransactionItem[]>('/transactions', { holdingId })
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
