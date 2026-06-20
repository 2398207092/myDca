import { get, post } from './request'

export interface CategoryItem {
  name: string
  type: string
  value: number
  percentage: number
  color: string
  items: HoldingItem[]
}

export interface HoldingItem {
  id: string
  name: string
  value: number
}

export interface AssetOverview {
  totalValue: number
  cashValue: number
  cryptoValue: number
  usStockValue: number
  goldValue: number
  dividendValue: number
  weeklyChange: number
  weeklyChangePercent: number
  monthlyChange: number
  monthlyChangePercent: number
  categories: CategoryItem[]
}

export interface HistoryPoint {
  date: string
  value: number
}

export interface AssetHistory {
  series: HistoryPoint[]
  totalChange: number
  totalChangePercent: number
  newInvestment: number
  dividendIncome: number
}

export async function getAssetOverview(): Promise<AssetOverview> {
  return get<AssetOverview>('/asset-overview')
}

export async function getAssetHistory(range: 'week' | 'month' = 'week'): Promise<AssetHistory> {
  return get<AssetHistory>('/asset-overview/history', { range })
}

export async function takeSnapshot(): Promise<void> {
  return post<void>('/asset-overview/snapshot')
}
