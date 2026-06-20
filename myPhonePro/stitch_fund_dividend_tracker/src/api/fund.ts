import { post, get } from './request'

export interface FundDividendRecord {
  id: string
  fundCode: string
  exDate: string
  regDate: string | null
  payDate: string | null
  dividendPerShare: number | null
  dividendYear: number | null
  source: string
}

export interface RefreshResult {
  fundCode: string
  newRecords: number
  totalRecords: number
}

export interface RefreshAllResult {
  totalNewRecords: number
}

/**
 * 手动刷新指定基金的分红数据（从天天基金抓取）
 */
export async function refreshFundDividends(code: string): Promise<RefreshResult> {
  return post<RefreshResult>(`/funds/${code}/dividends/refresh`, {})
}

/**
 * 手动刷新所有持仓基金的分红数据
 */
export async function refreshAllFundDividends(): Promise<RefreshAllResult> {
  return post<RefreshAllResult>('/funds/dividends/refresh-all', {})
}

/**
 * 查询基金的历史分红记录（从本地数据库）
 */
export async function getFundDividendRecords(code: string): Promise<FundDividendRecord[]> {
  return get<FundDividendRecord[]>(`/funds/${code}/dividends`)
}