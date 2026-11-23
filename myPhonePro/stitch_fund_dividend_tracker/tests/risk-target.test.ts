import { describe, expect, it } from 'vitest'
import { buildRiskTargetPlan, defaultTargets, type RiskTargetItem } from '@/utils/risk-target'

const items: RiskTargetItem[] = [
  { key: 'dividend', name: '红利', current: 3.97, target: 60 },
  { key: 'crypto', name: '加密', current: 0.4813, target: 20 },
  { key: 'us', name: '美股', current: 0.398, target: 15 },
  { key: 'gold', name: '黄金', current: 0.092, target: 5 },
]

describe('buildRiskTargetPlan', () => {
  it('A 总额不变：卖出额 = 买入额，总额不变', () => {
    const plan = buildRiskTargetPlan(items, 'A')
    expect(plan).not.toBeNull()
    expect(plan!.total).toBeCloseTo(4.94, 2)
    expect(plan!.newTotal).toBeCloseTo(4.94, 2)
    expect(plan!.sellTotal).toBeCloseTo(plan!.buyTotal, 2)
    expect(plan!.needNew).toBe(0)
    const dividend = plan!.rows.find((r) => r.key === 'dividend')!
    expect(dividend.delta).toBeLessThan(0)
    expect(dividend.targetAmount).toBeCloseTo(2.96478, 4)
  })

  it('B 保留最高项：红利不卖，其他按新总额补', () => {
    const plan = buildRiskTargetPlan(items, 'B')
    expect(plan).not.toBeNull()
    expect(plan!.fixedKey).toBe('dividend')
    expect(plan!.newTotal).toBeCloseTo(3.97 / 0.6, 2)
    const fixed = plan!.rows.find((r) => r.key === 'dividend')!
    expect(fixed.fixed).toBe(true)
    expect(fixed.delta).toBe(0)
    expect(fixed.targetAmount).toBeCloseTo(3.97, 4)
    const crypto = plan!.rows.find((r) => r.key === 'crypto')!
    expect(crypto.targetAmount).toBeCloseTo((3.97 / 0.6) * 0.2, 4)
    expect(plan!.needNew).toBeCloseTo(plan!.buyTotal, 4)
    expect(plan!.sellTotal).toBe(0)
  })

  it('保留项是动态的：美股金额最大时固定美股，不是红利', () => {
    const changed: RiskTargetItem[] = [
      { key: 'dividend', name: '红利', current: 1, target: 10 },
      { key: 'crypto', name: '加密', current: 1, target: 20 },
      { key: 'us', name: '美股', current: 5, target: 30 },
      { key: 'gold', name: '黄金', current: 1, target: 40 },
    ]
    const plan = buildRiskTargetPlan(changed, 'B')
    expect(plan!.fixedKey).toBe('us')
    const us = plan!.rows.find((r) => r.key === 'us')!
    expect(us.fixed).toBe(true)
    expect(us.delta).toBe(0)
    const dividend = plan!.rows.find((r) => r.key === 'dividend')!
    expect(dividend.fixed).toBeUndefined()
  })

  it('目标合计不是 100 或金额为 0 → 不计算', () => {
    expect(buildRiskTargetPlan(items.map((i) => ({ ...i, target: 60 })), 'A')).toBeNull()
    const zero = items.map((i) => ({ ...i, current: 0 }))
    expect(buildRiskTargetPlan(zero, 'A')).toBeNull()
    const fixedZero = items.map((i) => ({ ...i, target: i.key === 'dividend' ? 0 : i.target }))
    expect(buildRiskTargetPlan(fixedZero, 'B')).toBeNull()
  })
})

describe('defaultTargets', () => {
  it('按当前金额四舍五入成整数，合计补到 100', () => {
    const t = defaultTargets(items)
    expect(Object.values(t).reduce((s, v) => s + v, 0)).toBe(100)
    expect(t.dividend).toBe(80)
    expect(t.crypto).toBe(10)
    expect(t.us).toBe(8)
    expect(t.gold).toBe(2)
  })

  it('零总额返回空对象', () => {
    expect(defaultTargets([{ key: 'a', current: 0 }])).toEqual({})
  })
})