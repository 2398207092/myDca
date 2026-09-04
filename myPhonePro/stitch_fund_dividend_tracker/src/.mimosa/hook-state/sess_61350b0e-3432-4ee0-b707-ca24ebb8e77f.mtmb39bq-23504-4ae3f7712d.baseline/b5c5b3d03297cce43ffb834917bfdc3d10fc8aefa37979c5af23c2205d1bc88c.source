// ============================================================
// 共享业务类型（仅保留被消费的类型）
// 各 api/*.ts 模块自定接口类型，此处仅放多个页面共享的类型
// ============================================================

// === 分红事件 ===
export type DividendEventType = 'registration' | 'ex_dividend' | 'payout' | 'announcement'

export interface DividendEvent {
  id: string
  holdingId: string
  holdingName: string
  type: DividendEventType
  date: string // YYYY-MM-DD
  amount: number
  status: string
  description: string
  participated: boolean
  converted?: boolean
}

// === 页面状态 ===
export type PageState = 'loading' | 'ready' | 'empty' | 'error'

// === 底部导航 Tab ===
export type NavTab = 'holdings' | 'calendar' | 'discover' | 'profile'
