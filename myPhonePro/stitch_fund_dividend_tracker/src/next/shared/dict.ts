/** 后端枚举值 → 中文文案（以后端 enums 为准，不在页面里硬编码散落字符串）。 */

export const TX_TYPE_LABEL: Record<string, string> = {
  buy: '买入',
  sell: '卖出',
  bonus_share: '送股',
  reinvest: '分红复投',
}

export const EVENT_TYPE_LABEL: Record<string, string> = {
  registration: '股权登记',
  ex_dividend: '除权除息',
  payout: '派息',
  announcement: '公告',
}

export const EVENT_STATUS_LABEL: Record<string, string> = {
  pending: '待确认',
  distributed: '已到账',
  cancelled: '已取消',
}

export const DCA_FREQUENCY_LABEL: Record<string, string> = {
  daily: '每日',
  weekly: '每周',
  biweekly: '每两周',
  monthly: '每月',
}

export const DCA_STATUS_LABEL: Record<string, string> = {
  active: '进行中',
  paused: '已暂停',
  ended: '已结束',
}

export const HOLDING_TYPE_LABEL: Record<string, string> = {
  fund: '基金',
  ETF: 'ETF',
  cny_asset: '人民币资产',
  A股: 'A股',
  港股: '港股',
  美股: '美股',
  crypto: '加密资产',
  自定义: '自定义',
}

export const ACCOUNT_PURPOSE_LABEL: Record<string, string> = {
  daily: '日常',
  invest: '投资',
  exchange: '出入金',
  other: '其他',
}

export const ASSET_CATEGORY_LABEL: Record<string, string> = {
  dividend: '红利',
  us_stock: '美股',
  gold: '黄金',
  crypto: '加密',
  cash: '现金',
  uncategorized: '未分类',
}

export function labelOf(map: Record<string, string>, key: string | null | undefined): string {
  if (!key) return ''
  return map[key] ?? key
}
