// ============================================================
// API 请求/响应 类型定义
// 种树 - 基金分红追踪器 后端接口类型
// ============================================================

// ===================== 通用类型 =====================

/** 成本算法枚举 */
export type CostAlgorithm = 'diluted' | 'diluted_only' | 'weighted_avg'
// diluted: 分红摊薄 (买入 - 卖出 - 分红)
// diluted_only: 摊薄成本 (买入 - 卖出)
// weighted_avg: 加权平均 (总买入金额 ÷ 总买入份额)

/** 预测分红口径枚举 */
export type ForecastHorizon = '1y' | '3y' | '5y' | 'custom'
// 1y: 近 1 年均值, 3y: 近 3 年均值（默认）, 5y: 近 5 年均值, custom: 自定义

/** 通用分页请求参数 */
export interface PaginationParams {
  page?: number  // 页码，从 1 开始，默认 1
  pageSize?: number // 每页条数，默认 20
}

/** 通用分页响应 */
export interface PaginatedResponse<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

/** 通用 API 响应包装 */
export interface ApiResponse<T = unknown> {
  code: number      // 业务状态码，200 表示成功
  message: string   // 提示信息
  data: T           // 响应数据
}

/** 通用错误响应 */
export interface ApiError {
  code: number
  message: string
  errors?: Record<string, string[]> // 字段级错误信息
}

// ===================== 用户模块 =====================

/** 用户信息 */
export interface UserInfo {
  id: string
  name: string
  avatar: string
  membership: 'pro' | 'free'
  membershipExpiry: string  // YYYY-MM-DD
  phone: string             // 已脱敏，如 138****8888
  version?: string          // App 版本号，如 "2.4.0"
}

/** 获取用户信息 - 响应 */
export type GetUserProfileResp = UserInfo

/** 更新用户信息 - 请求体 */
export interface UpdateUserProfileReq {
  name?: string
  avatar?: string
  phone?: string
}

/** 更新用户信息 - 响应 */
export type UpdateUserProfileResp = UserInfo

// ===================== 持仓模块 =====================

/** 持仓信息 */
export interface HoldingItem {
  id: string
  name: string            // 基金/资产名称
  code: string            // 基金代码/资产代码
  type: 'fund' | 'cny_asset'  // 基金 或 人民币资产
  costAlgorithm: CostAlgorithm  // 成本算法
  shares: number          // 持有份额
  cost: number            // 总成本（元）
  marketValue: number     // 当前市值（元）
  predictedDividend: number     // 预测年分红（元）
  dividendRate: number          // 成本息率（%）
  priceDividendRate: number     // 股价息率（%）
  totalDividendReceived: number  // 累计已获分红（元）
  netInvestment: number         // 净投入（元）
  dividendRecoveryRate: number  // 分红回本进度（0-100）
  estimatedRecoveryYears: number // 预计回本年限
  reinvestRecoveryYears: number  // 复投模式预计回本年限
  color: string                 // 标识色，如 "#FF7A45"
  assetCategory?: string        // 资产分类：us_stock / gold / dividend
}

/** 获取持仓列表 - 请求参数 */
export interface GetHoldingsParams extends PaginationParams {
  type?: 'fund' | 'cny_asset'  // 按类型筛选
  keyword?: string              // 按名称/代码搜索
}

/** 获取持仓列表 - 响应 */
export type GetHoldingsResp = PaginatedResponse<HoldingItem>

/** 获取单个持仓 - 路径参数 */
export interface GetHoldingDetailParams {
  id: string
}

/** 获取单个持仓 - 响应 */
export type GetHoldingDetailResp = HoldingItem

/** 创建持仓 - 请求体 */
export interface CreateHoldingReq {
  name: string
  code: string
  type: 'fund' | 'cny_asset'
  costAlgorithm?: CostAlgorithm  // 默认 'diluted'
  shares: number
  cost: number
  // marketValue 由后端自动计算：以成本价作为初始市值
}

/** 创建持仓 - 响应 */
export type CreateHoldingResp = HoldingItem

/** 更新持仓 - 路径参数 */
export interface UpdateHoldingParams {
  id: string
}

/** 更新持仓 - 请求体 */
export interface UpdateHoldingReq {
  name?: string
  costAlgorithm?: CostAlgorithm
  shares?: number
  cost?: number
  marketValue?: number
}

/** 更新持仓 - 响应 */
export type UpdateHoldingResp = HoldingItem

/** 删除持仓 - 路径参数 */
export interface DeleteHoldingParams {
  id: string
}

/** 删除持仓 - 响应 */
export interface DeleteHoldingResp {
  success: boolean
}

