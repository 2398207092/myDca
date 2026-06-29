/**
 * ============================================================
 * L2 不变量测试
 * ============================================================
 *
 * 验证每个不变量检查函数在各种场景下的正确性。
 * 这些测试确保引擎本身是可靠的，场景测试才能放心依赖它。
 */

import { describe, it, expect, beforeEach } from 'vitest'
import {
  checkI1_marketValue,
  checkI2_cost,
  checkI3_shares,
  checkI4_costPerShare,
  checkI5_dividendRate,
  checkI6_netInvestment,
  checkI7_transactionConsistency,
  checkI8_cashBalance,
  checkAllInvariants,
  makeHolding,
  makeTransaction,
  resetIdCounter,
  type HoldingData,
  type TransactionData,
} from './engine'

beforeEach(() => {
  resetIdCounter()
})

// ============================================================
// I1: marketValue = shares × latestPrice
// ============================================================
describe('I1: 市值 = 份额 × 最新净值', () => {
  it('正确的持仓通过检查', () => {
    const h = makeHolding({ shares: 1000, latestPrice: 1.65, marketValue: 1650 })
    expect(checkI1_marketValue(h)).toBeNull()
  })

  it('latestPrice 为 0 时跳过检查（未获取净值）', () => {
    const h = makeHolding({ shares: 1000, latestPrice: 0, marketValue: 0 })
    expect(checkI1_marketValue(h)).toBeNull()
  })

  it('市值与份额×净值不匹配时报告违规', () => {
    const h = makeHolding({ shares: 1000, latestPrice: 1.65, marketValue: 2000 })
    const violation = checkI1_marketValue(h)
    expect(violation).not.toBeNull()
    expect(violation!.id).toBe('I1')
    expect(violation!.holdingId).toBe(h.id)
  })
})

// ============================================================
// I2: cost = costPerShare × shares
// ============================================================
describe('I2: 总成本 = 成本单价 × 份额', () => {
  it('正确的持仓通过检查', () => {
    const h = makeHolding({ shares: 500, costPerShare: 2.0, cost: 1000 })
    expect(checkI2_cost(h)).toBeNull()
  })

  it('成本不匹配时报告违规', () => {
    const h = makeHolding({ shares: 500, costPerShare: 2.0, cost: 800 })
    const violation = checkI2_cost(h)
    expect(violation).not.toBeNull()
    expect(violation!.id).toBe('I2')
  })

  it('份额为 0 时成本也应为 0', () => {
    const h = makeHolding({ shares: 0, costPerShare: 2.0, cost: 0 })
    expect(checkI2_cost(h)).toBeNull()
  })

  it('份额为 0 但成本不为 0 时报错', () => {
    const h = makeHolding({ shares: 0, costPerShare: 2.0, cost: 100 })
    const violation = checkI2_cost(h)
    expect(violation).not.toBeNull()
  })
})

// ============================================================
// I3: shares ≥ 0
// ============================================================
describe('I3: 份额 ≥ 0', () => {
  it('正份额通过检查', () => {
    expect(checkI3_shares(makeHolding({ shares: 100 }))).toBeNull()
  })

  it('零份额通过检查', () => {
    expect(checkI3_shares(makeHolding({ shares: 0 }))).toBeNull()
  })

  it('负份额报告违规', () => {
    const violation = checkI3_shares(makeHolding({ shares: -50 }))
    expect(violation).not.toBeNull()
    expect(violation!.id).toBe('I3')
  })
})

// ============================================================
// I4: costPerShare ≥ 0
// ============================================================
describe('I4: 成本单价 ≥ 0', () => {
  it('正成本单价通过检查', () => {
    expect(checkI4_costPerShare(makeHolding({ costPerShare: 1.5 }))).toBeNull()
  })

  it('零成本单价通过检查', () => {
    expect(checkI4_costPerShare(makeHolding({ costPerShare: 0 }))).toBeNull()
  })

  it('负成本单价报告违规', () => {
    const violation = checkI4_costPerShare(makeHolding({ costPerShare: -0.5 }))
    expect(violation).not.toBeNull()
  })
})

// ============================================================
// I5: dividendRate = predictedDividend / cost
// ============================================================
describe('I5: 成本息率一致性', () => {
  it('正确的息率通过检查', () => {
    // cost=1500, predictedDividend=75 → dividendRate=0.05
    const h = makeHolding({ cost: 1500, predictedDividend: 75, dividendRate: 0.05 })
    expect(checkI5_dividendRate(h)).toBeNull()
  })

  it('成本为 0 时跳过检查', () => {
    const h = makeHolding({ cost: 0, predictedDividend: 100, dividendRate: 0 })
    expect(checkI5_dividendRate(h)).toBeNull()
  })

  it('息率不匹配时报告违规', () => {
    const h = makeHolding({ cost: 1500, predictedDividend: 75, dividendRate: 0.10 })
    const violation = checkI5_dividendRate(h)
    expect(violation).not.toBeNull()
    expect(violation!.id).toBe('I5')
  })
})

