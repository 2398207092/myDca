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

export async function getAssetOverview(): Promise<AssetOverview> {
  return get<AssetOverview>('/asset-overview')
}

export async function takeSnapshot(): Promise<void> {
  return post<void>('/asset-overview/snapshot')
}