// ===================== 分红事件模块 =====================

/** 分红事件 */
export interface DividendEventItem {
  id: string
  holdingId: string
  holdingName: string
  type: 'registration' | 'ex_dividend' | 'payout' | 'announcement'
  date: string        // YYYY-MM-DD
  amount: number      // 分红金额（元）
  status: 'pending' | 'distributed' | 'cancelled'
  description: string // 事件描述文字
}

/** 获取分红事件列表 - 请求参数 */
export interface GetEventsParams extends PaginationParams {
  holdingId?: string     // 按持仓筛选
  month?: string         // 按月份筛选，格式 "2024-11"
  dateFrom?: string      // 开始日期 "YYYY-MM-DD"
  dateTo?: string        // 结束日期 "YYYY-MM-DD"
  type?: DividendEventItem['type'] // 按事件类型筛选
  status?: DividendEventItem['status']
}

/** 获取分红事件列表 - 响应 */
export type GetEventsResp = PaginatedResponse<DividendEventItem>

/** 获取指定日期的分红事件 - 请求参数 */
export interface GetEventsByDateParams {
  date: string  // YYYY-MM-DD
}

/** 获取指定日期的分红事件 - 响应 */
export type GetEventsByDateResp = DividendEventItem[]

/** 创建分红事件 - 请求体 */
export interface CreateEventReq {
  holdingId: string
  type: DividendEventItem['type']
  date: string
  amount?: number
  description?: string
}

/** 创建分红事件 - 响应 */
export type CreateEventResp = DividendEventItem

/** 标记分红已到账 - 路径参数 */
export interface MarkEventDistributedParams {
  id: string
}

/** 标记分红已到账 - 响应 */
export type MarkEventDistributedResp = DividendEventItem

/** 取消分红事件 - 路径参数 */
export interface CancelEventParams {
  id: string
}

/** 取消分红事件 - 响应 */
export interface CancelEventResp {
  id: string
  status: 'cancelled'
  updatedAt: string
}

// ===================== 交易记录模块 =====================

/** 交易记录 */
export interface TransactionItem {
  id: string
  holdingId: string
  type: 'buy' | 'sell' | 'bonus_share' | 'reinvest'
  date: string       // YYYY-MM-DD
  quantity: number   // 数量（份）
  price: number      // 单价（元）
  fee: number        // 交易费用（元）
  total: number      // 总金额（元）
}

/** 获取交易记录 - 请求参数 */
export interface GetTransactionsParams extends PaginationParams {
  holdingId?: string       // 按持仓筛选
  type?: TransactionItem['type']
  dateFrom?: string
  dateTo?: string
}

/** 获取交易记录 - 响应 */
export type GetTransactionsResp = PaginatedResponse<TransactionItem>

/** 创建交易 - 请求体 */
export interface CreateTransactionReq {
  holdingId: string
  type: TransactionItem['type']
  date: string
  quantity: number
  price: number
  fee?: number   // 可选，默认 0
}

/** 创建交易 - 响应 */
export type CreateTransactionResp = TransactionItem

/** 删除交易 - 路径参数 */
export interface DeleteTransactionParams {
  id: string
}

/** 删除交易 - 响应 */
export interface DeleteTransactionResp {
  success: boolean
}

// ===================== 首页看板模块 =====================

/** 首页看板摘要 */
export interface DashboardSummary {
  consecutiveDays: number       // 已连续收息天数
  predictedAnnualDividend: number // 预测年度分红总额（元）
  tenYearExpectedReturn: number // 10年预期收益（倍）
  monthlyPredictedDividend: number // 当月预计派息（元）
  monthlyMessage: string        // 通知横幅文案
  totalHoldings: number         // 持仓总数
  coveredCategories: number     // 已覆盖生活类目数
}

/** 获取首页看板 - 响应 */
export type GetDashboardResp = DashboardSummary

// ===================== 分红覆盖类目模块 =====================

/** 分红覆盖类目 */
export interface CoverageCategoryItem {
  id: string
  name: string        // 类目名称，如 "话费"
  icon: string        // Material Symbol 图标名
  percentage: number  // 覆盖百分比 (0-100)
  color: string       // 标识色，如 "#FF7A45"
}

/** 获取分红覆盖类目 - 响应 */
export type GetCoverageCategoriesResp = CoverageCategoryItem[]

/** 更新覆盖类目 - 路径参数 */
export interface UpdateCoverageCategoryParams {
  id: string
}

/** 更新覆盖类目 - 请求体 */
export interface UpdateCoverageCategoryReq {
  name?: string
  icon?: string
  percentage?: number
  color?: string
}

