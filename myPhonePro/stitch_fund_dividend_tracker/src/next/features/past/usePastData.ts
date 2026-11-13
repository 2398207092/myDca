/**
 * 过去面数据（真实接口，无假数据）。
 * - 交易：后端 /transactions 的 dateFrom/dateTo 不生效（2026-11-13 实测，lead 已确认），
 *   统一用 listTransactionsInRange() 在客户端按日期过滤；
 * - 分红事件：服务端 dateFrom/dateTo 生效，直接按月区间取；
 * - 持仓只用来把 holdingId 映射成名称；
 * - 走势 /asset-history/overview 只支持 month/quarter/all，range=month 是「近 30 天」，
 *   与所选月份无关，所以单独加载、单独标注口径。
 *
 * 钱的口径（与 TEAM-CONTRACT §6 一致）：
 * - 买入 = 现金流出（数量×净值 + 手续费）；卖出 = 现金流入（数量×净值 − 手续费）；
 * - 分红只有「派息 payout + 已到账 distributed + 未转复投」才计入现金；
 *   待确认、已复投、已取消都不计现金（待确认/复投在摘要里单独提示金额）。
 */
import { computed, watch, type Ref } from 'vue'
import { getAssetOverview, type TotalAssetSeries } from '@/api/assetHistory'
import { listEvents, type DividendEventItem } from '@/api/event'
import { listHoldings, type HoldingItem } from '@/api/holding'
import { listTransactionsInRange, type TransactionItem } from '@/api/transaction'
import { currentMonth, formatMonthDay, monthLabel, monthRange, shiftMonth } from '@/next/shared/date'
import { toNumber } from '@/next/shared/money'
import { groupByDate, unifyEvents, type EventGroup, type MoneyEvent } from '@/next/shared/stream'
import { useAsyncData } from '@/next/shared/useAsync'

export type PastFilter = 'all' | 'tx' | 'dividend' | 'dca'

export interface PastFilterOption {
  value: PastFilter
  label: string
}

export const PAST_FILTERS: PastFilterOption[] = [
  { value: 'all', label: '全部' },
  { value: 'tx', label: '交易' },
  { value: 'dividend', label: '分红' },
  { value: 'dca', label: '定投' },
]

export interface MonthStats {
  /** 买入现金流出 */
  buy: number
  /** 卖出现金流入 */
  sell: number
  /** 已到账分红现金流入（未转复投） */
  dividend: number
  /** 待确认分红金额（不计现金） */
  pendingDividend: number
  /** 已复投分红金额（不计现金） */
  reinvestDividend: number
}

export interface TrendPointLite {
  label: string
  value: number
}

interface MonthBundle {
  month: string
  transactions: TransactionItem[]
  events: DividendEventItem[]
  holdings: HoldingItem[]
}

/** 是否定投执行产生的交易 */
export function isDcaTx(t: TransactionItem): boolean {
  return Boolean(t.dcaPlanId) || t.source === 'dca' || t.source === 'scheduler'
}

/** 买入现金流出：后端 total = 数量×净值 + 手续费；total 缺失时按同口径兜底 */
function buyCashOut(t: TransactionItem): number {
  const total = Math.abs(toNumber(t.total))
  if (total > 0) return total
  return Math.abs(toNumber(t.quantity) * toNumber(t.price)) + toNumber(t.fee)
}

/** 卖出现金流入：后端 total 也含手续费，但到账口径是 数量×净值 − 手续费（与 TransactionService 删单口径一致） */
function sellCashIn(t: TransactionItem): number {
  const gross = Math.abs(toNumber(t.quantity) * toNumber(t.price))
  const base = gross > 0 ? gross : Math.abs(toNumber(t.total))
  return Math.max(0, base - toNumber(t.fee))
}

