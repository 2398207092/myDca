/**
 * 格式化金额：¥0.00 / ¥1.23万 / ¥1.23亿
 */
export function formatMoney(value: number | undefined | null): string {
  if (value == null || value === 0) return '¥0.00'
  if (Math.abs(value) >= 1_0000_0000) return `¥${(value / 1_0000_0000).toFixed(2)}亿`
  if (Math.abs(value) >= 1_0000) return `¥${(value / 1_0000).toFixed(2)}万`
  return `¥${value.toFixed(2)}`
}
