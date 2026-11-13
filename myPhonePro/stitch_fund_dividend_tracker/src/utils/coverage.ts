import type { ExpenseCoverageItem } from '@/api/expense'

/**
 * 计算「再攒多少分红才能点亮目标类目」。
 *
 * 为什么不用接口的 remainingDividend：
 * 后端在遇到第一个「奋斗中」类目时，会把该类目"吃掉"的额度直接记为已分配，
 * remainingDividend 因此恒为 0；若直接拿它算，会把目标类目的全年金额当成缺口
 * （2026-11-13 审计案例：显示「再攒 ¥9600.00」，实际应为 ¥8,059.10）。
 *
 * 这里改用「预测年度分红 − 已覆盖类目年金额合计」作为真实剩余额度：
 *   need = 目标类目年金额 − max(0, 预测年度分红 − 已覆盖类目年金额合计)
 * 结果收口到「分」，避免 2140.9 − 600 这类浮点尾差。
 */
export function computeCoverageNeed(
  target: Pick<ExpenseCoverageItem, 'annualAmount'>,
  expenses: ExpenseCoverageItem[],
  predictedAnnualDividend: number,
): number {
  const coveredSum = expenses
    .filter((e) => e.covered)
    .reduce((sum, e) => sum + (Number(e.annualAmount) || 0), 0)
  const remaining = Math.max(0, (Number(predictedAnnualDividend) || 0) - coveredSum)
  const need = (Number(target.annualAmount) || 0) - remaining
  return Math.max(0, Math.round(need * 100) / 100)
}
