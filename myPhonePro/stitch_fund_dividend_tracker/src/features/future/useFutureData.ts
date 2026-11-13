/**
 * 未来面数据：预测分红 / 生活支出覆盖 / 定投计划与本月预算 / 账户名。
 *
 * 口径说明：
 * - 预测分红 = dashboard.predictedAnnualDividend（后端按持仓 predictedDividend 汇总，是「预测」不是已到账）；
 * - 覆盖进度 = coverage.predictedAnnualDividend ÷ coverage.totalAnnualExpense；
 * - 本月预算 = dca-plans/budget（统一人民币口径，crypto 计划后端已折算）；
 * - dashboard.tenYearExpectedReturn 后端实现为「成本息率 × 10」（比率，不是金额），
 *   与设计稿的「10 年预期 ¥X」不一致，本面不展示，等 lead 确认口径。
 */
import { computed } from 'vue'
import { getDashboard, type DashboardData } from '@/api/dashboard'
import { getCoverageData, type CoverageData } from '@/api/expense'
import { getDcaBudget, listDcaPlans, type DcaBudgetVO, type DcaPlanVO } from '@/api/dca'
import { getAccounts, type AccountItem } from '@/api/account'
import { useAsyncData } from '@/shared/useAsync'
import { currentMonth, monthLabel } from '@/shared/date'
import { toNumber } from '@/shared/money'

export interface FutureData {
  dashboard: DashboardData
  coverage: CoverageData
  plans: DcaPlanVO[]
  accounts: AccountItem[]
  /** 预算接口单独兜底：失败时为 null，不影响预测与计划展示 */
  budget: DcaBudgetVO | null
  year: number
  month: number
  monthLabelText: string
}

/** 列表排序：进行中 → 已暂停 → 已结束 */
const STATUS_ORDER: Record<string, number> = { active: 0, paused: 1, ended: 2 }

export function useFutureData() {
  const asyncData = useAsyncData<FutureData>(async () => {
    const monthKey = currentMonth()
    const [yearText, monthText] = monthKey.split('-')
    const year = Number(yearText)
    const month = Number(monthText)
    const [dashboard, coverage, plans, accounts] = await Promise.all([
      getDashboard(),
      getCoverageData(),
      listDcaPlans(),
      getAccounts(),
    ])
    const budget = await getDcaBudget(year, month).catch(() => null)
    return { dashboard, coverage, plans, accounts, budget, year, month, monthLabelText: monthLabel(monthKey) }
  })

  const data = asyncData.data

  const predictedAnnual = computed(() => toNumber(data.value?.dashboard.predictedAnnualDividend))
  const monthlyPredicted = computed(() => toNumber(data.value?.dashboard.monthlyPredictedDividend))
  const overallDividendRate = computed(() => toNumber(data.value?.dashboard.overallDividendRate))
  /** 已收（非预测）与持仓数量：从「现在面」挪到未来面做对账用 */
  const totalReceived = computed(() => toNumber(data.value?.dashboard.totalDividendReceived))
  const holdingCount = computed(() => toNumber(data.value?.dashboard.totalHoldings))

  const totalAnnualExpense = computed(() => toNumber(data.value?.coverage.totalAnnualExpense))
  const coveredExpenseCount = computed(() => data.value?.coverage.coveredExpenses ?? 0)
  const totalExpenseCount = computed(() => data.value?.coverage.totalExpenses ?? 0)

  /** 覆盖比例（0~100，仅展示用；后端没有给百分比字段，这里按两个金额直接相除） */
  const coveragePercent = computed(() => {
    const total = totalAnnualExpense.value
    if (total <= 0) return 0
    return Math.min(100, (predictedAnnual.value / total) * 100)
  })

  /** 还差多少年支出才能被预测分红覆盖 */
  const coverageGap = computed(() => Math.max(0, totalAnnualExpense.value - predictedAnnual.value))

  const plans = computed(() => {
    const list = [...(data.value?.plans ?? [])]
    return list.sort((a, b) => (STATUS_ORDER[a.status] ?? 9) - (STATUS_ORDER[b.status] ?? 9))
  })

  const accountNames = computed(() => {
    const map: Record<string, string> = {}
    for (const account of data.value?.accounts ?? []) map[account.id] = account.name
    return map
  })

  function accountNameOf(accountId: string | null | undefined): string {
    if (!accountId) return ''
    return accountNames.value[accountId] ?? ''
  }

  const budget = computed(() => data.value?.budget ?? null)
  const budgetTotal = computed(() => toNumber(data.value?.budget?.totalAmount))
  const budgetPlans = computed(() => data.value?.budget?.plans ?? [])
  const budgetTradingDays = computed(() => data.value?.budget?.tradingDays ?? 0)
  const monthLabelText = computed(() => data.value?.monthLabelText ?? '')

  return {
    ...asyncData,
    data,
    predictedAnnual,
    monthlyPredicted,
    overallDividendRate,
    totalReceived,
    holdingCount,
    totalAnnualExpense,
    coveredExpenseCount,
    totalExpenseCount,
    coveragePercent,
    coverageGap,
    plans,
    accountNameOf,
    budget,
    budgetTotal,
    budgetPlans,
    budgetTradingDays,
    monthLabelText,
  }
}
