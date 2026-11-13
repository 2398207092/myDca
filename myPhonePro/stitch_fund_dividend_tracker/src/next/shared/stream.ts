/**
 * 现在面 / 过去面共用：把「交易流水」和「分红事件」统一成一条钱的轨迹。
 * 规则（钱的口径）：
 * - 买入 = 现金流出；卖出 = 现金流入；复投/送股不改变现金，只改份额；
 * - 分红事件：已到账 = 现金流入，待确认 = 不参与现金但展示预期，已取消 = 不展示金额符号；
 * - 颜色只表达「金额来源」，不用红绿：红绿属于盈亏，不是现金流。
 */
import type { HoldingItem } from '@/api/holding'
import type { DividendEventItem } from '@/api/event'
import type { TransactionItem } from '@/api/transaction'
import { fmtHoldingQuantity, fmtNav, fmtSignedMoney, toNumber } from './money'
import { EVENT_STATUS_LABEL, EVENT_TYPE_LABEL, TX_TYPE_LABEL } from './dict'

export type EventDirection = 'in' | 'out' | 'none'
export type EventSource = 'tx' | 'dividend'

export interface MoneyEvent {
  id: string
  source: EventSource
  date: string
  /** 主标题：买入 / 卖出 / 分红到账 / 定投执行 … */
  title: string
  holdingId?: string
  holdingName: string
  /** 持仓类型：crypto 用 8 位小数 + 枚展示数量 */
  holdingType?: string
  /** 现金方向：in = 流入，out = 流出，none = 不动现金 */
  direction: EventDirection
  /** 带符号的金额文本（不带颜色语义） */
  amountText: string
  /** 次级说明：数量/净值/状态/渠道 */
  meta: string
  /** 状态：已到账/待确认/已取消/已成交（无状态则为空串） */
  statusText: string
  tx?: TransactionItem
  ev?: DividendEventItem
}

const STATUS_BY_EVENT = EVENT_STATUS_LABEL

function txDirection(type: string): EventDirection {
  if (type === 'sell') return 'in'
  if (type === 'buy') return 'out'
  return 'none'
}

/**
 * 交易现金影响（与后端 TransactionService 口径一致）：
 * - 后端 total 恒等于 数量×净值 + 手续费（买卖都一样）；
 * - 但卖出实际到账 = 数量×净值 − 手续费，所以不能用 total 直接当现金流入；
 * - 买入实际扣款 = 数量×净值 + 手续费。
 */
function txCashAmount(t: TransactionItem): number {
  const gross = toNumber(t.quantity) * toNumber(t.price)
  const fee = toNumber(t.fee)
  if (gross > 0) {
    if (t.type === 'sell') return Math.max(0, gross - fee)
    if (t.type === 'buy') return gross + fee
  }
  return Math.abs(toNumber(t.total))
}

function txMeta(t: TransactionItem, holdingType?: string): string {
  const parts: string[] = []
  if (toNumber(t.quantity) !== 0) parts.push(fmtHoldingQuantity(t.quantity, holdingType))
  if (toNumber(t.price) !== 0) parts.push(`净值 ${fmtNav(t.price)}`)
  return parts.join(' · ')
}

function txTitle(t: TransactionItem): string {
  if (t.dcaPlanId) return '定投执行'
  if (t.source === 'dca' || t.source === 'scheduler') return '定投执行'
  return TX_TYPE_LABEL[t.type] ?? t.type
}

function dividendTitle(e: DividendEventItem): string {
  if (e.converted) return '分红复投'
  const base = EVENT_TYPE_LABEL[e.type] ?? '分红'
  if (e.type === 'payout') return STATUS_BY_EVENT[e.status] === '已到账' ? '分红到账' : base
  return base
}

function unifyTx(t: TransactionItem, holding: HoldingItem | undefined): MoneyEvent {
  const direction = txDirection(t.type)
  const amount = txCashAmount(t)
  const amountText =
    direction === 'in' ? fmtSignedMoney(amount) : direction === 'out' ? fmtSignedMoney(-amount) : ''
  return {
    id: `tx:${t.id}`,
    source: 'tx',
    date: t.date,
    title: txTitle(t),
    holdingId: t.holdingId,
    holdingName: holding?.name ?? '',
    holdingType: holding?.type,
    direction,
    amountText,
    meta: txMeta(t, holding?.type),
    statusText: '',
    tx: t,
  }
}

function unifyDividend(e: DividendEventItem, holding?: HoldingItem): MoneyEvent {
  const isPayout = e.type === 'payout'
  const status = STATUS_BY_EVENT[e.status] ?? ''
  const amount = toNumber(e.amount)
  const isConverted = Boolean(e.converted)
  // 只有「派息日」事件才是现金分红；registration / ex_dividend / announcement 是信息行，
  // 即使带金额也不计入现金（同步接口会给同一笔分红生成三种事件）。
  const isCash = isPayout && !isConverted && status === '已到账' && amount > 0
  const direction: EventDirection = isCash ? 'in' : 'none'
  let amountText = ''
  if (!isPayout) amountText = ''
  else if (isConverted) amountText = '已复投'
  else if (status === '已到账' && amount > 0) amountText = fmtSignedMoney(amount)
  else if (status === '待确认' && amount > 0) amountText = `预计 ${fmtSignedMoney(amount)}`
  const metaParts: string[] = []
  if (e.description) metaParts.push(e.description)
  // 状态由 StatusPill 单独展示，这里不再重复拼「待确认/已到账」，避免窄屏换行
  return {
    id: `ev:${e.id}`,
    source: 'dividend',
    date: e.date,
    title: dividendTitle(e),
    holdingId: e.holdingId,
    holdingName: e.holdingName || holding?.name || '',
    holdingType: holding?.type,
    direction,
    amountText,
    meta: metaParts.join(' · '),
    statusText: isPayout ? status : '',
    ev: e,
  }
}

export function unifyEvents(
  transactions: TransactionItem[],
  dividends: DividendEventItem[],
  holdings: HoldingItem[],
): MoneyEvent[] {
  const holdingById = new Map(holdings.map((h) => [h.id, h]))
  const list: MoneyEvent[] = [
    ...transactions.map((t) => unifyTx(t, holdingById.get(t.holdingId))),
    ...dividends.map((e) => unifyDividend(e, holdingById.get(e.holdingId))),
  ]
  return list.sort((a, b) => {
    if (a.date !== b.date) return a.date < b.date ? 1 : -1
    return a.id < b.id ? 1 : -1
  })
}

export interface EventGroup {
  date: string
  items: MoneyEvent[]
}

export function groupByDate(events: MoneyEvent[]): EventGroup[] {
  const map = new Map<string, MoneyEvent[]>()
  for (const ev of events) {
    const arr = map.get(ev.date)
    if (arr) arr.push(ev)
    else map.set(ev.date, [ev])
  }
  return [...map.entries()].map(([date, items]) => ({ date, items }))
}

/** 现金流合计（只统计真正的现金方向，复投/送股/待确认不参与） */
export function cashSummary(events: MoneyEvent[]): { inflow: number; outflow: number; dividend: number } {
  let inflow = 0
  let outflow = 0
  let dividend = 0
  for (const ev of events) {
    const amount = ev.ev ? toNumber(ev.ev.amount) : Math.abs(toNumber(ev.tx?.total))
    if (ev.direction === 'in') {
      inflow += amount
      if (ev.source === 'dividend') dividend += amount
    } else if (ev.direction === 'out') {
      outflow += amount
    }
  }
  return { inflow, outflow, dividend }
}
