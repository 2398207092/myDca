import { get, post } from './request'

// ========== 类型定义（与后端契约一致） ==========

/** 总资产走势数据点 */
export interface TotalAssetPoint {
  date: string
  totalMarketValue: number
  totalShares: number
  totalCostBasis: number
  totalProfitLoss: number
  totalProfitLossPct: number
}

/** 总资产走势响应 */
export interface TotalAssetSeries {
  series: TotalAssetPoint[]
  totalChange: number
  totalChangePercent: number
}

/** 持仓基本信息 */
export interface HoldingInfo {
  id: string
  name: string
  code: string
  assetCategory: string
}

/** 单持仓走势数据点 */
export interface HoldingPoint {
  date: string
  marketValue: number
  shares: number
  costBasis: number
  profitLoss: number
  profitLossPct: number
  pctOfTotal: number
}

/** 单持仓走势响应 */
export interface HoldingSeries {
  holding: HoldingInfo
  series: HoldingPoint[]
}

/** 快照摘要（用于 vs 上期） */
export interface SnapshotSummary {
  date: string
  marketValue: number
  shares: number
}

/** 单持仓 vs 上期变化 */
export interface HoldingDiff {
  holdingId: string
  current: SnapshotSummary
  previous: SnapshotSummary
  marketValueChange: number
  marketValueChangePct: number
  sharesChange: number
  sharesChangePct: number
  pctOfTotalChange: number
}

/** 年化收益率 */
export interface AnnualizedReturn {
  holdingId: string
  annualizedReturn: number | null
  totalInvested: number
  totalWithdrawn: number
  currentValue: number
  holdingDays: number
  firstTransactionDate: string
  irr: number | null
}

export type HistoryRange = 'month' | 'quarter' | 'all'

// ========== API 调用 ==========

/** 总资产走势 */
export function getAssetOverview(range: HistoryRange): Promise<TotalAssetSeries> {
  return get<TotalAssetSeries>('/asset-history/overview', { range })
}

/** 单持仓走势 */
export function getHoldingSeries(holdingId: string, range: HistoryRange): Promise<HoldingSeries> {
  return get<HoldingSeries>(`/asset-history/holding/${holdingId}`, { range })
}

/** 单持仓 vs 上期变化 */
export function getHoldingDiff(holdingId: string): Promise<HoldingDiff> {
  return get<HoldingDiff>(`/asset-history/holding/${holdingId}/diff`)
}

/** 单持仓年化收益率 */
export function getAnnualizedReturn(holdingId: string): Promise<AnnualizedReturn> {
  return get<AnnualizedReturn>(`/asset-history/holding/${holdingId}/annualized`)
}

/** 手动触发快照 */
export function triggerSnapshot(): Promise<void> {
  return post<void>('/asset-history/snapshot')
}
