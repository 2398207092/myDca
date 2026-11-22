/**
 * 交易表单纯计算（只做算术，不发请求，便于单测）。
 *
 * 2026-11-22：加密币买入原先要同时手填「数量 / 单价 / 金额」，三个框互不联动，
 * 用户想「花 100 USDT 买 BTC」只能自己用计算器除。这里把投入金额换算成币数。
 *
 * 口径与基金买入一致：手续费先从投入金额里扣，余额再除以单价；
 * 数量向下截断到 8 位小数（BTC 最小单位 1e-8 = 1 sat），保证
 * 数量 × 单价 + 手续费 不会超过用户填的金额（后端 total 仍按 2 位四舍五入）。
 */

function toNumber(value: number | string | null | undefined): number {
  if (value === null || value === undefined || value === '') return 0
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

function toPositive(value: number | string | null | undefined): number | null {
  const n = toNumber(value)
  return n > 0 ? n : null
}

/**
 * 加密币买入：投入金额 → 币数。
 * @param amount 本次投入的 USDT 金额（含手续费）
 * @param unitPrice 单价（USDT/枚）
 * @param fee 手续费（USDT，可空）
 * @returns 币数；金额/单价非正、或金额不足以覆盖手续费时返回 0
 */
export function calcCryptoBuyQuantity(
  amount: number | string | null | undefined,
  unitPrice: number | string | null | undefined,
  fee: number | string | null | undefined = 0,
): number {
  const amountNum = toNumber(amount)
  const price = toPositive(unitPrice)
  if (amountNum <= 0 || price === null) return 0
  const feeNum = Math.max(toNumber(fee), 0)
  const net = amountNum - feeNum
  if (net <= 0) return 0
  const qty = Math.floor((net / price) * 1e8) / 1e8
  return qty > 0 ? qty : 0
}

/** 加密币数量展示：最多 8 位小数，去掉末尾 0（基金数量仍用 2 位） */
export function formatCryptoQty(value: number | string | null | undefined): string {
  return toNumber(value).toLocaleString('zh-CN', { maximumFractionDigits: 8 })
}