export function usePastData(month: Ref<string>, filter: Ref<PastFilter>) {
  const monthText = computed(() => monthLabel(month.value))
  const isCurrentMonth = computed(() => month.value === currentMonth())
  const canGoNext = computed(() => month.value < currentMonth())

  const monthAsync = useAsyncData<MonthBundle>(async () => {
    const target = month.value
    const { from, to } = monthRange(target)
    const [transactions, events, holdings] = await Promise.all([
      listTransactionsInRange(from, to),
      listEvents({ dateFrom: from, dateTo: to }),
      listHoldings(),
    ])
    return { month: target, transactions, events, holdings }
  })

  watch(month, () => {
    filter.value = 'all'
    void monthAsync.load()
  })

  /** 只有「选中月份」的数据到位才算 ready，避免切月瞬间把旧月份数据挂在新标题下 */
  const data = computed<MonthBundle | null>(() => {
    const bundle = monthAsync.data.value
    return bundle && bundle.month === month.value ? bundle : null
  })
  const ready = computed(() => data.value !== null)

  const allEvents = computed<MoneyEvent[]>(() => {
    const bundle = data.value
    if (!bundle) return []
    return unifyEvents(bundle.transactions, bundle.events, bundle.holdings)
  })

  function isDcaEvent(ev: MoneyEvent): boolean {
    return ev.source === 'tx' && !!ev.tx && isDcaTx(ev.tx)
  }

  const filterCounts = computed<Record<PastFilter, number>>(() => {
    const counts: Record<PastFilter, number> = { all: 0, tx: 0, dividend: 0, dca: 0 }
    for (const ev of allEvents.value) {
      counts.all += 1
      if (ev.source === 'dividend') counts.dividend += 1
      else if (isDcaEvent(ev)) counts.dca += 1
      else counts.tx += 1
    }
    return counts
  })

  const filteredEvents = computed<MoneyEvent[]>(() => {
    switch (filter.value) {
      case 'tx':
        return allEvents.value.filter((ev) => ev.source !== 'dividend' && !isDcaEvent(ev))
      case 'dividend':
        return allEvents.value.filter((ev) => ev.source === 'dividend')
      case 'dca':
        return allEvents.value.filter(isDcaEvent)
      default:
        return allEvents.value
    }
  })

  const groups = computed<EventGroup[]>(() => groupByDate(filteredEvents.value))

  const stats = computed<MonthStats>(() => {
    const result: MonthStats = { buy: 0, sell: 0, dividend: 0, pendingDividend: 0, reinvestDividend: 0 }
    const bundle = data.value
    if (!bundle) return result
    for (const t of bundle.transactions) {
      if (t.type === 'buy') result.buy += buyCashOut(t)
      else if (t.type === 'sell') result.sell += sellCashIn(t)
    }
    for (const ev of bundle.events) {
      if (ev.type !== 'payout') continue
      const amount = Math.abs(toNumber(ev.amount))
      if (ev.status === 'distributed') {
        if (ev.converted) result.reinvestDividend += amount
        else result.dividend += amount
      } else if (ev.status === 'pending') {
        result.pendingDividend += amount
      }
    }
    return result
  })

  const emptyMonth = computed(() => ready.value && allEvents.value.length === 0)

  // ---- 近 30 天资产走势（与所选月份无关，单独请求） ----
  const trendAsync = useAsyncData<TotalAssetSeries>(() => getAssetOverview('month'))
  const trendPoints = computed<TrendPointLite[]>(() =>
    (trendAsync.data.value?.series ?? [])
      .map((p) => ({ label: formatMonthDay(p.date), value: toNumber(p.totalMarketValue) }))
      .filter((p) => Number.isFinite(p.value)),
  )
  const trendHasData = computed(() => trendPoints.value.length >= 2)
  const trendFirst = computed<TrendPointLite | null>(() => trendPoints.value[0] ?? null)
  const trendLast = computed<TrendPointLite | null>(
    () => trendPoints.value[trendPoints.value.length - 1] ?? null,
  )

  function goPrev(): void {
    month.value = shiftMonth(month.value, -1)
  }

  function goNext(): void {
    if (canGoNext.value) month.value = shiftMonth(month.value, 1)
  }

  function goCurrent(): void {
    if (!isCurrentMonth.value) month.value = currentMonth()
  }

  return {
    // 月份
    monthText,
    isCurrentMonth,
    canGoNext,
    goPrev,
    goNext,
    goCurrent,
    // 月度数据
    ready,
    loading: monthAsync.loading,
    error: monthAsync.error,
    load: monthAsync.load,
    data,
    allEvents,
    filterCounts,
    filteredEvents,
    groups,
    stats,
    emptyMonth,
    isDcaEvent,
    // 走势
    trendLoading: trendAsync.loading,
    trendError: trendAsync.error,
    trendData: trendAsync.data,
    trendPoints,
    trendHasData,
    trendFirst,
    trendLast,
    trendLoad: () => trendAsync.load(),
  }
}
