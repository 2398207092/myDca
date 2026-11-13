/**
 * 定投计划的「节奏文案」与频率选项（未来面列表行、定投 Sheet 共用，避免两处各写一套）。
 * 口径以后端 DcaFrequency 为准：daily / weekly / biweekly / monthly。
 */

export const WEEKDAY_NAMES = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日'] as const

export const FREQUENCY_CHOICES = [
  { value: 'daily', label: '每日' },
  { value: 'weekly', label: '每周' },
  { value: 'biweekly', label: '每两周' },
  { value: 'monthly', label: '每月' },
] as const

/** 1 → 周一 … 7 → 周日；非法值返回空串 */
export function weekdayName(day: number | null | undefined): string {
  if (day === null || day === undefined) return ''
  return WEEKDAY_NAMES[day] ?? ''
}

/** 计划节奏文案：每日 / 每周一 / 每两周 · 周五 / 每月 8 日 */
export function scheduleText(frequency: string, day: number | null | undefined): string {
  switch (frequency) {
    case 'daily':
      return '每日'
    case 'weekly':
      return day ? `每周${weekdayName(day)}` : '每周'
    case 'biweekly':
      return day ? `每两周 · ${weekdayName(day)}` : '每两周'
    case 'monthly':
      return day ? `每月 ${day} 日` : '每月'
    default:
      return frequency
  }
}
