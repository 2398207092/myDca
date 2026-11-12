/**
 * 展示层数字格式（只影响显示，不参与计算）
 *
 * 全站口径（2026-11-11 卡片化重构定稿）：
 * 1. 钱一律「¥ + 千分位 + 两位小数」，主数、行值、合计同口径；
 * 2. 不用「万 / 亿」压缩金额——同一页面里 ¥3.66万 和 ¥2196.33 混排是"乱"的主要来源；
 *    万/亿 只允许出现在极窄的说明文字（formatWide）；
 * 3. 负数写成 -¥1,234.56（负号在 ¥ 前），变化值写成 +¥133.16；
 * 4. 份额 / 数量两位小数；净值 / 单价四位小数（不带 ¥，单位在行尾说明）。
 */

export interface MoneyOptions {
  /** 小数位，默认 2 */
  digits?: number
  /** 正数是否带 +（负数永远带 -），默认 false */
  sign?: boolean
  /** 是否带 ¥，默认 true */
  symbol?: boolean
}

export function formatMoney(value: number | null | undefined, options: MoneyOptions = {}): string {
  const digits = options.digits ?? 2
  const symbol = options.symbol ?? true
  const v = Number.isFinite(value as number) ? Number(value) : 0
  const body = Math.abs(v).toLocaleString('en-US', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  })
  const sign = v < 0 ? '-' : options.sign ? '+' : ''
  return `${sign}${symbol ? '¥' : ''}${body}`
}

/** 百分比：+1.23% / -5.38% */
export function formatPercent(
  value: number | null | undefined,
  digits = 2,
  sign = true,
): string {
  const v = Number.isFinite(value as number) ? Number(value) : 0
  const body = Math.abs(v).toFixed(digits)
  const prefix = v < 0 ? '-' : sign ? '+' : ''
  return `${prefix}${body}%`
}

/** 份额 / 数量：32,477.86 */
export function formatQty(value: number | null | undefined, digits = 2): string {
  const v = Number.isFinite(value as number) ? Number(value) : 0
  return v.toLocaleString('en-US', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  })
}

/** 净值 / 单价：1.0590（默认四位；大数（加密单价/成本）自动带千分位） */
export function formatPrice(value: number | null | undefined, digits = 4): string {
  const v = Number.isFinite(value as number) ? Number(value) : 0
  return v.toLocaleString('en-US', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  })
}

/** 极窄说明文字才允许的万/亿口径 */
export function formatWide(value: number | null | undefined): string {
  const v = Number.isFinite(value as number) ? Number(value) : 0
  const abs = Math.abs(v)
  if (abs >= 1_0000_0000) return `¥${(v / 1_0000_0000).toFixed(2)}亿`
  if (abs >= 1_0000) return `¥${(v / 1_0000).toFixed(2)}万`
  return formatMoney(v)
}
