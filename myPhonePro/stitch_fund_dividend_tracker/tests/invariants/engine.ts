/**
 * ============================================================
 * 不变量验证引擎（L2 测试层核心）
 * ============================================================
 *
 * 这是纯函数模块，不依赖任何 HTTP mock。
 * 所有测试用例都可以直接导入这些验证函数，
 * 传入 Holding / Transaction 数据即可断言不变量。
 *
 * 核心不变量清单（I1-I8）:
 *   I1: marketValue = shares × latestPrice（有净值时）
 *   I2: totalCost = costPerShare × shares
 *   I3: shares ≥ 0
 *   I4: costPerShare ≥ 0（正常情况）
 *   I5: dividendRate = predictedDividendPerShare / costPerShare
 *   I6: 净投入 = 总买入 - 总卖出 - 总分红（diluted 算法）
 *   I7: 交易记录的 holdingId 一致性
 *   I8: 现金余额 = 初始 + 卖出 - 买入 + 分红到账
 */

// ============================================================
// 类型定义（与 API 层保持一致）
// ============================================================

export interface HoldingData {
  id: string
  name: string
  code: string
  type: string
  costAlgorithm: string
  shares: number
  costPerShare: number
  cost: number
  marketValue: number
  latestPrice: number
  priceDate: string
  predictedDividend: number
  dividendRate: number
  priceDividendRate: number
  totalDividendReceived: number
  netInvestment: number
  dividendRecoveryRate: number
  estimatedRecoveryYears: number
  reinvestRecoveryYears: number
  color: string
  assetCategory?: string
}

export interface TransactionData {
  id: string
  holdingId: string
  type: 'buy' | 'sell' | 'bonus_share' | 'reinvest'
  date: string
  quantity: number
  price: number
  fee: number
  total: number
}

export interface CashBalance {
  initial: number
  current: number
}

export interface InvariantViolation {
  id: string
  description: string
  holdingId?: string
  expected: string
  actual: string
}

// ============================================================
// 不变量检查函数
// ============================================================

/**
 * I1: marketValue = shares × latestPrice
 * 当有净值时，市值必须等于份额 × 最新净值
 */
export function checkI1_marketValue(holding: HoldingData): InvariantViolation | null {
  if (holding.latestPrice > 0) {
    const expected = holding.shares * holding.latestPrice
    const tolerance = Math.abs(expected) * 0.001 // 0.1% 容差
    if (Math.abs(holding.marketValue - expected) > tolerance) {
      return {
        id: 'I1',
        description: '市值必须等于份额 × 最新净值',
        holdingId: holding.id,
        expected: `shares(${holding.shares}) × latestPrice(${holding.latestPrice}) = ${expected}`,
        actual: `${holding.marketValue}`,
      }
    }
  }
  return null
}

/**
 * I2: cost = costPerShare × shares
 * 总成本必须等于成本单价 × 份额
 */
export function checkI2_cost(holding: HoldingData): InvariantViolation | null {
  const expected = holding.costPerShare * holding.shares
  const tolerance = Math.abs(expected) * 0.001
  if (Math.abs(holding.cost - expected) > tolerance) {
    return {
      id: 'I2',
      description: '总成本必须等于成本单价 × 份额',
      holdingId: holding.id,
      expected: `costPerShare(${holding.costPerShare}) × shares(${holding.shares}) = ${expected}`,
      actual: `${holding.cost}`,
    }
  }
  return null
}

/**
 * I3: shares ≥ 0
 * 份额不能为负
 */
export function checkI3_shares(holding: HoldingData): InvariantViolation | null {
  if (holding.shares < 0) {
    return {
      id: 'I3',
      description: '份额不能为负数',
      holdingId: holding.id,
      expected: 'shares ≥ 0',
      actual: `${holding.shares}`,
    }
  }
  return null
}

/**
 * I4: costPerShare ≥ 0
 * 成本单价不能为负（正常持仓）
 */
export function checkI4_costPerShare(holding: HoldingData): InvariantViolation | null {
  if (holding.costPerShare < 0) {
    return {
      id: 'I4',
      description: '成本单价不能为负数',
      holdingId: holding.id,
      expected: 'costPerShare ≥ 0',
      actual: `${holding.costPerShare}`,
    }
  }
  return null
}

