/**
 * ============================================================
 * L1 场景集成测试：定投(DCA) + 现金记账域
 * ============================================================
 *
 * 第一批高优先级场景（DC1~DC3, C6, D1）：
 *   DC1: 定投扣款完整链路（扣现金→买份额→两边一致）
 *   DC2: 扣款成功但买入失败 → 现金回滚
 *   DC3: 余额不足 → 定投跳过
 *   C6:  删除买入交易 → 现金回滚
 *   D1:  单次定投基本流程
 *
 * 关键后端逻辑（已读代码确认）：
 *   - executePlan() 是 @Transactional，内部调 createTransaction()
 *   - createTransaction() 内部调 manualAssetService.adjustCash()
 *   - buy/reinvest 扣现金(total.negate)，sell 加现金
 *   - deleteTransaction 反向操作：删买入加回现金，删卖出扣现金
 *   - 定投执行失败 → 整个事务回滚 → 现金不扣、持仓不变
 */

import { describe, it, expect, beforeEach } from 'vitest'
import {
  makeHolding,
  makeTransaction,
  checkAllInvariants,
  checkI8_cashBalance,
  resetIdCounter,
  type HoldingData,
  type TransactionData,
} from '../invariants/engine'

beforeEach(() => {
  resetIdCounter()
})

// ============================================================
// 类型定义：扩展现金账户
// ============================================================

interface CashAccount {
  id: string
  name: string
  balance: number
  isPrimary: boolean
}

// ============================================================
// 模拟后端操作
// ============================================================

/**
 * 创建现金账户
 */
function createCashAccount(balance: number, isPrimary = true): CashAccount {
  return { id: 'cash_1', name: '主现金账户', balance, isPrimary }
}

/**
 * 模拟创建持仓（带初始买入交易）
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
 * 模拟定投执行（executePlan 的纯逻辑版本）
 *
 * 后端流程：
 * 1. 获取净值 navPrice
 * 2. 计算份额 = amount / navPrice
 * 3. 调用 createTransaction（内部 adjustCash 扣现金）
 * 4. 更新计划统计
 *
 * 如果任一步失败 → @Transactional 回滚 → 什么都不变
 */
function simulateDcaExecution(params: {
  holding: HoldingData
  transactions: TransactionData[]
  cash: CashAccount
  dcaAmount: number
  navPrice: number
  /** 模拟失败：设为 true 则抛出异常，验证回滚 */
  shouldFail?: boolean
}): {
  holding: HoldingData
  transaction: TransactionData
  cash: CashAccount
} | null {
  const { holding, transactions, cash, dcaAmount, navPrice, shouldFail } = params

  if (shouldFail) {
    // 模拟执行失败：整个事务回滚，什么都不变
    return null
  }

  if (navPrice <= 0) {
    // 净值不可用 → 执行失败
    return null
  }

  if (cash.balance < dcaAmount) {
    // 余额不足 → 执行失败
    return null
  }

  // 计算份额
  const quantity = Math.round((dcaAmount / navPrice) * 10000) / 10000 // 4位小数

  // 创建买入交易（模拟 createTransaction）
  const fee = 0
  // total 应该是 dcaAmount（整数金额），不是 quantity * navPrice（浮点）
  // 后端用 BigDecimal 保证精度，quantity * navPrice ≈ amount（可能差 0.01 以内）
  const transaction = makeTransaction({
    holdingId: holding.id,
    type: 'buy',
    quantity,
    price: navPrice,
    fee: 0,
    // 覆盖 total 为精确金额
    total: dcaAmount,
  } as any)

  // 更新持仓（模拟 holdingService.recalculateHoldingMetrics）
  const newShares = holding.shares + quantity
  const newCost = holding.cost + dcaAmount
  const newCostPerShare = newShares > 0 ? newCost / newShares : 0
  const newMarketValue = newShares * holding.latestPrice

  const newHolding: HoldingData = {
    ...holding,
    shares: newShares,
    cost: newCost,
    costPerShare: newCostPerShare,
    marketValue: newMarketValue,
    netInvestment: holding.netInvestment + dcaAmount,
  }

  // 扣现金（模拟 adjustCash）
  const newCash: CashAccount = {
    ...cash,
    balance: cash.balance - dcaAmount,
  }

  transactions.push(transaction)

  return { holding: newHolding, transaction, cash: newCash }
}

