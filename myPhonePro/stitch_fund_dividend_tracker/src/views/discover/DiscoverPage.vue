<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { getAssetOverview, takeSnapshot } from '@/api/assetOverview'
import { listHoldings, updateHoldingCategory, getValueChange } from '@/api/holding'
import type { ValueChangeResult } from '@/api/holding'
import { listManualAssets, createManualAsset, updateManualAsset, deleteManualAsset } from '@/api/manualAsset'
import { listDcaPlans, getDcaBudget } from '@/api/dca'
import type { AssetOverview, CategoryItem } from '@/api/assetOverview'
import type { ManualAssetItem, CreateManualAssetReq, UpdateManualAssetReq } from '@/api/manualAsset'
import type { HoldingItem } from '@/api/holding'
import type { DcaPlanVO, DcaBudgetVO } from '@/api/dca'
import AppHeader from '@/components/shared/AppHeader.vue'

const router = useRouter()
const pageState = ref<'loading' | 'ready' | 'error'>('loading')
const errorMsg = ref('')
const overview = ref<AssetOverview | null>(null)
const allHoldings = ref<HoldingItem[]>([])
const allManualAssets = ref<ManualAssetItem[]>([])
const valueChange = ref<ValueChangeResult | null>(null)
const showValueDetail = ref(false)
const detailTab = ref<'week' | 'month' | 'year'>('week')
const showAddTypePicker = ref(false)
const showAllDcaPlans = ref(false)

// Manual asset management
const showAssetSheet = ref(false)
const editingAssetId = ref<string | null>(null)
const assetForm = ref({ name: '', type: 'cash' as 'cash' | 'crypto', amount: 0, note: '' })
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

function formatMoney(v: number | undefined | null): string {
  if (v == null || v === 0) return '¥0.00'
  if (Math.abs(v) >= 1_0000_0000) return `¥${(v / 1_0000_0000).toFixed(2)}亿`
  if (Math.abs(v) >= 1_0000) return `¥${(v / 1_0000).toFixed(2)}万`
  return `¥${v.toFixed(2)}`
}

function changeDisplay(num: number): string {
  return num >= 0 ? `+${num.toFixed(2)}` : num.toFixed(2)
}

function formatPercent(num: number): string {
  return num >= 0 ? `+${num.toFixed(2)}%` : `${num.toFixed(2)}%`
}

async function loadData() {
  pageState.value = 'loading'
  errorMsg.value = ''
  try {
    const [ov, holdings, manualAssets, vc] = await Promise.all([
      getAssetOverview(),
      listHoldings(),
      listManualAssets(),
      getValueChange(),
    ])
    overview.value = ov
    allHoldings.value = holdings
    allManualAssets.value = manualAssets
    valueChange.value = vc

    // Auto snapshot on first load of the day
    takeSnapshot().catch(() => {})

    // Load DCA plans
    loadDcaPlans()

    pageState.value = 'ready'
  } catch (e: any) {
    console.error('加载总资产概览失败:', e)
    errorMsg.value = e.message || '加载失败'
    pageState.value = 'error'
  }
}

onActivated(loadData)

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
const activeDcaPlans = computed(() => dcaPlans.value.filter(p => p.status === 'active'))
const dcaDailyTotal = computed(() => {
  return activeDcaPlans.value
    .filter(p => p.frequency === 'daily')
    .reduce((s, p) => s + p.amount, 0)
})
const dcaTotalInvested = computed(() => {
  return dcaPlans.value.reduce((s, p) => s + p.totalInvested, 0)
})
const dcaPersistenceDays = computed(() => {
  if (dcaPlans.value.length === 0) return 0
  const earliest = dcaPlans.value.reduce((earliest, p) =>
    p.startedAt < earliest ? p.startedAt : earliest,
    dcaPlans.value[0].startedAt
  )
  const diff = Date.now() - new Date(earliest).getTime()
  return Math.floor(diff / 86400000)
})

// Category helpers
const uncategorizedCount = ref(0)

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
    // Reload everything
    await loadData()
    showBatchCatSheet.value = false
  } catch (e: any) {
    console.error('批量分类失败:', e)
  } finally {
    savingBatch.value = false
  }
}

// Dismiss banner
const bannerDismissed = ref(false)
function dismissBanner() {
  bannerDismissed.value = true
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
    // Refresh overview to recalculate
    const ov = await getAssetOverview()
    overview.value = ov
  } catch (e: any) {
    console.error('保存分类失败:', e)
  } finally {
    savingCategory.value = false
  }
}

// Manual asset CRUD
const currentBalance = ref(0)

function openAddSheet(type: 'cash' | 'crypto') {
  editingAssetId.value = null
  currentBalance.value = 0
  assetForm.value = { name: '', type, amount: 0, note: '' }
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

function openEditSheet(asset: ManualAssetItem) {
  editingAssetId.value = asset.id
  currentBalance.value = asset.amount
  assetForm.value = {
    name: asset.name,
    type: asset.type as 'cash' | 'crypto',
    amount: asset.amount,
    note: asset.note || '',
  }
  showAssetSheet.value = true
}

async function saveAsset() {
  const finalAmount = parseAmountInput(assetForm.value.amount)
  if (finalAmount < 0) {
    if (!window.confirm('现金余额将变为负数（¥' + finalAmount.toFixed(2) + '），是否继续？')) {
      return
    }
  }
  if (!assetForm.value.name) return
  savingAsset.value = true
  try {
    if (editingAssetId.value) {
      const req: UpdateManualAssetReq = {
        name: assetForm.value.name,
        type: assetForm.value.type,
        amount: finalAmount,
        note: assetForm.value.note || undefined,
      }
      const updated = await updateManualAsset(editingAssetId.value, req)
      const idx = allManualAssets.value.findIndex(a => a.id === updated.id)
      if (idx >= 0) allManualAssets.value[idx] = updated
    } else {
      const req: CreateManualAssetReq = {
        name: assetForm.value.name,
        type: assetForm.value.type,
        amount: finalAmount,
        note: assetForm.value.note || undefined,
      }
      const created = await createManualAsset(req)
      allManualAssets.value.push(created)
    }
    showAssetSheet.value = false
    // Refresh overview
    const ov = await getAssetOverview()
    overview.value = ov
  } catch (e: any) {
    console.error('保存资产失败:', e)
  } finally {
    savingAsset.value = false
  }
}

function confirmDelete(asset: ManualAssetItem) {
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
    const ov = await getAssetOverview()
    overview.value = ov
  } catch (e: any) {
    console.error('删除失败:', e)
  }
}
</script>

