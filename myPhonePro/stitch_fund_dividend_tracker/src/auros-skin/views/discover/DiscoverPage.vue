<script setup lang="ts">
import { ref, computed, watch, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { getAssetOverview, takeSnapshot } from '@/api/assetOverview'
// 90 天走势读的是 asset-history 的快照序列（只读），与上面的总览接口是两件事
import { getAssetOverview as getAssetHistoryOverview } from '@/api/assetHistory'
import type { TotalAssetSeries } from '@/api/assetHistory'
import { listHoldings, updateHoldingCategory, getValueChange } from '@/api/holding'
import type { ValueChangeResult } from '@/api/holding'
import { listManualAssets, createManualAsset, updateManualAsset, deleteManualAsset } from '@/api/manualAsset'
import { listExchangeRates } from '@/api/exchangeRate'
import { getAccounts, type AccountItem } from '@/api/account'
import { listDcaPlans, getDcaBudget } from '@/api/dca'
import type { AssetOverview, CategoryItem } from '@/api/assetOverview'
import type { ManualAssetItem, CreateManualAssetReq, UpdateManualAssetReq } from '@/api/manualAsset'
import type { HoldingItem } from '@/api/holding'
import type { DcaPlanVO, DcaBudgetVO } from '@/api/dca'
import AurosButton from '@/auros-skin/components/auros/AurosButton.vue'
import AurosChapter from '@/auros-skin/components/auros/AurosChapter.vue'
import AurosDisclosure from '@/auros-skin/components/auros/AurosDisclosure.vue'
import AurosField from '@/auros-skin/components/auros/AurosField.vue'
import AurosGroup from '@/auros-skin/components/auros/AurosGroup.vue'
import AurosNavBar from '@/auros-skin/components/auros/AurosNavBar.vue'
import AurosRail from '@/auros-skin/components/auros/AurosRail.vue'
import AurosRow from '@/auros-skin/components/auros/AurosRow.vue'
import AurosSheet from '@/auros-skin/components/auros/AurosSheet.vue'
import AurosStat from '@/auros-skin/components/auros/AurosStat.vue'
import AurosState from '@/auros-skin/components/auros/AurosState.vue'
import AurosTrend from '@/auros-skin/components/auros/AurosTrend.vue'
import ToastNotification from '@/auros-skin/components/shared/ToastNotification.vue'
import { formatMoney } from '@/auros-skin/utils/format'

const router = useRouter()

// Toast 通知（写操作失败必须可见，禁止静默失败）
const toastMsg = ref<string | null>(null)
const toastType = ref<'success' | 'error' | 'info'>('error')
function showToast(msg: string, type: 'success' | 'error' | 'info' = 'error') {
  toastMsg.value = msg
  toastType.value = type
}
const pageState = ref<'loading' | 'ready' | 'error'>('loading')
const errorMsg = ref('')
const overview = ref<AssetOverview | null>(null)
const allHoldings = ref<HoldingItem[]>([])
const allManualAssets = ref<ManualAssetItem[]>([])
const accounts = ref<AccountItem[]>([])
const trendSeries = ref<TotalAssetSeries | null>(null)

/** 账户用途 → 中文 */
const purposeMeta: Record<string, string> = {
  daily: '日常',
  invest: '理财',
  exchange: '交易所',
  other: '其他',
}
function purposeLabel(p: string): string { return purposeMeta[p] || purposeMeta.other }

/** 金额统一走 formatMoney（¥ + 千分位 + 两位小数），不再有 万 压缩 */
function fmtExact(v: number | null | undefined): string {
  return formatMoney(v)
}

/** 资产类型标签：与名称重复时不显示（避免「现金 现金」） */
function assetLabel(a: { assetCode?: string; name: string }): string {
  const code = a.assetCode || ''
  return code && code !== a.name ? code : ''
}

/** 有资产的账户排前面，其次按金额降序；空账户自然沉底 */
const sortedAccounts = computed(() => [...accounts.value].sort((a, b) => {
  if ((a.assetCount > 0) !== (b.assetCount > 0)) return b.assetCount > 0 ? 1 : -1
  return b.totalValueCny - a.totalValueCny
}))
const valueChange = ref<ValueChangeResult | null>(null)
const detailTab = ref<'week' | 'month' | 'year'>('week')
const showAddTypePicker = ref(false)
const showAllDcaPlans = ref(false)

// Manual asset management
const showAssetSheet = ref(false)
const editingAssetId = ref<string | null>(null)
const assetForm = ref({ name: '', type: 'cash' as 'cash' | 'crypto', amount: 0, note: '', accountId: '' })

/** USD/CNY 汇率：加密资产按 USDT 数量录入，前端实时预览折算，后端保存时统一折算 */
const usdCny = ref<number | null>(null)

/**
 * 账户按本位币过滤：加密资产只能挂 USDT 账户（交易所），人民币资产挂 CNY 账户。
 * 与后端 AccountService.matchesAssetType 同一条规则。
 */
const availableAccounts = computed(() =>
  accounts.value.filter((a) =>
    assetForm.value.type === 'crypto' ? a.baseCurrency === 'USDT' : a.baseCurrency !== 'USDT'
  )
)

// 切换资产类型时，如果当前账户不再匹配就清掉，避免把 USDT 资产挂进 CNY 账户
watch(() => assetForm.value.type, () => {
  if (assetForm.value.accountId && !availableAccounts.value.some((a) => a.id === assetForm.value.accountId)) {
    // 资产必须归属账户：默认选中第一个与本位币匹配的账户
    assetForm.value.accountId = availableAccounts.value[0]?.id || ''
  }
})
onMounted(async () => {
  try {
    const rates = await listExchangeRates()
    const usd = rates.find((r) => r.pair === 'USD/CNY')
    usdCny.value = usd?.rate ?? null
  } catch {
    usdCny.value = null
  }
})
const savingAsset = ref(false)

// Category mapping
const showCatSheet = ref(false)
const selectedHolding = ref<HoldingItem | null>(null)
const selectedCategory = ref('')
const savingCategory = ref(false)

// Delete confirm
const showDeleteConfirm = ref(false)
const deletingAssetId = ref<string | null>(null)
const deletingAssetName = ref('')

function changeDisplay(num: number): string {
  return formatMoney(num, { sign: true })
}

function formatPercent(num: number): string {
  return num >= 0 ? `+${num.toFixed(2)}%` : `${num.toFixed(2)}%`
}

async function loadData() {
  pageState.value = 'loading'
  errorMsg.value = ''
  try {
    const [ov, holdings, manualAssets, vc, accs] = await Promise.all([
      getAssetOverview(),
      listHoldings(),
      listManualAssets(),
      getValueChange(),
      getAccounts(),
    ])
    overview.value = ov
    allHoldings.value = holdings
    allManualAssets.value = manualAssets
    valueChange.value = vc
    accounts.value = accs

    // Auto snapshot on first load of the day
    takeSnapshot().catch(() => {})

    // Load DCA plans
    loadDcaPlans()

    // 90 天走势（只读；失败不回滚主数据）
    loadTrend()

    pageState.value = 'ready'
  } catch (e: any) {
    console.error('加载总资产概览失败:', e)
    errorMsg.value = e.message || '加载失败'
    pageState.value = 'error'
  }
}

/** 静默刷新全部数据（不显示 loading 状态），用于编辑后的增量刷新 */
async function silentRefresh() {
  try {
    const [ov, holdings, manualAssets, vc, accs] = await Promise.all([
      getAssetOverview(),
      listHoldings(),
      listManualAssets(),
      getValueChange(),
      getAccounts(),
    ])
    overview.value = ov
    allHoldings.value = holdings
    allManualAssets.value = manualAssets
    valueChange.value = vc
    accounts.value = accs
    loadDcaPlans()
    loadTrend()
  } catch (e: any) {
    console.error('静默刷新失败:', e)
  }
}

onActivated(loadData)

function goToHistory() {
  router.push({ name: 'asset-history' })
}

/* ============================================================
 * 90 天走势（章 02 的形状）
 * ========================================================== */
async function loadTrend() {
  try {
    trendSeries.value = await getAssetHistoryOverview('quarter')
  } catch {
    trendSeries.value = null
  }
}

function fmtMonthDay(dateStr: string): string {
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

const trendPoints = computed(() => (trendSeries.value?.series ?? []).map((p) => p.totalMarketValue))
const trendStart = computed(() => {
  const first = trendSeries.value?.series?.[0]
  return first ? fmtMonthDay(first.date) : ''
})
const trendEnd = computed(() => {
  const list = trendSeries.value?.series
  const last = list?.[list.length - 1]
  return last ? fmtMonthDay(last.date) : ''
})

/* ============================================================
 * 章 01 结构
 * ========================================================== */
const topCategory = computed(() => overview.value?.categories?.[0] ?? null)
const categoryPctSum = computed(() =>
  (overview.value?.categories ?? []).reduce((s, c) => s + c.percentage, 0).toFixed(1),
)

/* ============================================================
 * 章 02 表现：去重后的三行（最大涨 / 最大跌 / 其余合计）
 * 规则本意是「同一张列表不在两章各列一遍」：这里只点名 1–2 个对象，不重列整张表
 * ========================================================== */
interface PerfRow {
  key: string
  label: string
  sub: string
  value: string
  mark: 'none' | 'lit' | 'dim'
  holdingId: string
  clickable: boolean
}
function changeBasisTag(basis?: string): string {
  return basis === 'nav' ? '估算 · ' : basis && basis !== 'fund' ? '余额 · ' : ''
}
function canOpenHolding(id?: string): boolean {
  return !!id && !id.startsWith('manual:')
}
const perfRows = computed<PerfRow[]>(() => {
  const details = valueChange.value?.periods[detailTab.value]?.details ?? []
  if (!details.length) return []
  const sorted = [...details].sort((a, b) => (b.change ?? 0) - (a.change ?? 0))
  const top = sorted[0]
  const bottom = sorted[sorted.length - 1]
  const topChange = top?.change ?? 0
  const bottomChange = bottom?.change ?? 0
  if (topChange <= 0 && bottomChange >= 0) {
    return [{ key: 'flat', label: `全部 ${details.length} 只`, sub: '区间内无变化', value: '', mark: 'dim', holdingId: '', clickable: false }]
  }
  const rows: PerfRow[] = []
  if (topChange > 0) {
    rows.push({
      key: 'top',
      label: `最大涨 · ${top.name}`,
      sub: `${changeBasisTag(top.basis)}${formatPercent(top.percent)}`,
      value: changeDisplay(top.change),
      mark: 'lit',
      holdingId: top.holdingId,
      clickable: canOpenHolding(top.holdingId),
    })
  }
  if (bottomChange < 0 && bottom !== top) {
    rows.push({
      key: 'bottom',
      label: `最大跌 · ${bottom.name}`,
      sub: `${changeBasisTag(bottom.basis)}${formatPercent(bottom.percent)}`,
      value: changeDisplay(bottom.change),
      mark: 'dim',
      holdingId: bottom.holdingId,
      clickable: canOpenHolding(bottom.holdingId),
    })
  }
  const rest = details.filter((d) => !(topChange > 0 && d === top) && !(bottomChange < 0 && d === bottom))
  if (rest.length) {
    const restZero = rest.filter((d) => (d.change ?? 0) === 0).length
    const restNonZero = rest.length - restZero
    const restSum = rest.reduce((s, d) => s + (d.change ?? 0), 0)
    rows.push({
      key: 'rest',
      label: `其余 ${rest.length} 只合计`,
      sub: restNonZero ? '' : '区间内无变化',
      value: restNonZero ? changeDisplay(restSum) : '',
      mark: 'none',
      holdingId: '',
      clickable: false,
    })
  }
  return rows
})

/* ============================================================
 * 章 03 持仓：一组「全部持仓」，行按类别排序（章节语义单一）
 * ========================================================== */
const HOLDING_CAT_ORDER = ['us_stock', 'gold', 'crypto', 'dividend']
const HOLDING_CAT_LABEL: Record<string, string> = {
  us_stock: '美股',
  gold: '黄金',
  crypto: '加密资产',
  dividend: '红利',
}
interface HoldingRow {
  h: HoldingItem
  catLabel: string
}
const holdingsRows = computed<HoldingRow[]>(() => {
  const rank = (cat?: string) => {
    const i = cat ? HOLDING_CAT_ORDER.indexOf(cat) : -1
    return i < 0 ? HOLDING_CAT_ORDER.length : i
  }
  return [...allHoldings.value]
    .sort((a, b) => rank(a.assetCategory) - rank(b.assetCategory))
    .map((h) => ({ h, catLabel: h.assetCategory ? HOLDING_CAT_LABEL[h.assetCategory] || h.assetCategory : '未分类' }))
})
const holdingsTotal = computed(() => holdingsRows.value.reduce((s, r) => s + (r.h.marketValue || 0), 0))
const holdingsShown = computed(() => holdingsRows.value.slice(0, 5))
const holdingsRest = computed(() => holdingsRows.value.slice(5))

interface CashRow {
  key: string
  kind: 'account' | 'asset'
  acc: AccountItem
  a?: AccountItem['assets'][number]
}
const cashRows = computed<CashRow[]>(() => {
  const rows: CashRow[] = []
  for (const acc of sortedAccounts.value) {
    rows.push({ key: `acc-${acc.id}`, kind: 'account', acc })
    for (const a of acc.assets) rows.push({ key: `asset-${a.id}`, kind: 'asset', acc, a })
  }
  return rows
})
const cashShown = computed(() => cashRows.value.slice(0, 5))
const cashRest = computed(() => cashRows.value.slice(5))
const manualTotal = computed(() => allManualAssets.value.reduce((s, a) => s + (Number(a.amount) || 0), 0))

function cashLabel(row: CashRow): string {
  return row.kind === 'account' ? row.acc.name : row.a?.name ?? ''
}
function cashSub(row: CashRow): string {
  if (row.kind === 'account') {
    return `${purposeLabel(row.acc.purpose)} · ${row.acc.baseCurrency}${row.acc.isDefault ? ' · 默认' : ''}`
  }
  const a = row.a
  if (!a) return ''
  const parts = [a.type === 'cash' ? '现金' : '加密资产']
  const code = assetLabel(a)
  if (code) parts.push(code)
  if (a.type === 'crypto' && (a as any).quantity != null) parts.push(`${Number((a as any).quantity).toFixed(4)} USDT`)
  return parts.join(' · ')
}
function cashValue(row: CashRow): string {
  return row.kind === 'asset' && row.a ? fmtExact(row.a.amount) : ''
}
function cashMark(row: CashRow): 'lit' | 'dim' {
  if (row.kind === 'account') return row.acc.isDefault ? 'lit' : 'dim'
  return row.a?.type === 'cash' ? 'lit' : 'dim'
}
function cashClick(row: CashRow) {
  if (row.kind === 'asset' && row.a) openEditSheet(row.a)
}

/** 持仓行点击：与改造前一致 —— 单标的类别跳详情，多标的类别打开分类弹层 */
function onHoldingRow(h: HoldingItem) {
  const cat = h.assetCategory || ''
  const count = cat ? categorizedHoldings(cat).length : uncategorizedHoldings().length
  if (count === 1) router.push(`/holding/${h.id}`)
  else openCategorySheet(h)
}

// DCA plans overview
const dcaPlans = ref<DcaPlanVO[]>([])

// Sort DCA plans by holding shares (highest first)
const sortedDcaPlans = computed(() => {
  const sorted = [...dcaPlans.value].sort((a, b) => {
    const sharesA = allHoldings.value.find(h => h.id === a.holdingId)?.shares ?? 0
    const sharesB = allHoldings.value.find(h => h.id === b.holdingId)?.shares ?? 0
    return sharesB - sharesA
  })
  return sorted
})

// Budget dialog
const showBudgetDialog = ref(false)
const budgetLoading = ref(false)
const budgetMonth = ref(new Date().getMonth() + 1)
const budgetYear = ref(new Date().getFullYear())
const budgetData = ref<DcaBudgetVO | null>(null)

async function loadBudget() {
  if (budgetLoading.value) return  // 防止快速切换重复请求
  budgetLoading.value = true
  try {
    budgetData.value = await getDcaBudget(budgetYear.value, budgetMonth.value)
  } catch (e) {
    console.error('加载定投预算失败:', e)
  } finally {
    budgetLoading.value = false
  }
}

function openBudget() {
  const now = new Date()
  budgetYear.value = now.getFullYear()
  budgetMonth.value = now.getMonth() + 1
  showBudgetDialog.value = true
  loadBudget()
}

function prevBudgetMonth() {
  if (budgetMonth.value === 1) { budgetMonth.value = 12; budgetYear.value-- }
  else { budgetMonth.value-- }
  loadBudget()
}

function nextBudgetMonth() {
  if (budgetMonth.value === 12) { budgetMonth.value = 1; budgetYear.value++ }
  else { budgetMonth.value++ }
  loadBudget()
}

async function loadDcaPlans() {
  try {
    dcaPlans.value = await listDcaPlans()
  } catch (e) {
    console.error('加载定投计划失败:', e)
  }
}

// Computed DCA summary
// 口径：加密币计划的 amount/totalInvested 单位是 USDT（与后端 DcaPlanService 一致），
// 汇总成人民币前必须先乘 USD/CNY 折算，禁止与 CNY 计划裸相加
const activeDcaPlans = computed(() => dcaPlans.value.filter(p => p.status === 'active'))
const hasCryptoDcaPlan = computed(() => dcaPlans.value.some(p => p.tradingMarket === 'crypto'))
const cryptoRate = computed(() => (hasCryptoDcaPlan.value ? usdCny.value : null))
function toCny(amount: number, market: string): number {
  return market === 'crypto' ? amount * (cryptoRate.value ?? 0) : amount
}
const dcaDailyTotal = computed(() => {
  return activeDcaPlans.value
    .filter(p => p.frequency === 'daily')
    .reduce((s, p) => s + toCny(p.amount, p.tradingMarket), 0)
})
const dcaTotalInvested = computed(() => {
  return dcaPlans.value.reduce((s, p) => s + toCny(p.totalInvested, p.tradingMarket), 0)
})
/** 存在加密币定投但汇率缺失时，人民币汇总无法完整（缺部分不冒充完整值） */
const dcaRateMissing = computed(() => hasCryptoDcaPlan.value && !usdCny.value)

const dcaPersistenceDays = computed(() => {  if (dcaPlans.value.length === 0) return 0
  const earliest = dcaPlans.value.reduce((earliest, p) =>
    p.startedAt < earliest ? p.startedAt : earliest,
    dcaPlans.value[0].startedAt
  )
  const diff = Date.now() - new Date(earliest).getTime()
  return Math.floor(diff / 86400000)
})

// Category helpers

function categorizedHoldings(cat: string): HoldingItem[] {
  return allHoldings.value.filter(h => h.assetCategory === cat && h.marketValue > 0)
}
function uncategorizedHoldings(): HoldingItem[] {
  return allHoldings.value.filter(h => !h.assetCategory && h.marketValue > 0)
}

// Batch category mapping
const showBatchCatSheet = ref(false)
const batchCategory = ref('')
const savingBatch = ref(false)

function openBatchCategory() {
  batchCategory.value = ''
  showBatchCatSheet.value = true
}

async function saveBatchCategory() {
  if (!batchCategory.value) return
  savingBatch.value = true
  try {
    const uncat = uncategorizedHoldings()
    await Promise.all(uncat.map(h => updateHoldingCategory(h.id, batchCategory.value)))
      // Reload everything silently
      await silentRefresh()
      showBatchCatSheet.value = false
  } catch (e: any) {
    console.error('批量分类失败:', e)
    showToast(e?.message || '批量分类失败，请重试', 'error')
  } finally {
    savingBatch.value = false
  }
}

// Open category mapping sheet
function openCategorySheet(holding: HoldingItem) {
  selectedHolding.value = holding
  selectedCategory.value = holding.assetCategory || ''
  showCatSheet.value = true
}

async function saveCategory() {
  if (!selectedHolding.value) return
  savingCategory.value = true
  try {
    const updated = await updateHoldingCategory(selectedHolding.value.id, selectedCategory.value)
    const idx = allHoldings.value.findIndex(h => h.id === updated.id)
    if (idx >= 0) allHoldings.value[idx] = { ...allHoldings.value[idx], ...updated }
    showCatSheet.value = false
    // Refresh all data silently
    await silentRefresh()
  } catch (e: any) {
    console.error('保存分类失败:', e)
    showToast(e?.message || '保存分类失败，请重试', 'error')
  } finally {
    savingCategory.value = false
  }
}

// Manual asset CRUD
const currentBalance = ref(0)

function openAddSheet(type: 'cash' | 'crypto') {
  editingAssetId.value = null
  currentBalance.value = 0
  const def = accounts.value.find(a => a.isDefault) || accounts.value[0]
  assetForm.value = { name: '', type, amount: 0, note: '', accountId: def?.id || '' }
  showAssetSheet.value = true
}

function parseAmountInput(input: string | number): number {
  const str = String(input).trim()
  if (str.startsWith('+') || str.startsWith('-')) {
    const delta = parseFloat(str)
    if (isNaN(delta)) return -1
    return currentBalance.value + delta
  }
  const abs = parseFloat(str)
  return isNaN(abs) ? -1 : abs
}

/** 账户分组里的资产与手动资产共用同一套编辑/删除入口 */
type AssetLike = {
  id: string
  name: string
  type: string
  amount: number
  note?: string | null
  currency?: string | null
  accountId?: string | null
}

function openEditSheet(asset: AssetLike) {
  editingAssetId.value = asset.id
  currentBalance.value = asset.amount
  assetForm.value = {
    name: asset.name,
    type: asset.type as 'cash' | 'crypto',
    // 加密资产编辑的是 USDT 数量（与录入口径一致），不是人民币金额
    amount: (asset.type === 'crypto' && (asset as any).quantity != null) ? (asset as any).quantity : asset.amount,
    note: asset.note || '',
    accountId: asset.accountId || '',
  }
  showAssetSheet.value = true
}

async function saveAsset() {
  if (!assetForm.value.accountId) {
    window.alert('请选择归属账户')
    return
  }
  const finalAmount = parseAmountInput(assetForm.value.amount)
  if (finalAmount < 0) {
    const unit = assetForm.value.type === 'crypto' ? ' USDT' : ' 元（¥' + finalAmount.toFixed(2) + '）'
    if (!window.confirm((assetForm.value.type === 'crypto' ? '数量将变为 ' + finalAmount + unit : '余额将变为负数' + unit) + '，是否继续？')) {
      return
    }
  }
  if (!assetForm.value.name) return
  // 加密资产的输入是 USDT 数量：传 quantity，金额由后端按当前汇率折算成人民币
  const isCrypto = assetForm.value.type === 'crypto'
  if (isCrypto && !usdCny.value) {
    window.alert('汇率获取失败，暂时无法保存加密资产，请稍后重试')
    return
  }
  savingAsset.value = true
  try {
    if (editingAssetId.value) {
      const req: UpdateManualAssetReq = {
        name: assetForm.value.name,
        type: assetForm.value.type,
        amount: isCrypto ? undefined : finalAmount,
        quantity: isCrypto ? finalAmount : undefined,
        assetCode: isCrypto ? 'USDT' : undefined,
        note: assetForm.value.note || undefined,
        accountId: assetForm.value.accountId,
      }
      const updated = await updateManualAsset(editingAssetId.value, req)
      const idx = allManualAssets.value.findIndex(a => a.id === updated.id)
      if (idx >= 0) allManualAssets.value[idx] = updated
    } else {
      const req: CreateManualAssetReq = {
        name: assetForm.value.name,
        type: assetForm.value.type,
        amount: isCrypto ? 0 : finalAmount,
        quantity: isCrypto ? finalAmount : undefined,
        note: assetForm.value.note || undefined,
        accountId: assetForm.value.accountId,
      }
      const created = await createManualAsset(req)
      allManualAssets.value.push(created)
    }
    showAssetSheet.value = false
    // Refresh all data silently
    await silentRefresh()
  } catch (e: any) {
    console.error('保存资产失败:', e)
    showToast(e?.message || '保存资产失败，请重试', 'error')
  } finally {
    savingAsset.value = false
  }
}

function confirmDelete(asset: AssetLike) {
  deletingAssetId.value = asset.id
  deletingAssetName.value = asset.name
  showDeleteConfirm.value = true
}

async function doDelete() {
  if (!deletingAssetId.value) return
  try {
    await deleteManualAsset(deletingAssetId.value)
    allManualAssets.value = allManualAssets.value.filter(a => a.id !== deletingAssetId.value)
    showDeleteConfirm.value = false
    // Refresh all data silently
    await silentRefresh()
  } catch (e: any) {
    console.error('删除失败:', e)
    showToast(e?.message || '删除资产失败，请重试', 'error')
  }
}
</script>

<template>
  <div class="page">
    <ToastNotification :message="toastMsg" :type="toastType" @close="toastMsg = null" />

    <AurosNavBar title="资产概览" sub="ASSETS" :back="false">
      <template #right>
        <button type="button" class="navlink" @click="goToHistory">
          资产历史
          <svg viewBox="0 0 24 24" width="12" height="12" fill="none" aria-hidden="true">
            <path d="M8 16 16 8M9.2 8H16v6.8" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
      </template>
    </AurosNavBar>

    <main class="body">
      <AurosState
        v-if="pageState !== 'ready'"
        :state="pageState === 'loading' ? 'loading' : 'error'"
        :rows="4"
        :text="pageState === 'error' ? errorMsg || '资产数据没有取到' : ''"
        :action-label="pageState === 'error' ? '重试' : ''"
        @action="loadData"
      />

      <template v-else>
        <!-- ============================================================ -->
        <!-- 章 01 STRUCTURE 结构：主角数 + 占比条 + 一组类别行（≤5） -->
        <!-- ============================================================ -->
        <AurosChapter
          label="STRUCTURE"
          title="结构"
          meta="按市值排序"
          :gap="false"
        >
          <AurosStat :value="formatMoney(overview?.totalValue)" size="lg" caption="总资产 · 人民币" />

          <div v-if="topCategory" class="shape">
            <AurosRail
              :percent="topCategory.percentage"
              :label-left="topCategory.name"
              :label-right="`其余 ${Math.max(0, (overview?.categories.length ?? 1) - 1)} 类`"
            />
          </div>

          <AurosGroup
            v-if="overview && overview.categories.length"
            class="g1"
            title="资产类别"
            :count="`${overview.categories.length} 类`"
            :total="`${categoryPctSum}%`"
          >
            <AurosRow
              v-for="(cat, i) in overview.categories"
              :key="cat.type"
              class="anim"
              :style="{ '--d': `${i * 32}ms` }"
              :label="cat.name"
              :value="`${cat.percentage.toFixed(1)}%`"
            />
          </AurosGroup>
          <p v-else class="blank">暂无资产数据 · 添加基金持仓或手动录入比特币/现金</p>
        </AurosChapter>

        <!-- ============================================================ -->
        <!-- 章 02 PERFORMANCE 表现：区间切换 + 主角数 + 90 天走势 + 3 行去重 -->
        <!-- ============================================================ -->
        <AurosChapter label="PERFORMANCE" title="表现" meta="近 90 天">
          <div class="seg" role="tablist" aria-label="时间区间">
            <button
              v-for="tab in (['week', 'month', 'year'] as const)"
              :key="tab"
              type="button"
              class="seg__item"
              :class="{ 'is-active': detailTab === tab }"
              :aria-pressed="detailTab === tab"
              @click="detailTab = tab"
            >
              {{ tab === 'week' ? '本周' : tab === 'month' ? '本月' : '本年' }}
            </button>
          </div>

          <template v-if="valueChange?.periods[detailTab]">
            <AurosStat
              class="perf"
              :value="formatPercent(valueChange.periods[detailTab].percent)"
              pink
              size="lg"
              :delta="changeDisplay(valueChange.periods[detailTab].change ?? 0)"
              :delta-tone="(valueChange.periods[detailTab].change ?? 0) > 0 ? 'up' : (valueChange.periods[detailTab].change ?? 0) < 0 ? 'down' : 'flat'"
              caption="区间变化"
            />

            <div v-if="trendPoints.length >= 2" class="shape">
              <AurosTrend :points="trendPoints" :height="64" :label="trendStart" :caption="trendEnd" />
            </div>

            <AurosGroup
              v-if="perfRows.length"
              class="g2"
              title="变化"
              :count="`${valueChange.periods[detailTab].details.length} 只参与`"
            >
              <AurosRow
                v-for="(row, i) in perfRows"
                :key="row.key"
                class="anim"
                :style="{ '--d': `${i * 32}ms` }"
                :label="row.label"
                :sub="row.sub"
                :value="row.value"
                :mark="row.mark"
                :clickable="row.clickable"
                :chevron="row.clickable"
                @click="row.clickable && router.push(`/holding/${row.holdingId}`)"
              />
            </AurosGroup>
            <p v-else class="blank">暂无变动数据</p>
          </template>
          <p v-else class="blank">暂无变动数据</p>
        </AurosChapter>

        <!-- ============================================================ -->
        <!-- 章 03 HOLDINGS 持仓：一组「全部持仓」，行按类别排序（≤5 + Disclosure） -->
        <!-- ============================================================ -->
        <AurosChapter label="HOLDINGS" title="持仓" meta="按类别排序">
          <AurosGroup
            v-if="holdingsRows.length"
            title="全部持仓"
            :count="`${holdingsRows.length} 只`"
            :total="formatMoney(holdingsTotal)"
          >
            <AurosRow
              v-for="(row, i) in holdingsShown"
              :key="row.h.id"
              class="anim"
              :style="{ '--d': `${i * 28}ms` }"
              :label="row.h.name"
              :sub="`${row.catLabel} · ${row.h.code}`"
              :value="formatMoney(row.h.marketValue)"
              clickable
              chevron
              @click="onHoldingRow(row.h)"
            />
            <AurosDisclosure v-if="holdingsRest.length" :count="holdingsRest.length">
              <AurosRow
                v-for="row in holdingsRest"
                :key="row.h.id"
                :label="row.h.name"
                :sub="`${row.catLabel} · ${row.h.code}`"
                :value="formatMoney(row.h.marketValue)"
                clickable
                chevron
                @click="onHoldingRow(row.h)"
              />
            </AurosDisclosure>
          </AurosGroup>
          <p v-else class="blank">暂无持仓 · 添加基金持仓或手动录入比特币/现金</p>
        </AurosChapter>

        <!-- ============================================================ -->
        <!-- 章 04 ACCOUNTS 现金与账户：一组账户，账户行 + 其下资产行（≤5 + Disclosure） -->
        <!-- ============================================================ -->
        <AurosChapter label="ACCOUNTS" title="现金与账户" meta="按账户分组">
          <AurosGroup
            v-if="cashRows.length"
            title="账户"
            :count="`${accounts.length} 个账户`"
            :total="fmtExact(manualTotal)"
          >
            <template v-for="(row, i) in cashShown" :key="row.key">
              <AurosRow
                v-if="row.kind === 'account'"
                :label="cashLabel(row)"
                :sub="cashSub(row)"
                :mark="cashMark(row)"
                tone="muted"
              />
              <div v-else class="sub-row">
                <AurosRow
                  class="anim"
                  :style="{ '--d': `${i * 28}ms` }"
                  :label="cashLabel(row)"
                  :sub="cashSub(row)"
                  :value="cashValue(row)"
                  :mark="cashMark(row)"
                  clickable
                  chevron
                  @click="cashClick(row)"
                />
              </div>
            </template>
            <AurosDisclosure v-if="cashRest.length" :count="cashRest.length">
              <template v-for="row in cashRest" :key="row.key">
                <AurosRow
                  v-if="row.kind === 'account'"
                  :label="cashLabel(row)"
                  :sub="cashSub(row)"
                  :mark="cashMark(row)"
                  tone="muted"
                />
                <div v-else class="sub-row">
                  <AurosRow
                    :label="cashLabel(row)"
                    :sub="cashSub(row)"
                    :value="cashValue(row)"
                    :mark="cashMark(row)"
                    clickable
                    chevron
                    @click="cashClick(row)"
                  />
                </div>
              </template>
            </AurosDisclosure>
          </AurosGroup>
          <p v-else class="blank">暂无手动资产</p>

          <!-- 章末固定行：未分类 / 定投汇总（原第 4 组降级为一行）/ 账户管理 / 添加 -->
          <div class="acts">
            <AurosRow
              v-if="uncategorizedHoldings().length"
              label="未分类持仓"
              :sub="`${uncategorizedHoldings().length} 只未归类`"
              value="立即分类"
              clickable
              chevron
              @click="openBatchCategory"
            />
            <AurosRow
              v-if="dcaPlans.length"
              label="定投计划"
              :sub="`${activeDcaPlans.length} 个活跃 · 坚持 ${dcaPersistenceDays} 天`"
              :value="dcaRateMissing ? '每日 ¥--' : `每日 ¥${dcaDailyTotal}`"
              mark="lit"
              clickable
              chevron
              @click="showAllDcaPlans = true"
            />
            <AurosRow
              label="管理账户"
              sub="新建 / 编辑 / 设为默认"
              clickable
              chevron
              @click="router.push({ name: 'accounts' })"
            />
            <AurosRow
              label="添加手动资产"
              sub="现金 / USDT 等"
              clickable
              chevron
              @click="showAddTypePicker = true"
            />
          </div>
        </AurosChapter>
      </template>
    </main>

    <!-- ==================== 弹层：一律 AurosSheet ==================== -->

    <!-- 选择资产类型 -->
    <AurosSheet v-model="showAddTypePicker" title="选择资产类型">
      <AurosRow
        label="现金"
        sub="活期 / 零钱 / 余额"
        mark="lit"
        clickable
        chevron
        @click="showAddTypePicker = false; openAddSheet('cash')"
      />
      <AurosRow
        label="加密资产（USDT 等）"
        sub="数量按 USDT 录入，保存时折算人民币"
        mark="dim"
        clickable
        chevron
        @click="showAddTypePicker = false; openAddSheet('crypto')"
      />
    </AurosSheet>

    <!-- 手动资产：新增 / 编辑 -->
    <AurosSheet
      v-model="showAssetSheet"
      :title="(editingAssetId ? '编辑' : '添加') + (assetForm.type === 'cash' ? '现金' : '加密资产')"
    >
      <div class="form">
        <AurosField v-model="assetForm.name" label="名称" placeholder="如：活期存款" />

        <div v-if="accounts.length > 0">
          <p class="form__k">归属账户</p>
          <div class="chips">
            <button
              v-for="acc in availableAccounts"
              :key="acc.id"
              type="button"
              class="chip"
              :class="{ 'is-on': assetForm.accountId === acc.id }"
              @click="assetForm.accountId = acc.id"
            >
              {{ acc.name }}{{ acc.isDefault ? ' ·默认' : '' }}
            </button>
          </div>
          <p v-if="availableAccounts.length === 0" class="form__err">
            {{ assetForm.type === 'crypto' ? '没有 USDT 本位币账户，请先在「账户」里创建交易所账户' : '没有人民币本位币账户' }}
          </p>
        </div>

        <label class="field">
          <span class="field__label">{{ assetForm.type === 'crypto' ? '数量 (USDT)' : '金额 (元)' }}</span>
          <span class="field__box">
            <input
              v-model="assetForm.amount"
              type="text"
              class="field__input is-mono"
              placeholder="0.00 或 +5000 或 -3000"
            />
          </span>
        </label>
        <p v-if="assetForm.type === 'crypto' && usdCny" class="form__hint">
          ≈ ¥{{ (Number(assetForm.amount) * usdCny).toLocaleString('zh-CN', { maximumFractionDigits: 2 }) }}（1 USDT ≈ {{ usdCny }} CNY，保存后按此折算）
        </p>
        <p v-else-if="assetForm.type === 'crypto' && !usdCny" class="form__err">
          汇率获取失败，暂时无法保存加密资产
        </p>

        <AurosField v-model="assetForm.note" label="备注 (可选)" placeholder="备注信息" />

        <div class="sheet__acts">
          <AurosButton variant="ghost" block @click="showAssetSheet = false">取消</AurosButton>
          <AurosButton block :disabled="savingAsset || !assetForm.name || assetForm.amount <= 0" @click="saveAsset">
            {{ savingAsset ? '保存中...' : '保存' }}
          </AurosButton>
        </div>

        <button
          v-if="editingAssetId"
          type="button"
          class="sheet__del"
          @click="deletingAssetId = editingAssetId; deletingAssetName = assetForm.name; showAssetSheet = false; showDeleteConfirm = true"
        >
          删除该资产
        </button>
      </div>
    </AurosSheet>

    <!-- 删除手动资产确认 -->
    <AurosSheet v-model="showDeleteConfirm" title="确认删除">
      <p class="confirm">确定删除"{{ deletingAssetName }}"吗？此操作不可撤销。</p>
      <div class="sheet__acts">
        <button type="button" class="sbtn" @click="showDeleteConfirm = false">取消</button>
        <button type="button" class="sbtn is-danger" @click="doDelete">删除</button>
      </div>
    </AurosSheet>

    <!-- 持仓分类（多标的类别点开） -->
    <AurosSheet v-model="showCatSheet" :title="selectedHolding?.name || ''">
      <p class="sheet-hint">设置该持仓的资产类别</p>
      <AurosRow
        v-for="opt in [
          { value: '', label: '未分类' },
          { value: 'us_stock', label: '美股 · 纳斯达克100联接' },
          { value: 'gold', label: '黄金 · ETF' },
          { value: 'dividend', label: '红利 · 基金' },
        ]"
        :key="opt.value"
        :label="opt.label"
        :mark="selectedCategory === opt.value ? 'lit' : 'dim'"
        clickable
        @click="selectedCategory = opt.value"
      >
        <svg v-if="selectedCategory === opt.value" class="opt-check" viewBox="0 0 24 24" width="14" height="14" fill="none" aria-hidden="true">
          <path d="m6.8 12.4 3.4 3.4 7-7" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </AurosRow>
      <div class="sheet__acts">
        <AurosButton variant="ghost" block @click="showCatSheet = false">取消</AurosButton>
        <AurosButton block :disabled="savingCategory" @click="saveCategory">
          {{ savingCategory ? '保存中...' : '确认' }}
        </AurosButton>
      </div>
    </AurosSheet>

    <!-- 批量分类 -->
    <AurosSheet v-model="showBatchCatSheet" title="批量分类">
      <p class="sheet-hint">将 {{ uncategorizedHoldings().length }} 只未分类基金全部设为同一类别</p>
      <AurosRow
        v-for="opt in [
          { value: 'us_stock', label: '美股 · 纳斯达克100联接' },
          { value: 'gold', label: '黄金 · ETF' },
          { value: 'dividend', label: '红利 · 基金' },
        ]"
        :key="opt.value"
        :label="opt.label"
        :mark="batchCategory === opt.value ? 'lit' : 'dim'"
        clickable
        @click="batchCategory = opt.value"
      >
        <svg v-if="batchCategory === opt.value" class="opt-check" viewBox="0 0 24 24" width="14" height="14" fill="none" aria-hidden="true">
          <path d="m6.8 12.4 3.4 3.4 7-7" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </AurosRow>
      <div class="sheet__acts">
        <AurosButton variant="ghost" block @click="showBatchCatSheet = false">取消</AurosButton>
        <AurosButton block :disabled="savingBatch || !batchCategory" @click="saveBatchCategory">
          {{ savingBatch ? '分类中...' : '全部设为该类别' }}
        </AurosButton>
      </div>
    </AurosSheet>

    <!-- 全部定投计划（章末定投行点开） -->
    <AurosSheet v-model="showAllDcaPlans" title="全部定投计划">
      <AurosGroup
        title="计划"
        :count="`${sortedDcaPlans.length} 个`"
        :total="dcaRateMissing ? '累计 ¥--' : `累计 ¥${dcaTotalInvested.toLocaleString()}`"
      >
        <AurosRow
          v-for="plan in sortedDcaPlans"
          :key="plan.id"
          :label="plan.holdingName"
          :sub="`${plan.frequency === 'daily' ? '每日' : plan.frequency === 'weekly' ? '每周' : plan.frequency === 'biweekly' ? '双周' : '每月'} ${plan.tradingMarket === 'crypto' ? plan.amount + ' USDT' : '¥' + plan.amount} · ${plan.totalExecutions} 期`"
          :value="plan.tradingMarket === 'crypto' ? plan.totalInvested.toLocaleString() + ' USDT' : '¥' + plan.totalInvested.toLocaleString()"
          clickable
          chevron
          @click="showAllDcaPlans = false; router.push({ name: 'dca-plan-detail', params: { id: plan.id } })"
        />
      </AurosGroup>
      <div class="acts">
        <AurosRow label="月度预算" sub="按月查看扣款金额与交易日" clickable chevron @click="showAllDcaPlans = false; openBudget()" />
      </div>
    </AurosSheet>

    <!-- 定投预算 -->
    <AurosSheet v-model="showBudgetDialog" title="定投预算">
      <div class="budget__nav">
        <button type="button" class="iconbtn" :disabled="budgetLoading" aria-label="上一月" @click="prevBudgetMonth()">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" aria-hidden="true">
            <path d="m14.5 7-5 5 5 5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
        <span class="budget__month au-num">{{ budgetYear }}年{{ budgetMonth }}月</span>
        <button type="button" class="iconbtn" :disabled="budgetLoading" aria-label="下一月" @click="nextBudgetMonth()">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" aria-hidden="true">
            <path d="m9.5 7 5 5-5 5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
      </div>

      <AurosState v-if="budgetLoading" state="loading" :rows="3" />

      <template v-else-if="budgetData">
        <AurosGroup
          v-if="budgetData.plans.length"
          title="计划"
          :count="`${budgetData.plans.length} 个 · ${budgetData.tradingDays} 个交易日`"
          :total="formatMoney(budgetData.totalAmount)"
        >
          <AurosRow
            v-for="plan in budgetData.plans"
            :key="plan.holdingName"
            :label="plan.holdingName"
            :sub="`${plan.frequency === 'daily' ? '每日' : plan.frequency === 'weekly' ? '每周' : plan.frequency === 'biweekly' ? '双周' : '每月'} ${plan.tradingMarket === 'crypto' ? plan.amount.toLocaleString() + ' USDT' : '¥' + plan.amount.toLocaleString()} · ${plan.executions} 次`"
            :value="formatMoney(plan.budgetAmount)"
          />
        </AurosGroup>
        <p v-else class="blank">该月无定投计划</p>
      </template>
    </AurosSheet>
  </div>
