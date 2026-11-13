import { describe, expect, it } from 'vitest'
import type { AccountItem } from '@/api/account'
import type { AssetOverview, CategoryItem } from '@/api/assetOverview'
import type { HoldingItem } from '@/api/holding'
import { buildComposition, resolveCashValue } from '@/next/shared/composition'

const account = (name: string, assets: AccountItem['assets']): AccountItem =>
  ({
    id: name,
    name,
    purpose: 'daily',
    baseCurrency: assets.some((a) => a.type === 'crypto') ? 'USDT' : 'CNY',
    isDefault: false,
    sortOrder: 0,
    totalValueCny: 0,
    assetCount: assets.length,
    assets,
  }) as AccountItem

describe('首页现金口径', () => {
  it('优先使用 asset-overview.cashValue（含稳定币），而不是账户 cash 合计', () => {
    const overview = { cashValue: 59997.7, categories: [{ type: 'cash', value: 59997.7 }] } as AssetOverview
    const accounts = [
      account('理财账户', [{ id: 'c1', name: '现金', type: 'cash', amount: 58922.67 }]),
      account('交易所', [{ id: 'u1', name: 'USDT', type: 'crypto', amount: 563.09, assetCode: 'USDT' }]),
      account('日常钱包', [{ id: 'c2', name: '零钱', type: 'cash', amount: 511.94 }]),
    ]
    // 账户 cash 合计 59434.61；概览 cash 59997.70（含 USDT 563.09）
    expect(resolveCashValue(overview, accounts)).toBe(59997.7)
  })

  it('概览缺失时退回账户 cash 合计', () => {
    const accounts = [
      account('理财账户', [{ id: 'c1', name: '现金', type: 'cash', amount: 100 }]),
      account('日常钱包', [{ id: 'c2', name: '零钱', type: 'cash', amount: 20 }]),
    ]
    expect(resolveCashValue(null, accounts)).toBe(120)
  })
})

describe('构成子行只把真持仓接到详情', () => {
  it('现金/手动资产不标 holdingId，基金持仓才标', () => {
    const categories: CategoryItem[] = [
      {
        name: '现金',
        type: 'cash',
        value: 120,
        percentage: 54.5,
        color: '',
        items: [{ id: 'manual-cash-1', name: '零钱', value: 120 }],
      },
      {
        name: '红利',
        type: 'dividend',
        value: 100,
        percentage: 45.5,
        color: '',
        items: [{ id: 'fund-1', name: '红利ETF', value: 100 }],
      },
    ]
    const overview = { totalValue: 220, cashValue: 120, categories } as AssetOverview
    const holdings = [{ id: 'fund-1', name: '红利ETF', type: 'ETF', marketValue: 100 }] as HoldingItem[]
    const groups = buildComposition(overview, holdings, 220)
    const items = groups.flatMap((g) => g.items)
    expect(items.find((i) => i.id === 'manual-cash-1')?.holdingId).toBeUndefined()
    expect(items.find((i) => i.id === 'fund-1')?.holdingId).toBe('fund-1')
  })
})
