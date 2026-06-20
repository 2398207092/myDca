import { get } from './request'

export interface DashboardData {
  consecutiveDays: number
  predictedAnnualDividend: number
  tenYearExpectedReturn: number
  monthlyPredictedDividend: number
  monthlyMessage: string
  totalHoldings: number
  coveredCategories: number
  totalDividendReceived: number
  totalCost: number
  totalMarketValue: number
  overallDividendRate: number
  priceDividendRate: number
}

export async function getDashboard(): Promise<DashboardData> {
  return get<DashboardData>('/dashboard')
}
