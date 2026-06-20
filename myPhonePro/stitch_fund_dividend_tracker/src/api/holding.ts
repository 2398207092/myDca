import { get, post, put, del } from './request'

export interface HoldingItem {
  id: string
  name: string
  code: string
  type: string
  costAlgorithm: string
  shares: number
  costPerShare: number
  cost: number
  marketValue: number
  latestPrice: number
  priceDate: string
  predictedDividend: number
  dividendRate: number
  priceDividendRate: number
  totalDividendReceived: number
  netInvestment: number
  dividendRecoveryRate: number
  estimatedRecoveryYears: number
  color: string
  assetCategory?: string
}

export interface CreateHoldingReq {
  name: string
  code: string
  type: string
  costAlgorithm?: string
  shares: number
  cost: number
  assetCategory?: string
}

export interface HoldingSearchResult {
  code: string
  name: string
  type: string
  pinyin: string
  netWorth: string
  fullName: string
}

export interface UpdateHoldingReq {
  name?: string
  costAlgorithm?: string
  shares?: number
  cost?: number
  marketValue?: number
  assetCategory?: string
}

export interface ForecastPoint {
  label: string
  value: number
}

export interface ForecastData {
  holdingId: string
  period: string
  series: ForecastPoint[]
  trendPercentage: number
}

export async function listHoldings(type?: string, keyword?: string): Promise<HoldingItem[]> {
  return get<HoldingItem[]>('/holdings', { type, keyword })
}

export async function getHolding(id: string): Promise<HoldingItem> {
  return get<HoldingItem>(`/holdings/${id}`)
}

export async function createHolding(req: CreateHoldingReq): Promise<HoldingItem> {
  return post<HoldingItem>('/holdings', req)
}

export async function searchHoldings(keyword: string): Promise<HoldingSearchResult[]> {
  return get<HoldingSearchResult[]>('/holdings/search', { keyword })
}

export async function updateHolding(id: string, req: UpdateHoldingReq): Promise<HoldingItem> {
  return put<HoldingItem>(`/holdings/${id}`, req)
}

export async function deleteHolding(id: string): Promise<{ success: boolean }> {
  return del<{ success: boolean }>(`/holdings/${id}`)
}

export async function getForecast(id: string, period: string = '12m'): Promise<ForecastData> {
  return get<ForecastData>(`/holdings/${id}/forecast`, { period })
}

export interface DividendInfo {
  annualDividendPerShare: number
  unitText: string
  source: string
}

export async function getDividendInfo(code: string, type?: string, method?: string, horizon?: string): Promise<DividendInfo> {
  return get<DividendInfo>('/holdings/dividend-info', { code, type, method, horizon })
}

export async function updateHoldingCategory(id: string, assetCategory: string): Promise<HoldingItem> {
  return put<HoldingItem>(`/holdings/${id}/category`, { assetCategory })
}

export interface HoldingChangeDetail {
  holdingId: string
  name: string
  code: string
  change: number
  percent: number
  currentValue: number
  pastValue: number
}

export interface PeriodChange {
  change: number
  percent: number
  pastValue: number
  details: HoldingChangeDetail[]
}

export interface ValueChangeResult {
  currentValue: number
  periods: Record<string, PeriodChange>
}

export async function getValueChange(): Promise<ValueChangeResult> {
  return get<ValueChangeResult>('/holdings/value-change')
}
