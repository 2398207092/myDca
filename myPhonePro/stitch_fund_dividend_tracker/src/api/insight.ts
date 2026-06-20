import { get } from './request'

export interface MonthlyInsight {
  richestSource: {
    holdingName: string
    amount: number
  }
  monthlyActivity: {
    payoutCount: number
    fundCount: number
  }
  nextDividend: {
    holdingName: string
    amount: number
    daysRemaining: number
  }
}

export interface MonthlyDetail {
  details: Array<{
    holdingName: string
    amount: number
    code: string
    eventTypes: string[]
  }>
}

export interface AnnualInsight {
  summary: {
    totalDividend: number
    totalPayoutCount: number
    fundCount: number
    peakMonth: string
    peakMonthAmount: number
  }
  monthlyBars: Array<{
    month: number
    amount: number
    percentage: number
  }>
  fundRanks: Array<{
    holdingName: string
    amount: number
    percentage: number
    rank: number
  }>
}

export async function getMonthlyInsight(year: number, month: number): Promise<MonthlyInsight> {
  return get<MonthlyInsight>('/insights/monthly', { year, month })
}

export async function getMonthlyDetail(year: number, month: number): Promise<MonthlyDetail> {
  return get<MonthlyDetail>('/insights/monthly-detail', { year, month })
}

export async function getAnnualInsight(year: number): Promise<AnnualInsight> {
  return get<AnnualInsight>('/insights/annual', { year })
}