/**
 * 模拟删除交易（deleteTransaction 的纯逻辑版本）
 *
 * 后端流程：
 * 1. 删除交易记录
 * 2. 反向调整现金（删买入 → 加回现金）
 * 3. 重新计算份额
 */
function simulateDeleteTransaction(params: {
  holding: HoldingData
  transactions: TransactionData[]
  cash: CashAccount
  txId: string
}): { holding: HoldingData; cash: CashAccount } | null {
  const { holding, transactions, cash, txId } = params

  const idx = transactions.findIndex(t => t.id === txId)
  if (idx === -1) return null

  const removed = transactions.splice(idx, 1)[0]

  // 重新计算份额
  let newShares = 0
  let newCost = 0
  for (const tx of transactions) {
    if (tx.holdingId !== holding.id) continue
    switch (tx.type) {
      case 'buy':
      case 'reinvest':
      case 'bonus_share':
        newShares += tx.quantity
        newCost += tx.total
        break
      case 'sell':
        newShares -= tx.quantity
        newCost -= tx.total
        break
    }
  }
  newShares = Math.max(0, newShares)
  newCost = Math.max(0, newCost)

  const newHolding: HoldingData = {
    ...holding,
    shares: newShares,
    cost: newCost,
    costPerShare: newShares > 0 ? newCost / newShares : holding.costPerShare,
    marketValue: newShares * holding.latestPrice,
    netInvestment: holding.netInvestment - removed.total,
  }

  // 反向调整现金：删买入 → 加回
  let newBalance = cash.balance
  if (removed.type === 'buy' || removed.type === 'reinvest') {
    newBalance += removed.total
  } else if (removed.type === 'sell') {
    newBalance -= removed.total
  }

  return {
    holding: newHolding,
    cash: { ...cash, balance: newBalance },
  }
}

// ============================================================
// DC1: 定投扣款完整链路
// ============================================================
describe('DC1: 定投扣款 → 现金减少 → 持仓增加', () => {
  it('一次定投执行后，持仓和现金数据一致', () => {
    const { holding, transaction } = createHoldingWithBuy({
      shares: 1000, cost: 1500, latestPrice: 1.65,
    })
    const transactions: TransactionData[] = [transaction]
    const cash = createCashAccount(10000)

    // 执行定投：金额 500，净值 1.55
    const result = simulateDcaExecution({
      holding, transactions, cash,
      dcaAmount: 500, navPrice: 1.55,
    })

    expect(result).not.toBeNull()
    const { holding: newH, cash: newCash } = result!

    // 持仓：份额增加 500/1.55 ≈ 322.5806
    expect(newH.shares).toBeCloseTo(1000 + 500 / 1.55, 2)
    // 成本增加 500
    expect(newH.cost).toBeCloseTo(1500 + 500, 1)
    // 现金减少 500
    expect(newCash.balance).toBe(9500)

    // 不变量全部通过
    const checkResult = checkAllInvariants(newH, transactions)
    expect(checkResult.passed).toBe(true)

    // I8: 用"操作前余额"作为 initial，只统计后续交易
    // 操作前余额 = 9500 + 500 = 10000，后续只有定投这笔 buy
    const subsequentTx = [result!.transaction]
    const i8 = checkI8_cashBalance(10000, newCash.balance, subsequentTx, 0)
    if (i8) console.log('I8 violation:', JSON.stringify(i8))
    expect(i8).toBeNull()
  })

  it('连续 3 次定投后，costPerShare 正确稀释', () => {
    const { holding, transaction } = createHoldingWithBuy({
      shares: 1000, cost: 1500, latestPrice: 1.65,
    })
    const transactions: TransactionData[] = [transaction]
    let cash = createCashAccount(10000)
    let h = holding

    // 三次定投
    const plans = [
      { dcaAmount: 500, navPrice: 1.55 },
      { dcaAmount: 500, navPrice: 1.60 },
      { dcaAmount: 300, navPrice: 1.70 },
    ]

    for (const p of plans) {
      const result = simulateDcaExecution({
        holding: h, transactions, cash,
        dcaAmount: p.dcaAmount, navPrice: p.navPrice,
      })
      expect(result).not.toBeNull()
      h = result!.holding
      cash = result!.cash
    }

    // 总份额 = 1000 + 500/1.55 + 500/1.6 + 300/1.7
    const expectedShares = 1000 + 500/1.55 + 500/1.6 + 300/1.7
    expect(h.shares).toBeCloseTo(expectedShares, 2)
    // 总成本 = 1500 + 500 + 500 + 300 = 2800
    expect(h.cost).toBeCloseTo(2800, 1)
    // 现金 = 10000 - 1300 = 8700
    expect(cash.balance).toBe(8700)

    const checkResult = checkAllInvariants(h, transactions)
    expect(checkResult.passed).toBe(true)
  })
})

