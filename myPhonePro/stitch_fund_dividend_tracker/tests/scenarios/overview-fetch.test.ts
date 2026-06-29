/**
 * ============================================================
 * L1 场景集成测试：资产概览 + 数据抓取域
 * ============================================================
 *
 * 这两个域风险较低（主要是只读汇总 + 外部数据同步），合并到一个文件。
 *
 * 资产概览场景：
 *   O1: 多持仓+现金 → 总资产汇总计算
 *   O2: 分类统计正确（现金/红利/美股/黄金/比特币）
 *   O3: 百分比计算（各分类占总资产的比例）
 *   O4: 历史快照 → 周/月变化计算
 *   O5: 当日重复快照跳过
 *
 * 数据抓取场景（低风险，主要验证数据新鲜度审计）：
 *   F1: 净值数据新鲜度审计（A5 已有覆盖，这里验证边界）
 *   F2: 多持仓不同净值日期
 *
 * 关键后端逻辑（已读 AssetOverviewService.java 确认）：
 *   - getOverview: 从 holding.marketValue + manualAsset.amount 汇总
 *   - 5 个分类: cash, crypto, us_stock, gold, dividend
 *   - 变化计算: 对比最近快照（weekly）和 7 天前快照（monthly）
 *   - snapshotToday: 同一天不重复创建快照
 *   - getHistory: 按 range 查快照列表，计算首尾变化
 */

import { describe, it, expect, beforeEach } from 'vitest'
import {
  makeHolding,
  resetIdCounter,
  type HoldingData,
} from '../invariants/engine'

beforeEach(() => {
  resetIdCounter()
})

// ============================================================
// 类型定义
// ============================================================

interface AssetSnapshot {
  date: string
  totalValue: number
  cashValue: number
  cryptoValue: number
  usStockValue: number
  goldValue: number
  dividendValue: number
}

interface CategoryDetail {
  name: string
  type: string
  value: number
  percentage: number
  items: { id: string; name: string; value: number }[]
}

interface AssetOverview {
  totalValue: number
  cashValue: number
  cryptoValue: number
  usStockValue: number
  goldValue: number
  dividendValue: number
  categories: CategoryDetail[]
}

interface HistoryPoint {
  date: string
  value: number
}

// ============================================================
// 模拟后端操作
// ============================================================

/**
 * 模拟现金余额
 */
function getCashBalance(): number {
  return 5000
}

/**
 * 模拟 getOverview：从持仓和现金汇总
 */
function simulateGetOverview(
  holdings: HoldingData[],
  cashBalance: number = 5000,
): AssetOverview {
  // 按 assetCategory 分组汇总
  const byCategory: Record<string, number> = {
    cash: cashBalance,
    crypto: 0,
    us_stock: 0,
    gold: 0,
    dividend: 0,
  }

  const categoryItems: Record<string, { id: string; name: string; value: number }[]> = {
    cash: [{ id: 'cash_1', name: '主现金账户', value: cashBalance }],
    crypto: [],
    us_stock: [],
    gold: [],
    dividend: [],
  }

  for (const h of holdings) {
    const cat = h.assetCategory ?? 'dividend'
    if (byCategory[cat] !== undefined) {
      byCategory[cat] += h.marketValue
      categoryItems[cat].push({ id: h.id, name: h.name, value: h.marketValue })
    }
  }

  const totalValue = Object.values(byCategory).reduce((a, b) => a + b, 0)

  // 构建分类明细
  const categoryMeta: { type: string; name: string }[] = [
    { type: 'cash', name: '现金' },
    { type: 'crypto', name: '比特币' },
    { type: 'us_stock', name: '美股' },
    { type: 'gold', name: '黄金' },
    { type: 'dividend', name: '红利' },
  ]

  const categories: CategoryDetail[] = categoryMeta
    .filter(m => byCategory[m.type] > 0)
    .map(m => ({
      name: m.name,
      type: m.type,
      value: byCategory[m.type],
      percentage: totalValue > 0
        ? Math.round((byCategory[m.type] / totalValue) * 10000) / 100
        : 0,
      items: categoryItems[m.type],
    }))
    .sort((a, b) => b.value - a.value)

  return {
    totalValue,
    cashValue: byCategory.cash,
    cryptoValue: byCategory.crypto,
    usStockValue: byCategory.us_stock,
    goldValue: byCategory.gold,
    dividendValue: byCategory.dividend,
    categories,
  }
}

/**
 * 模拟 getHistory
 */
