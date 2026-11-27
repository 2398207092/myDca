import { get, post, put, del } from './request'

/** 预算类型（年度额度 + 今年已用） */
export interface BudgetCategoryItem {
  id: string
  name: string
  amount: number
  spent: number
  remaining: number
  recordCount: number
  sortOrder: number
}

/** 年度预算总览 */
export interface BudgetOverview {
  year: number
  totalAmount: number
  totalSpent: number
  remaining: number
  usagePercent: number
  categories: BudgetCategoryItem[]
}

/** 一笔记账 */
export interface BudgetRecordItem {
  id: string
  categoryId: string
  categoryName: string
  /** yyyy-MM-dd */
  occurDate: string
  amount: number
  note: string
}

export interface CreateBudgetCategoryReq {
  year: number
  name: string
  amount: number
}

export interface UpdateBudgetCategoryReq {
  name?: string
  amount?: number
}

export interface CreateBudgetRecordReq {
  categoryId: string
  occurDate: string
  amount: number
  note?: string
}

export function getBudgetOverview(year: number): Promise<BudgetOverview> {
  return get<BudgetOverview>('/budget/overview', { year })
}

export function listBudgetCategories(year: number): Promise<BudgetCategoryItem[]> {
  return get<BudgetCategoryItem[]>('/budget/categories', { year })
}

export function createBudgetCategory(req: CreateBudgetCategoryReq): Promise<BudgetCategoryItem> {
  return post<BudgetCategoryItem>('/budget/categories', req)
}

export function updateBudgetCategory(id: string, req: UpdateBudgetCategoryReq): Promise<BudgetCategoryItem> {
  return put<BudgetCategoryItem>(`/budget/categories/${id}`, req)
}

export function deleteBudgetCategory(id: string): Promise<void> {
  return del<void>(`/budget/categories/${id}`)
}

export function listBudgetRecords(year: number): Promise<BudgetRecordItem[]> {
  return get<BudgetRecordItem[]>('/budget/records', { year })
}

export function createBudgetRecord(req: CreateBudgetRecordReq): Promise<BudgetRecordItem> {
  return post<BudgetRecordItem>('/budget/records', req)
}

export function deleteBudgetRecord(id: string): Promise<void> {
  return del<void>(`/budget/records/${id}`)
}