/** 更新覆盖类目 - 响应 */
export type UpdateCoverageCategoryResp = CoverageCategoryItem

// ===================== 汇率模块 =====================

/** 汇率信息 */
export interface ExchangeRateItem {
  pair: string     // 货币对，如 "HKD/CNY"
  label: string    // 展示标签，如 "港币/人民币"
  rate: number     // 汇率值
  updatedAt: string // 更新时间
}

/** 获取汇率 - 响应 */
export type GetExchangeRatesResp = ExchangeRateItem[]

/** 刷新汇率 - 响应 */
export interface RefreshExchangeRatesResp {
  rates: ExchangeRateItem[]
  refreshedAt: string
}

// ===================== 分红预测模块 =====================

/** 分红预测数据点 */
export interface ForecastDataPoint {
  label: string // 标签，如 "1月" 或 "2025"
  value: number // 预测值（元）
}

/** 获取持仓分红预测 - 路径参数 */
export interface GetForecastParams {
  holdingId: string
}

/** 获取持仓分红预测 - 请求参数（query） */
export interface GetForecastQuery {
  period: '12m' | '5y'  // 预测周期：近12月 或 未来5年
}

/** 获取持仓分红预测 - 响应 */
export interface GetForecastResp {
  holdingId: string
  period: '12m' | '5y'
  series: ForecastDataPoint[]
  trendPercentage: number  // 增长趋势百分比
}

// ===================== 分红记录模块 =====================

/** 分红记录 */
export interface DividendRecordItem {
  id: string
  holdingId: string
  holdingName: string
  date: string        // 派息日期
  amount: number      // 派息金额（元）
  status: 'pending' | 'distributed' | 'cancelled'
  exDividendDate?: string   // 除权除息日
  registrationDate?: string // 股权登记日
}

/** 获取分红记录 - 请求参数 */
export interface GetDividendRecordsParams extends PaginationParams {
  holdingId?: string
  year?: number        // 按年份筛选
  status?: DividendRecordItem['status']
}

/** 获取分红记录 - 响应 */
export type GetDividendRecordsResp = PaginatedResponse<DividendRecordItem>

// ===================== 统计/月度洞察模块 =====================

/** 月度洞察 */
export interface MonthlyInsight {
  richestSource: {
    holdingName: string
    amount: number
  }
  dividendIntensity: {
    monthOverMonth: number    // 环比变化百分比
    monthOverMonthText: string // 展示文案，如 "+12%"
    progressPercentage: number // 进度条百分比 (0-100)
  }
}

/** 获取月度洞察 - 请求参数 */
export interface GetMonthlyInsightParams {
  year: number
  month: number  // 1-12
}

/** 获取月度洞察 - 响应 */
export type GetMonthlyInsightResp = MonthlyInsight

// ===================== 用户设置模块 =====================

/** 用户设置 */
export interface UserSettings {
  currency: string         // 本位币，如 "CNY"
  currencyLabel: string    // 展示标签，如 "人民币"
  forecastHorizon: ForecastHorizon  // 预测分红口径，默认 '3y'
  customForecastValue?: number       // 自定义预测值，口径为 custom 时必填
  notificationsEnabled: boolean
}

/** 获取用户设置 - 响应 */
export type GetUserSettingsResp = UserSettings

/** 更新用户设置 - 请求体 */
export interface UpdateUserSettingsReq {
  currency?: string
  currencyLabel?: string
  forecastHorizon?: ForecastHorizon
  customForecastValue?: number
  notificationsEnabled?: boolean
}

/** 更新用户设置 - 响应 */
export type UpdateUserSettingsResp = UserSettings

// ===================== 通用错误码 =====================

/** 业务错误码枚举 */
export enum ErrorCode {
  // 通用错误
  SUCCESS = 200,
  BAD_REQUEST = 400,
  UNAUTHORIZED = 401,
  FORBIDDEN = 403,
  NOT_FOUND = 404,
  METHOD_NOT_ALLOWED = 405,
  CONFLICT = 409,
  TOO_MANY_REQUESTS = 429,
  INTERNAL_ERROR = 500,
  SERVICE_UNAVAILABLE = 503,

  // 业务错误（自定义编码 1000+）
  HOLDING_NOT_FOUND = 1001,
  HOLDING_CODE_EXISTS = 1002,
  EVENT_NOT_FOUND = 2001,
  TRANSACTION_NOT_FOUND = 3001,
  INSUFFICIENT_SHARES = 3002,      // 卖出份额不足
  INVALID_TRANSACTION_TYPE = 3003,
  RATE_LIMIT_EXCEEDED = 4001,      // 30 秒内重复刷新汇率
}
