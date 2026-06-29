/**
 * ============================================================
 * L3 审计规则测试
 * ============================================================
 *
 * 审计规则是 L1/L2 的补充防线，用于发现更隐蔽的异常：
 *   A1: 成本偏差 — costPerShare 与交易加权均价偏差过大
 *   A2: 息率异常 — dividendRate 超出合理范围（0% ~ 20%）
 *   A3: 市值与成本偏离 — marketValue / cost 极端值
 *   A4: 分红回收率异常 — dividendRecoveryRate > 100% 或 < 0%
 *   A5: 数据新鲜度 — latestPrice 超过 7 天未更新
 *   A6: 交易日期顺序 — 交易日期不能乱序
 *   A7: 零份额非零成本 — shares=0 但 cost≠0
 */

import { describe, it, expect, beforeEach } from 'vitest'
import {
  makeHolding,
  makeTransaction,
  resetIdCounter,
  type HoldingData,
  type TransactionData,
} from '../invariants/engine'

beforeEach(() => {
  resetIdCounter()
})

// ============================================================
// 审计规则函数
// ============================================================

interface AuditWarning {
  rule: string
  severity: 'error' | 'warning' | 'info'
  message: string
  holdingId?: string
}

/**
 * A1: costPerShare 与交易加权均价偏差 > 5%
 */
function auditA1_costBias(
  holding: HoldingData,
  transactions: TransactionData[],
): AuditWarning | null {
  const buys = transactions.filter(t =>
    t.holdingId === holding.id && t.type === 'buy' && t.quantity > 0
  )
  if (buys.length === 0) return null

  let totalCost = 0
  let totalQty = 0
  for (const b of buys) {
    totalCost += b.total
    totalQty += b.quantity
  }
  const weightedAvgPrice = totalQty > 0 ? totalCost / totalQty : 0
  const deviation = Math.abs(holding.costPerShare - weightedAvgPrice) / weightedAvgPrice

  if (deviation > 0.05) {
    return {
      rule: 'A1',
      severity: 'error',
      message: `成本偏差 ${(deviation * 100).toFixed(1)}%: cps=${holding.costPerShare.toFixed(4)} vs 加权均价=${weightedAvgPrice.toFixed(4)}`,
      holdingId: holding.id,
    }
  }
  return null
}

/**
 * A2: dividendRate 超出合理范围
 */
function auditA2_dividendRateRange(holding: HoldingData): AuditWarning | null {
  if (holding.dividendRate < 0) {
    return {
      rule: 'A2',
      severity: 'error',
      message: `息率为负: ${holding.dividendRate}`,
      holdingId: holding.id,
    }
  }
  if (holding.dividendRate > 0.20) {
    return {
      rule: 'A2',
      severity: 'warning',
      message: `息率异常偏高: ${(holding.dividendRate * 100).toFixed(1)}%`,
      holdingId: holding.id,
    }
  }
  return null
}

/**
 * A3: marketValue / cost 极端值
 */
function auditA3_valueRatio(holding: HoldingData): AuditWarning | null {
  if (holding.cost <= 0) return null
  const ratio = holding.marketValue / holding.cost
  if (ratio < 0.3) {
    return {
      rule: 'A3',
      severity: 'warning',
      message: `市值/成本比率极低: ${(ratio * 100).toFixed(1)}%（浮亏超过70%）`,
      holdingId: holding.id,
    }
  }
  if (ratio > 5) {
    return {
      rule: 'A3',
      severity: 'info',
      message: `市值/成本比率极高: ${(ratio * 100).toFixed(1)}%（浮盈超过400%）`,
      holdingId: holding.id,
    }
  }
  return null
}

/**
 * A4: dividendRecoveryRate 异常
 */
function auditA4_recoveryRate(holding: HoldingData): AuditWarning | null {
  if (holding.dividendRecoveryRate < 0) {
    return {
      rule: 'A4',
      severity: 'error',
      message: `回收率为负: ${holding.dividendRecoveryRate}`,
      holdingId: holding.id,
    }
  }
  if (holding.dividendRecoveryRate > 100) {
    return {
      rule: 'A4',
      severity: 'info',
      message: `回收率超过100%: ${holding.dividendRecoveryRate}%（已通过分红完全回本）`,
      holdingId: holding.id,
    }
  }
  return null
}

