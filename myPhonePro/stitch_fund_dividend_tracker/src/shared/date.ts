/** 日期工具：全部按本地时区（Asia/Shanghai）解析，避免 UTC 偏移导致跨日错位。 */

const WEEKDAY = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

export function pad2(n: number): string {
  return String(n).padStart(2, '0')
}

/** YYYY-MM-DD */
export function todayStr(now: Date = new Date()): string {
  return `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-${pad2(now.getDate())}`
}

export function formatTimeHM(now: Date = new Date()): string {
  return `${pad2(now.getHours())}:${pad2(now.getMinutes())}`
}

/** 解析 YYYY-MM-DD 为本地时间（不经过 UTC） */
export function parseDate(date: string): Date | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(date)
  if (!m) return null
  return new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]))
}

/** 2026-08-14 → 周五 */
export function weekdayZH(date: string): string {
  const d = parseDate(date)
  return d ? WEEKDAY[d.getDay()] : ''
}

/** 2026-08-14 → 08-14 */
export function formatMonthDay(date: string): string {
  const d = parseDate(date)
  return d ? `${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}` : date
}

/** 2026-08-14 → 08-14 周五 */
export function formatShortDate(date: string): string {
  const w = weekdayZH(date)
  return w ? `${formatMonthDay(date)} ${w}` : formatMonthDay(date)
}

/** 时间轴分组标题：今天 → 「08-14 今天 · 周五」；其余 → 「08-13 周四」 */
export function dateGroupLabel(date: string, now: Date = new Date()): string {
  const md = formatMonthDay(date)
  const w = weekdayZH(date)
  if (date === todayStr(now)) return `${md} 今天${w ? ` · ${w}` : ''}`
  return w ? `${md} ${w}` : md
}

/** 当前月份 YYYY-MM */
export function currentMonth(now: Date = new Date()): string {
  return `${now.getFullYear()}-${pad2(now.getMonth() + 1)}`
}

export function isMonthString(v: string): boolean {
  return /^\d{4}-\d{2}$/.test(v)
}

/** 月份加减 */
export function shiftMonth(ym: string, delta: number): string {
  const [y, m] = ym.split('-').map(Number)
  const d = new Date(y, m - 1 + delta, 1)
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}`
}

/** 2026-08 → 2026 年 8 月 */
export function monthLabel(ym: string): string {
  const [y, m] = ym.split('-')
  return `${y} 年 ${Number(m)} 月`
}

/** 月份的闭区间（含首尾） */
export function monthRange(ym: string): { from: string; to: string } {
  const [y, m] = ym.split('-').map(Number)
  const lastDay = new Date(y, m, 0).getDate()
  return { from: `${ym}-01`, to: `${ym}-${pad2(lastDay)}` }
}

/** 2026-08-14 → 2026-08-14（原样，语义化命名） */
export function formatDateLong(date: string): string {
  return date
}