</template>

<style scoped>
.page {
  min-height: 100svh;
  display: flex;
  flex-direction: column;
}
.body {
  flex: 1;
  padding: 8px 16px calc(env(safe-area-inset-bottom, 0px) + 76px);
}

/* 顶栏右侧文字链 */
.navlink {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 0;
  background: none;
  padding: 0;
  font-family: inherit;
  font-size: 12px;
  letter-spacing: 0.04em;
  color: var(--au-silver);
  transition: color 200ms var(--au-ease);
}
.navlink:active {
  color: var(--au-mist);
}

/* 章内形状与间距（卡片化后收紧：卡头之下 14px 起算） */
.shape {
  margin-top: 14px;
}
.g1 {
  margin-top: 18px;
}
.g2 {
  margin-top: 18px;
}
.perf {
  margin-top: 4px;
}
.acts {
  margin-top: 18px;
}
/* 账户行下的资产行：缩进一级，表示归属关系 */
.sub-row {
  padding-left: 12px;
}
.blank {
  margin: 0;
  padding: 14px 0;
  font-size: 12px;
  line-height: 1.6;
  letter-spacing: 0.02em;
  color: rgba(187, 199, 198, 0.6);
}

/* 区间胶囊（同表单页分段） */
.seg {
  display: flex;
  gap: 4px;
  padding: 3px;
  border-radius: 8px;
  background: rgba(0, 55, 52, 0.55);
}
.seg__item {
  flex: 1;
  height: 34px;
  border: 0;
  border-radius: 6px;
  background: none;
  font-family: inherit;
  font-size: 13px;
  letter-spacing: 0.04em;
  color: rgba(187, 199, 198, 0.75);
  transition:
    background 200ms var(--au-ease),
    color 200ms var(--au-ease);
}
.seg__item.is-active {
  background: rgba(203, 255, 252, 0.14);
  color: var(--au-mist);
}

