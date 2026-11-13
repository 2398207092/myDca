/**
 * 资产构成：后端 /asset-overview 的分类 + 未分类持仓补差。
 * 颜色刻意避开红绿（红绿只用于盈亏），用冷灰/靛蓝/琥珀三色系。
 */
import type { AssetOverview } from '@/api/assetOverview'
import type { AccountItem } from '@/api/account'
import type { HoldingItem } from '@/api/holding'
import { toNumber } from './money'

export interface CompositionItem {
  id: string
  label: string
  value: number
  /** 只有真正的持仓才有 holdingId；现金/手动资产（如 USDT 余额）为 undefined */
  holdingId?: string
}

export interface CompositionGroup {
  key: string
  label: string
  color: string
  value: number
  percentage: number
  items: CompositionItem[]
}

const CATEGORY_COLOR: Record<string, string> = {
  cash: '#8c95a6',
  dividend: '#2e4bc6',
  us_stock: '#5f7391',
  gold: '#b08a3e',
  crypto: '#6c7ca8',
  uncategorized: '#a7b2c8',
}

const CATEGORY_FALLBACK_COLOR = '#8c95a6'

export function categoryColor(type: string): string {
  return CATEGORY_COLOR[type] ?? CATEGORY_FALLBACK_COLOR
}

/**
 * 首页「现金」口径：优先用 /asset-overview 的 cashValue（后端已把 USDT 等稳定币
 * 从 crypto 挪进 cash）；概览缺失时才退回账户里 type=cash 的资产合计。
 * 2026-11-13 修复：此前只累加账户 cash，导致同一屏「现金」比构成少掉稳定币。
 */
export function resolveCashValue(overview: AssetOverview | null, accounts: AccountItem[]): number {
  if (overview && Number.isFinite(Number(overview.cashValue))) return toNumber(overview.cashValue)

  const cashCategory = overview?.categories?.find((cat) => cat.type === 'cash')
  if (cashCategory) return toNumber(cashCategory.value)

  let sum = 0
  for (const account of accounts) {
    for (const asset of account.assets ?? []) {
      if (asset.type === 'cash') sum += toNumber(asset.amount)
    }
  }
  return sum
}

export function buildComposition(
  overview: AssetOverview | null,
  holdings: HoldingItem[],
  totalValue: number,
): CompositionGroup[] {
  const groups: CompositionGroup[] = []
  const seen = new Set<string>()
  const holdingIds = new Set(holdings.map((h) => h.id))

  for (const cat of overview?.categories ?? []) {
    const value = toNumber(cat.value)
    if (value <= 0) continue
    seen.add(cat.type)
    groups.push({
      key: cat.type,
      label: cat.name,
      color: categoryColor(cat.type),
      value,
      percentage: totalValue > 0 ? (value / totalValue) * 100 : 0,
      items: (cat.items ?? []).map((it) => ({
        id: it.id,
        label: it.name,
        value: toNumber(it.value),
        holdingId: holdingIds.has(it.id) ? it.id : undefined,
      })),
    })
  }

  const categorized = groups.reduce((sum, g) => sum + g.value, 0)
  const rest = totalValue - categorized
  if (rest > 0.01) {
    const uncategorized = holdings.filter((h) => !seen.has(h.assetCategory ?? ''))
    groups.push({
      key: 'uncategorized',
      label: '其他持仓',
      color: categoryColor('uncategorized'),
      value: rest,
      percentage: totalValue > 0 ? (rest / totalValue) * 100 : 0,
      items: uncategorized.map((h) => ({
        id: h.id,
        label: h.name,
        value: toNumber(h.marketValue),
        holdingId: h.id,
      })),
    })
  }

  return groups
}

/** 只保留正数段，给进度条/堆叠条用 */
export function toSegments(groups: CompositionGroup[]) {
  return groups
    .filter((g) => g.value > 0)
    .map((g) => ({ key: g.key, label: g.label, value: g.value, color: g.color }))
}
