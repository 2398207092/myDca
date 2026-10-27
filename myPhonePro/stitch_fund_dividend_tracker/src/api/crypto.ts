import { get, post } from './request'

export interface CryptoPrice {
  symbol: string
  /** USDT 计价（≈USD） */
  usdtPrice: number | null
  /** 折算后的人民币价格 */
  cnyPrice: number | null
  source: string | null
  updatedAt: string | null
}

export interface RefreshCryptoPricesResp {
  prices: CryptoPrice[]
  updatedHoldings: number
  refreshedAt: string
  /** true = 所有数据源失败，返回的是旧价 */
  stale: boolean
  message: string
}

/** 手动刷新加密币价格（首次可能要等 OKX 超时，约 30 秒） */
export function refreshCryptoPrices(): Promise<RefreshCryptoPricesResp> {
  return post<RefreshCryptoPricesResp>('/crypto/refresh-prices')
}

/** 只读当前价格，不发外部请求 */
export function getCryptoPrices(): Promise<CryptoPrice[]> {
  return get<CryptoPrice[]>('/crypto/prices')
}
