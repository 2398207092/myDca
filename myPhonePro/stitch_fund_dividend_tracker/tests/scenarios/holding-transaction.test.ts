/**
 * ============================================================
 * L1 场景集成测试：持仓 + 交易域
 * ============================================================
 *
 * 模拟完整的用户操作序列，每次操作后验证不变量。
 * 这是之前 35 个单元测试漏掉的关键覆盖。
 *
 * 核心场景：
 *   S1: 创建持仓 → 多次买入 → 验证不变量
 *   S2: 创建持仓 → 买入 → 编辑份额 → 验证成本计算
 *   S3: 创建持仓 → 买入 → 卖出 → 验证不变量
 *   S4: 创建持仓 → 买入 → 删除交易 → 验证回滚
 *   S5: 创建持仓 → 买入 → 复投 → 验证净投入
 *   S6: 创建持仓 → 买入 → 送股 → 验证份额稀释
 *   S7: 多次买入卖出 → 分红到账 → 验证回收率
 *   S8: 编辑持仓 → 成本扭曲检测
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
// 辅助：模拟后端操作
// ============================================================

/**
 * 模拟创建持仓。
 * 同时生成一条初始买入交易，使 I6 能正确验证。
 */
function simulateCreateHolding(params: {
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
 * 模拟买入。
 * dilute 算法：新 costPerShare = (原cost + 买入总额) / (原份额 + 买入份额)
 */
function simulateBuy(
  holding: HoldingData,
  tx: { quantity: number; price: number; fee?: number },
): { holding: HoldingData; transaction: TransactionData } {
  const fee = tx.fee ?? 0
  const buyTotal = tx.quantity * tx.price + fee
  const newShares = holding.shares + tx.quantity
  const newCost = holding.cost + buyTotal

  const transaction = makeTransaction({
    holdingId: holding.id,
    type: 'buy',
    quantity: tx.quantity,
    price: tx.price,
    fee,
  })

  const newPredictedDividend = holding.shares > 0
    ? holding.predictedDividend * (newShares / holding.shares)
    : 0

  return {
    holding: {
      ...holding,
      shares: newShares,
      cost: newCost,
      costPerShare: newShares > 0 ? newCost / newShares : 0,
      marketValue: newShares * holding.latestPrice,
      netInvestment: holding.netInvestment + buyTotal,
      predictedDividend: newPredictedDividend,
      dividendRate: newCost > 0 ? newPredictedDividend / newCost : 0,
    },
    transaction,
  }
}

/**
 * 模拟卖出。
 * 份额减少，成本按比例减少，costPerShare 保持不变。
 */
function simulateSell(
  holding: HoldingData,
  tx: { quantity: number; price: number; fee?: number },
): { holding: HoldingData; transaction: TransactionData } {
  const fee = tx.fee ?? 0
  const sellIncome = tx.quantity * tx.price - fee
  const costReduction = holding.costPerShare * tx.quantity

  const transaction = makeTransaction({
    holdingId: holding.id,
    type: 'sell',
    quantity: tx.quantity,
    price: tx.price,
    fee,
  })

  const newShares = holding.shares - tx.quantity
  const newCost = holding.cost - costReduction
  const newPredictedDividend = newShares > 0
    ? holding.predictedDividend * (newShares / holding.shares)
    : 0

  return {
    holding: {
      ...holding,
      shares: newShares,
      cost: newCost,
      marketValue: newShares * holding.latestPrice,
      netInvestment: holding.netInvestment - sellIncome,
      predictedDividend: newPredictedDividend,
      dividendRate: newCost > 0 ? newPredictedDividend / newCost : 0,
    },
    transaction,
  }
}

/**
 * 模拟分红到账。
 */
function simulateDividendDistributed(
  holding: HoldingData,
  amount: number,
): HoldingData {
  return {
    ...holding,
    totalDividendReceived: holding.totalDividendReceived + amount,
    netInvestment: holding.netInvestment - amount,
    // I5: dividendRate = predictedDividend / cost
    dividendRate: holding.cost > 0 ? holding.predictedDividend / holding.cost : 0,
  }
}

/**
 * 模拟编辑份额（保持 costPerShare 不变）。
 */
function simulateEditShares(
  holding: HoldingData,
  newShares: number,
): HoldingData {
  const newCost = holding.costPerShare * newShares
  return {
    ...holding,
    shares: newShares,
    cost: newCost,
    marketValue: newShares * holding.latestPrice,
  }
}

// ============================================================
// S1: 创建持仓 → 多次买入
// ============================================================
describe('S1: 创建持仓 → 多次买入', () => {
  it('3次买入后所有不变量通过', () => {
    const init = simulateCreateHolding({ shares: 1000, cost: 1500, latestPrice: 1.65 })
    let holding = init.holding
    const transactions: TransactionData[] = [init.transaction]

    const r1 = simulateBuy(holding, { quantity: 500, price: 1.55 })
    holding = r1.holding; transactions.push(r1.transaction)

    const r2 = simulateBuy(holding, { quantity: 300, price: 1.60 })
    holding = r2.holding; transactions.push(r2.transaction)

    const r3 = simulateBuy(holding, { quantity: 200, price: 1.70 })
    holding = r3.holding; transactions.push(r3.transaction)

    expect(holding.shares).toBe(2000)

    const result = checkAllInvariants(holding, transactions)
    if (!result.passed) console.error('Violations:', JSON.stringify(result.violations, null, 2))
    expect(result.passed).toBe(true)
  })

  it('买入后 costPerShare 正确稀释', () => {
    const init = simulateCreateHolding({ shares: 1000, cost: 1500 })
    let holding = init.holding

    const r1 = simulateBuy(holding, { quantity: 500, price: 2.0 })
    holding = r1.holding
    // cps = (1500 + 1000) / (1000 + 500) = 2500/1500
    expect(holding.costPerShare).toBeCloseTo(2500 / 1500, 4)
  })
})

// ============================================================
// S2: 创建持仓 → 买入 → 编辑份额
// ============================================================
describe('S2: 创建持仓 → 买入 → 编辑份额', () => {
  it('编辑份额后成本正确更新，I2 通过', () => {
    const init = simulateCreateHolding({ shares: 1000, cost: 1500 })
    let holding = init.holding
    const transactions: TransactionData[] = [init.transaction]

    const r1 = simulateBuy(holding, { quantity: 500, price: 2.0 })
    holding = r1.holding; transactions.push(r1.transaction)

    holding = simulateEditShares(holding, 3000)
    expect(holding.shares).toBe(3000)
    expect(holding.cost).toBeCloseTo(3000 * (2500 / 1500), 1)

    const result = checkAllInvariants(holding, transactions)
    if (!result.passed) console.error('Violations:', JSON.stringify(result.violations, null, 2))
    expect(result.passed).toBe(true)
  })

  it('编辑份额为 0 时成本归零', () => {
    const init = simulateCreateHolding({ shares: 1000, cost: 1500 })
    let holding = init.holding

    holding = simulateEditShares(holding, 0)
    expect(holding.shares).toBe(0)
    expect(holding.cost).toBe(0)
    expect(holding.costPerShare).toBeGreaterThan(0)
  })
})

// ============================================================
// S3: 创建持仓 → 买入 → 卖出
// ============================================================
describe('S3: 创建持仓 → 买入 → 卖出', () => {
  it('买入后部分卖出，不变量全部通过', () => {
    const init = simulateCreateHolding({ shares: 1000, cost: 1500, latestPrice: 1.65 })
    let holding = init.holding
    const transactions: TransactionData[] = [init.transaction]

    const r1 = simulateBuy(holding, { quantity: 500, price: 1.6 })
    holding = r1.holding; transactions.push(r1.transaction)

    const r2 = simulateSell(holding, { quantity: 600, price: 1.65 })
    holding = r2.holding; transactions.push(r2.transaction)

    // netInvestment = 1500 + 800 - 990 = 1310
    expect(holding.netInvestment).toBeCloseTo(1310, 0)

    const result = checkAllInvariants(holding, transactions)
    if (!result.passed) console.error('Violations:', JSON.stringify(result.violations, null, 2))
    expect(result.passed).toBe(true)
  })

  it('全仓卖出后份额为 0', () => {
    const init = simulateCreateHolding({ shares: 1000, cost: 1500, latestPrice: 1.65 })
    let holding = init.holding
    const transactions: TransactionData[] = [init.transaction]

    const r1 = simulateSell(holding, { quantity: 1000, price: 1.65 })
    holding = r1.holding; transactions.push(r1.transaction)

    expect(holding.shares).toBe(0)
    expect(holding.cost).toBe(0)

    const result = checkAllInvariants(holding, transactions)
    if (!result.passed) console.error('Violations:', JSON.stringify(result.violations, null, 2))
    expect(result.passed).toBe(true)
  })
})

// ============================================================
// S4: 创建持仓 → 买入 → 删除交易 → 验证回滚
// ============================================================
describe('S4: 删除交易回滚', () => {
  it('删除一笔买入后持仓恢复之前的状态', () => {
    const init = simulateCreateHolding({ shares: 1000, cost: 1500, latestPrice: 1.65 })
    let holding = init.holding
    const transactions: TransactionData[] = [init.transaction]

    const r1 = simulateBuy(holding, { quantity: 500, price: 1.6 })
    holding = r1.holding; transactions.push(r1.transaction)

    // 删除该交易
    const idx = transactions.findIndex(t => t.id === r1.transaction.id)
    const removed = transactions.splice(idx, 1)[0]

    holding = {
      ...holding,
      shares: holding.shares - removed.quantity,
      cost: holding.cost - removed.total,
      costPerShare: holding.shares - removed.quantity > 0
        ? (holding.cost - removed.total) / (holding.shares - removed.quantity)
        : 0,
      marketValue: (holding.shares - removed.quantity) * holding.latestPrice,
      netInvestment: holding.netInvestment - removed.total,
    }

    expect(holding.shares).toBe(1000)
    expect(holding.cost).toBe(1500)

    const result = checkAllInvariants(holding, transactions)
    if (!result.passed) console.error('Violations:', JSON.stringify(result.violations, null, 2))
    expect(result.passed).toBe(true)
  })
})

// ============================================================
// S5: 创建持仓 → 买入 → 复投
// ============================================================
describe('S5: 复投操作', () => {
  it('买入+复投后 netInvestment 正确', () => {
    const init = simulateCreateHolding({ shares: 1000, cost: 1500, latestPrice: 1.65 })
    let holding = init.holding
    const transactions: TransactionData[] = [init.transaction]

    const r1 = simulateBuy(holding, { quantity: 500, price: 1.6 })
    holding = r1.holding; transactions.push(r1.transaction)

    // 复投
    const reinvestTx = makeTransaction({
      holdingId: holding.id, type: 'reinvest',
      quantity: 100, price: 1.65, fee: 0,
    })
    transactions.push(reinvestTx)
    holding = {
      ...holding,
      shares: holding.shares + reinvestTx.quantity,
      cost: holding.cost + reinvestTx.total,
      costPerShare: (holding.cost + reinvestTx.total) / (holding.shares + reinvestTx.quantity),
      marketValue: (holding.shares + reinvestTx.quantity) * holding.latestPrice,
      netInvestment: holding.netInvestment + reinvestTx.total,
    }

    expect(holding.netInvestment).toBeCloseTo(2465, 0)

    const result = checkAllInvariants(holding, transactions)
    if (!result.passed) console.error('Violations:', JSON.stringify(result.violations, null, 2))
    expect(result.passed).toBe(true)
  })
})

// ============================================================
// S6: 送股（bonus_share）
// ============================================================
describe('S6: 送股操作', () => {
  it('送股增加份额但不增加成本和净投入', () => {
    const init = simulateCreateHolding({ shares: 1000, cost: 1500, latestPrice: 1.65 })
    let holding = init.holding
    const transactions: TransactionData[] = [init.transaction]

    const r1 = simulateBuy(holding, { quantity: 500, price: 1.6 })
    holding = r1.holding; transactions.push(r1.transaction)

    const bonusTx = makeTransaction({
      holdingId: holding.id, type: 'bonus_share',
      quantity: 200, price: 0, fee: 0,
    })
    transactions.push(bonusTx)
    holding = {
      ...holding,
      shares: holding.shares + bonusTx.quantity,
      marketValue: (holding.shares + bonusTx.quantity) * holding.latestPrice,
      costPerShare: holding.cost / (holding.shares + bonusTx.quantity),
    }

    expect(holding.shares).toBe(1700)
    expect(holding.cost).toBe(2300)

    const result = checkAllInvariants(holding, transactions)
    if (!result.passed) console.error('Violations:', JSON.stringify(result.violations, null, 2))
    expect(result.passed).toBe(true)
  })
})

// ============================================================
// S7: 分红到账影响
// ============================================================
describe('S7: 分红到账影响', () => {
  it('分红到账后 netInvestment 减少', () => {
    const init = simulateCreateHolding({ shares: 1000, cost: 1500, latestPrice: 1.65 })
    let holding = { ...init.holding, predictedDividend: 75, dividendRate: 75 / 1500 }
    const transactions: TransactionData[] = [init.transaction]

    const r1 = simulateBuy(holding, { quantity: 500, price: 1.6 })
    holding = r1.holding; transactions.push(r1.transaction)

    holding = simulateDividendDistributed(holding, 200)

    expect(holding.totalDividendReceived).toBe(200)
    expect(holding.netInvestment).toBeCloseTo(2100, 0)

    const result = checkAllInvariants(holding, transactions)
    if (!result.passed) console.error('Violations:', JSON.stringify(result.violations, null, 2))
    expect(result.passed).toBe(true)
  })
})

// ============================================================
// S8: 成本扭曲检测
// ============================================================
describe('S8: 成本扭曲检测', () => {
  it('不合理的 costPerShare 修改被 I2 检测', () => {
    const init = simulateCreateHolding({ shares: 1000, cost: 1500, latestPrice: 1.65 })
    let holding = init.holding
    const transactions: TransactionData[] = [init.transaction]

    const r1 = simulateBuy(holding, { quantity: 500, price: 1.6 })
    holding = r1.holding; transactions.push(r1.transaction)

    // 模拟 bug：修改了 costPerShare 但没有同步 cost
    holding = { ...holding, costPerShare: 3.0 }

    const result = checkAllInvariants(holding, transactions)
    expect(result.passed).toBe(false)
    const i2 = result.violations.find(v => v.id === 'I2')
    expect(i2).toBeDefined()
  })
})