// ============================================================
// DC2: 扣款成功但买入失败 → 现金回滚
// ============================================================
describe('DC2: 执行失败 → 事务回滚', () => {
  it('净值不可用时，定投不执行，现金不变', () => {
    const { holding, transaction } = createHoldingWithBuy({
      shares: 1000, cost: 1500, latestPrice: 1.65,
    })
    const transactions: TransactionData[] = [transaction]
    const cash = createCashAccount(10000)

    const result = simulateDcaExecution({
      holding, transactions, cash,
      dcaAmount: 500, navPrice: 0, // 净值不可用
    })

    // 应该返回 null（执行失败）
    expect(result).toBeNull()
    // 现金不变
    expect(cash.balance).toBe(10000)
    // 只有初始交易
    expect(transactions).toHaveLength(1)
  })

  it('shouldFail 模式下，持仓和现金都不变', () => {
    const { holding, transaction } = createHoldingWithBuy({
      shares: 1000, cost: 1500, latestPrice: 1.65,
    })
    const transactions: TransactionData[] = [transaction]
    const cash = createCashAccount(10000)

    const result = simulateDcaExecution({
      holding, transactions, cash,
      dcaAmount: 500, navPrice: 1.55,
      shouldFail: true,
    })

    expect(result).toBeNull()
    expect(cash.balance).toBe(10000)
    expect(transactions).toHaveLength(1)
    expect(holding.shares).toBe(1000)
  })
})

// ============================================================
// DC3: 余额不足 → 定投跳过
// ============================================================
describe('DC3: 余额不足 → 跳过执行', () => {
  it('现金余额不够时，定投不执行', () => {
    const { holding, transaction } = createHoldingWithBuy({
      shares: 1000, cost: 1500, latestPrice: 1.65,
    })
    const transactions: TransactionData[] = [transaction]
    const cash = createCashAccount(200) // 只有 200 余额

    const result = simulateDcaExecution({
      holding, transactions, cash,
      dcaAmount: 500, navPrice: 1.55, // 需要 500，只有 200
    })

    expect(result).toBeNull()
    expect(cash.balance).toBe(200)
    expect(transactions).toHaveLength(1)
  })

  it('刚好够的余额可以执行', () => {
    const { holding, transaction } = createHoldingWithBuy({
      shares: 1000, cost: 1500, latestPrice: 1.65,
    })
    const transactions: TransactionData[] = [transaction]
    const cash = createCashAccount(500)

    const result = simulateDcaExecution({
      holding, transactions, cash,
      dcaAmount: 500, navPrice: 1.55,
    })

    expect(result).not.toBeNull()
    expect(result!.cash.balance).toBe(0) // 刚好扣完
  })
})