<template>
  <div class="min-h-screen bg-page-bg">
    <!-- 内联 Header（不依赖 AppHeader 旧 token） -->
    <header class="fixed top-0 w-full z-50 bg-card-bg border-b border-border-light/40 transition-shadow duration-200">
      <div class="flex items-center justify-between px-gutter h-14 w-full max-w-[600px] mx-auto">
        <div class="flex items-center gap-2">
          <span class="material-symbols-outlined text-brand text-2xl">park</span>
          <h1 class="font-display text-2xl text-brand">资产概览</h1>
        </div>
        <div class="flex items-center gap-2">
          <button class="w-10 h-10 flex items-center justify-center rounded-full hover:bg-card-alt transition-colors active:opacity-80">
            <span class="material-symbols-outlined text-text-secondary">account_circle</span>
          </button>
        </div>
      </div>
    </header>

    <!-- Loading State -->
    <main v-if="pageState === 'loading'" class="max-w-[600px] mx-auto px-gutter pt-20 pb-24">
      <div class="flex flex-col items-center justify-center py-24">
        <span class="material-symbols-outlined text-[48px] text-text-tertiary animate-spin">sync</span>
        <p class="font-body text-sm text-text-tertiary mt-md">加载中...</p>
      </div>
    </main>

    <!-- Error State -->
    <main v-else-if="pageState === 'error'" class="max-w-[600px] mx-auto px-gutter pt-20 pb-24">
      <div class="flex flex-col items-center justify-center py-24">
        <span class="material-symbols-outlined text-[48px] text-error mb-md">error</span>
        <p class="font-body text-sm font-medium text-text-primary mb-1">加载失败</p>
        <p class="font-body text-xs text-text-tertiary mb-lg">{{ errorMsg }}</p>
        <button class="px-lg py-2 bg-brand text-white rounded-lg font-body text-sm font-medium active:scale-[0.98] transition-transform"
                @click="loadData">重试</button>
      </div>
    </main>

    <!-- Ready State -->
    <main v-else class="max-w-[600px] mx-auto px-gutter pt-20 pb-28 space-y-md">
      <!-- Classification Banner (above hero) -->
      <section v-if="uncategorizedHoldings().length > 0 && !bannerDismissed"
               class="bg-brand-light/60 rounded-lg p-lg card-shadow border border-border-light/40 relative overflow-hidden">
        <button class="absolute top-2 right-2 w-6 h-6 flex items-center justify-center text-text-tertiary hover:text-text-primary transition-colors"
                @click="dismissBanner">
          <span class="material-symbols-outlined text-[16px]">close</span>
        </button>
        <div class="flex items-start gap-3">
          <div class="w-10 h-10 rounded-full bg-brand-light flex items-center justify-center shrink-0">
            <span class="material-symbols-outlined text-brand text-[22px]">category</span>
          </div>
          <div class="flex-1 min-w-0">
            <h3 class="font-body text-sm font-medium text-text-primary mb-1">有 {{ uncategorizedHoldings().length }} 只基金还未分类</h3>
            <p class="font-body text-xs text-text-tertiary mb-3">
              将它们归类到"美股"、"黄金"或"红利"，以便在资产概览中准确统计
            </p>
            <div class="flex gap-2">
              <button class="px-4 py-2 bg-brand text-white rounded-lg font-body text-xs font-medium active:scale-[0.98] transition-transform"
                      @click="openBatchCategory">
                立即分类
              </button>
              <button class="px-4 py-2 bg-card-alt text-text-secondary rounded-lg font-body text-xs font-medium active:scale-[0.98] transition-transform"
                      @click="dismissBanner">
                稍后再说
              </button>
            </div>
          </div>
        </div>
      </section>

      <!-- Total Assets Hero with Value Change -->
      <section class="bg-card-bg rounded-lg p-lg card-shadow border border-border-light/40">
        <!-- Header: label left, label · arrow right -->
        <div class="flex items-center justify-between mb-1">
          <span class="font-body text-xs text-text-tertiary">总资产 (元)</span>
          <span class="flex items-center gap-1 font-body text-xs text-text-tertiary/40 group cursor-pointer"
                @click="showValueDetail = true">
            <span class="group-hover:text-text-tertiary/80 transition-colors">点击查看明细</span>
            <span class="group-hover:text-text-tertiary/80 transition-colors">·</span>
            <span class="text-[17px] leading-none group-hover:text-text-primary transition-colors">›</span>
          </span>
        </div>

        <!-- Total assets centered -->
        <div class="text-center font-display text-3xl font-medium text-text-primary mb-md tracking-tight">
          {{ formatMoney(overview?.totalValue) }}
        </div>

        <!-- Three pill cards -->
        <div v-if="valueChange"
             class="flex gap-2 cursor-pointer"
             @click="showValueDetail = true">
          <div v-for="period in (['week', 'month', 'year'] as const)" :key="period"
               class="flex-1 rounded-lg py-2 text-center transition-opacity hover:opacity-80"
               :class="(valueChange.periods[period]?.change || 0) >= 0
                  ? 'bg-brand-light text-brand'
                  : 'bg-error/10 text-error'">
            <div class="font-body text-xs opacity-80">
              {{ period === 'week' ? '本周' : period === 'month' ? '本月' : '本年' }}
            </div>
            <div class="font-body text-sm font-medium mt-0.5">
              {{ formatPercent(valueChange.periods[period]?.percent || 0) }}
            </div>
          </div>
        </div>
      </section>

      <!-- Asset Allocation (Bento) -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-md">
        <!-- Allocation Treemap -->
        <section class="bg-card-bg rounded-lg p-lg card-shadow border border-border-light/40">
          <div class="flex items-center justify-between mb-3">
            <h2 class="font-body text-sm font-medium text-text-primary">持仓占比</h2>
          </div>
          <div v-if="overview && overview.categories.length > 0" class="space-y-3">
            <div class="flex w-full h-8 rounded-lg overflow-hidden">
              <div v-for="cat in overview.categories" :key="cat.type"
                   class="flex items-center justify-center transition-all"
                   :style="{ width: cat.percentage + '%', backgroundColor: cat.color }">
                <span v-if="cat.percentage > 15" class="font-body text-xs text-white">
                  {{ cat.percentage.toFixed(0) }}%
                </span>
              </div>
            </div>
            <div class="flex flex-col gap-1.5">
              <div v-for="cat in overview.categories" :key="cat.type"
                   class="flex items-center gap-1">
                <div class="w-2.5 h-2.5 rounded-full shrink-0" :style="{ backgroundColor: cat.color }"></div>
                <span class="font-body text-[11px] text-text-tertiary shrink-0 w-12 truncate">{{ cat.name }}</span>
                <!-- 进度条：flex-1 确保所有行左右对齐 -->
                <div class="flex-1 h-1 rounded-full bg-progress-bg overflow-hidden min-w-[24px]">
                  <div class="h-full rounded-full transition-all duration-500"
                       :style="{ width: cat.percentage + '%', backgroundColor: cat.color }"></div>
                </div>
                <!-- 占比 Pill + 金额：合并为右侧固定区域 -->
                <span class="font-body text-[10px] font-medium px-1 py-[1px] rounded-full shrink-0 mr-1"
                      :style="{ backgroundColor: cat.color + '18', color: cat.color }">
                  {{ cat.percentage.toFixed(0) }}%
                </span>
                <span class="font-body text-[11px] text-text-primary tabular-nums shrink-0 text-right min-w-[60px]">{{ formatMoney(cat.value) }}</span>
              </div>
            </div>
          </div>
          <div v-else class="flex items-center justify-center h-20 font-body text-xs text-text-tertiary">
            暂无资产数据
          </div>
        </section>
      </div>

      <!-- Category Detail Cards -->
      <section class="space-y-md">
        <div class="flex items-center justify-between px-1">
          <h2 class="font-body text-sm font-medium text-text-primary">各类资产</h2>
        </div>

        <!-- ============================================================ -->
        <!-- 分类资产卡片（统一布局：1个标的 → 紧凑行，>1个标的 → 展开列表） -->
        <!-- ============================================================ -->

        <!-- US Stocks -->
        <div v-if="overview && overview.usStockValue > 0" class="bg-card-bg rounded-lg p-lg card-shadow border border-border-light/40">
          <!-- 1 个标的 → 紧凑行（整行可点击跳转） -->
          <template v-if="categorizedHoldings('us_stock').length === 1">
            <div v-for="h in categorizedHoldings('us_stock')" :key="h.id"
                 class="flex items-center gap-3 cursor-pointer hover:bg-card-alt -m-lg p-lg rounded-lg transition-colors"
                 @click="router.push(`/holding/${h.id}`)">
              <div class="w-3 h-3 rounded-full shrink-0" style="background-color:#3B82F6"></div>
              <div class="flex-1 min-w-0">
                <p class="font-body text-sm font-medium text-text-primary truncate">{{ h.name }}</p>
                <p class="font-body text-xs text-text-tertiary">美股 · 纳斯达克100联接</p>
              </div>
              <div class="text-right shrink-0">
                <p class="font-body text-sm font-medium text-text-primary">{{ formatMoney(h.marketValue) }}</p>
              </div>
            </div>
          </template>
          <!-- > 1 个标的 → 展开列表 -->
          <template v-else>
            <div class="flex items-start gap-3">
              <div class="w-3 h-3 rounded-full mt-[5px] shrink-0" style="background-color:#3B82F6"></div>
              <div class="flex-1 min-w-0">
                <div class="flex items-center justify-between">
                  <h3 class="font-body text-sm font-medium text-text-primary">美股 · 纳斯达克100联接</h3>
                  <span class="font-body text-sm font-medium text-text-primary shrink-0 ml-2">{{ formatMoney(overview.usStockValue) }}</span>
                </div>
                <span class="font-body text-xs text-text-tertiary">{{ categorizedHoldings('us_stock').length }} 个标的</span>
              </div>
            </div>
            <div class="border-t border-border-light/40 my-md"></div>
            <div class="space-y-0.5">
              <div v-for="h in categorizedHoldings('us_stock')" :key="h.id"
                   class="flex items-center justify-between py-2.5 px-3 rounded-lg hover:bg-card-alt cursor-pointer transition-colors"
                   @click="openCategorySheet(h)">
                <span class="font-body text-xs text-text-primary truncate flex-1 min-w-0">{{ h.name }}</span>
                <span class="font-body text-xs text-text-tertiary shrink-0 ml-2">{{ formatMoney(h.marketValue) }}</span>
              </div>
            </div>
          </template>
        </div>

        <!-- Gold -->
        <div v-if="overview && overview.goldValue > 0" class="bg-card-bg rounded-lg p-lg card-shadow border border-border-light/40">
          <template v-if="categorizedHoldings('gold').length === 1">
            <div v-for="h in categorizedHoldings('gold')" :key="h.id"
                 class="flex items-center gap-3 cursor-pointer hover:bg-card-alt -m-lg p-lg rounded-lg transition-colors"
                 @click="router.push(`/holding/${h.id}`)">
              <div class="w-3 h-3 rounded-full shrink-0" style="background-color:#F59E0B"></div>
              <div class="flex-1 min-w-0">
                <p class="font-body text-sm font-medium text-text-primary truncate">{{ h.name }}</p>
                <p class="font-body text-xs text-text-tertiary">黄金 · ETF</p>
              </div>
              <div class="text-right shrink-0">
                <p class="font-body text-sm font-medium text-text-primary">{{ formatMoney(h.marketValue) }}</p>
              </div>
            </div>
          </template>
          <template v-else>
            <div class="flex items-start gap-3">
              <div class="w-3 h-3 rounded-full mt-[5px] shrink-0" style="background-color:#F59E0B"></div>
              <div class="flex-1 min-w-0">
                <div class="flex items-center justify-between">
                  <h3 class="font-body text-sm font-medium text-text-primary">黄金 · ETF</h3>
                  <span class="font-body text-sm font-medium text-text-primary shrink-0 ml-2">{{ formatMoney(overview.goldValue) }}</span>
                </div>
                <span class="font-body text-xs text-text-tertiary">{{ categorizedHoldings('gold').length }} 个标的</span>
              </div>
            </div>
            <div class="border-t border-border-light/40 my-md"></div>
            <div class="space-y-0.5">
              <div v-for="h in categorizedHoldings('gold')" :key="h.id"
                   class="flex items-center justify-between py-2.5 px-3 rounded-lg hover:bg-card-alt cursor-pointer transition-colors"
                   @click="openCategorySheet(h)">
                <span class="font-body text-xs text-text-primary truncate flex-1 min-w-0">{{ h.name }}</span>
                <span class="font-body text-xs text-text-tertiary shrink-0 ml-2">{{ formatMoney(h.marketValue) }}</span>
              </div>
            </div>
          </template>
        </div>

        <!-- Dividend / Fund Holdings -->
        <div v-if="overview && overview.dividendValue > 0" class="bg-card-bg rounded-lg p-lg card-shadow border border-border-light/40">
          <template v-if="categorizedHoldings('dividend').length === 1">
            <div v-for="h in categorizedHoldings('dividend')" :key="h.id"
                 class="flex items-center gap-3 cursor-pointer hover:bg-card-alt -m-lg p-lg rounded-lg transition-colors"
                 @click="router.push(`/holding/${h.id}`)">
              <div class="w-3 h-3 rounded-full shrink-0" style="background-color:#EAB308"></div>
              <div class="flex-1 min-w-0">
                <p class="font-body text-sm font-medium text-text-primary truncate">{{ h.name }}</p>
                <p class="font-body text-xs text-text-tertiary">红利 · 基金</p>
              </div>
              <div class="text-right shrink-0">
                <p class="font-body text-sm font-medium text-text-primary">{{ formatMoney(h.marketValue) }}</p>
              </div>
            </div>
          </template>
          <template v-else>
            <div class="flex items-start gap-3">
              <div class="w-3 h-3 rounded-full mt-[5px] shrink-0" style="background-color:#EAB308"></div>
              <div class="flex-1 min-w-0">
                <div class="flex items-center justify-between">
                  <h3 class="font-body text-sm font-medium text-text-primary">红利 · 基金</h3>
                  <span class="font-body text-sm font-medium text-text-primary shrink-0 ml-2">{{ formatMoney(overview.dividendValue) }}</span>
                </div>
                <span class="font-body text-xs text-text-tertiary">{{ categorizedHoldings('dividend').length }} 个标的</span>
              </div>
            </div>
            <div class="border-t border-border-light/40 my-md"></div>
            <div class="space-y-0.5">
              <div v-for="h in categorizedHoldings('dividend')" :key="h.id"
                   class="flex items-center justify-between py-2.5 px-3 rounded-lg hover:bg-card-alt cursor-pointer transition-colors"
                   @click="openCategorySheet(h)">
                <span class="font-body text-xs text-text-primary truncate flex-1 min-w-0">{{ h.name }}</span>
                <span class="font-body text-xs text-text-tertiary shrink-0 ml-2">{{ formatMoney(h.marketValue) }}</span>
              </div>
            </div>
          </template>
        </div>

        <!-- Manual Assets (Cash + Crypto) -->
        <div v-if="overview"
             class="bg-card-bg rounded-lg p-lg card-shadow border border-border-light/40">
          <template v-if="allManualAssets.length > 0">
            <div class="flex items-start gap-3">
              <div class="w-3 h-3 rounded-full mt-[5px] shrink-0" style="background-color:#6366F1"></div>
              <div class="flex-1 min-w-0">
                <div class="flex items-center justify-between">
                  <h3 class="font-body text-sm font-medium text-text-primary">手动资产</h3>
                  <span class="font-body text-sm font-medium text-text-primary shrink-0 ml-2">{{ formatMoney((overview.cashValue || 0) + (overview.cryptoValue || 0)) }}</span>
                </div>
                <span class="font-body text-xs text-text-tertiary">{{ allManualAssets.length }} 个标的</span>
              </div>
            </div>
            <div class="border-t border-border-light/40 my-md"></div>
            <div class="space-y-0.5">
              <div v-for="a in allManualAssets" :key="a.id"
                   class="flex items-center justify-between py-2.5 px-3 rounded-lg hover:bg-card-alt cursor-pointer transition-colors"
                   @click="openEditSheet(a)">
                <div class="flex items-center gap-2 min-w-0 flex-1">
                  <span class="w-2 h-2 rounded-full shrink-0"
                        :class="a.type === 'cash' ? 'bg-[#34A853]' : 'bg-[#F59E0B]'"></span>
                  <span class="font-body text-xs text-text-primary truncate">{{ a.name }}</span>
                  <span class="font-body text-[10px] text-text-tertiary/50 shrink-0">{{ a.type === 'cash' ? '现金' : '比特币' }}</span>
                </div>
                <div class="flex items-center gap-2 shrink-0">
                  <span class="font-body text-xs text-text-tertiary">{{ formatMoney(a.amount) }}</span>
                  <button class="text-text-tertiary hover:text-error transition-colors"
                          @click.stop="confirmDelete(a)">
                    <span class="material-symbols-outlined text-[16px]">close</span>
                  </button>
                </div>
              </div>
            </div>
            <div class="border-t border-border-light/40 my-md"></div>
            <button class="w-full py-2 text-center font-body text-xs text-text-tertiary hover:bg-card-alt rounded-lg transition-colors flex items-center justify-center gap-1"
                    @click="showAddTypePicker = true">
              <span class="material-symbols-outlined text-[14px]">add</span>
              添加手动资产
            </button>
          </template>
          <template v-else>
            <div class="py-lg flex flex-col items-center gap-3">
              <span class="material-symbols-outlined text-[32px] text-text-tertiary">handyman</span>
              <p class="font-body text-xs text-text-tertiary">暂无手动资产</p>
              <button class="px-lg py-2 bg-brand text-white rounded-lg font-body text-xs font-medium flex items-center gap-1.5 active:scale-[0.98] transition-transform"
                      @click="showAddTypePicker = true">
                <span class="material-symbols-outlined text-[16px]">add</span>
                添加手动资产
              </button>
            </div>
          </template>
        </div>

        <!-- Uncategorized Holdings -->
        <div v-if="uncategorizedHoldings().length > 0"
             class="bg-card-bg rounded-lg p-lg card-shadow border border-border-light/40">
          <template v-if="uncategorizedHoldings().length === 1">
            <div v-for="h in uncategorizedHoldings()" :key="h.id"
                 class="flex items-center gap-3 cursor-pointer hover:bg-card-alt -m-lg p-lg rounded-lg transition-colors"
                 @click="router.push(`/holding/${h.id}`)">
              <div class="w-3 h-3 rounded-full shrink-0" style="background-color:#9CA3AF"></div>
              <div class="flex-1 min-w-0">
                <p class="font-body text-sm font-medium text-text-primary truncate">{{ h.name }}</p>
                <p class="font-body text-xs text-text-tertiary">未分类</p>
              </div>
              <div class="flex items-center gap-2 shrink-0">
                <span class="font-body text-sm font-medium text-text-primary">{{ formatMoney(h.marketValue) }}</span>
                <span class="material-symbols-outlined text-text-tertiary text-[16px]">category</span>
              </div>
            </div>
          </template>
          <template v-else>
            <div class="flex items-start gap-3">
              <div class="w-3 h-3 rounded-full mt-[5px] shrink-0" style="background-color:#9CA3AF"></div>
              <div class="flex-1 min-w-0">
                <div class="flex items-center justify-between">
                  <h3 class="font-body text-sm font-medium text-text-primary">未分类</h3>
                  <span class="font-body text-sm font-medium text-text-primary shrink-0 ml-2">{{ formatMoney(uncategorizedHoldings().reduce((s, h) => s + (h.marketValue || 0), 0)) }}</span>
                </div>
                <span class="font-body text-xs text-text-tertiary">{{ uncategorizedHoldings().length }} 个标的</span>
              </div>
            </div>
            <div class="border-t border-border-light/40 my-md"></div>
            <div class="space-y-0.5">
              <div v-for="h in uncategorizedHoldings()" :key="h.id"
                   class="flex items-center justify-between py-2.5 px-3 rounded-lg hover:bg-card-alt cursor-pointer transition-colors"
                   @click="openCategorySheet(h)">
                <span class="font-body text-xs text-text-primary truncate flex-1 min-w-0">{{ h.name }}</span>
                <div class="flex items-center gap-2 shrink-0">
                  <span class="font-body text-xs text-text-tertiary">{{ formatMoney(h.marketValue) }}</span>
                  <span class="material-symbols-outlined text-text-tertiary text-[16px]">category</span>
                </div>
              </div>
            </div>
          </template>
        </div>

        <!-- Empty State -->
        <div v-if="!overview || overview.categories.length === 0" class="text-center py-xl font-body text-xs text-text-tertiary">
          <span class="material-symbols-outlined text-[48px]">account_balance_wallet</span>
          <p class="mt-2">暂无资产数据</p>
          <p class="mt-1">添加基金持仓或手动录入比特币/现金</p>
        </div>
      </section>

      <!-- DCA Plans Overview -->
      <section v-if="sortedDcaPlans.length > 0" class="bg-card-bg rounded-lg p-lg card-shadow border border-border-light/40">
        <!-- Two-line header -->
        <div class="flex items-start gap-3 mb-md">
          <div class="w-3 h-3 rounded-full mt-[5px] shrink-0 bg-brand-dim"></div>
          <div class="flex-1 min-w-0">
            <div class="flex items-center justify-between">
              <h3 class="font-body text-sm font-medium text-text-primary">定投计划</h3>
              <span v-if="activeDcaPlans.length > 0"
                    class="font-body text-[11px] px-2 py-0.5 rounded-full shrink-0 ml-2 bg-brand-light text-brand">
                {{ activeDcaPlans.length }} 个活跃
              </span>
            </div>
            <span class="font-body text-xs text-text-tertiary">
              坚持定投 {{ dcaPersistenceDays }} 天
              <button class="text-text-tertiary/50 hover:text-text-tertiary transition-colors ml-2"
                      @click="openBudget()">· 预算</button>
            </span>
          </div>
        </div>

        <!-- Compact stats row -->
        <div class="flex bg-card-alt rounded-lg mb-md">
          <div class="flex-1 py-2 text-center border-r border-card-bg">
            <span class="font-body text-xs text-text-tertiary">每日 ¥{{ dcaDailyTotal }}</span>
          </div>
          <div class="flex-1 py-2 text-center border-r border-card-bg">
            <span class="font-body text-xs text-text-tertiary">累计 ¥{{ dcaTotalInvested.toLocaleString() }}</span>
          </div>
          <div class="flex-1 py-2 text-center">
            <span class="font-body text-xs text-text-tertiary">{{ dcaPersistenceDays }} 天</span>
          </div>
        </div>

        <!-- Plan list (max 3) -->
        <div class="border-t border-border-light/40"></div>
        <div class="space-y-0.5 mt-md">
          <div v-for="plan in sortedDcaPlans.slice(0, 3)" :key="plan.id"
               class="flex items-center py-2 px-2 rounded-lg hover:bg-card-alt cursor-pointer transition-colors"
               @click="router.push({ name: 'dca-plan-detail', params: { id: plan.id } })">
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2">
                <span class="w-2 h-2 rounded-full shrink-0"
                      :class="plan.status === 'active' ? 'bg-brand' : plan.status === 'paused' ? 'bg-yellow-500' : 'bg-text-tertiary'"></span>
                <span class="font-body text-xs text-text-primary truncate">{{ plan.holdingName }}</span>
              </div>
              <div class="flex items-center gap-2 mt-0.5 ml-4">
                <span class="font-body text-[11px] text-text-tertiary/60">
                  {{ plan.frequency === 'daily' ? '每日' : plan.frequency === 'weekly' ? '每周' : plan.frequency === 'biweekly' ? '双周' : '每月' }}
                  ¥{{ plan.amount }}
                </span>
                <span class="font-body text-[11px] text-text-tertiary/40">·</span>
                <span class="font-body text-[11px] text-text-tertiary/60">{{ plan.totalExecutions }} 期</span>
              </div>
            </div>
            <span class="font-body text-xs text-text-primary shrink-0 ml-2">¥{{ plan.totalInvested.toLocaleString() }}</span>
          </div>
        </div>

        <!-- Show all link -->
        <div v-if="sortedDcaPlans.length > 3" class="border-t border-border-light/40 mt-md pt-md">
          <button class="w-full text-center font-body text-xs text-text-tertiary hover:text-text-primary transition-colors flex items-center justify-center gap-1"
                  @click="showAllDcaPlans = true">
            <span class="material-symbols-outlined text-[14px]">expand_more</span>
            还有 {{ sortedDcaPlans.length - 3 }} 个定投计划
          </button>
        </div>
      </section>

      <!-- Brand Image — 财富之林 -->
      <section class="h-28 rounded-lg overflow-hidden relative">
        <div class="w-full h-full bg-gradient-to-br from-brand-light via-[#f0f7f3] to-card-alt flex items-center justify-center relative">
          <!-- 后排：小树丛（半透明） -->
          <span class="material-symbols-outlined text-[24px] text-brand-dim/30 absolute left-[15%] top-[18%]">forest</span>
          <span class="material-symbols-outlined text-[20px] text-brand-dim/25 absolute right-[20%] top-[12%]">forest</span>
          <!-- 中排：中等树木 -->
          <span class="material-symbols-outlined text-[32px] text-brand-dim/60 absolute left-[38%] top-[28%]">forest</span>
          <span class="material-symbols-outlined text-[30px] text-brand-dim/50 absolute right-[12%] top-[32%]">forest</span>
          <!-- 前排：主树（品牌色） -->
          <span class="material-symbols-outlined text-[40px] text-brand absolute left-[44%] top-[30%]">forest</span>
        </div>
        <div class="absolute inset-0 bg-gradient-to-t from-black/25 via-black/5 to-transparent flex items-end p-md">
          <p class="text-white font-body text-xs font-medium drop-shadow-sm">您的"财富之林"正茁壮成长</p>
        </div>
      </section>
    </main>

    <!-- Budget Dialog -->
    <Teleport to="body">
      <div v-if="showBudgetDialog"
           class="fixed inset-0 z-[60] flex items-center justify-center p-md"
           @click.self="showBudgetDialog = false">
        <div class="absolute inset-0 bg-black/40" @click="showBudgetDialog = false"></div>
        <!-- 固定高度弹窗：flex-col + max-h 确保外框不随内容变化 -->
        <div class="relative bg-card-bg rounded-lg card-shadow w-[90%] max-w-sm z-10 flex flex-col max-h-[70vh]">
          <!-- Header with month nav（固定，不滚动） -->
          <div class="flex items-center justify-between px-lg pt-lg pb-3 shrink-0">
            <button class="w-8 h-8 flex items-center justify-center rounded-lg transition-all active:scale-90"
                    :class="budgetLoading ? 'text-text-tertiary/40 cursor-not-allowed' : 'text-text-tertiary hover:bg-card-alt hover:text-text-primary'"
                    :disabled="budgetLoading"
                    @click="prevBudgetMonth()">
              <span class="material-symbols-outlined text-[18px]">chevron_left</span>
            </button>
            <h3 class="font-body text-sm font-medium text-text-primary">
              {{ budgetYear }}年{{ budgetMonth }}月 定投预算
            </h3>
            <button class="w-8 h-8 flex items-center justify-center rounded-lg transition-all active:scale-90"
                    :class="budgetLoading ? 'text-text-tertiary/40 cursor-not-allowed' : 'text-text-tertiary hover:bg-card-alt hover:text-text-primary'"
                    :disabled="budgetLoading"
                    @click="nextBudgetMonth()">
              <span class="material-symbols-outlined text-[18px]">chevron_right</span>
            </button>
          </div>

          <!-- 内容区域（固定占位，内部滚动） -->
          <div class="flex-1 overflow-y-auto px-lg min-h-[200px] relative">
            <!-- 数据态（始终在 DOM 中，opacity 控制显示） -->
            <div v-if="budgetData" class="py-1"
                 :class="budgetLoading ? 'opacity-0 pointer-events-none' : 'opacity-100 transition-opacity duration-200'">
              <!-- Total -->
              <div class="bg-card-alt rounded-lg p-3 text-center mb-3">
                <p class="font-body text-xs text-text-tertiary">总预算</p>
                <p class="font-display text-xl font-medium text-text-primary mt-1">{{ formatMoney(budgetData.totalAmount) }}</p>
                <p class="font-body text-xs text-text-tertiary mt-1">{{ budgetData.tradingDays }} 个交易日</p>
              </div>

              <!-- Plan details -->
              <div v-if="budgetData.plans.length > 0" class="space-y-0">
                <div v-for="(plan, index) in budgetData.plans" :key="plan.holdingName"
                     class="flex items-center justify-between py-2 px-3 rounded-lg hover:bg-card-alt transition-colors"
                     :class="index < budgetData.plans.length - 1 ? 'border-b border-border-light/40' : ''">
                  <div class="flex-1 min-w-0">
                    <p class="font-body text-xs text-text-primary truncate">{{ plan.holdingName }}</p>
                    <p class="font-body text-[11px] text-text-tertiary/60 mt-0.5">
                      {{ plan.frequency === 'daily' ? '每日' : plan.frequency === 'weekly' ? '每周' : plan.frequency === 'biweekly' ? '双周' : '每月' }}
                      ¥{{ plan.amount.toLocaleString() }}
                      · {{ plan.executions }} 次
                    </p>
                  </div>
                  <span class="font-body text-xs text-text-primary shrink-0 ml-2">{{ formatMoney(plan.budgetAmount) }}</span>
                </div>
              </div>
              <div v-else class="text-center py-6 font-body text-xs text-text-tertiary">
                该月无定投计划
              </div>
            </div>
            <!-- 加载态骨架屏（绝对定位覆盖在数据态之上，淡入淡出） -->
            <div v-if="budgetLoading" class="absolute inset-0 px-lg py-1 bg-card-bg"
                 :class="budgetLoading ? 'opacity-100 transition-opacity duration-200' : 'opacity-0 pointer-events-none'">
              <div class="space-y-3">
                <div class="bg-card-alt rounded-lg p-3 text-center animate-pulse">
                  <div class="h-3 w-16 bg-progress-bg rounded mx-auto mb-2"></div>
                  <div class="h-6 w-24 bg-progress-bg rounded mx-auto mb-2"></div>
                  <div class="h-3 w-20 bg-progress-bg rounded mx-auto"></div>
                </div>
                <div class="space-y-1">
                  <div v-for="i in 3" :key="i" class="h-[44px] bg-card-alt rounded-lg animate-pulse"></div>
                </div>
              </div>
            </div>
          </div>

          <!-- 底部关闭按钮（固定） -->
          <div class="text-center px-lg pb-lg pt-2 border-t border-border-light/40 shrink-0">
            <button class="font-body text-xs text-text-tertiary hover:text-text-primary transition-colors"
                    @click="showBudgetDialog = false">关闭</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- All DCA Plans Dialog -->
    <Teleport to="body">
      <div v-if="showAllDcaPlans"
           class="fixed inset-0 z-[60] flex items-center justify-center p-md"
           @click.self="showAllDcaPlans = false">
        <div class="absolute inset-0 bg-black/40" @click="showAllDcaPlans = false"></div>
        <div class="relative bg-card-bg rounded-lg card-shadow w-[90%] max-w-sm z-10 p-lg max-h-[70vh] flex flex-col">
          <div class="flex items-center justify-between mb-3">
            <h3 class="font-body text-sm font-medium text-text-primary">全部定投计划</h3>
            <button class="font-body text-xs text-text-tertiary hover:text-text-primary transition-colors"
                    @click="showAllDcaPlans = false">关闭</button>
          </div>
          <div class="overflow-y-auto flex-1 -mx-1">
            <div v-for="plan in sortedDcaPlans" :key="plan.id"
                 class="flex items-center py-2.5 px-2 rounded-lg hover:bg-card-alt cursor-pointer transition-colors"
                 @click="showAllDcaPlans = false; router.push({ name: 'dca-plan-detail', params: { id: plan.id } })">
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2">
                  <span class="w-2 h-2 rounded-full shrink-0"
                        :class="plan.status === 'active' ? 'bg-brand' : plan.status === 'paused' ? 'bg-yellow-500' : 'bg-text-tertiary'"></span>
                  <span class="font-body text-xs text-text-primary truncate">{{ plan.holdingName }}</span>
                </div>
                <div class="flex items-center gap-2 mt-0.5 ml-4">
                  <span class="font-body text-[11px] text-text-tertiary/60">
                    {{ plan.frequency === 'daily' ? '每日' : plan.frequency === 'weekly' ? '每周' : plan.frequency === 'biweekly' ? '双周' : '每月' }}
                    ¥{{ plan.amount }}
                  </span>
                  <span class="font-body text-[11px] text-text-tertiary/40">·</span>
                  <span class="font-body text-[11px] text-text-tertiary/60">{{ plan.totalExecutions }} 期</span>
                </div>
              </div>
              <span class="font-body text-xs text-text-primary shrink-0 ml-2">¥{{ plan.totalInvested.toLocaleString() }}</span>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Manual Asset Bottom Sheet -->
    <Teleport to="body">
      <div v-if="showAssetSheet"
           class="fixed inset-0 z-[100] bg-black/40"
           @click="showAssetSheet = false">
        <div class="absolute bottom-0 left-0 right-0 bg-card-bg rounded-t-2xl p-lg max-h-[80vh] overflow-y-auto"
             @click.stop>
          <div class="w-10 h-1 bg-border-light rounded-full mx-auto mb-lg"></div>
          <h3 class="font-body text-md font-medium text-text-primary mb-lg">
            {{ editingAssetId ? '编辑' : '添加' }}{{ assetForm.type === 'cash' ? '现金' : '比特币' }}
          </h3>
          <div class="space-y-md">
            <div>
              <label class="font-body text-xs text-text-tertiary block mb-1">名称</label>
              <input v-model="assetForm.name" placeholder="如：活期存款"
                     class="w-full px-md py-3 bg-card-alt rounded-lg text-text-primary outline-none font-body text-sm transition-colors focus:ring-2 focus:ring-brand" />
            </div>
            <div>
              <label class="font-body text-xs text-text-tertiary block mb-1">金额 (元)</label>
              <input v-model="assetForm.amount" type="text" placeholder="0.00 或 +5000 或 -3000"
                     class="w-full px-md py-3 bg-card-alt rounded-lg text-text-primary outline-none font-body text-sm transition-colors focus:ring-2 focus:ring-brand" />
            </div>
            <div>
              <label class="font-body text-xs text-text-tertiary block mb-1">备注 (可选)</label>
              <input v-model="assetForm.note" placeholder="备注信息"
                     class="w-full px-md py-3 bg-card-alt rounded-lg text-text-primary outline-none font-body text-sm transition-colors focus:ring-2 focus:ring-brand" />
            </div>
            <div class="flex gap-3 pt-2">
              <button class="flex-1 py-3 bg-card-alt text-text-secondary rounded-lg font-body text-sm font-medium active:scale-[0.98] transition-transform"
                      @click="showAssetSheet = false">取消</button>
              <button class="flex-1 py-3 bg-brand text-white rounded-lg font-body text-sm font-medium active:scale-[0.98] transition-transform disabled:opacity-50"
                      :disabled="savingAsset || !assetForm.name || assetForm.amount <= 0"
                      @click="saveAsset">
                {{ savingAsset ? '保存中...' : '保存' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Category Mapping Bottom Sheet -->
    <Teleport to="body">
      <div v-if="showCatSheet && selectedHolding"
           class="fixed inset-0 z-[100] bg-black/40"
           @click="showCatSheet = false">
        <div class="absolute bottom-0 left-0 right-0 bg-card-bg rounded-t-2xl p-lg"
             @click.stop>
          <div class="w-10 h-1 bg-border-light rounded-full mx-auto mb-lg"></div>
          <h3 class="font-body text-md font-medium text-text-primary mb-1">{{ selectedHolding.name }}</h3>
          <p class="font-body text-xs text-text-tertiary mb-lg">设置该持仓的资产类别</p>
          <div class="space-y-2">
            <button v-for="opt in [
              { value: '', label: '未分类' },
              { value: 'us_stock', label: '🇺🇸 美股 · 纳斯达克100联接' },
              { value: 'gold', label: '🥇 黄金 · ETF' },
              { value: 'dividend', label: '📋 红利 · 基金' },
            ]" :key="opt.value"
              class="w-full py-3 px-4 rounded-lg text-left font-body text-sm transition-colors"
              :class="selectedCategory === opt.value
                ? 'bg-brand-light text-brand'
                : 'bg-card-alt text-text-primary hover:bg-card-alt/80'"
              @click="selectedCategory = opt.value">
              {{ opt.label }}
            </button>
          </div>
          <div class="flex gap-3 pt-4">
            <button class="flex-1 py-3 bg-card-alt text-text-secondary rounded-lg font-body text-sm font-medium active:scale-[0.98] transition-transform"
                    @click="showCatSheet = false">取消</button>
            <button class="flex-1 py-3 bg-brand text-white rounded-lg font-body text-sm font-medium active:scale-[0.98] transition-transform disabled:opacity-50"
                    :disabled="savingCategory"
                    @click="saveCategory">
              {{ savingCategory ? '保存中...' : '确认' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Batch Category Bottom Sheet -->
    <Teleport to="body">
      <div v-if="showBatchCatSheet"
           class="fixed inset-0 z-[100] bg-black/40"
           @click="showBatchCatSheet = false">
        <div class="absolute bottom-0 left-0 right-0 bg-card-bg rounded-t-2xl p-lg"
             @click.stop>
          <div class="w-10 h-1 bg-border-light rounded-full mx-auto mb-lg"></div>
          <div class="flex items-start gap-3 mb-lg">
            <div class="w-10 h-10 rounded-full bg-brand-light flex items-center justify-center shrink-0">
              <span class="material-symbols-outlined text-brand">category</span>
            </div>
            <div>
              <h3 class="font-body text-md font-medium text-text-primary">批量分类</h3>
              <p class="font-body text-xs text-text-tertiary mt-1">
                将 {{ uncategorizedHoldings().length }} 只未分类基金全部设为同一类别
              </p>
            </div>
          </div>
          <div class="space-y-2">
            <button v-for="opt in [
              { value: 'us_stock', label: '📈 美股 · 纳斯达克100联接' },
              { value: 'gold', label: '🥇 黄金 · ETF' },
              { value: 'dividend', label: '📋 红利 · 基金' },
            ]" :key="opt.value"
              class="w-full py-3 px-4 rounded-lg text-left font-body text-sm transition-colors"
              :class="batchCategory === opt.value
                ? 'bg-brand-light text-brand'
                : 'bg-card-alt text-text-primary hover:bg-card-alt/80'"
              @click="batchCategory = opt.value">
              {{ opt.label }}
            </button>
          </div>
          <div class="flex gap-3 pt-4">
            <button class="flex-1 py-3 bg-card-alt text-text-secondary rounded-lg font-body text-sm font-medium active:scale-[0.98] transition-transform"
                    @click="showBatchCatSheet = false">取消</button>
            <button class="flex-1 py-3 bg-brand text-white rounded-lg font-body text-sm font-medium active:scale-[0.98] transition-transform disabled:opacity-50"
                    :disabled="savingBatch || !batchCategory"
                    @click="saveBatchCategory">
              {{ savingBatch ? '分类中...' : '全部设为该类别' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Delete Confirm -->
    <Teleport to="body">
      <div v-if="showDeleteConfirm"
           class="fixed inset-0 z-[100] bg-black/40 flex items-center justify-center p-lg"
           @click="showDeleteConfirm = false">
        <div class="bg-card-bg rounded-2xl p-lg max-w-sm w-full card-shadow" @click.stop>
          <div class="flex flex-col items-center text-center">
            <div class="w-12 h-12 rounded-full bg-error/10 flex items-center justify-center mb-md">
              <span class="material-symbols-outlined text-2xl text-error">delete_forever</span>
            </div>
            <h3 class="font-body text-md font-medium text-text-primary mb-sm">确认删除</h3>
            <p class="font-body text-sm text-text-tertiary mb-xl">
              确定删除"{{ deletingAssetName }}"吗？此操作不可撤销。
            </p>
            <div class="flex gap-3 w-full">
              <button class="flex-1 py-3 bg-card-alt text-text-secondary rounded-lg font-body text-sm font-medium active:scale-[0.98] transition-transform"
                      @click="showDeleteConfirm = false">取消</button>
              <button class="flex-1 py-3 bg-error text-white rounded-lg font-body text-sm font-medium active:scale-[0.98] transition-transform flex items-center justify-center gap-1"
                      @click="doDelete">
                <span class="material-symbols-outlined text-lg">delete</span>
                删除
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Value Change Detail Dialog -->
    <Teleport to="body">
      <div v-if="showValueDetail"
           class="fixed inset-0 z-50 flex items-center justify-center p-md"
           @click.self="showValueDetail = false">
        <div class="absolute inset-0 bg-black/40" @click="showValueDetail = false"></div>
        <div class="relative bg-card-bg rounded-lg card-shadow w-full max-w-sm max-h-[80vh] overflow-y-auto z-10 p-lg">
          <!-- Header -->
          <div class="flex items-center justify-between mb-lg">
            <h3 class="font-body text-sm font-medium text-text-primary">资产变动明细</h3>
            <button class="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-card-alt text-text-tertiary transition-colors"
                    @click="showValueDetail = false">
              <span class="material-symbols-outlined text-[18px]">close</span>
            </button>
          </div>

          <!-- Pill tabs -->
          <div class="flex gap-2 mb-lg">
            <button v-for="tab in (['week', 'month', 'year'] as const)" :key="tab"
                    class="flex-1 py-2 rounded-lg font-body text-xs transition-all"
                    :class="detailTab === tab
                      ? ((valueChange?.periods[tab]?.change ?? 0) >= 0
                        ? 'bg-brand-light text-brand'
                        : 'bg-error/10 text-error')
                      : 'bg-card-alt text-text-tertiary'"
                    @click="detailTab = tab">
              {{ tab === 'week' ? '本周' : tab === 'month' ? '本月' : '本年' }}
            </button>
          </div>

          <template v-if="valueChange?.periods[detailTab]">
            <!-- Summary -->
            <div class="mb-lg pb-3 border-b border-border-light/40">
              <span class="font-body text-xs text-text-tertiary">合计变化</span>
              <div class="flex items-center gap-3 mt-1">
                <span class="font-display text-2xl font-medium" :class="(valueChange.periods[detailTab].change ?? 0) >= 0 ? 'text-brand' : 'text-error'">
                  {{ changeDisplay(valueChange.periods[detailTab].change) }}
                </span>
                <span class="inline-block px-2 py-0.5 rounded-md font-body text-xs"
                      :class="(valueChange.periods[detailTab].change ?? 0) >= 0
                        ? 'bg-brand-light text-brand'
                        : 'bg-error/10 text-error'">
                  {{ formatPercent(valueChange.periods[detailTab].percent) }}
                </span>
              </div>
            </div>

            <!-- Holding list -->
            <div v-if="valueChange.periods[detailTab].details.length" class="space-y-1">
              <div v-for="d in valueChange.periods[detailTab].details" :key="d.holdingId"
                   class="flex items-center justify-between py-2 px-2 rounded-lg cursor-pointer hover:bg-card-alt transition-colors"
                   @click="router.push(`/holding/${d.holdingId}`)">
                <div class="flex items-center gap-2 min-w-0 flex-1">
                  <span class="material-symbols-outlined text-[16px] shrink-0"
                        :class="(d.change ?? 0) >= 0 ? 'text-brand' : 'text-error'">
                    {{ (d.change ?? 0) >= 0 ? 'trending_up' : 'trending_down' }}
                  </span>
                  <span class="font-body text-xs text-text-primary truncate">{{ d.name }}</span>
                </div>
                <div class="flex items-center gap-3 shrink-0 ml-2">
                  <span class="font-body text-xs" :class="(d.change ?? 0) >= 0 ? 'text-brand' : 'text-error'">
                    {{ changeDisplay(d.change) }}
                  </span>
                  <span class="font-body text-xs text-text-tertiary/60 w-12 text-right">
                    {{ formatPercent(d.percent) }}
                  </span>
                </div>
              </div>
            </div>
            <div v-else class="font-body text-xs text-text-tertiary text-center py-lg">
              暂无变动数据
            </div>
          </template>

          <!-- Close -->
          <div class="text-center mt-lg">
            <button class="font-body text-xs text-text-tertiary underline underline-offset-2 hover:text-text-primary transition-colors"
                    @click="showValueDetail = false">
              关闭
            </button>
          </div>
        </div>
      </div>
    </Teleport>
    <!-- Add Type Picker -->
    <Teleport to="body">
      <div v-if="showAddTypePicker"
           class="fixed inset-0 z-[60] flex items-center justify-center p-md"
           @click.self="showAddTypePicker = false">
        <div class="absolute inset-0 bg-black/40" @click="showAddTypePicker = false"></div>
        <div class="relative bg-card-bg rounded-lg card-shadow w-56 z-10 p-lg">
          <p class="font-body text-xs text-text-tertiary text-center mb-3">选择资产类型</p>
          <button class="w-full flex items-center gap-3 py-2.5 px-3 rounded-lg hover:bg-card-alt transition-colors"
                  @click="showAddTypePicker = false; openAddSheet('cash')">
            <span class="w-3 h-3 rounded-full shrink-0" style="background-color:#34A853"></span>
            <span class="font-body text-xs text-text-primary">现金</span>
          </button>
          <button class="w-full flex items-center gap-3 py-2.5 px-3 rounded-lg hover:bg-card-alt transition-colors mt-1"
                  @click="showAddTypePicker = false; openAddSheet('crypto')">
            <span class="w-3 h-3 rounded-full shrink-0" style="background-color:#F59E0B"></span>
            <span class="font-body text-xs text-text-primary">比特币</span>
          </button>
          <div class="text-center mt-2 pt-2 border-t border-border-light/40">
            <button class="font-body text-xs text-text-tertiary hover:text-text-primary transition-colors"
                    @click="showAddTypePicker = false">取消</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.2s ease;
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(6px);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