/* ---------- 弹层 ---------- */
.sheet-hint {
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 1.6;
  letter-spacing: 0.02em;
  color: var(--au-silver);
}
.opt-check {
  flex: none;
  color: var(--au-lit);
}
.form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 2px 0 4px;
}
.form__k {
  margin: 0 0 8px;
  font-size: 10px;
  letter-spacing: 0.16em;
  color: var(--au-silver);
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.chip {
  height: 34px;
  padding: 0 14px;
  border: 0;
  border-radius: 6px;
  background: rgba(0, 55, 52, 0.55);
  color: rgba(187, 199, 198, 0.8);
  font-family: inherit;
  font-size: 13px;
  letter-spacing: 0.04em;
  transition:
    background 200ms var(--au-ease),
    color 200ms var(--au-ease);
}
.chip.is-on {
  background: rgba(203, 255, 252, 0.14);
  color: var(--au-mist);
}
.form__err {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.6;
  letter-spacing: 0.02em;
  color: rgba(253, 233, 255, 0.88);
}
.form__hint {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.6;
  letter-spacing: 0.02em;
  color: rgba(187, 199, 198, 0.66);
}

/* 原生金额输入：保留「可输入 +/- 相对值」的字符串语义 */
.field {
  display: block;
}
.field__label {
  display: block;
  margin-bottom: 7px;
  font-size: 10px;
  letter-spacing: 0.16em;
  color: var(--au-silver);
}
.field__box {
  position: relative;
  display: flex;
  align-items: center;
  height: 46px;
  padding: 0 12px;
  border-radius: 6px;
  background: rgba(0, 55, 52, 0.55);
}
.field__box::before {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 1px;
  background: rgba(237, 255, 254, 0.12);
  transition: background 200ms var(--au-ease);
}
.field:focus-within .field__box::before {
  background: rgba(203, 255, 252, 0.75);
}
.field__input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: none;
  outline: none;
  color: var(--au-mist);
  font-family: inherit;
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 0.01em;
}
.field__input.is-mono {
  font-variant-numeric: tabular-nums;
}
.field__input::placeholder {
  color: rgba(187, 199, 198, 0.45);
  font-weight: 400;
}

