import type { HoldingItem } from '@/api/holding'

/** 类目舱板的一个舱格 */
export interface DeckCategory {
  key: string
  label: string
  /** 市值合计 */
  value: number
  /** 标的数 */
  count: number
  /** 占全部持仓的百分比 0..100 */
  pct: number
  /** 边缘色（深水色阶里的一档） */
  tone: string
  /** 舱格填面（同色相的极低透明度，靠亮度分档） */
  tint: string
  holdings: HoldingItem[]
}

const LABELS: Record<string, string> = {
  dividend: '红利',
  us_stock: '美股',
  a_stock: 'A股',
  bond: '债券',
  gold: '黄金',
  crypto: '加密',
  cash: '现金',
  other: '其他',
}

/** 深水色阶：只做亮度分档，不引入新色相 */
const TONES = ['#cbfffc', '#8fe3d9', '#5fb8ae', '#3f8b84', '#2f6b66']
const TINTS = [
  'rgba(203, 255, 252, 0.15)',
  'rgba(203, 255, 252, 0.105)',
  'rgba(203, 255, 252, 0.075)',
  'rgba(203, 255, 252, 0.055)',
  'rgba(203, 255, 252, 0.04)',
]

/**
 * 按资产类别聚合。类别数封顶 5：第 6 类起并入「其他」，
 * 保证舱板永远是 4–5 块可读的格子，而不是几十根发丝。
 */
export function buildCategories(holdings: HoldingItem[], max = 5): DeckCategory[] {
  const groups = new Map<string, HoldingItem[]>()
  for (const h of holdings) {
    const key = h.assetCategory || 'other'
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key)!.push(h)
  }

  const sumOf = (list: HoldingItem[]) => list.reduce((s, h) => s + (h.marketValue || 0), 0)

  let arr = [...groups.entries()].map(([key, list]) => ({
    key,
    label: LABELS[key] || key,
    holdings: [...list].sort((a, b) => (b.marketValue || 0) - (a.marketValue || 0)),
    value: sumOf(list),
    count: list.length,
  }))

  arr.sort((a, b) => b.value - a.value)

  if (arr.length > max) {
    const tail = arr.slice(max - 1)
    const merged = {
      key: 'other',
      label: '其他',
      holdings: tail.flatMap((g) => g.holdings),
      value: tail.reduce((s, g) => s + g.value, 0),
      count: tail.reduce((s, g) => s + g.count, 0),
    }
    arr = [...arr.slice(0, max - 1), merged]
  }

  const total = arr.reduce((s, g) => s + g.value, 0) || 1
  return arr.map((g, i) => ({
    ...g,
    pct: (g.value / total) * 100,
    tone: TONES[Math.min(i, TONES.length - 1)],
    tint: TINTS[Math.min(i, TINTS.length - 1)],
  }))
}
