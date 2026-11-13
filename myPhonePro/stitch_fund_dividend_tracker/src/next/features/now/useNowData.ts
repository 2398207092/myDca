/** 现在面数据：总资产 / 构成 / 账户 / 今日事件（全部来自真实接口）。 */
import { computed } from 'vue'
import { getAssetOverview, type AssetOverview } from '@/api/assetOverview'
import { getDashboard, type DashboardData } from '@/api/dashboard'
import { getAccounts, type AccountItem } from '@/api/account'
import { listHoldings, getValueChange, type HoldingItem } from '@/api/holding'
import { listEvents, type DividendEventItem } from '@/api/event'
import { listTransactionsInRange, type TransactionItem } from '@/api/transaction'
import { useAsyncData } from '@/next/shared/useAsync'
import { todayStr, formatTimeHM } from '@/next/shared/date'
import { unifyEvents, type MoneyEvent } from '@/next/shared/stream'
import { toNumber } from '@/next/shared/money'
import { buildComposition, resolveCashValue, toSegments, type CompositionGroup } from '@/next/shared/composition'

interface PeriodChangeLike {
  change: number
  percent: number
}

interface ValueChangeLike {
  currentValue: number
  periods: Record<string, PeriodChangeLike>
}

interface NowData {
  overview: AssetOverview
  dashboard: DashboardData
  accounts: AccountItem[]
  holdings: HoldingItem[]
  valueChange: ValueChangeLike | null
  todayTx: TransactionItem[]
  todayEvents: DividendEventItem[]
  today: string
  loadedAt: string
}

export function useNowData() {
  const asyncData = useAsyncData<NowData>(async () => {
    const today = todayStr()
    const [overview, dashboard, accounts, holdings, valueChange, todayTx, todayEvents] = await Promise.all([
      getAssetOverview(),
      getDashboard(),
      getAccounts(),
      listHoldings(),
      getValueChange().catch(() => null as ValueChangeLike | null),
      listTransactionsInRange(today, today),
      listEvents({ dateFrom: today, dateTo: today }),
    ])
    return { overview, dashboard, accounts, holdings, valueChange, todayTx, todayEvents, today, loadedAt: formatTimeHM() }
  })

  const data = asyncData.data

  /** 总资产口径：账户合计（含持仓）优先；账户为空时退回 asset-overview */
  const totalValue = computed(() => {
    const accounts = data.value?.accounts ?? []
    const sum = accounts.reduce((acc, a) => acc + toNumber(a.totalValueCny), 0)
    return sum > 0 ? sum : toNumber(data.value?.overview.totalValue)
  })

  const holdingValue = computed(() =>
    (data.value?.holdings ?? []).reduce((sum, h) => sum + toNumber(h.marketValue), 0),
  )

  /** 与构成/总资产同一口径：概览 cash 分类（含稳定币），概览缺失才退回账户现金 */
  const cashValue = computed(() =>
    resolveCashValue(data.value?.overview ?? null, data.value?.accounts ?? []),
  )

  const costValue = computed(() =>
    (data.value?.holdings ?? []).reduce((sum, h) => sum + toNumber(h.cost), 0),
  )

  const weekly = computed<PeriodChangeLike | null>(
    () => data.value?.valueChange?.periods?.week ?? null,
  )

  const composition = computed<CompositionGroup[]>(() =>
    buildComposition(data.value?.overview ?? null, data.value?.holdings ?? [], totalValue.value),
  )

  const compositionSegments = computed(() => toSegments(composition.value))

  const todayEvents = computed<MoneyEvent[]>(() => {
    if (!data.value) return []
    return unifyEvents(data.value.todayTx, data.value.todayEvents, data.value.holdings)
  })

  const isTotallyEmpty = computed(() => {
    const holdings = data.value?.holdings.length ?? 0
    const accounts = data.value?.accounts.length ?? 0
    return holdings === 0 && accounts === 0
  })

  const latestPriceDate = computed(() => data.value?.holdings[0]?.priceDate ?? '')

  return {
    ...asyncData,
    data,
    totalValue,
    holdingValue,
    cashValue,
    costValue,
    weekly,
    composition,
    compositionSegments,
    todayEvents,
    isTotallyEmpty,
    latestPriceDate,
  }
}