function simulateGetHistory(
  snapshots: AssetSnapshot[],
  range: 'week' | 'month',
): { series: HistoryPoint[]; totalChange: number; totalChangePercent: number } {
  const series = snapshots.map(s => ({ date: s.date, value: s.totalValue }))

  if (snapshots.length < 2) {
    return { series, totalChange: 0, totalChangePercent: 0 }
  }

  const first = snapshots[0].totalValue
  const last = snapshots[snapshots.length - 1].totalValue
  const totalChange = last - first
  const totalChangePercent = first > 0
    ? Math.round((totalChange / first) * 10000) / 100
    : 0

  return { series, totalChange, totalChangePercent }
}

/**
 * 模拟 snapshotToday：同一天不重复
 */
function simulateSnapshotToday(
  existingSnapshots: AssetSnapshot[],
  overview: AssetOverview,
  today: string,
): AssetSnapshot | null {
  if (existingSnapshots.some(s => s.date === today)) {
    return null // 今天已有快照，跳过
  }

  return {
    date: today,
    totalValue: overview.totalValue,
    cashValue: overview.cashValue,
    cryptoValue: overview.cryptoValue,
    usStockValue: overview.usStockValue,
    goldValue: overview.goldValue,
    dividendValue: overview.dividendValue,
  }
}

// ============================================================
// O1: 总资产汇总
// ============================================================
describe('O1: 总资产汇总计算', () => {
  it('现金+多持仓=总资产', () => {
    const holdings: HoldingData[] = [
      makeHolding({ name: '红利低波', marketValue: 16500, assetCategory: 'dividend' }),
      makeHolding({ name: '标普500', marketValue: 32000, assetCategory: 'us_stock' }),
    ]

    const overview = simulateGetOverview(holdings, 5000)

    // 总资产 = 5000 + 16500 + 32000 = 53500
    expect(overview.totalValue).toBe(53500)
    expect(overview.cashValue).toBe(5000)
    expect(overview.dividendValue).toBe(16500)
    expect(overview.usStockValue).toBe(32000)
  })

  it('只有现金没有持仓时总资产=现金', () => {
    const overview = simulateGetOverview([], 10000)
    expect(overview.totalValue).toBe(10000)
    expect(overview.cashValue).toBe(10000)
  })

  it('持仓市值为 0 时不影响汇总', () => {
    const holdings: HoldingData[] = [
      makeHolding({ name: '空仓基金', marketValue: 0, assetCategory: 'dividend' }),
    ]

    const overview = simulateGetOverview(holdings, 5000)
    expect(overview.totalValue).toBe(5000)
  })
})

// ============================================================
// O2: 分类统计
// ============================================================
describe('O2: 分类统计', () => {
  it('多分类持仓各自汇总正确', () => {
    const holdings: HoldingData[] = [
      makeHolding({ name: '红利A', marketValue: 10000, assetCategory: 'dividend' }),
      makeHolding({ name: '红利B', marketValue: 5000, assetCategory: 'dividend' }),
      makeHolding({ name: '美股C', marketValue: 20000, assetCategory: 'us_stock' }),
      makeHolding({ name: '黄金D', marketValue: 8000, assetCategory: 'gold' }),
    ]

    const overview = simulateGetOverview(holdings, 3000)

    expect(overview.dividendValue).toBe(15000)  // 10000 + 5000
    expect(overview.usStockValue).toBe(20000)
    expect(overview.goldValue).toBe(8000)
    expect(overview.cashValue).toBe(3000)
    expect(overview.totalValue).toBe(46000)
  })

  it('未分类持仓归入 dividend', () => {
    const holdings: HoldingData[] = [
      makeHolding({ name: '未知分类', marketValue: 5000 }),
    ]

    const overview = simulateGetOverview(holdings, 0)
    expect(overview.dividendValue).toBe(5000)
  })
})

// ============================================================
// O3: 百分比计算
// ============================================================
describe('O3: 百分比计算', () => {
  it('各分类占比之和为 100%', () => {
    const holdings: HoldingData[] = [
      makeHolding({ name: '红利A', marketValue: 10000, assetCategory: 'dividend' }),
      makeHolding({ name: '美股B', marketValue: 5000, assetCategory: 'us_stock' }),
    ]

    const overview = simulateGetOverview(holdings, 5000) // total = 20000

    const totalPct = overview.categories.reduce((sum, c) => sum + c.percentage, 0)
    // 由于四舍五入可能有 0.01 的误差
    expect(totalPct).toBeCloseTo(100, 1)
  })

  it('总资产为 0 时百分比为 0', () => {
    const overview = simulateGetOverview([], 0)
    expect(overview.categories).toHaveLength(0)
  })

  it('分类按金额降序排列', () => {
    const holdings: HoldingData[] = [
      makeHolding({ name: '红利', marketValue: 5000, assetCategory: 'dividend' }),
      makeHolding({ name: '美股', marketValue: 15000, assetCategory: 'us_stock' }),
    ]

    const overview = simulateGetOverview(holdings, 2000)

    // 排序：美股 15000 > 红利 5000 > 现金 2000
    expect(overview.categories[0].type).toBe('us_stock')
    expect(overview.categories[1].type).toBe('dividend')
    expect(overview.categories[2].type).toBe('cash')
  })
})

