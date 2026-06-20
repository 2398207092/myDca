import { get, post, put, del } from './request'

export interface LiveExpenseItem {
  id: string
  name: string
  icon: string
  monthlyAmount: number
  sortOrder: number
}

export interface CreateExpenseReq {
  name: string
  icon: string
  monthlyAmount: number
  sortOrder?: number
}

export interface UpdateExpenseReq {
  name?: string
  icon?: string
  monthlyAmount?: number
  sortOrder?: number
}

export interface ExpenseCoverageItem {
  id: string
  name: string
  icon: string
  annualAmount: number
  covered: boolean
  inProgress: boolean
}

export interface MilestoneInfo {
  name: string
  icon: string
  requiredExpenses: number
  description: string
}

export interface CoverageData {
  totalExpenses: number
  coveredExpenses: number
  totalAnnualExpense: number
  predictedAnnualDividend: number
  totalDividendReceived: number
  remainingDividend: number
  expenses: ExpenseCoverageItem[]
  currentMilestone: MilestoneInfo
  currentMilestoneIndex: number
  nextMilestoneName: string
  nextMilestoneRemaining: number
}

export async function listExpenses(): Promise<LiveExpenseItem[]> {
  return get<LiveExpenseItem[]>('/expenses')
}

export async function createExpense(req: CreateExpenseReq): Promise<LiveExpenseItem> {
  return post<LiveExpenseItem>('/expenses', req)
}

export async function updateExpense(id: string, req: UpdateExpenseReq): Promise<LiveExpenseItem> {
  return put<LiveExpenseItem>(`/expenses/${id}`, req)
}

export async function deleteExpense(id: string): Promise<void> {
  return del<void>(`/expenses/${id}`)
}

export async function getCoverageData(): Promise<CoverageData> {
  return get<CoverageData>('/expenses/coverage')
}
