import { post } from './request'

/**
 * 手动刷新所有持仓基金的分红数据
 */
export async function refreshAllFundDividends(): Promise<void> {
  await post<void>('/funds/dividends/refresh-all', {})
}