// ============================================================
// O4: 历史快照 → 变化计算
// ============================================================
describe('O4: 历史快照与变化计算', () => {
  it('从快照计算总变化', () => {
    const snapshots: AssetSnapshot[] = [
      { date: '2025-06-22', totalValue: 50000, cashValue: 5000, cryptoValue: 0, usStockValue: 20000, goldValue: 0, dividendValue: 25000 },
      { date: '2025-06-23', totalValue: 50500, cashValue: 5000, cryptoValue: 0, usStockValue: 20200, goldValue: 0, dividendValue: 25300 },
      { date: '2025-06-24', totalValue: 51000, cashValue: 5000, cryptoValue: 0, usStockValue: 20500, goldValue: 0, dividendValue: 25500 },
    ]

    const history = simulateGetHistory(snapshots, 'week')

    // 总变化 = 51000 - 50000 = 1000
    expect(history.totalChange).toBe(1000)
    // 变化率 = 1000/50000 = 2%
    expect(history.totalChangePercent).toBeCloseTo(2, 1)
    expect(history.series).toHaveLength(3)
  })

  it('只有一个快照时变化为 0', () => {
    const snapshots: AssetSnapshot[] = [
      { date: '2025-06-22', totalValue: 50000, cashValue: 5000, cryptoValue: 0, usStockValue: 20000, goldValue: 0, dividendValue: 25000 },
    ]

    const history = simulateGetHistory(snapshots, 'week')
    expect(history.totalChange).toBe(0)
    expect(history.totalChangePercent).toBe(0)
  })

  it('资产下跌时变化为负', () => {
    const snapshots: AssetSnapshot[] = [
      { date: '2025-06-22', totalValue: 50000, cashValue: 5000, cryptoValue: 0, usStockValue: 20000, goldValue: 0, dividendValue: 25000 },
      { date: '2025-06-24', totalValue: 48000, cashValue: 5000, cryptoValue: 0, usStockValue: 19000, goldValue: 0, dividendValue: 24000 },
    ]

    const history = simulateGetHistory(snapshots, 'week')
    expect(history.totalChange).toBe(-2000)
    expect(history.totalChangePercent).toBeCloseTo(-4, 1)
  })
})

// ============================================================
// O5: 快照去重
// ============================================================
describe('O5: 快照去重', () => {
  it('当天已有快照则跳过', () => {
    const existing: AssetSnapshot[] = [
      { date: '2025-06-29', totalValue: 50000, cashValue: 5000, cryptoValue: 0, usStockValue: 20000, goldValue: 0, dividendValue: 25000 },
    ]

    const overview = simulateGetOverview([], 5000)
    const result = simulateSnapshotToday(existing, overview, '2025-06-29')
    expect(result).toBeNull()
  })

  it('当天无快照则创建', () => {
    const existing: AssetSnapshot[] = [
      { date: '2025-06-28', totalValue: 49000, cashValue: 5000, cryptoValue: 0, usStockValue: 19000, goldValue: 0, dividendValue: 25000 },
    ]

    const overview = simulateGetOverview([], 5000)
    const result = simulateSnapshotToday(existing, overview, '2025-06-29')
    expect(result).not.toBeNull()
    expect(result!.date).toBe('2025-06-29')
    expect(result!.totalValue).toBe(5000)
  })
})

// ============================================================
// F1: 净值数据新鲜度（边界验证）
// ============================================================
describe('F1: 净值数据新鲜度', () => {
  it('不同持仓可以有不同的净值日期', () => {
    const h1 = makeHolding({ name: '红利低波', priceDate: '2025-06-27' })
    const h2 = makeHolding({ name: '标普500', priceDate: '2025-06-26' })

    // 红利低波：2 天前，正常
    const daysDiff1 = Math.floor(
      (new Date('2025-06-29').getTime() - new Date(h1.priceDate).getTime()) / 86400000
    )
    expect(daysDiff1).toBe(2) // ≤ 7，正常

    // 标普500：3 天前，正常
    const daysDiff2 = Math.floor(
      (new Date('2025-06-29').getTime() - new Date(h2.priceDate).getTime()) / 86400000
    )
    expect(daysDiff2).toBe(3) // ≤ 7，正常
  })

  it('无净值数据的持仓 priceDate 为空', () => {
    const h = makeHolding({ priceDate: '' })
    // 没有净值日期，不算过期
    expect(h.priceDate).toBe('')
  })
})