/**
 * A5: 净值数据新鲜度（超过 7 天未更新）
 */
function auditA5_priceFreshness(holding: HoldingData, today: string = '2025-06-29'): AuditWarning | null {
  if (!holding.priceDate) return null
  const daysDiff = Math.floor(
    (new Date(today).getTime() - new Date(holding.priceDate).getTime()) / 86400000
  )
  if (daysDiff > 7) {
    return {
      rule: 'A5',
      severity: 'warning',
      message: `净值数据过期: ${daysDiff} 天未更新（最近: ${holding.priceDate}）`,
      holdingId: holding.id,
    }
  }
  return null
}

/**
 * A6: 交易日期顺序
 */
function auditA6_transactionOrder(transactions: TransactionData[]): AuditWarning[] {
  const warnings: AuditWarning[] = []
  const sorted = [...transactions].sort(
    (a, b) => new Date(a.date).getTime() - new Date(b.date).getTime()
  )
  for (let i = 0; i < transactions.length; i++) {
    if (transactions[i].id !== sorted[i].id) {
      warnings.push({
        rule: 'A6',
        severity: 'warning',
        message: `交易顺序异常: ${transactions[i].id}(${transactions[i].date}) 排在 ${sorted[i].id}(${sorted[i].date}) 的位置`,
      })
      break // 只报告第一个乱序
    }
  }
  return warnings
}

/**
 * A7: 零份额非零成本
 */
function auditA7_zeroShares(holding: HoldingData): AuditWarning | null {
  if (holding.shares === 0 && holding.cost !== 0) {
    return {
      rule: 'A7',
      severity: 'error',
      message: `份额为 0 但成本不为 0: cost=${holding.cost}`,
      holdingId: holding.id,
    }
  }
  return null
}

/**
 * 运行全部审计规则
 */
function runAllAudits(
  holdings: HoldingData[],
  transactions: TransactionData[],
): AuditWarning[] {
  const warnings: AuditWarning[] = []

  for (const h of holdings) {
    const a1 = auditA1_costBias(h, transactions)
    if (a1) warnings.push(a1)
    const a2 = auditA2_dividendRateRange(h)
    if (a2) warnings.push(a2)
    const a3 = auditA3_valueRatio(h)
    if (a3) warnings.push(a3)
    const a4 = auditA4_recoveryRate(h)
    if (a4) warnings.push(a4)
    const a5 = auditA5_priceFreshness(h)
    if (a5) warnings.push(a5)
    const a7 = auditA7_zeroShares(h)
    if (a7) warnings.push(a7)
  }

  const a6 = auditA6_transactionOrder(transactions)
  warnings.push(...a6)

  return warnings
}

// ============================================================
// 测试用例
// ============================================================

describe('A1: 成本偏差检测', () => {
  it('costPerShare 与加权均价一致时不报警', () => {
    const h = makeHolding({ shares: 1000, costPerShare: 1.5, cost: 1500 })
    const txs = [
      makeTransaction({ holdingId: h.id, type: 'buy', quantity: 1000, price: 1.5, fee: 0 }),
    ]
    expect(auditA1_costBias(h, txs)).toBeNull()
  })

  it('costPerShare 与加权均价偏差 > 5% 时报警', () => {
    const h = makeHolding({ shares: 1000, costPerShare: 2.0, cost: 2000 })
    const txs = [
      makeTransaction({ holdingId: h.id, type: 'buy', quantity: 1000, price: 1.5, fee: 0 }),
    ]
    const w = auditA1_costBias(h, txs)
    expect(w).not.toBeNull()
    expect(w!.rule).toBe('A1')
    expect(w!.severity).toBe('error')
  })
})

describe('A2: 息率范围检测', () => {
  it('正常息率不报警', () => {
    const h = makeHolding({ cost: 1500, predictedDividend: 75, dividendRate: 0.05 })
    expect(auditA2_dividendRateRange(h)).toBeNull()
  })

  it('息率为负报警', () => {
    const h = makeHolding({ dividendRate: -0.01 })
    const w = auditA2_dividendRateRange(h)
    expect(w).not.toBeNull()
    expect(w!.severity).toBe('error')
  })

  it('息率 > 20% 报警', () => {
    const h = makeHolding({ dividendRate: 0.25 })
    const w = auditA2_dividendRateRange(h)
    expect(w).not.toBeNull()
    expect(w!.severity).toBe('warning')
  })
})

