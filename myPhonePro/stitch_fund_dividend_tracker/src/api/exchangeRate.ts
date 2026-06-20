import { get, post } from './request'

export interface ExchangeRateItem {
  pair: string
  label: string
  rate: number
  updatedAt: string
}

export interface RefreshRatesResp {
  rates: ExchangeRateItem[]
  refreshedAt: string
}

export async function listExchangeRates(): Promise<ExchangeRateItem[]> {
  return get<ExchangeRateItem[]>('/exchange-rates')
}

export async function refreshExchangeRates(): Promise<RefreshRatesResp> {
  return post<RefreshRatesResp>('/exchange-rates/refresh')
}
