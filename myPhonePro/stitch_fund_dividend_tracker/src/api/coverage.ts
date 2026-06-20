import { get, put } from './request'

export interface CoverageCategoryItem {
  id: string
  name: string
  icon: string
  percentage: number
  color: string
}

export async function listCoverageCategories(): Promise<CoverageCategoryItem[]> {
  return get<CoverageCategoryItem[]>('/coverage-categories')
}