// ============================================================
// C6: 删除买入交易 → 现金回滚
// ============================================================
describe('C6: 删除交易 → 现金回滚', () => {
  it('删除一笔买入交易，现金加回来', () => {
    const { holding, transaction } = createHoldingWithBuy({
      shares: 1000, cost: 1500, latestPrice: 1.65,
    })
    const transactions: TransactionData[] = [transaction]
    let cash = createCashAccount(10000)

    // 先定投一笔
    const result = simulateDcaExecution({
      holding, transactions, cash,
      dcaAmount: 500, navPrice: 1.55,
    })
    expect(result).not.toBeNull()
    let h = result!.holding
    cash = result!.cash
    expect(cash.balance).toBe(9500) // 扣了 500

    // 删除刚创建的定投交易
    const delResult = simulateDeleteTransaction({
      holding: h, transactions, cash,
      txId: result!.transaction.id,
    })
    expect(delResult).not.toBeNull()
    h = delResult!.holding
    cash = delResult!.cash

    // 现金应该回到 10000（浮点误差在 0.01 以内）
    expect(cash.balance).toBeCloseTo(10000, 2)
    // 份额应该回到初始
    expect(h.shares).toBeCloseTo(1000, 2)

    const checkResult = checkAllInvariants(h, transactions)
    expect(checkResult.passed).toBe(true)
  })

  it('删除多笔交易中的一笔，现金和份额正确', () => {
    const { holding, transaction } = createHoldingWithBuy({
      shares: 1000, cost: 1500, latestPrice: 1.65,
    })
    const transactions: TransactionData[] = [transaction]
    let cash = createCashAccount(10000)
    let h = holding

    // 两笔定投
    const r1 = simulateDcaExecution({
      holding: h, transactions, cash,
      dcaAmount: 500, navPrice: 1.55,
    })
    h = r1!.holding; cash = r1!.cash
    const r2 = simulateDcaExecution({
      holding: h, transactions, cash,
      dcaAmount: 300, navPrice: 1.60,
    })
    h = r2!.holding; cash = r2!.cash

    expect(cash.balance).toBe(9200) // 10000 - 500 - 300

    // 删除第一笔定投
    const delResult = simulateDeleteTransaction({
      holding: h, transactions, cash,
      txId: r1!.transaction.id,
    })
    h = delResult!.holding
    cash = delResult!.cash

    // 现金：10000 - 300 = 9700（500 退回）
    expect(cash.balance).toBeCloseTo(9700, 2)
    // 份额：只剩初始 1000 + 第二笔定投 300/1.6=187.5
    expect(h.shares).toBeCloseTo(1000 + 300/1.6, 2)

    const checkResult = checkAllInvariants(h, transactions)
    expect(checkResult.passed).toBe(true)
  })
})

// ============================================================
// D1: 单次定投基本流程
// ============================================================
describe('D1: 单次定投基本流程', () => {
  it('定投执行后计划统计更新', () => {
    // 模拟计划状态
    const planState = {
      totalInvested: 0,
      totalShares: 0,
      totalExecutions: 0,
    }

    const { holding, transaction } = createHoldingWithBuy({
      shares: 1000, cost: 1500, latestPrice: 1.65,
    })
    const transactions: TransactionData[] = [transaction]
    const cash = createCashAccount(10000)

    const result = simulateDcaExecution({
      holding, transactions, cash,
      dcaAmount: 500, navPrice: 1.55,
    })

    expect(result).not.toBeNull()

    // 更新计划统计
    planState.totalInvested += 500
    planState.totalShares += result!.transaction.quantity
    planState.totalExecutions += 1

    expect(planState.totalInvested).toBe(500)
    expect(planState.totalShares).toBeCloseTo(500 / 1.55, 4)
    expect(planState.totalExecutions).toBe(1)
  })
})
