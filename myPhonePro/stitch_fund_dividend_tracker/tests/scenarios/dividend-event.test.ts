/**
 * ============================================================
 * L1 场景集成测试：分红事件域
 * ============================================================
 *
 * 覆盖分红事件完整生命周期：
 *   E1: 创建 pending 事件 → 标记到账 → 持仓 totalDividendReceived 增加
 *   E2: 标记到账 → 现金增加（跨域联动）
 *   E3: 创建 pending → 取消 → 状态变 cancelled
 *   E4: 已到账事件不可取消（负面测试）
 *   E5: 到账后持仓 netInvestment 减少（不变量验证）
 *   E6: 同一持仓多个事件分别到账
 *
 * 关键后端逻辑（已读 EventService.java 确认）：
 *   - createEvent: status = pending
 *   - markDistributed: status → distributed, holding.totalDividendReceived += amount
 *   - markDistributed: 同时调 manualAssetService.adjustCash（分红到账加现金）
 *   - cancelEvent: 只能取消 pending 的，distributed 的不可取消
 *   - cancelEvent: 不修改持仓数据（没有回滚 totalDividendReceived）
 */

import { describe, it, expect, beforeEach } from 'vitest'
import {
  makeHolding,
  makeTransaction,
  checkAllInvariants,
  resetIdCounter,
  type HoldingData,
  type TransactionData,
} from '../invariants/engine'

beforeEach(() => {
  resetIdCounter()
})

// ============================================================
// 类型定义
// ============================================================

type EventStatus = 'pending' | 'distributed' | 'cancelled'
type EventType = 'registration' | 'ex_dividend' | 'payout' | 'announcement'

interface DividendEvent {
  id: string
  holdingId: string
  holdingName: string
  type: EventType
  date: string
  amount: number
  status: EventStatus
}

interface CashAccount {
  id: string
  balance: number
}

let _eventIdCounter = 0
function nextEventId(): string {
  return `evt_${++_eventIdCounter}`
}

// ============================================================
// 模拟后端操作
// ============================================================

/**
 * 创建持仓（带初始买入）
 */
function createHoldingWithBuy(params: {
  shares: number
  cost: number
  latestPrice?: number
}): { holding: HoldingData; transaction: TransactionData } {
  const holding = makeHolding({
    shares: params.shares,
    costPerShare: params.cost / params.shares,
    cost: params.cost,
    latestPrice: params.latestPrice ?? 0,
    marketValue: params.shares * (params.latestPrice ?? 0),
    netInvestment: params.cost,
    predictedDividend: 0,
    dividendRate: 0,
    totalDividendReceived: 0,
  })
  const transaction = makeTransaction({
    holdingId: holding.id,
    type: 'buy',
    quantity: params.shares,
    price: params.cost / params.shares,
    fee: 0,
  })
  return { holding, transaction }
}

/**
 * 模拟创建分红事件。
 * 后端 createEvent: status = pending
 */
function simulateCreateEvent(params: {
  holdingId: string
  holdingName: string
  type: EventType
  date: string
  amount: number
}): DividendEvent {
  return {
    id: nextEventId(),
    holdingId: params.holdingId,
    holdingName: params.holdingName,
    type: params.type,
    date: params.date,
    amount: params.amount,
    status: 'pending',
  }
}

/**
 * 模拟标记到账。
 * 后端 markDistributed:
 *   1. status → distributed
 *   2. holding.totalDividendReceived += amount
 *   3. manualAssetService.adjustCash（现金增加）
 */
function simulateMarkDistributed(params: {
  event: DividendEvent
  holding: HoldingData
  cash?: CashAccount
}): { event: DividendEvent; holding: HoldingData; cash?: CashAccount } {
  const { event, holding, cash } = params

  if (event.status !== 'pending') {
    throw new Error(`事件状态不是 pending: ${event.status}`)
  }

  const distributedEvent: DividendEvent = { ...event, status: 'distributed' }

  const newHolding: HoldingData = {
    ...holding,
    totalDividendReceived: holding.totalDividendReceived + event.amount,
    netInvestment: holding.netInvestment - event.amount,
  }

  let newCash: CashAccount | undefined
  if (cash) {
    newCash = { ...cash, balance: cash.balance + event.amount }
  }

  return { event: distributedEvent, holding: newHolding, cash: newCash }
}

/**
 * 模拟取消事件。
 * 后端 cancelEvent:
 *   1. 只能取消 pending 的
 *   2. status → cancelled
 *   3. 不修改持仓数据
 */
