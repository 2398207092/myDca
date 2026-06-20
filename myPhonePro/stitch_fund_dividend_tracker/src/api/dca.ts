import { get, post, put, del } from './request'

export interface DcaPlanVO {
  id: string
  holdingId: string
  holdingName: string
  holdingCode: string
  amount: number
  frequency: string
  day: number | null
  tradingMarket: string
  status: string
  totalInvested: number
  totalShares: number
  totalExecutions: number
  nextExecutionDate: string
  lastExecutedAt: string | null
  startedAt: string
  endedAt: string | null
  createdAt: string
}

export interface CreateDcaPlanReq {
  holdingId: string
  amount: number
  frequency: string
  day?: number | null
}

export interface UpdateDcaPlanReq {
  amount?: number
  frequency?: string
  day?: number | null
  status?: string
}

export interface DcaExecutionResultVO {
  transactionId: string
  amount: number
  quantity: number
  navPrice: number
  navDate: string
  executionDate: string
  holdingName: string
  holdingCode: string
}

export interface DcaBudgetVO {
  month: string
  tradingDays: number
  totalAmount: number
  plans: DcaBudgetItem[]
}

export interface DcaBudgetItem {
  holdingName: string
  frequency: string
  amount: number
  executions: number
  budgetAmount: number
}

export async function getDcaBudget(year: number, month: number): Promise<DcaBudgetVO> {
  return get<DcaBudgetVO>('/dca-plans/budget', { year, month })
}

export async function listDcaPlans(holdingId?: string): Promise<DcaPlanVO[]> {
  return get<DcaPlanVO[]>('/dca-plans', { holdingId })
}

export async function getDcaPlan(id: string): Promise<DcaPlanVO> {
  return get<DcaPlanVO>(`/dca-plans/${id}`)
}

export async function createDcaPlan(req: CreateDcaPlanReq): Promise<DcaPlanVO> {
  return post<DcaPlanVO>('/dca-plans', req)
}

export async function updateDcaPlan(id: string, req: UpdateDcaPlanReq): Promise<DcaPlanVO> {
  return put<DcaPlanVO>(`/dca-plans/${id}`, req)
}

export async function deleteDcaPlan(id: string): Promise<{ success: boolean }> {
  return del<{ success: boolean }>(`/dca-plans/${id}`)
}

export async function executeDcaPlan(id: string): Promise<DcaExecutionResultVO> {
  return post<DcaExecutionResultVO>(`/dca-plans/${id}/execute`)
}
