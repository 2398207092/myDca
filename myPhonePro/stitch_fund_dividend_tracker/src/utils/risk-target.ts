/**
 * 风险资产目标配置的计算（只做规划，不产生交易）。
 *
 * 口径：目标百分比 = 占风险资产，四项合计 100%；现金及等价物不计入。
 * 模式 A（总额不变）：按当前风险总额重分，卖出高配、买入低配。
 * 模式 B（保留最高项）：固定当前金额最大的那只，按它的目标比例反推新总额，
 *   其他项按各自目标比例补到新总额。最高项是动态的（max current），不写死红利。
 */

export interface RiskTargetItem {
  key: string
  name: string
  current: number
  target: number
}

export interface RiskTargetRow extends RiskTargetItem {
  targetAmount: number
  delta: number
  fixed?: boolean
}

export interface RiskTargetPlan {
  mode: 'A' | 'B'
  total: number
  newTotal: number
  fixedKey: string | null
  rows: RiskTargetRow[]
  sellTotal: number
  buyTotal: number
  needNew: number
}

export interface RiskTargetConfig {
  mode: 'A' | 'B'
  targets: Record<string, number>
}

/** 解析云端/本地保存的配置；坏数据一律返回 null，避免污染页面 */
export function parseRiskTargetConfig(raw: string | null | undefined): RiskTargetConfig | null {
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object') return null
    const mode = parsed.mode
    if (mode !== 'A' && mode !== 'B') return null
    const targets = parsed.targets
    if (!targets || typeof targets !== 'object' || Array.isArray(targets)) return null

    const out: Record<string, number> = {}
    let sum = 0
    let count = 0
    for (const [key, value] of Object.entries(targets)) {
      if (typeof value !== 'number' || !Number.isFinite(value) || value < 0 || value > 100) return null
      out[key] = value
      sum += value
      count++
    }
    if (!count || Math.abs(sum - 100) > 0.01) return null
    return { mode, targets: out }
  } catch (_) {
    return null
  }
}

function round(n: number, digits = 2): number {
  const f = 10 ** digits
  return Math.round(n * f) / f
}

export function buildRiskTargetPlan(
  items: RiskTargetItem[],
  mode: 'A' | 'B',
): RiskTargetPlan | null {
  const valid = items.filter(
    (i) => Number.isFinite(i.current) && i.current >= 0 && Number.isFinite(i.target) && i.target >= 0,
  )
  if (!valid.length) return null

  const targetSum = valid.reduce((sum, i) => sum + i.target, 0)
  if (Math.abs(targetSum - 100) > 0.01) return null

  const total = valid.reduce((sum, i) => sum + i.current, 0)
  if (total <= 0) return null

  let rows: RiskTargetRow[]
  let newTotal = total
  let fixedKey: string | null = null

  if (mode === 'A') {
    rows = valid.map((i) => {
      const targetAmount = (total * i.target) / 100
      return { ...i, targetAmount, delta: targetAmount - i.current }
    })
  } else {
    const fixed = valid.reduce((a, b) => (b.current > a.current ? b : a), valid[0])
    if (fixed.target <= 0) return null
    fixedKey = fixed.key
    newTotal = fixed.current / (fixed.target / 100)
    rows = valid.map((i) => {
      if (i.key === fixed.key) {
        return { ...i, targetAmount: fixed.current, delta: 0, fixed: true }
      }
      const targetAmount = (newTotal * i.target) / 100
      return { ...i, targetAmount, delta: targetAmount - i.current }
    })
  }

  const sellTotal = rows.reduce((sum, r) => sum + Math.max(0, -r.delta), 0)
  const buyTotal = rows.reduce((sum, r) => sum + Math.max(0, r.delta), 0)

  return {
    mode,
    total: round(total),
    newTotal: round(newTotal),
    fixedKey,
    rows,
    sellTotal: round(sellTotal),
    buyTotal: round(buyTotal),
    needNew: round(mode === 'B' ? buyTotal : 0),
  }
}

/** 把当前占比四舍五入成整数，并微调最大项，保证合计 100 */
export function defaultTargets(items: { key: string; current: number }[]): Record<string, number> {
  const total = items.reduce((sum, i) => sum + i.current, 0)
  const out: Record<string, number> = {}
  if (total <= 0) return out
  items.forEach((i) => {
    out[i.key] = Math.round((i.current / total) * 100)
  })
  const sum = Object.values(out).reduce((s, v) => s + v, 0)
  const diff = 100 - sum
  if (diff !== 0 && items.length) {
    const largest = items.reduce((a, b) => (b.current > a.current ? b : a), items[0])
    out[largest.key] = Math.max(0, (out[largest.key] ?? 0) + diff)
  }
  return out
}