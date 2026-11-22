import { describe, expect, it } from 'vitest'
import { calcCryptoBuyQuantity, formatCryptoQty } from '@/utils/trade-calc'

describe('calcCryptoBuyQuantity', () => {
  it('100 USDT / 85000 USDT 单价 → 8 位小数', () => {
    expect(calcCryptoBuyQuantity(100, 85000)).toBe(0.00117647)
  })

  it('手续费先从金额里扣，数量按净额算', () => {
    expect(calcCryptoBuyQuantity(100, 85000, 0.1)).toBe(0.00117529)
  })

  it('向下截断：数量 × 单价 + 手续费 永不超过投入金额', () => {
    for (const price of [85000, 79827.4, 123456.78, 0.5, 0.0001]) {
      const qty = calcCryptoBuyQuantity(100, price, 0.1)
      expect(qty * price + 0.1).toBeLessThanOrEqual(100 + 1e-9)
    }
  })

  it('金额、单价非正或非法 → 0', () => {
    expect(calcCryptoBuyQuantity(100, 0)).toBe(0)
    expect(calcCryptoBuyQuantity(100, null)).toBe(0)
    expect(calcCryptoBuyQuantity(100, undefined)).toBe(0)
    expect(calcCryptoBuyQuantity(100, 'abc')).toBe(0)
    expect(calcCryptoBuyQuantity(0, 85000)).toBe(0)
    expect(calcCryptoBuyQuantity(null, 85000)).toBe(0)
  })

  it('金额不足以覆盖手续费 → 0（提交时由页面给出专门提示）', () => {
    expect(calcCryptoBuyQuantity(0.1, 85000, 0.1)).toBe(0)
    expect(calcCryptoBuyQuantity(0.05, 85000, 0.1)).toBe(0)
  })

  it('AurosField 传出的是字符串，也要能算', () => {
    expect(calcCryptoBuyQuantity('100', '85000', '0.1')).toBe(0.00117529)
  })

  it('极小数量不会被 8 位截断成 0', () => {
    expect(calcCryptoBuyQuantity(100, 10_000_000)).toBe(0.00001)
  })
})

describe('formatCryptoQty', () => {
  it('最多 8 位小数，去掉末尾 0', () => {
    expect(formatCryptoQty(0.00117647)).toBe('0.00117647')
    expect(formatCryptoQty(0.001)).toBe('0.001')
    expect(formatCryptoQty(1)).toBe('1')
    expect(formatCryptoQty(0)).toBe('0')
    expect(formatCryptoQty('0.00008631')).toBe('0.00008631')
  })
})