function simulateCancelEvent(event: DividendEvent): DividendEvent | null {
  if (event.status === 'distributed') {
    return null // 已到账不可取消
  }
  if (event.status === 'cancelled') {
    return null // 已取消不可重复取消
  }
  return { ...event, status: 'cancelled' }
}

// ============================================================
// E1: 创建 → 到账 → 持仓数据更新
// ============================================================
describe('E1: 分红事件创建→到账', () => {
  it('pending 事件标记到账后，持仓 totalDividendReceived 增加', () => {
    const { holding, transaction } = createHoldingWithBuy({
      shares: 1000, cost: 1500, latestPrice: 1.65,
    })
    const transactions: TransactionData[] = [transaction]

    // 创建分红事件
    const event = simulateCreateEvent({
      holdingId: holding.id,
      holdingName: '测试基金',
      type: 'payout',
      date: '2025-06-29',
      amount: 200,
    })
    expect(event.status).toBe('pending')

    // 标记到账
    const result = simulateMarkDistributed({ event, holding })
    expect(result.event.status).toBe('distributed')
    expect(result.holding.totalDividendReceived).toBe(200)
    expect(result.holding.netInvestment).toBe(1500 - 200)

    // 不变量验证
    const checkResult = checkAllInvariants(result.holding, transactions)
    expect(checkResult.passed).toBe(true)
  })

  it('金额为 0 的事件到账不影响持仓', () => {
    const { holding } = createHoldingWithBuy({
      shares: 1000, cost: 1500,
    })

    const event = simulateCreateEvent({
      holdingId: holding.id,
      holdingName: '测试基金',
      type: 'announcement',
      date: '2025-06-29',
      amount: 0,
    })

    const result = simulateMarkDistributed({ event, holding })
    expect(result.holding.totalDividendReceived).toBe(0)
    expect(result.holding.netInvestment).toBe(1500)
  })
})

// ============================================================
// E2: 到账 → 现金增加（跨域联动）
// ============================================================
describe('E2: 分红到账 → 现金联动', () => {
  it('分红到账后现金余额增加', () => {
    const { holding } = createHoldingWithBuy({
      shares: 1000, cost: 1500, latestPrice: 1.65,
    })
    const cash: CashAccount = { id: 'cash_1', balance: 5000 }

    const event = simulateCreateEvent({
      holdingId: holding.id,
      holdingName: '测试基金',
      type: 'payout',
      date: '2025-06-29',
      amount: 350,
    })

    const result = simulateMarkDistributed({ event, holding, cash })
    expect(result.cash!.balance).toBe(5350)
  })

  it('多次分红到账，现金累加', () => {
    const { holding } = createHoldingWithBuy({
      shares: 1000, cost: 1500, latestPrice: 1.65,
    })
    let cash: CashAccount = { id: 'cash_1', balance: 5000 }
    let h = holding

    // 三次分红
    const amounts = [200, 150, 300]
    for (const amount of amounts) {
      const event = simulateCreateEvent({
        holdingId: h.id,
        holdingName: '测试基金',
        type: 'payout',
        date: '2025-06-29',
        amount,
      })
      const result = simulateMarkDistributed({ event, holding: h, cash })
      h = result.holding
      cash = result.cash!
    }

    expect(h.totalDividendReceived).toBe(650)
    expect(cash.balance).toBe(5650) // 5000 + 650
  })
})

// ============================================================
// E3: 创建 → 取消
// ============================================================
describe('E3: 取消 pending 事件', () => {
  it('pending 事件可以取消，状态变 cancelled', () => {
    const event = simulateCreateEvent({
      holdingId: 'h_1',
      holdingName: '测试基金',
      type: 'payout',
      date: '2025-06-29',
      amount: 200,
    })

    const cancelled = simulateCancelEvent(event)
    expect(cancelled).not.toBeNull()
    expect(cancelled!.status).toBe('cancelled')
  })

  it('取消 pending 事件不影响持仓数据', () => {
    const { holding } = createHoldingWithBuy({
      shares: 1000, cost: 1500,
    })

    const event = simulateCreateEvent({
      holdingId: holding.id,
      holdingName: '测试基金',
      type: 'payout',
      date: '2025-06-29',
      amount: 200,
    })

    const cancelled = simulateCancelEvent(event)
    expect(cancelled).not.toBeNull()

    // 持仓数据不变（没有到账过）
    expect(holding.totalDividendReceived).toBe(0)
    expect(holding.netInvestment).toBe(1500)
  })
})

