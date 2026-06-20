// === 用户 ===
export interface UserProfile {
  id: string
  name: string
  avatar: string
  membership: 'pro' | 'free'
  membershipExpiry: string
  phone: string
}

// === 持仓 ===
export interface Holding {
  id: string
  name: string
  code: string
  type: string  // 'fund' | 'cny_asset' | 'ETF' | 'A股' | 等
  shares: number
  cost: number
  marketValue: number
  predictedDividend: number
  dividendRate: number // 成本息率
  priceDividendRate: number // 股价息率
  totalDividendReceived: number
  netInvestment: number
  dividendRecoveryRate: number // 回本进度（0-100）
  estimatedRecoveryYears: number
  color: string
}

// === 分红事件 ===
export type DividendEventType = 'registration' | 'ex_dividend' | 'payout' | 'announcement'
export type DividendEventStatus = 'pending' | 'distributed' | 'cancelled'

export interface DividendEvent {
  id: string
  holdingId: string
  holdingName: string
  type: DividendEventType
  date: string // YYYY-MM-DD
  amount: number
  status: DividendEventStatus
  description: string
}

// === 交易 ===
export type TransactionType = 'buy' | 'sell' | 'bonus_share' | 'reinvest'

export interface Transaction {
  id: string
  holdingId: string
  type: TransactionType
  date: string
  quantity: number
  price: number
  fee: number
  total: number
}

// === 汇率 ===
export interface ExchangeRate {
  pair: string
  label: string
  rate: number
  updatedAt: string
}

// === 日历 ===
export interface CalendarDay {
  date: string
  day: number
  isCurrentMonth: boolean
  events: DividendEvent[]
  isSelected: boolean
}

// === 分红覆盖类目 ===
export interface CoverageCategory {
  id: string
  name: string
  icon: string
  percentage: number
  color: string
}

// === 分红预测 ===
export interface DividendForecast {
  year: number
  amount: number
}

// === 页面状态 ===
export type PageState = 'loading' | 'ready' | 'empty' | 'error'

// === 底部导航 Tab ===
export type NavTab = 'holdings' | 'calendar' | 'discover' | 'profile'