/**
 * I5: dividendRate = predictedDividend / cost（年化息率一致性）
 * predictedDividend 是年化预测分红，cost 是总成本
 */
export function checkI5_dividendRate(holding: HoldingData): InvariantViolation | null {
  if (holding.cost > 0 && holding.predictedDividend >= 0) {
    const expected = holding.predictedDividend / holding.cost
    const tolerance = 0.0001 // 4 位小数容差
    if (Math.abs(holding.dividendRate - expected) > tolerance) {
      return {
        id: 'I5',
        description: '成本息率必须等于预测年化分红 / 总成本',
        holdingId: holding.id,
        expected: `predictedDividend(${holding.predictedDividend}) / cost(${holding.cost}) = ${expected}`,
        actual: `${holding.dividendRate}`,
      }
    }
  }
  return null
}

/**
 * I6: netInvestment 一致性（diluted 算法）
 * 净投入 = 总买入成本 - 总卖出收入 - 总分红到账
 * 交易记录传入后计算
 */
export function checkI6_netInvestment(
  holding: HoldingData,
  transactions: TransactionData[],
): InvariantViolation | null {
  const holdingTx = transactions.filter(t => t.holdingId === holding.id)

  // 没有交易记录时跳过 I6 检查（无法从流水推算净投入）
  if (holdingTx.length === 0) return null

  let totalBuy = 0      // 买入总成本
  let totalSell = 0     // 卖出总收入（扣费后）
  let totalReinvest = 0 // 复投总成本

  for (const tx of holdingTx) {
    switch (tx.type) {
      case 'buy':
        totalBuy += tx.total
        break
      case 'sell':
        totalSell += tx.total
        break
      case 'reinvest':
        totalReinvest += tx.total
        break
      // bonus_share 送股不影响净投入
    }
  }

  const expected = totalBuy - totalSell + totalReinvest - holding.totalDividendReceived
  const tolerance = Math.abs(expected) * 0.01

  if (Math.abs(holding.netInvestment - expected) > tolerance) {
    return {
      id: 'I6',
      description: '净投入 = 总买入 - 总卖出 + 复投 - 总分红',
      holdingId: holding.id,
      expected: `totalBuy(${totalBuy}) - totalSell(${totalSell}) + totalReinvest(${totalReinvest}) - dividends(${holding.totalDividendReceived}) = ${expected}`,
      actual: `${holding.netInvestment}`,
    }
  }
  return null
}

/**
 * I7: 交易记录 holdingId 一致性
 * 所有传入的交易必须属于指定 holding
 */
export function checkI7_transactionConsistency(
  holdingId: string,
  transactions: TransactionData[],
): InvariantViolation | null {
  const mismatched = transactions.filter(t => t.holdingId !== holdingId)
  if (mismatched.length > 0) {
    return {
      id: 'I7',
      description: '所有交易记录必须属于同一持仓',
      holdingId,
      expected: `所有交易的 holdingId = ${holdingId}`,
      actual: `${mismatched.length} 条交易属于其他持仓: ${mismatched.map(t => `${t.id}→${t.holdingId}`).join(', ')}`,
    }
  }
  return null
}

/**
 * I8: 现金余额一致性
 * 现金余额 = 初始余额 - 买入总额 + 卖出总额 + 分红到账总额
 */
export function checkI8_cashBalance(
  initialBalance: number,
  currentBalance: number,
  transactions: TransactionData[],
  totalDividends: number,
): InvariantViolation | null {
  let totalBuy = 0
  let totalSell = 0
  let totalReinvest = 0

  for (const tx of transactions) {
    switch (tx.type) {
      case 'buy':
        totalBuy += tx.total
        break
      case 'sell':
        totalSell += tx.total
        break
      case 'reinvest':
        totalReinvest += tx.total
        break
    }
  }

  const expected = initialBalance - totalBuy + totalSell - totalReinvest + totalDividends
  const tolerance = Math.abs(expected) * 0.01

  if (Math.abs(currentBalance - expected) > tolerance) {
    return {
      id: 'I8',
      description: '现金余额一致性',
      expected: `initial(${initialBalance}) - buy(${totalBuy}) + sell(${totalSell}) - reinvest(${totalReinvest}) + dividends(${totalDividends}) = ${expected}`,
      actual: `${currentBalance}`,
    }
  }
  return null
}