// ============================================================
// E4: 已到账事件不可取消
// ============================================================
describe('E4: 已到账事件不可取消', () => {
  it('distributed 事件取消失败', () => {
    const { holding } = createHoldingWithBuy({
      shares: 1000, cost: 1500,
    })

    const event = simulateCreateEvent({
      holdingId: holding.id,
      holdingName: '测试基金',
      type: 'payout',
      date: '2025-06-29',
      amount: 200,
    })

    // 先到账
    const distributed = simulateMarkDistributed({ event, holding })
    expect(distributed.event.status).toBe('distributed')

    // 再尝试取消 → 失败
    const cancelled = simulateCancelEvent(distributed.event)
    expect(cancelled).toBeNull()
  })

  it('cancelled 事件不可重复取消', () => {
    const event = simulateCreateEvent({
      holdingId: 'h_1',
      holdingName: '测试基金',
      type: 'payout',
      date: '2025-06-29',
      amount: 200,
    })

    const cancelled1 = simulateCancelEvent(event)
    expect(cancelled1).not.toBeNull()

    const cancelled2 = simulateCancelEvent(cancelled1!)
    expect(cancelled2).toBeNull()
  })
})

// ============================================================
// E5: 到账后 netInvestment 不变量
// ============================================================
describe('E5: 到账后不变量验证', () => {
  it('分红到账后 netInvestment 正确减少', () => {
    const { holding, transaction } = createHoldingWithBuy({
      shares: 1000, cost: 1500, latestPrice: 1.65,
    })
    const transactions: TransactionData[] = [transaction]

    const event = simulateCreateEvent({
      holdingId: holding.id,
      holdingName: '测试基金',
      type: 'payout',
      date: '2025-06-29',
      amount: 200,
    })

    const result = simulateMarkDistributed({ event, holding })
    // netInvestment = 1500 - 200 = 1300
    expect(result.holding.netInvestment).toBe(1300)

    // I6: netInvestment = totalBuy - totalSell + totalReinvest - totalDividendReceived
    // totalBuy = 1500, totalDividendReceived = 200 → 1500 - 0 + 0 - 200 = 1300
    const checkResult = checkAllInvariants(result.holding, transactions)
    expect(checkResult.passed).toBe(true)
  })
})

// ============================================================
// E6: 同一持仓多个事件
// ============================================================
describe('E6: 同一持仓多个分红事件', () => {
  it('多个事件分别到账，累加正确', () => {
    const { holding, transaction } = createHoldingWithBuy({
      shares: 1000, cost: 1500, latestPrice: 1.65,
    })
    const transactions: TransactionData[] = [transaction]
    let h = holding

    // 创建两个事件
    const e1 = simulateCreateEvent({
      holdingId: h.id, holdingName: '测试基金',
      type: 'payout', date: '2025-03-15', amount: 150,
    })
    const e2 = simulateCreateEvent({
      holdingId: h.id, holdingName: '测试基金',
      type: 'payout', date: '2025-06-15', amount: 200,
    })

    // 分别到账
    const r1 = simulateMarkDistributed({ event: e1, holding: h })
    h = r1.holding
    const r2 = simulateMarkDistributed({ event: e2, holding: h })
    h = r2.holding

    expect(h.totalDividendReceived).toBe(350)
    expect(h.netInvestment).toBe(1500 - 350)

    const checkResult = checkAllInvariants(h, transactions)
    expect(checkResult.passed).toBe(true)
  })

  it('一个到账一个取消，持仓只有到账的数据', () => {
    const { holding } = createHoldingWithBuy({
      shares: 1000, cost: 1500, latestPrice: 1.65,
    })
    let h = holding

    const e1 = simulateCreateEvent({
      holdingId: h.id, holdingName: '测试基金',
      type: 'payout', date: '2025-03-15', amount: 150,
    })
    const e2 = simulateCreateEvent({
      holdingId: h.id, holdingName: '测试基金',
      type: 'payout', date: '2025-06-15', amount: 200,
    })

    // e1 到账
    const r1 = simulateMarkDistributed({ event: e1, holding: h })
    h = r1.holding

    // e2 取消
    const cancelled = simulateCancelEvent(e2)
    expect(cancelled).not.toBeNull()
    expect(cancelled!.status).toBe('cancelled')

    // 持仓只有 e1 的 150
    expect(h.totalDividendReceived).toBe(150)
    expect(h.netInvestment).toBe(1500 - 150)
  })
})
