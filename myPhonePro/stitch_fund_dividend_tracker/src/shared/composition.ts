/**
 * 资产构成：后端 /asset-overview 的分类 + 未分类持仓补差。
 * 颜色刻意避开红绿（红绿只用于盈亏），用冷灰/靛蓝/琥珀三色系。
 */
import type { AssetOverview } from '@/api/assetOverview'
import type { HoldingItem } from '@/api/holding'
import { toNumber } from './money'

export interface CompositionItem {
  id: string
  label: string
  value: number
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

export function buildComposition(
  overview: AssetOverview | null,
  holdings: HoldingItem[],
  totalValue: number,
): CompositionGroup[] {
  const groups: CompositionGroup[] = []
  const seen = new Set<string>()

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
      items: (cat.items ?? []).map((it) => ({ id: it.id, label: it.name, value: toNumber(it.value) })),
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
      items: uncategorized.map((h) => ({ id: h.id, label: h.name, value: toNumber(h.marketValue) })),
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
