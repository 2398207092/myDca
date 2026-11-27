/**
 * 格式化金额：¥0.00 / ¥1.23万 / ¥1.23亿
 */
export function formatMoney(value: number | undefined | null): string {
  if (value == null || value === 0) return '¥0.00'
  if (Math.abs(value) >= 1_0000_0000) return `¥${(value / 1_0000_0000).toFixed(2)}亿`
  if (Math.abs(value) >= 1_0000) return `¥${(value / 1_0000).toFixed(2)}万`
  return `¥${value.toFixed(2)}`
}

const cnyFormatter = new Intl.NumberFormat('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

/**
 * 格式化人民币：¥1,234.56
 * 用于预算/记账这类小额场景（不做万元压缩，避免「¥0.00万」看不清）
 */
export function formatYuan(value: number | undefined | null): string {
  return '¥' + cnyFormatter.format(Number(value ?? 0))
}

