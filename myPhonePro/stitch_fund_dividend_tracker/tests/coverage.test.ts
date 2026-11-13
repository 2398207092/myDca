import { describe, expect, it } from 'vitest'
import { computeCoverageNeed } from '@/utils/coverage'
import type { ExpenseCoverageItem } from '@/api/expense'

function expense(
  annualAmount: number,
  flags: { covered?: boolean; inProgress?: boolean } = {},
): ExpenseCoverageItem {
  return {
    id: `${annualAmount}-${flags.covered ? 'c' : ''}${flags.inProgress ? 'p' : ''}`,
    name: '测试类目',
    icon: '📌',
    annualAmount,
    covered: flags.covered ?? false,
    inProgress: flags.inProgress ?? false,
  }
}

describe('computeCoverageNeed（覆盖卡「再攒」口径）', () => {
  it('2026-11-13 审计案例：目标 9600、预测 2140.90、话费 600 已覆盖 → 8059.10', () => {
    const expenses = [
      expense(600, { covered: true }),
      expense(9600, { inProgress: true }),
      expense(9600),
      expense(14400),
    ]
    expect(computeCoverageNeed(expenses[1], expenses, 2140.9)).toBe(8059.1)
  })

  it('没有已覆盖类目时，缺口 = 目标全年金额', () => {
    const expenses = [expense(9600, { inProgress: true }), expense(14400)]
    expect(computeCoverageNeed(expenses[0], expenses, 0)).toBe(9600)
  })

  it('多项已覆盖时，按已覆盖合计扣减', () => {
    const expenses = [
      expense(600, { covered: true }),
      expense(360, { covered: true }),
      expense(9600, { inProgress: true }),
    ]
    // 真实剩余 = 2140.9 − (600 + 360) = 1180.9 → 9600 − 1180.9 = 8419.1
    expect(computeCoverageNeed(expenses[2], expenses, 2140.9)).toBe(8419.1)
  })

  it('浮点尾差收口到分（2140.9 − 600 在 JS 里有尾差）', () => {
    expect(computeCoverageNeed(expense(9600), [expense(600, { covered: true })], 2140.9)).toBe(8059.1)
  })

  it('已覆盖合计超过预测（异常数据）时不出现负剩余', () => {
    expect(
      computeCoverageNeed(expense(9600), [expense(1000, { covered: true }), expense(2000, { covered: true })], 500),
    ).toBe(9600)
  })

  it('目标金额小于剩余额度时归零，不显示负数', () => {
    expect(computeCoverageNeed(expense(600), [], 5000)).toBe(0)
  })
})
