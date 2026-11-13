/**
 * 金额/份额/净值格式化（全站唯一入口）。
 * 口径：¥ + 千分位 + 两位小数；不压缩万/亿；份额 2 位；净值 4 位；涨跌带符号。
 */
export type Numeric = number | string | null | undefined

export function toNumber(v: Numeric): number {
  if (v === null || v === undefined || v === '') return 0
  const n = typeof v === 'number' ? v : Number(v)
  return Number.isFinite(n) ? n : 0
}

export function hasNumber(v: Numeric): boolean {
  if (v === null || v === undefined || v === '') return false
  return Number.isFinite(typeof v === 'number' ? v : Number(v))
}

function groupFixed(n: number, digits: number): string {
  const fixed = Math.abs(n).toFixed(digits)
  const [int, dec] = fixed.split('.')
  const grouped = int.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return dec ? `${grouped}.${dec}` : grouped
}

/** 金额（带 ¥、千分位、两位小数；负数在 ¥ 前） */
export function fmtMoney(v: Numeric, fallback = '—'): string {
  if (!hasNumber(v)) return fallback
  const n = toNumber(v)
  return `${n < 0 ? '-' : ''}¥${groupFixed(n, 2)}`
}

/** 带符号金额：+¥1,240.35 / -¥320.00 / ¥0.00 */
export function fmtSignedMoney(v: Numeric, fallback = '—'): string {
  if (!hasNumber(v)) return fallback
  const n = toNumber(v)
  const sign = n > 0 ? '+' : n < 0 ? '-' : ''
  return `${sign}¥${groupFixed(n, 2)}`
}

/** 百分比：默认两位小数；signed=true 时正数带 + */
export function fmtPercent(v: Numeric, digits = 2, signed = false, fallback = '—'): string {
  if (!hasNumber(v)) return fallback
  const n = toNumber(v)
  const sign = signed && n > 0 ? '+' : ''
  return `${sign}${n.toFixed(digits)}%`
}

/** 份额：10,000.00 份 */
export function fmtShares(v: Numeric, fallback = '—'): string {
  if (!hasNumber(v)) return fallback
  return `${groupFixed(toNumber(v), 2)} 份`
}

/**
 * 持仓数量：加密币用 8 位小数 + 枚（BTC 0.01139738 不能显示成 0.01 份），
 * 基金/ETF 等沿用 2 位小数 + 份。所有展示份额/数量的地方必须走这里。
 */
export function fmtHoldingQuantity(
  v: Numeric,
  holdingType?: string | null,
  fallback = '—',
): string {
  if (!hasNumber(v)) return fallback
  if (holdingType === 'crypto') return `${groupFixed(toNumber(v), 8)} 枚`
  return fmtShares(v, fallback)
}

/** 净值：4 位小数（带千分位） */
export function fmtNav(v: Numeric, fallback = '—'): string {
  if (!hasNumber(v)) return fallback
  return groupFixed(toNumber(v), 4)
}

/** 纯数字（份额/数量等）：指定小数位，不带单位 */
export function fmtNumber(v: Numeric, digits = 2, fallback = '—'): string {
  if (!hasNumber(v)) return fallback
  return groupFixed(toNumber(v), digits)
}

export type DeltaTone = 'up' | 'down' | 'flat'

export function deltaTone(v: Numeric): DeltaTone {
  const n = toNumber(v)
  if (n > 0) return 'up'
  if (n < 0) return 'down'
  return 'flat'
}

export function deltaArrow(v: Numeric): '▲' | '▼' | '' {
  const tone = deltaTone(v)
  return tone === 'up' ? '▲' : tone === 'down' ? '▼' : ''
}

/** 涨跌整体文案：▲ +¥1,240.35（+0.97%） */
export function fmtDelta(change: Numeric, percent?: Numeric): string {
  const arrow = deltaArrow(change)
  const money = fmtSignedMoney(change)
  const pct = hasNumber(percent) ? `（${fmtPercent(percent, 2, true)}）` : ''
  return `${arrow} ${money}${pct}`
}
