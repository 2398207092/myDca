import type { DashboardData } from './dashboard'

export interface MetricConfig {
  key: string
  label: string
  /** money | percent | plain */
  formatter: 'money' | 'percent' | 'plain'
  /** 默认是否启用 */
  defaultEnabled: boolean
  /** 从 DashboardData 中取值，支持计算字段 */
  getValue: (d: DashboardData) => number
}

const STORAGE_KEY = 'enabled_metric_keys'

export const ALL_METRICS: MetricConfig[] = [
  // ── 已选（6项）──
  {
    key: 'totalDividendReceived',
    label: '今年已收',
    formatter: 'money',
    defaultEnabled: true,
    getValue: (d) => d.totalDividendReceived ?? 0,
  },
  {
    key: 'totalCost',
    label: '总成本',
    formatter: 'money',
    defaultEnabled: true,
    getValue: (d) => d.totalCost ?? 0,
  },
  {
    key: 'totalMarketValue',
    label: '总市值',
    formatter: 'money',
    defaultEnabled: true,
    getValue: (d) => d.totalMarketValue ?? 0,
  },
  {
    key: 'overallDividendRate',
    label: '成本息率',
    formatter: 'percent',
    defaultEnabled: true,
    getValue: (d) => d.overallDividendRate ?? 0,
  },
  {
    key: 'priceDividendRate',
    label: '市值息率',
    formatter: 'percent',
    defaultEnabled: true,
    getValue: (d) => d.priceDividendRate ?? 0,
  },
  {
    key: 'monthlyPredictedDividend',
    label: '月均预测分红',
    formatter: 'money',
    defaultEnabled: true,
    getValue: (d) => d.monthlyPredictedDividend ?? 0,
  },
  // ── 新增（5项）──
  {
    key: 'totalHoldings',
    label: '持仓数量',
    formatter: 'plain',
    defaultEnabled: false,
    getValue: (d) => d.totalHoldings ?? 0,
  },
  {
    key: 'coveredCategories',
    label: '覆盖类目',
    formatter: 'plain',
    defaultEnabled: false,
    getValue: (d) => d.coveredCategories ?? 0,
  },
  {
    key: 'profitLoss',
    label: '盈亏额',
    formatter: 'money',
    defaultEnabled: false,
    getValue: (d) => (d.totalMarketValue ?? 0) - (d.totalCost ?? 0),
  },
  {
    key: 'profitLossRate',
    label: '盈亏率',
    formatter: 'percent',
    defaultEnabled: false,
    getValue: (d) => {
      const cost = d.totalCost ?? 0
      if (cost === 0) return 0
      return ((d.totalMarketValue ?? 0) - cost) / cost * 100
    },
  },
  {
    key: 'dividendCostRatio',
    label: '分红收益率',
    formatter: 'percent',
    defaultEnabled: false,
    getValue: (d) => {
      const cost = d.totalCost ?? 0
      if (cost === 0) return 0
      return (d.totalDividendReceived ?? 0) / cost * 100
    },
  },
]

/** 读取 localStorage 中用户启用的指标 key 列表 */
export function loadEnabledKeys(): string[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) return JSON.parse(raw) as string[]
  } catch { /* ignore */ }
  // 首次使用：返回所有 defaultEnabled === true 的 key
  return ALL_METRICS.filter((m) => m.defaultEnabled).map((m) => m.key)
}

/** 保存用户启用的指标 key 列表 */
export function saveEnabledKeys(keys: string[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(keys))
}
