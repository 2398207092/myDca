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

onMounted(loadData)
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
function openAddSheet(type: 'cash' | 'crypto') {
  editingAssetId.value = null
  assetForm.value = { name: '', type, amount: 0, note: '' }
  showAssetSheet.value = true
}

function openEditSheet(asset: ManualAssetItem) {
  editingAssetId.value = asset.id
  assetForm.value = {
    name: asset.name,
    type: asset.type as 'cash' | 'crypto',
    amount: asset.amount,
    note: asset.note || '',
  }
  showAssetSheet.value = true
}

async function saveAsset() {
  if (!assetForm.value.name || assetForm.value.amount <= 0) return
  savingAsset.value = true
  try {
    if (editingAssetId.value) {
      const req: UpdateManualAssetReq = {
        name: assetForm.value.name,
        type: assetForm.value.type,
        amount: assetForm.value.amount,
        note: assetForm.value.note || undefined,
      }
      const updated = await updateManualAsset(editingAssetId.value, req)
      const idx = allManualAssets.value.findIndex(a => a.id === updated.id)
      if (idx >= 0) allManualAssets.value[idx] = updated
    } else {
      const req: CreateManualAssetReq = {
        name: assetForm.value.name,
        type: assetForm.value.type,
        amount: assetForm.value.amount,
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
  <div class="min-h-screen bg-background">
    <AppHeader title="资产概览" :showLogo="true" right-icon="account_circle" />

    <!-- Loading State -->
    <main v-if="pageState === 'loading'" class="max-w-[600px] mx-auto px-gutter pt-20 pb-24">
      <div class="flex flex-col items-center justify-center py-24">
        <span class="material-symbols-outlined text-[48px] text-outline-variant animate-spin">sync</span>
        <p class="text-on-surface-variant mt-4">加载中...</p>
      </div>
    </main>

    <!-- Error State -->
    <main v-else-if="pageState === 'error'" class="max-w-[600px] mx-auto px-gutter pt-20 pb-24">
      <div class="flex flex-col items-center justify-center py-24">
        <span class="material-symbols-outlined text-[48px] text-error mb-4">error</span>
        <p class="text-on-surface-variant mb-2">加载失败</p>
        <p class="text-outline-variant text-caption mb-6">{{ errorMsg }}</p>
        <button class="px-6 py-2 bg-primary-container text-on-primary-container rounded-full font-caption"
                @click="loadData">重试</button>
      </div>
    </main>

    <!-- Ready State -->
    <main v-else class="max-w-[600px] mx-auto px-gutter pt-20 pb-28 space-y-4">
      <!-- Classification Banner (above hero) -->
      <section v-if="uncategorizedHoldings().length > 0 && !bannerDismissed"
               class="bg-primary-fixed/15 rounded-xl p-md shadow-[0_4px_12px_rgba(0,0,0,0.05)] relative overflow-hidden">
        <button class="absolute top-2 right-2 w-6 h-6 flex items-center justify-center text-on-surface-variant hover:text-on-surface transition-colors"
                @click="dismissBanner">
          <span class="material-symbols-outlined text-[16px]">close</span>
        </button>
        <div class="flex items-start gap-3">
          <div class="w-10 h-10 rounded-full bg-primary-fixed/30 flex items-center justify-center shrink-0">
            <span class="material-symbols-outlined text-primary text-[22px]">category</span>
          </div>
          <div class="flex-1 min-w-0">
            <h3 class="font-label-bold text-label-bold text-on-surface mb-1">有 {{ uncategorizedHoldings().length }} 只基金还未分类</h3>
            <p class="font-caption text-caption text-on-surface-variant mb-3">
              将它们归类到"美股"、"黄金"或"红利"，以便在资产概览中准确统计
            </p>
            <div class="flex gap-2">
              <button class="px-4 py-2 bg-primary text-on-primary rounded-full font-caption text-caption
                             active:scale-[0.97] transition-transform"
                      @click="openBatchCategory">
                立即分类
              </button>
              <button class="px-4 py-2 bg-surface-container-high text-on-surface rounded-full font-caption text-caption
                             active:scale-[0.97] transition-transform"
                      @click="dismissBanner">
                稍后再说
              </button>
            </div>
          </div>
        </div>
      </section>

      <!-- Total Assets Hero with Value Change -->
      <section class="bg-surface-container-lowest rounded-xl p-md shadow-[0_4px_12px_rgba(0,0,0,0.05)]">
        <!-- Header: label left, label · arrow right -->
        <div class="flex items-center justify-between mb-1">
          <span class="font-caption text-caption text-on-surface-variant">总资产 (元)</span>
          <span class="flex items-center gap-1 font-caption text-caption text-on-surface-variant/40 group cursor-pointer"
                @click="showValueDetail = true">
            <span class="translate-y-[1px] group-hover:text-on-surface-variant/80 transition-colors">点击查看明细</span>
            <span class="group-hover:text-on-surface-variant/80 transition-colors">·</span>
            <span class="text-[17px] leading-none -translate-y-[1px] group-hover:text-on-surface transition-colors">›</span>
          </span>
        </div>

        <!-- Total assets centered -->
        <div class="text-center font-headline-lg text-headline-lg-mobile text-on-surface mb-4 tracking-tight">
          {{ formatMoney(overview?.totalValue) }}
        </div>

        <!-- Three pill cards -->
        <div v-if="valueChange"
             class="flex gap-2 cursor-pointer"
             @click="showValueDetail = true">
          <div v-for="period in (['week', 'month', 'year'] as const)" :key="period"
               class="flex-1 rounded-lg py-2 text-center transition-opacity hover:opacity-80"
               :class="(valueChange.periods[period]?.change || 0) >= 0
                  ? 'bg-primary-fixed text-primary'
                  : 'bg-error-container text-on-error-container'">
            <div class="font-caption text-caption opacity-80">
              {{ period === 'week' ? '本周' : period === 'month' ? '本月' : '本年' }}
            </div>
            <div class="font-label-bold text-label-bold mt-0.5">
              {{ formatPercent(valueChange.periods[period]?.percent || 0) }}
            </div>
          </div>
        </div>
      </section>

      <!-- Asset Allocation (Bento) -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <!-- Allocation Treemap -->
        <section class="bg-surface-container-lowest rounded-xl p-md shadow-[0_4px_12px_rgba(0,0,0,0.05)]">
          <div class="flex items-center justify-between mb-3">
            <h2 class="font-label-bold text-label-bold text-on-surface">持仓占比</h2>
          </div>
          <div v-if="overview && overview.categories.length > 0" class="space-y-3">
            <div class="flex w-full h-8 rounded-lg overflow-hidden">
              <div v-for="cat in overview.categories" :key="cat.type"
                   class="flex items-center justify-center transition-all"
                   :style="{ width: cat.percentage + '%', backgroundColor: cat.color }">
                <span v-if="cat.percentage > 15" class="font-caption text-caption text-white text-[11px]">
                  {{ cat.percentage.toFixed(0) }}%
                </span>
              </div>
            </div>
            <div class="flex flex-col gap-2">
              <div v-for="cat in overview.categories" :key="cat.type"
                   class="flex items-center gap-3">
                <div class="w-3 h-3 rounded-full" :style="{ backgroundColor: cat.color }"></div>
                <span class="font-caption text-caption text-on-surface-variant">{{ cat.name }}</span>
                <span class="ml-auto font-caption text-caption text-on-surface">{{ formatMoney(cat.value) }}</span>
              </div>
            </div>
          </div>
          <div v-else class="flex items-center justify-center h-20 text-outline-variant font-caption">
            暂无资产数据
          </div>
        </section>
      </div>

      <!-- Category Detail Cards -->
      <section class="space-y-3">
        <div class="flex items-center justify-between px-1">
          <h2 class="font-label-bold text-label-bold text-on-surface">各类资产</h2>
        </div>

        <!-- US Stocks -->
        <div v-if="overview && overview.usStockValue > 0" class="bg-surface-container-lowest rounded-xl p-md shadow-[0_4px_12px_rgba(0,0,0,0.05)]">
          <div class="flex items-start gap-3">
            <div class="w-3 h-3 rounded-full mt-[5px] shrink-0" style="background-color:#3B82F6"></div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center justify-between">
                <h3 class="font-label-bold text-label-bold text-on-surface">美股 · 纳斯达克100联接</h3>
                <span class="font-label-bold text-label-bold text-on-surface shrink-0 ml-2">{{ formatMoney(overview.usStockValue) }}</span>
              </div>
              <span class="font-caption text-caption text-on-surface-variant">{{ categorizedHoldings('us_stock').length }} 个标的</span>
            </div>
          </div>
          <div class="border-t border-outline-variant/20 my-2.5"></div>
          <div v-if="allHoldings.length > 0" class="space-y-0.5">
            <div v-for="h in categorizedHoldings('us_stock')" :key="h.id"
                 class="flex items-center justify-between py-2.5 px-3 rounded-lg hover:bg-surface-container cursor-pointer transition-colors"
                 @click="openCategorySheet(h)">
              <span class="font-caption text-caption text-on-surface truncate flex-1 min-w-0">{{ h.name }}</span>
              <span class="font-caption text-caption text-on-surface-variant shrink-0 ml-2">{{ formatMoney(h.marketValue) }}</span>
            </div>
          </div>
          <div v-if="uncategorizedHoldings().length > 0 && categorizedHoldings('us_stock').length === 0" class="text-outline-variant font-caption py-2">
            暂无分类 — 在持仓详情中设置分类为"美股"
          </div>
        </div>

        <!-- Gold -->
        <div v-if="overview && overview.goldValue > 0" class="bg-surface-container-lowest rounded-xl p-md shadow-[0_4px_12px_rgba(0,0,0,0.05)]">
          <div class="flex items-start gap-3">
            <div class="w-3 h-3 rounded-full mt-[5px] shrink-0" style="background-color:#F59E0B"></div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center justify-between">
                <h3 class="font-label-bold text-label-bold text-on-surface">黄金 · ETF</h3>
                <span class="font-label-bold text-label-bold text-on-surface shrink-0 ml-2">{{ formatMoney(overview.goldValue) }}</span>
              </div>
              <span class="font-caption text-caption text-on-surface-variant">{{ categorizedHoldings('gold').length }} 个标的</span>
            </div>
          </div>
          <div class="border-t border-outline-variant/20 my-2.5"></div>
          <div v-if="categorizedHoldings('gold').length > 0" class="space-y-0.5">
            <div v-for="h in categorizedHoldings('gold')" :key="h.id"
                 class="flex items-center justify-between py-2.5 px-3 rounded-lg hover:bg-surface-container cursor-pointer transition-colors"
                 @click="openCategorySheet(h)">
              <span class="font-caption text-caption text-on-surface truncate flex-1 min-w-0">{{ h.name }}</span>
              <span class="font-caption text-caption text-on-surface-variant shrink-0 ml-2">{{ formatMoney(h.marketValue) }}</span>
            </div>
          </div>
        </div>

        <!-- Dividend / Fund Holdings -->
        <div v-if="overview && overview.dividendValue > 0" class="bg-surface-container-lowest rounded-xl p-md shadow-[0_4px_12px_rgba(0,0,0,0.05)]">
          <div class="flex items-start gap-3">
            <div class="w-3 h-3 rounded-full mt-[5px] shrink-0" style="background-color:#EAB308"></div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center justify-between">
                <h3 class="font-label-bold text-label-bold text-on-surface">红利 · 基金</h3>
                <span class="font-label-bold text-label-bold text-on-surface shrink-0 ml-2">{{ formatMoney(overview.dividendValue) }}</span>
              </div>
              <span class="font-caption text-caption text-on-surface-variant">{{ categorizedHoldings('dividend').length }} 个标的</span>
            </div>
          </div>
          <div class="border-t border-outline-variant/20 my-2.5"></div>
          <div v-if="categorizedHoldings('dividend').length > 0" class="space-y-0.5">
            <div v-for="h in categorizedHoldings('dividend')" :key="h.id"
                 class="flex items-center justify-between py-2.5 px-3 rounded-lg hover:bg-surface-container cursor-pointer transition-colors"
                 @click="openCategorySheet(h)">
              <span class="font-caption text-caption text-on-surface truncate flex-1 min-w-0">{{ h.name }}</span>
              <span class="font-caption text-caption text-on-surface-variant shrink-0 ml-2">{{ formatMoney(h.marketValue) }}</span>
            </div>
          </div>
        </div>

        <!-- Manual Assets (Cash + Crypto) -->
        <div v-if="overview"
             class="bg-surface-container-lowest rounded-xl p-md shadow-[0_4px_12px_rgba(0,0,0,0.05)]">
          <template v-if="allManualAssets.length > 0">
            <div class="flex items-start gap-3">
              <div class="w-3 h-3 rounded-full mt-[5px] shrink-0" style="background-color:#6366F1"></div>
              <div class="flex-1 min-w-0">
                <div class="flex items-center justify-between">
                  <h3 class="font-label-bold text-label-bold text-on-surface">手动资产</h3>
                  <span class="font-label-bold text-label-bold text-on-surface shrink-0 ml-2">{{ formatMoney((overview.cashValue || 0) + (overview.cryptoValue || 0)) }}</span>
                </div>
                <span class="font-caption text-caption text-on-surface-variant">{{ allManualAssets.length }} 个标的</span>
              </div>
            </div>
            <div class="border-t border-outline-variant/20 my-2.5"></div>
            <div class="space-y-0.5">
              <div v-for="a in allManualAssets" :key="a.id"
                   class="flex items-center justify-between py-2.5 px-3 rounded-lg hover:bg-surface-container cursor-pointer transition-colors"
                   @click="openEditSheet(a)">
                <div class="flex items-center gap-2 min-w-0 flex-1">
                  <span class="w-2 h-2 rounded-full shrink-0"
                        :class="a.type === 'cash' ? 'bg-[#34A853]' : 'bg-[#F59E0B]'"></span>
                  <span class="font-caption text-caption text-on-surface truncate">{{ a.name }}</span>
                  <span class="font-caption text-[10px] text-on-surface-variant/50 shrink-0">{{ a.type === 'cash' ? '现金' : '比特币' }}</span>
                </div>
                <div class="flex items-center gap-2 shrink-0">
                  <span class="font-caption text-caption text-on-surface-variant">{{ formatMoney(a.amount) }}</span>
                  <button class="text-outline-variant hover:text-error transition-colors"
                          @click.stop="confirmDelete(a)">
                    <span class="material-symbols-outlined text-[16px]">close</span>
                  </button>
                </div>
              </div>
            </div>
            <div class="border-t border-outline-variant/20 my-2.5"></div>
            <button class="w-full py-2 text-center font-caption text-caption text-on-surface-variant hover:bg-surface-container rounded-lg transition-colors flex items-center justify-center gap-1"
                    @click="showAddTypePicker = true">
              <span class="material-symbols-outlined text-[14px]">add</span>
              添加手动资产
            </button>
          </template>
          <template v-else>
            <div class="py-6 flex flex-col items-center gap-3">
              <span class="material-symbols-outlined text-[32px] text-outline-variant">handyman</span>
              <p class="font-caption text-caption text-outline-variant">暂无手动资产</p>
              <button class="px-5 py-2 bg-primary-container text-on-primary-container rounded-xl font-label-bold text-label-bold flex items-center gap-1.5"
                      @click="showAddTypePicker = true">
                <span class="material-symbols-outlined text-[16px]">add</span>
                添加手动资产
              </button>
            </div>
          </template>
        </div>

        <!-- Uncategorized Holdings -->
        <div v-if="uncategorizedHoldings().length > 0"
             class="bg-surface-container-lowest rounded-xl p-md shadow-[0_4px_12px_rgba(0,0,0,0.05)]">
          <div class="flex items-start gap-3">
            <div class="w-3 h-3 rounded-full mt-[5px] shrink-0" style="background-color:#9CA3AF"></div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center justify-between">
                <h3 class="font-label-bold text-label-bold text-on-surface">未分类</h3>
                <span class="font-label-bold text-label-bold text-on-surface shrink-0 ml-2">{{ formatMoney(uncategorizedHoldings().reduce((s, h) => s + (h.marketValue || 0), 0)) }}</span>
              </div>
              <span class="font-caption text-caption text-on-surface-variant">{{ uncategorizedHoldings().length }} 个标的</span>
            </div>
          </div>
          <div class="border-t border-outline-variant/20 my-2.5"></div>
          <div class="space-y-0.5">
            <div v-for="h in uncategorizedHoldings()" :key="h.id"
                 class="flex items-center justify-between py-2.5 px-3 rounded-lg hover:bg-surface-container cursor-pointer transition-colors"
                 @click="openCategorySheet(h)">
              <span class="font-caption text-caption text-on-surface truncate flex-1 min-w-0">{{ h.name }}</span>
              <div class="flex items-center gap-2 shrink-0">
                <span class="font-caption text-caption text-on-surface-variant">{{ formatMoney(h.marketValue) }}</span>
                <span class="material-symbols-outlined text-outline-variant text-[16px]">category</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty State -->
        <div v-if="!overview || overview.categories.length === 0" class="text-center py-12 text-outline-variant">
          <span class="material-symbols-outlined text-[48px]">account_balance_wallet</span>
          <p class="mt-2 font-caption">暂无资产数据</p>
          <p class="text-caption mt-1">添加基金持仓或手动录入比特币/现金</p>
        </div>
      </section>

      <!-- DCA Plans Overview -->
      <section v-if="sortedDcaPlans.length > 0" class="bg-surface-container-lowest rounded-xl p-md shadow-[0_4px_12px_rgba(0,0,0,0.05)]">
        <!-- Two-line header -->
        <div class="flex items-start gap-3 mb-2.5">
          <div class="w-3 h-3 rounded-full mt-[5px] shrink-0 bg-primary-fixed-dim"></div>
          <div class="flex-1 min-w-0">
            <div class="flex items-center justify-between">
              <h3 class="font-label-bold text-label-bold text-on-surface">定投计划</h3>
              <span v-if="activeDcaPlans.length > 0"
                    class="text-caption text-[11px] px-2 py-0.5 rounded-full shrink-0 ml-2 bg-success/10 text-success">
                {{ activeDcaPlans.length }} 个活跃
              </span>
            </div>
            <span class="font-caption text-caption text-on-surface-variant">
              坚持定投 {{ dcaPersistenceDays }} 天
              <button class="text-on-surface-variant/50 hover:text-on-surface-variant transition-colors ml-2"
                      @click="openBudget()">· 预算</button>
            </span>
          </div>
        </div>

        <!-- Compact stats row -->
        <div class="flex bg-surface-container rounded-lg mb-2.5">
          <div class="flex-1 py-2 text-center border-r border-surface-container-lowest">
            <span class="font-caption text-caption text-on-surface-variant">每日 ¥{{ dcaDailyTotal }}</span>
          </div>
          <div class="flex-1 py-2 text-center border-r border-surface-container-lowest">
            <span class="font-caption text-caption text-on-surface-variant">累计 ¥{{ dcaTotalInvested.toLocaleString() }}</span>
          </div>
          <div class="flex-1 py-2 text-center">
            <span class="font-caption text-caption text-on-surface-variant">{{ dcaPersistenceDays }} 天</span>
          </div>
        </div>

        <!-- Plan list (max 3) -->
        <div class="border-t border-outline-variant/20"></div>
        <div class="space-y-0.5 mt-2.5">
          <div v-for="plan in sortedDcaPlans.slice(0, 3)" :key="plan.id"
               class="flex items-center py-2 px-2 rounded-lg hover:bg-surface-container cursor-pointer transition-colors"
               @click="router.push({ name: 'dca-plan-detail', params: { id: plan.id } })">
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2">
                <span class="w-2 h-2 rounded-full shrink-0"
                      :class="plan.status === 'active' ? 'bg-success' : plan.status === 'paused' ? 'bg-yellow-500' : 'bg-outline-variant'"></span>
                <span class="font-caption text-caption text-on-surface truncate">{{ plan.holdingName }}</span>
              </div>
              <div class="flex items-center gap-2 mt-0.5 ml-4">
                <span class="font-caption text-[11px] text-on-surface-variant/60">
                  {{ plan.frequency === 'daily' ? '每日' : plan.frequency === 'weekly' ? '每周' : plan.frequency === 'biweekly' ? '双周' : '每月' }}
                  ¥{{ plan.amount }}
                </span>
                <span class="font-caption text-[11px] text-on-surface-variant/40">·</span>
                <span class="font-caption text-[11px] text-on-surface-variant/60">{{ plan.totalExecutions }} 期</span>
              </div>
            </div>
            <span class="font-caption text-caption text-on-surface shrink-0 ml-2">¥{{ plan.totalInvested.toLocaleString() }}</span>
          </div>
        </div>

        <!-- Show all link -->
        <div v-if="sortedDcaPlans.length > 3" class="border-t border-outline-variant/20 mt-2.5 pt-2.5">
          <button class="w-full text-center font-caption text-caption text-on-surface-variant hover:text-on-surface transition-colors flex items-center justify-center gap-1"
                  @click="showAllDcaPlans = true">
            <span class="material-symbols-outlined text-[14px]">expand_more</span>
            还有 {{ sortedDcaPlans.length - 3 }} 个定投计划
          </button>
        </div>
      </section>

      <!-- Brand Image -->
      <section class="h-36 rounded-xl overflow-hidden relative">
        <div class="w-full h-full bg-gradient-to-br from-primary-fixed/30 to-surface-container-high flex items-center justify-center">
          <span class="material-symbols-outlined text-[48px] text-primary-fixed-dim">forest</span>
        </div>
        <div class="absolute inset-0 bg-gradient-to-t from-black/20 to-transparent flex items-end p-md">
          <p class="text-white font-caption text-caption">您的"财富之林"正茁壮成长</p>
        </div>
      </section>
    </main>

    <!-- Budget Dialog -->
    <Teleport to="body">
      <div v-if="showBudgetDialog"
           class="fixed inset-0 z-[60] flex items-center justify-center p-md"
           @click.self="showBudgetDialog = false">
        <div class="absolute inset-0 bg-black/40" @click="showBudgetDialog = false"></div>
        <div class="relative bg-surface-container-lowest rounded-xl shadow-xl w-[90%] max-w-sm z-10 p-4">
          <!-- Header with month nav -->
          <div class="flex items-center justify-between mb-3">
            <button class="w-8 h-8 flex items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container hover:text-on-surface transition-all active:scale-90"
                    @click="prevBudgetMonth()">
              <span class="material-symbols-outlined text-[18px]">chevron_left</span>
            </button>
            <h3 class="font-label-bold text-label-bold text-on-surface">
              {{ budgetYear }}年{{ budgetMonth }}月 定投预算
            </h3>
            <button class="w-8 h-8 flex items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container hover:text-on-surface transition-all active:scale-90"
                    @click="nextBudgetMonth()">
              <span class="material-symbols-outlined text-[18px]">chevron_right</span>
            </button>
          </div>

          <Transition name="fade-slide" mode="out-in">
            <div v-if="budgetLoading" key="loading" class="text-center py-8 text-caption text-on-surface-variant">加载中...</div>
            <div v-else-if="budgetData" key="data">
              <!-- Total -->
              <div class="bg-surface-container rounded-lg p-3 text-center mb-3">
                <p class="font-caption text-caption text-on-surface-variant">总预算</p>
                <p class="font-headline-md text-headline-md text-on-surface mt-1">{{ formatMoney(budgetData.totalAmount) }}</p>
                <p class="font-caption text-caption text-on-surface-variant mt-1">{{ budgetData.tradingDays }} 个交易日</p>
              </div>

              <!-- Plan details -->
              <div class="max-h-[40vh] overflow-y-auto -mx-1 px-1">
                <div v-for="(plan, index) in budgetData.plans" :key="plan.holdingName"
                     class="flex items-center justify-between py-2 px-3 rounded-lg hover:bg-surface-container transition-colors"
                     :class="index < budgetData.plans.length - 1 ? 'border-b border-outline-variant/10' : ''">
                  <div class="flex-1 min-w-0">
                    <p class="font-caption text-caption text-on-surface truncate">{{ plan.holdingName }}</p>
                    <p class="font-caption text-[11px] text-on-surface-variant/60 mt-0.5">
                      {{ plan.frequency === 'daily' ? '每日' : plan.frequency === 'weekly' ? '每周' : plan.frequency === 'biweekly' ? '双周' : '每月' }}
                      ¥{{ plan.amount.toLocaleString() }}
                      · {{ plan.executions }} 次
                    </p>
                  </div>
                  <span class="font-caption text-caption text-on-surface shrink-0 ml-2">{{ formatMoney(plan.budgetAmount) }}</span>
                </div>
              </div>
            </div>
          </Transition>

          <div class="text-center mt-3 pt-2 border-t border-outline-variant/20">
            <button class="font-caption text-caption text-on-surface-variant"
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
        <div class="relative bg-surface-container-lowest rounded-xl shadow-xl w-[90%] max-w-sm z-10 p-4 max-h-[70vh] flex flex-col">
          <div class="flex items-center justify-between mb-3">
            <h3 class="font-label-bold text-label-bold text-on-surface">全部定投计划</h3>
            <button class="font-caption text-caption text-on-surface-variant hover:text-on-surface"
                    @click="showAllDcaPlans = false">关闭</button>
          </div>
          <div class="overflow-y-auto flex-1 -mx-1">
            <div v-for="plan in sortedDcaPlans" :key="plan.id"
                 class="flex items-center py-2.5 px-2 rounded-lg hover:bg-surface-container cursor-pointer transition-colors"
                 @click="showAllDcaPlans = false; router.push({ name: 'dca-plan-detail', params: { id: plan.id } })">
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2">
                  <span class="w-2 h-2 rounded-full shrink-0"
                        :class="plan.status === 'active' ? 'bg-success' : plan.status === 'paused' ? 'bg-yellow-500' : 'bg-outline-variant'"></span>
                  <span class="font-caption text-caption text-on-surface truncate">{{ plan.holdingName }}</span>
                </div>
                <div class="flex items-center gap-2 mt-0.5 ml-4">
                  <span class="font-caption text-[11px] text-on-surface-variant/60">
                    {{ plan.frequency === 'daily' ? '每日' : plan.frequency === 'weekly' ? '每周' : plan.frequency === 'biweekly' ? '双周' : '每月' }}
                    ¥{{ plan.amount }}
                  </span>
                  <span class="font-caption text-[11px] text-on-surface-variant/40">·</span>
                  <span class="font-caption text-[11px] text-on-surface-variant/60">{{ plan.totalExecutions }} 期</span>
                </div>
              </div>
              <span class="font-caption text-caption text-on-surface shrink-0 ml-2">¥{{ plan.totalInvested.toLocaleString() }}</span>
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
        <div class="absolute bottom-0 left-0 right-0 bg-surface-container-lowest rounded-t-2xl p-6 max-h-[80vh] overflow-y-auto"
             @click.stop>
          <div class="w-10 h-1 bg-outline-variant rounded-full mx-auto mb-4"></div>
          <h3 class="font-headline-md text-headline-md mb-4">
            {{ editingAssetId ? '编辑' : '添加' }}{{ assetForm.type === 'cash' ? '现金' : '比特币' }}
          </h3>
          <div class="space-y-4">
            <div>
              <label class="font-caption text-caption text-on-surface-variant block mb-1">名称</label>
              <input v-model="assetForm.name" placeholder="如：活期存款"
                     class="w-full px-4 py-3 bg-surface-container rounded-xl text-on-surface outline-none font-body-md" />
            </div>
            <div>
              <label class="font-caption text-caption text-on-surface-variant block mb-1">金额 (元)</label>
              <input v-model.number="assetForm.amount" type="number" step="0.01" min="0" placeholder="0.00"
                     class="w-full px-4 py-3 bg-surface-container rounded-xl text-on-surface outline-none font-body-md" />
            </div>
            <div>
              <label class="font-caption text-caption text-on-surface-variant block mb-1">备注 (可选)</label>
              <input v-model="assetForm.note" placeholder="备注信息"
                     class="w-full px-4 py-3 bg-surface-container rounded-xl text-on-surface outline-none font-body-md" />
            </div>
            <div class="flex gap-3 pt-2">
              <button class="flex-1 py-3 bg-surface-container-high text-on-surface rounded-xl font-label-bold"
                      @click="showAssetSheet = false">取消</button>
              <button class="flex-1 py-3 bg-primary text-white rounded-xl font-label-bold
                             disabled:opacity-50"
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
        <div class="absolute bottom-0 left-0 right-0 bg-surface-container-lowest rounded-t-2xl p-6"
             @click.stop>
          <div class="w-10 h-1 bg-outline-variant rounded-full mx-auto mb-4"></div>
          <h3 class="font-headline-md text-headline-md mb-1">{{ selectedHolding.name }}</h3>
          <p class="font-caption text-caption text-outline-variant mb-4">设置该持仓的资产类别</p>
          <div class="space-y-2">
            <button v-for="opt in [
              { value: '', label: '未分类' },
              { value: 'us_stock', label: '🇺🇸 美股 · 纳斯达克100联接' },
              { value: 'gold', label: '🥇 黄金 · ETF' },
              { value: 'dividend', label: '📋 红利 · 基金' },
            ]" :key="opt.value"
              class="w-full py-3 px-4 rounded-xl text-left font-body-md transition-colors"
              :class="selectedCategory === opt.value
                ? 'bg-primary-container text-on-primary-container'
                : 'bg-surface-container text-on-surface hover:bg-surface-container-high'"
              @click="selectedCategory = opt.value">
              {{ opt.label }}
            </button>
          </div>
          <div class="flex gap-3 pt-4">
            <button class="flex-1 py-3 bg-surface-container-high text-on-surface rounded-xl font-label-bold"
                    @click="showCatSheet = false">取消</button>
            <button class="flex-1 py-3 bg-primary text-white rounded-xl font-label-bold disabled:opacity-50"
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
        <div class="absolute bottom-0 left-0 right-0 bg-surface-container-lowest rounded-t-2xl p-6"
             @click.stop>
          <div class="w-10 h-1 bg-outline-variant rounded-full mx-auto mb-4"></div>
          <div class="flex items-start gap-3 mb-4">
            <div class="w-10 h-10 rounded-full bg-primary-fixed/30 flex items-center justify-center shrink-0">
              <span class="material-symbols-outlined text-primary">category</span>
            </div>
            <div>
              <h3 class="font-headline-md text-headline-md">批量分类</h3>
              <p class="font-caption text-caption text-outline-variant mt-1">
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
              class="w-full py-3 px-4 rounded-xl text-left font-body-md transition-colors"
              :class="batchCategory === opt.value
                ? 'bg-primary-container text-on-primary-container'
                : 'bg-surface-container text-on-surface hover:bg-surface-container-high'"
              @click="batchCategory = opt.value">
              {{ opt.label }}
            </button>
          </div>
          <div class="flex gap-3 pt-4">
            <button class="flex-1 py-3 bg-surface-container-high text-on-surface rounded-xl font-label-bold"
                    @click="showBatchCatSheet = false">取消</button>
            <button class="flex-1 py-3 bg-primary text-white rounded-xl font-label-bold disabled:opacity-50"
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
           class="fixed inset-0 z-[100] bg-black/40 flex items-center justify-center p-6"
           @click="showDeleteConfirm = false">
        <div class="bg-surface-container-lowest rounded-2xl p-6 max-w-sm w-full shadow-xl" @click.stop>
          <h3 class="font-headline-md text-headline-md mb-2">确认删除</h3>
          <p class="text-on-surface-variant font-body-md mb-6">
            确定删除"{{ deletingAssetName }}"吗？此操作不可撤销。
          </p>
          <div class="flex gap-3">
            <button class="flex-1 py-3 bg-surface-container-high text-on-surface rounded-xl font-label-bold"
                    @click="showDeleteConfirm = false">取消</button>
            <button class="flex-1 py-3 bg-error text-white rounded-xl font-label-bold"
                    @click="doDelete">删除</button>
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
        <div class="relative bg-surface-container-lowest rounded-xl shadow-xl w-full max-w-sm max-h-[80vh] overflow-y-auto z-10 p-md">
          <!-- Header -->
          <div class="flex items-center justify-between mb-4">
            <h3 class="font-label-bold text-label-bold text-on-surface">资产变动明细</h3>
            <button class="w-8 h-8 flex items-center justify-center rounded-full hover:bg-surface-container text-on-surface-variant"
                    @click="showValueDetail = false">
              <span class="material-symbols-outlined text-[18px]">close</span>
            </button>
          </div>

          <!-- Pill tabs -->
          <div class="flex gap-2 mb-4">
            <button v-for="tab in (['week', 'month', 'year'] as const)" :key="tab"
                    class="flex-1 py-2 rounded-lg text-caption font-caption transition-all"
                    :class="detailTab === tab
                      ? ((valueChange?.periods[tab]?.change ?? 0) >= 0
                        ? 'bg-primary-fixed text-primary'
                        : 'bg-error-container text-on-error-container')
                      : 'bg-surface-container text-on-surface-variant'"
                    @click="detailTab = tab">
              {{ tab === 'week' ? '本周' : tab === 'month' ? '本月' : '本年' }}
            </button>
          </div>

          <template v-if="valueChange?.periods[detailTab]">
            <!-- Summary -->
            <div class="mb-4 pb-3 border-b border-outline-variant/40">
              <span class="font-caption text-caption text-on-surface-variant">合计变化</span>
              <div class="flex items-center gap-3 mt-1">
                <span class="font-headline-lg text-headline-lg" :class="(valueChange.periods[detailTab].change ?? 0) >= 0 ? 'text-primary' : 'text-error'">
                  {{ changeDisplay(valueChange.periods[detailTab].change) }}
                </span>
                <span class="inline-block px-2 py-0.5 rounded-md text-caption font-caption"
                      :class="(valueChange.periods[detailTab].change ?? 0) >= 0
                        ? 'bg-primary-fixed text-primary'
                        : 'bg-error-container text-on-error-container'">
                  {{ formatPercent(valueChange.periods[detailTab].percent) }}
                </span>
              </div>
            </div>

            <!-- Holding list -->
            <div v-if="valueChange.periods[detailTab].details.length" class="space-y-1">
              <div v-for="d in valueChange.periods[detailTab].details" :key="d.holdingId"
                   class="flex items-center justify-between py-2 px-2 rounded-lg cursor-pointer hover:bg-surface-container transition-colors"
                   @click="router.push(`/holding/${d.holdingId}`)">
                <div class="flex items-center gap-2 min-w-0 flex-1">
                  <span class="material-symbols-outlined text-[16px] shrink-0"
                        :class="(d.change ?? 0) >= 0 ? 'text-primary' : 'text-error'">
                    {{ (d.change ?? 0) >= 0 ? 'trending_up' : 'trending_down' }}
                  </span>
                  <span class="font-caption text-caption text-on-surface truncate">{{ d.name }}</span>
                </div>
                <div class="flex items-center gap-3 shrink-0 ml-2">
                  <span class="font-caption text-caption" :class="(d.change ?? 0) >= 0 ? 'text-primary' : 'text-error'">
                    {{ changeDisplay(d.change) }}
                  </span>
                  <span class="text-caption text-on-surface-variant/60 w-12 text-right">
                    {{ formatPercent(d.percent) }}
                  </span>
                </div>
              </div>
            </div>
            <div v-else class="text-caption font-caption text-on-surface-variant text-center py-6">
              暂无变动数据
            </div>
          </template>

          <!-- Close -->
          <div class="text-center mt-4">
            <button class="font-caption text-caption text-on-surface-variant underline underline-offset-2 hover:text-on-surface transition-colors"
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
        <div class="relative bg-surface-container-lowest rounded-xl shadow-xl w-56 z-10 p-4">
          <p class="font-caption text-caption text-on-surface-variant text-center mb-3">选择资产类型</p>
          <button class="w-full flex items-center gap-3 py-2.5 px-3 rounded-lg hover:bg-surface-container transition-colors"
                  @click="showAddTypePicker = false; openAddSheet('cash')">
            <span class="w-3 h-3 rounded-full shrink-0" style="background-color:#34A853"></span>
            <span class="font-caption text-caption text-on-surface">现金</span>
          </button>
          <button class="w-full flex items-center gap-3 py-2.5 px-3 rounded-lg hover:bg-surface-container transition-colors mt-1"
                  @click="showAddTypePicker = false; openAddSheet('crypto')">
            <span class="w-3 h-3 rounded-full shrink-0" style="background-color:#F59E0B"></span>
            <span class="font-caption text-caption text-on-surface">比特币</span>
          </button>
          <div class="text-center mt-2 pt-2 border-t border-outline-variant/20">
            <button class="font-caption text-caption text-on-surface-variant"
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