// ============================================================
// I6: netInvestment 一致性
// ============================================================
describe('I6: 净投入一致性', () => {
  it('只有买入时：净投入 = 总买入 - 总分红', () => {
    const h = makeHolding({
      cost: 1500,
      totalDividendReceived: 200,
      netInvestment: 1300,  // 1500 - 200
    })
    const tx = makeTransaction({ holdingId: h.id, type: 'buy', quantity: 1000, price: 1.5, fee: 0 })
    expect(checkI6_netInvestment(h, [tx])).toBeNull()
  })

  it('买入+卖出时：净投入 = 总买入 - 总卖出 - 总分红', () => {
    const h = makeHolding({
      cost: 1500,
      totalDividendReceived: 200,
      netInvestment: 550,  // 1500 - 750 - 200
    })
    const tx1 = makeTransaction({ holdingId: h.id, type: 'buy', quantity: 1000, price: 1.5, fee: 0 })
    const tx2 = makeTransaction({ holdingId: h.id, type: 'sell', quantity: 500, price: 1.5, fee: 0 })
    expect(checkI6_netInvestment(h, [tx1, tx2])).toBeNull()
  })

  it('含复投时：净投入 = 总买入 - 总卖出 + 复投 - 总分红', () => {
    const h = makeHolding({
      cost: 1500,
      totalDividendReceived: 200,
      netInvestment: 1350,  // 1000 - 500 + 1050 - 200
    })
    const txs = [
      makeTransaction({ holdingId: h.id, type: 'buy', quantity: 1000, price: 1.0, fee: 0 }),
      makeTransaction({ holdingId: h.id, type: 'sell', quantity: 500, price: 1.0, fee: 0 }),
      makeTransaction({ holdingId: h.id, type: 'reinvest', quantity: 700, price: 1.5, fee: 0 }),
    ]
    expect(checkI6_netInvestment(h, txs)).toBeNull()
  })

  it('送股不影响净投入', () => {
    const h = makeHolding({
      cost: 1500,
      totalDividendReceived: 0,
      netInvestment: 1500,
    })
    const txs = [
      makeTransaction({ holdingId: h.id, type: 'buy', quantity: 1000, price: 1.5, fee: 0 }),
      makeTransaction({ holdingId: h.id, type: 'bonus_share', quantity: 200, price: 0, fee: 0 }),
    ]
    expect(checkI6_netInvestment(h, txs)).toBeNull()
  })

  it('净投入不匹配时报告违规', () => {
    const h = makeHolding({
      cost: 1500,
      totalDividendReceived: 200,
      netInvestment: 5000,  // 明显不对
    })
    const tx = makeTransaction({ holdingId: h.id, type: 'buy', quantity: 1000, price: 1.5, fee: 0 })
    const violation = checkI6_netInvestment(h, [tx])
    expect(violation).not.toBeNull()
    expect(violation!.id).toBe('I6')
  })
})

// ============================================================
// I7: 交易记录 holdingId 一致性
// ============================================================
describe('I7: 交易记录 holdingId 一致性', () => {
  it('所有交易属于同一持仓时通过', () => {
    const h = makeHolding()
    const txs = [
      makeTransaction({ holdingId: h.id }),
      makeTransaction({ holdingId: h.id }),
    ]
    expect(checkI7_transactionConsistency(h.id, txs)).toBeNull()
  })

  it('存在不属于该持仓的交易时报告违规', () => {
    const h = makeHolding()
    const txs = [
      makeTransaction({ holdingId: h.id }),
      makeTransaction({ holdingId: 'other_holding' }),
    ]
    const violation = checkI7_transactionConsistency(h.id, txs)
    expect(violation).not.toBeNull()
    expect(violation!.id).toBe('I7')
  })
})

// ============================================================
// I8: 现金余额一致性
// ============================================================
describe('I8: 现金余额一致性', () => {
  it('余额正确时通过', () => {
    // 初始 10000, 买入 1500, 卖出 750, 分红 200
    // 预期: 10000 - 1500 + 750 + 200 = 9450
    const txs = [
      makeTransaction({ type: 'buy', quantity: 1000, price: 1.5, fee: 0 }),
      makeTransaction({ type: 'sell', quantity: 500, price: 1.5, fee: 0 }),
    ]
    expect(checkI8_cashBalance(10000, 9450, txs, 200)).toBeNull()
  })

  it('余额不匹配时报告违规', () => {
    const txs = [
      makeTransaction({ type: 'buy', quantity: 1000, price: 1.5, fee: 0 }),
    ]
    const violation = checkI8_cashBalance(10000, 5000, txs, 0)
    expect(violation).not.toBeNull()
    expect(violation!.id).toBe('I8')
  })
})

// ============================================================
// 批量检查
// ============================================================
describe('checkAllInvariants: 批量不变量检查', () => {
  it('完美数据全部通过', () => {
    const h = makeHolding()
    const result = checkAllInvariants(h, [])
    expect(result.passed).toBe(true)
    expect(result.violations).toHaveLength(0)
  })

  it('多个违规同时检出', () => {
    const h = makeHolding({
      shares: -10,               // I3 违规
      costPerShare: -1.5,        // I4 违规
      marketValue: 99999,        // I1 违规
    })
    const result = checkAllInvariants(h, [])
    expect(result.passed).toBe(false)
    expect(result.violations.length).toBeGreaterThanOrEqual(3)
  })

  it('多持仓批量检查', () => {
    const h1 = makeHolding()
    const h2 = makeHolding({ shares: -5 })
    const results = [h1, h2].map(h => checkAllInvariants(h))
    expect(results[0].passed).toBe(true)
    expect(results[1].passed).toBe(false)
  })
})