describe('A3: 市值/成本比率', () => {
  it('正常比率不报警', () => {
    const h = makeHolding({ cost: 1500, marketValue: 1650 })
    expect(auditA3_valueRatio(h)).toBeNull()
  })

  it('浮亏超70%报警', () => {
    const h = makeHolding({ cost: 1500, marketValue: 300 })
    const w = auditA3_valueRatio(h)
    expect(w).not.toBeNull()
    expect(w!.severity).toBe('warning')
  })

  it('浮盈超400%通知', () => {
    const h = makeHolding({ cost: 1500, marketValue: 9000 })
    const w = auditA3_valueRatio(h)
    expect(w).not.toBeNull()
    expect(w!.severity).toBe('info')
  })
})

describe('A4: 分红回收率异常', () => {
  it('正常回收率不报警', () => {
    const h = makeHolding({ dividendRecoveryRate: 50 })
    expect(auditA4_recoveryRate(h)).toBeNull()
  })

  it('负回收率报警', () => {
    const h = makeHolding({ dividendRecoveryRate: -5 })
    const w = auditA4_recoveryRate(h)
    expect(w).not.toBeNull()
    expect(w!.severity).toBe('error')
  })

  it('回收率超100%通知', () => {
    const h = makeHolding({ dividendRecoveryRate: 120 })
    const w = auditA4_recoveryRate(h)
    expect(w).not.toBeNull()
    expect(w!.severity).toBe('info')
  })
})

describe('A5: 净值数据新鲜度', () => {
  it('3天前的数据不报警', () => {
    const h = makeHolding({ priceDate: '2025-06-26' })
    expect(auditA5_priceFreshness(h, '2025-06-29')).toBeNull()
  })

  it('10天前的数据报警', () => {
    const h = makeHolding({ priceDate: '2025-06-19' })
    const w = auditA5_priceFreshness(h, '2025-06-29')
    expect(w).not.toBeNull()
    expect(w!.severity).toBe('warning')
  })
})

describe('A6: 交易日期顺序', () => {
  it('按日期排序的交易不报警', () => {
    const txs = [
      makeTransaction({ date: '2025-01-01' }),
      makeTransaction({ date: '2025-03-15' }),
      makeTransaction({ date: '2025-06-29' }),
    ]
    expect(auditA6_transactionOrder(txs)).toHaveLength(0)
  })

  it('乱序交易报警', () => {
    const txs = [
      makeTransaction({ date: '2025-06-29' }),
      makeTransaction({ date: '2025-01-01' }),
    ]
    const warnings = auditA6_transactionOrder(txs)
    expect(warnings.length).toBeGreaterThan(0)
    expect(warnings[0].rule).toBe('A6')
  })
})

describe('A7: 零份额非零成本', () => {
  it('零份额零成本不报警', () => {
    const h = makeHolding({ shares: 0, cost: 0 })
    expect(auditA7_zeroShares(h)).toBeNull()
  })

  it('零份额非零成本报警', () => {
    const h = makeHolding({ shares: 0, cost: 1500 })
    const w = auditA7_zeroShares(h)
    expect(w).not.toBeNull()
    expect(w!.severity).toBe('error')
  })
})

describe('runAllAudits: 全量审计', () => {
  it('正常数据无审计警告', () => {
    const h = makeHolding()
    const txs = [
      makeTransaction({ holdingId: h.id, type: 'buy', quantity: 1000, price: 1.5 }),
    ]
    const warnings = runAllAudits([h], txs)
    expect(warnings).toHaveLength(0)
  })

  it('异常数据检出多项警告', () => {
    const h = makeHolding({
      shares: 0,
      cost: 1500,        // A7
      dividendRate: 0.5, // A2
      marketValue: 0,
      priceDate: '2025-01-01', // A5
    })
    const txs: TransactionData[] = []
    const warnings = runAllAudits([h], txs)
    // 至少 A2, A5, A7
    const rules = warnings.map(w => w.rule)
    expect(rules).toContain('A2')
    expect(rules).toContain('A5')
    expect(rules).toContain('A7')
  })
})