// ============================================================
// 批量不变量检查
// ============================================================

export interface InvariantCheckResult {
  passed: boolean
  violations: InvariantViolation[]
  checked: string[]
}

/**
 * 对单个持仓执行全部不变量检查
 */
export function checkAllInvariants(
  holding: HoldingData,
  transactions: TransactionData[] = [],
): InvariantCheckResult {
  const violations: InvariantViolation[] = []

  const checks: [string, () => InvariantViolation | null][] = [
    ['I1', () => checkI1_marketValue(holding)],
    ['I2', () => checkI2_cost(holding)],
    ['I3', () => checkI3_shares(holding)],
    ['I4', () => checkI4_costPerShare(holding)],
    ['I5', () => checkI5_dividendRate(holding)],
    ['I6', () => checkI6_netInvestment(holding, transactions)],
    ['I7', () => checkI7_transactionConsistency(holding.id, transactions)],
  ]

  for (const [id, check] of checks) {
    const violation = check()
    if (violation) {
      violations.push(violation)
    }
  }

  return {
    passed: violations.length === 0,
    violations,
    checked: checks.map(([id]) => id),
  }
}

/**
 * 批量检查多个持仓
 */
export function checkAllHoldings(
  holdings: HoldingData[],
  transactions: TransactionData[] = [],
): InvariantCheckResult[] {
  return holdings.map(h => checkAllInvariants(h, transactions))
}

// ============================================================
// 辅助工厂函数
// ============================================================

let _idCounter = 0
function nextId(prefix: string): string {
  return `${prefix}_${++_idCounter}`
}

export function resetIdCounter(): void {
  _idCounter = 0
}

/**
 * 创建测试用持仓数据
 */
export function makeHolding(overrides: Partial<HoldingData> = {}): HoldingData {
  const shares = overrides.shares ?? 1000
  const costPerShare = overrides.costPerShare ?? 1.5
  const cost = overrides.cost ?? costPerShare * shares
  const latestPrice = overrides.latestPrice ?? 1.65
  const predictedDividend = overrides.predictedDividend ?? 80
  const totalDividendReceived = overrides.totalDividendReceived ?? 0

  return {
    id: nextId('h'),
    name: '测试持仓',
    code: '000001',
    type: 'fund',
    costAlgorithm: 'diluted',
    shares,
    costPerShare,
    cost,
    marketValue: shares * latestPrice,
    latestPrice,
    priceDate: '2025-06-29',
    predictedDividend,
    dividendRate: cost > 0 ? predictedDividend / cost : 0,
    priceDividendRate: latestPrice > 0 ? predictedDividend / (shares * latestPrice) : 0,
    totalDividendReceived,
    // netInvestment: 默认与 cost 一致（假设全是买入且无分红），
    // 但在场景测试中通常会传入匹配的 transactions 来验证 I6
    netInvestment: cost - totalDividendReceived,
    dividendRecoveryRate: 0,
    estimatedRecoveryYears: 0,
    reinvestRecoveryYears: 0,
    color: '#3b82f6',
    ...overrides,
  }
}

/**
 * 创建测试用交易数据
 */
export function makeTransaction(overrides: Partial<TransactionData> = {}): TransactionData {
  const quantity = overrides.quantity ?? 100
  const price = overrides.price ?? 1.5
  const fee = overrides.fee ?? 0
  const type = overrides.type ?? 'buy'

  // total: buy/reinvest 为支出（正），sell 为收入（正）
  const total = type === 'sell'
    ? quantity * price - fee   // 卖出收入 = 数量×价格 - 费用
    : quantity * price + fee   // 买入/复投支出 = 数量×价格 + 费用

  return {
    id: nextId('tx'),
    holdingId: overrides.holdingId ?? 'h_unknown',
    type,
    date: overrides.date ?? '2025-06-29',
    quantity,
    price,
    fee,
    total,
    ...overrides,
  }
}