.sheet__acts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--au-hair);
}
.sheet__del {
  width: 100%;
  height: 42px;
  margin-top: 2px;
  border: 0;
  border-radius: 6px;
  background: none;
  color: rgba(253, 233, 255, 0.85);
  font-family: inherit;
  font-size: 13px;
  letter-spacing: 0.08em;
}
.sheet__del:active {
  background: rgba(253, 233, 255, 0.06);
}
.confirm {
  margin: 0 0 4px;
  font-size: 13px;
  line-height: 1.7;
  letter-spacing: 0.02em;
  color: var(--au-silver);
}
.sbtn {
  flex: 1;
  height: 44px;
  border: 0;
  border-radius: 6px;
  background: rgba(0, 55, 52, 0.9);
  color: var(--au-mist);
  font-family: inherit;
  font-size: 13px;
  letter-spacing: 0.08em;
  transition: transform 160ms var(--au-ease);
}
.sbtn.is-danger {
  color: rgba(253, 233, 255, 0.9);
}
.sbtn:active {
  transform: scale(0.98);
}

/* 预算月切换 */
.budget__nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 0 12px;
}
.iconbtn {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: rgba(237, 255, 254, 0.05);
  color: var(--au-silver);
}
.iconbtn:disabled {
  opacity: 0.4;
}
.budget__month {
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.04em;
  color: var(--au-mist);
}

/* ---------- 动效 ---------- */
.anim {
  animation: rise 440ms var(--au-ease) var(--d, 0ms) both;
}
@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@media (prefers-reduced-motion: reduce) {
  .anim {
    animation: none;
  }
}
</style>
