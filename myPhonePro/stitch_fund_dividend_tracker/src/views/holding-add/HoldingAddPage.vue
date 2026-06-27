<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { searchHoldings, createHolding, getDividendInfo } from '@/api/holding'
import type { HoldingSearchResult, DividendInfo } from '@/api/holding'

const router = useRouter()

// Market type filter
const selectedType = ref('A股')

// Step 1: Search
const keyword = ref('')
const searchResults = ref<HoldingSearchResult[]>([])
const isSearching = ref(false)
const selectedHolding = ref<HoldingSearchResult | null>(null)
const showResults = ref(false)

// Step 2: Holding Info
const shares = ref<number | null>(null)
const buyDate = ref(new Date().toISOString().split('T')[0])
const fee = ref(0)
const costAlgorithm = ref<'diluted' | 'diluted_only' | 'weighted_avg'>('diluted')
const costInput = ref<number | null>(null)
const isNegativeCost = ref(false)

// Step 3: Forecast Horizon
const forecastMethod = ref<'ex_date' | 'report_period' | 'custom'>('ex_date')
const forecastYears = ref<'1y' | '3y' | '5y'>('3y')

// Dividend info (fetched after holding selection)
const dividendInfo = ref<DividendInfo | null>(null)
const isFetchingDividend = ref(false)

// Submit state
const isSubmitting = ref(false)
const error = ref('')

// Step availability
const step2Active = computed(() => selectedHolding.value !== null)
const step3Active = computed(() => step2Active.value && (shares.value ?? 0) > 0 && (costInput.value ?? 0) > 0)
const canSubmit = computed(() => step3Active.value)

// 是否有分红数据（用于 Step 3 简化展示）
const hasDividendData = computed(() => {
  return dividendInfo.value != null && dividendInfo.value.annualDividendPerShare > 0
})

// Search logic
let searchTimer: ReturnType<typeof setTimeout> | null = null
let isSelecting = false

watch(keyword, (val) => {
  if (isSelecting) return
  if (selectedHolding.value) {
    selectedHolding.value = null
    searchResults.value = []
  }
  if (searchTimer) clearTimeout(searchTimer)
  if (!val || val.trim().length < 1) {
    searchResults.value = []
    showResults.value = false
    return
  }
  isSearching.value = true
  searchTimer = setTimeout(async () => {
    try {
      const res = await searchHoldings(val.trim())
      searchResults.value = res
      showResults.value = true
    } catch (e) {
      console.error('Search failed:', e)
      searchResults.value = []
    } finally {
      isSearching.value = false
    }
  }, 300)
})

function selectHolding(item: HoldingSearchResult) {
  selectedHolding.value = item
  showResults.value = false
  searchResults.value = []
  isSelecting = true
  keyword.value = item.name
  nextTick(() => { isSelecting = false })

  // Fetch dividend info with current method/horizon
  fetchDividendInfo(item.code, item.type, forecastMethod.value, forecastYears.value)
}

async function fetchDividendInfo(code: string, type: string, method?: string, horizon?: string) {
  isFetchingDividend.value = true
  try {
    const info = await getDividendInfo(code, type, method ?? 'ex_date', horizon ?? '3y')
    dividendInfo.value = info
  } catch (e) {
    console.error('获取分红信息失败:', e)
    dividendInfo.value = null
  } finally {
    isFetchingDividend.value = false
  }
}

// 当分红方法或年限变化时，如果有选中标的则重新获取分红数据
watch(forecastMethod, (newMethod) => {
  if (selectedHolding.value) {
    if (newMethod === 'custom') {
      // 自定义模式，清除 API 数据
      dividendInfo.value = null
    } else {
      fetchDividendInfo(selectedHolding.value.code, selectedHolding.value.type, newMethod, forecastYears.value)
    }
  }
})

watch(forecastYears, (newYears) => {
  if (selectedHolding.value && forecastMethod.value !== 'custom') {
    fetchDividendInfo(selectedHolding.value.code, selectedHolding.value.type, forecastMethod.value, newYears)
  }
})

function clearSelection() {
  selectedHolding.value = null
  keyword.value = ''
  searchResults.value = []
  showResults.value = false
  dividendInfo.value = null
}

function handleSearchClick() {
  if (selectedHolding.value) {
    clearSelection()
  }
}

function goBack() {
  router.push('/')
}

async function handleSubmit() {
  if (!canSubmit.value) return
  isSubmitting.value = true
  error.value = ''
  try {
    await createHolding({
      name: selectedHolding.value!.name,
      code: selectedHolding.value!.code,
      type: selectedHolding.value!.type,
      costAlgorithm: costAlgorithm.value,
      shares: shares.value!,
      cost: costInput.value!,
    })
    router.push('/')
  } catch (e: any) {
    error.value = e.message || '添加失败'
  } finally {
    isSubmitting.value = false
  }
}

// Estimated impact calculation
const estimatedAnnualDividend = computed(() => {
  if (!shares.value || !costInput.value) return '--'
  // Rough estimate based on 3% dividend rate
  const estDividend = costInput.value * 0.03
  return estDividend.toFixed(2)
})
</script>

<template>
  <div class="min-h-screen bg-page-bg flex flex-col">

    <!-- Header — 统一 -->
    <header class="flex items-center justify-between px-gutter h-14 sticky top-0 z-50 bg-card-bg border-b border-border-light/40">
      <button @click="goBack" class="w-10 h-10 flex items-center justify-center -ml-2 active:opacity-80">
        <span class="material-symbols-outlined text-text-secondary">arrow_back</span>
      </button>
      <div class="flex-1 text-center">
        <h1 class="font-body text-md font-medium text-text-primary">添加标的</h1>
      </div>
      <button @click="router.push('/')" class="w-10 h-10 flex items-center justify-center active:opacity-80 transition-opacity">
        <span class="material-symbols-outlined text-text-secondary">home</span>
      </button>
    </header>

    <!-- Main Content -->
    <main class="flex-1 px-gutter pb-32 overflow-y-auto space-y-md">

      <!-- ==================== Step 1: 选择标的 ==================== -->
      <section class="mt-md bg-card-bg rounded-xl p-lg card-shadow border border-border-light/40">
        <!-- Step number + title -->
        <div class="flex items-center gap-3 mb-lg">
          <span class="flex items-center justify-center w-7 h-7 rounded-full bg-brand text-white text-xs font-display font-semibold">1</span>
          <h2 class="font-display text-xl text-text-primary">选择标的</h2>
        </div>

        <!-- Search input -->
        <div class="relative">
          <div class="flex items-center border border-border-light rounded-lg px-3 py-[10px] bg-card-bg focus-within:border-brand transition-colors">
            <span class="material-symbols-outlined text-text-tertiary mr-2 text-[18px]">search</span>
            <input
              v-model="keyword"
              type="text"
              placeholder="输入基金名称或代码搜索..."
              class="flex-1 bg-transparent border-none outline-none font-body text-sm text-text-primary placeholder:text-text-tertiary"
              @click="handleSearchClick"
            />
            <span v-if="isSearching" class="material-symbols-outlined text-text-tertiary animate-spin text-[18px]">refresh</span>
          </div>
        </div>

        <!-- Search results dropdown -->
        <div v-if="showResults && searchResults.length > 0" class="mt-md border border-border-light rounded-lg max-h-48 overflow-y-auto">
          <div
            v-for="item in searchResults"
            :key="item.code"
            @click="selectHolding(item)"
            class="flex items-center justify-between px-md py-3 border-b border-border-light last:border-b-0 hover:bg-card-alt cursor-pointer active:scale-[0.99] transition-all"
          >
            <div>
              <div class="font-body text-sm font-medium text-text-primary">{{ item.name }}</div>
              <div class="font-body text-xs text-text-tertiary">{{ item.code }} · {{ item.type }}</div>
            </div>
            <span class="material-symbols-outlined text-text-tertiary text-[18px]">chevron_right</span>
          </div>
        </div>

        <!-- No results -->
        <div v-if="showResults && !isSearching && searchResults.length === 0 && keyword.trim().length >= 1" class="mt-md text-center py-lg">
          <span class="text-2xl block mb-1">🔍</span>
          <p class="font-body text-sm text-text-tertiary">未找到匹配的标的</p>
        </div>

        <!-- Selected holding display -->
        <div v-if="selectedHolding" class="mt-md flex items-center justify-between p-md bg-brand-light/60 rounded-lg border border-brand/20">
          <div>
            <div class="font-body text-sm font-medium text-text-primary">{{ selectedHolding.name }}</div>
            <div class="font-body text-xs text-text-tertiary">{{ selectedHolding.code }} · {{ selectedHolding.type }}</div>
          </div>
          <span class="material-symbols-outlined text-brand" style="font-variation-settings:'FILL' 1">check_circle</span>
        </div>
      </section>

      <!-- ==================== Step 2: 持仓信息 ==================== -->
      <section
        :class="['bg-card-bg rounded-xl p-lg card-shadow border border-border-light/40', !step2Active ? 'opacity-40 pointer-events-none' : '']"
      >
        <div class="flex items-center justify-between mb-lg">
          <div class="flex items-center gap-3">
            <span :class="['flex items-center justify-center w-7 h-7 rounded-full text-white text-xs font-display font-semibold', step2Active ? 'bg-brand' : 'bg-text-tertiary']">2</span>
            <h2 :class="['font-display text-xl', step2Active ? 'text-text-primary' : 'text-text-secondary']">持仓信息</h2>
          </div>
          <span class="font-body text-xs text-text-tertiary" v-if="!step2Active">选股后自动展开</span>
        </div>

        <div class="grid grid-cols-2 gap-lg mb-lg">
          <div class="space-y-1">
            <label class="font-body text-xs font-medium text-error">*持仓数量</label>
            <input v-model.number="shares" type="number" placeholder="股数/份数" :disabled="!step2Active" class="w-full bg-transparent border-b border-border-light py-2 font-body text-sm text-text-primary placeholder:text-text-tertiary outline-none focus:border-brand transition-colors" />
          </div>
          <div class="space-y-1">
            <label class="font-body text-xs font-medium text-error">*买入日期</label>
            <div class="flex items-center border-b border-border-light py-2">
              <input v-model="buyDate" type="date" :disabled="!step2Active" class="flex-1 bg-transparent border-none outline-none font-body text-sm text-text-primary" />
            </div>
          </div>
        </div>

        <div class="mb-lg">
          <label class="font-body text-xs font-medium text-text-secondary block mb-1">交易费用</label>
          <input v-model.number="fee" type="number" placeholder="交易费用（可选）" :disabled="!step2Active" class="w-full bg-transparent border-b border-border-light py-2 font-body text-sm text-text-primary placeholder:text-text-tertiary outline-none focus:border-brand transition-colors" />
        </div>

        <div class="space-y-lg">
          <!-- 当前成本算法选择器 -->
          <div class="flex items-center gap-2">
            <label class="font-body text-xs font-medium text-error whitespace-nowrap">*当前成本</label>
            <div class="flex-1 flex bg-card-alt rounded-lg p-[3px] max-w-[280px]">
              <button @click="costAlgorithm='diluted'" :class="costAlgorithm==='diluted' ? 'bg-card-bg text-text-primary shadow-sm' : 'text-text-secondary'" class="flex-1 py-[5px] rounded font-body font-medium text-xs transition-all">分红摊薄</button>
              <button @click="costAlgorithm='diluted_only'" :class="costAlgorithm==='diluted_only' ? 'bg-card-bg text-text-primary shadow-sm' : 'text-text-secondary'" class="flex-1 py-[5px] rounded font-body font-medium text-xs transition-all">摊薄成本</button>
              <button @click="costAlgorithm='weighted_avg'" :class="costAlgorithm==='weighted_avg' ? 'bg-card-bg text-text-primary shadow-sm' : 'text-text-secondary'" class="flex-1 py-[5px] rounded font-body font-medium text-xs transition-all">加权平均</button>
            </div>
            <span class="material-symbols-outlined text-text-tertiary text-[18px]">help</span>
          </div>
          <!-- 成本输入 -->
          <div>
            <input v-model.number="costInput" type="number" placeholder="可输入正数、零、或者负数（人民币）" :disabled="!step2Active" class="w-full bg-transparent border-b border-border-light py-2 font-body text-sm text-text-primary placeholder:text-text-tertiary outline-none focus:border-brand transition-colors" />
          </div>
          <!-- 负成本勾选 -->
          <div class="flex items-center gap-2">
            <div
              @click="isNegativeCost = !isNegativeCost"
              :class="['w-4 h-4 rounded border transition-all flex items-center justify-center', isNegativeCost ? 'bg-brand border-brand' : 'border-border-light bg-card-bg']"
            >
              <span v-if="isNegativeCost" class="text-white text-[10px] font-bold">✓</span>
            </div>
            <span class="font-body text-xs text-text-secondary">标记为负成本（成本已通过卖出全部收回）</span>
          </div>
        </div>
      </section>

      <!-- ==================== Step 3: 分红预测口径 ==================== -->
      <section
        :class="['bg-card-bg rounded-xl p-lg card-shadow border border-border-light/40', !step3Active ? 'opacity-40 pointer-events-none' : '']"
      >
        <div class="flex items-center justify-between mb-lg">
          <div class="flex items-center gap-3">
            <span :class="['flex items-center justify-center w-7 h-7 rounded-full text-white text-xs font-display font-semibold', step3Active ? 'bg-brand' : 'bg-text-tertiary']">3</span>
            <h2 :class="['font-display text-xl flex items-center gap-1', step3Active ? 'text-text-primary' : 'text-text-secondary']">
              分红预测口径
              <span class="material-symbols-outlined text-text-tertiary text-[18px]">help</span>
            </h2>
          </div>
          <span class="font-body text-xs text-text-tertiary" v-if="!step3Active">选股后自动展开</span>
        </div>

        <!-- 加载中 -->
        <div v-if="isFetchingDividend" class="text-center py-lg">
          <span class="text-xl block mb-1">🔄</span>
          <p class="font-body text-sm text-text-tertiary">查询分红数据中...</p>
        </div>

        <!-- 无分红数据 -->
        <div v-else-if="dividendInfo && !hasDividendData" class="text-center py-lg">
          <span class="text-2xl block mb-1">📋</span>
          <p class="font-body text-sm text-text-tertiary">该基金暂无分红记录，已跳过分红预测</p>
        </div>

        <!-- 有分红数据：完整预测 UI -->
        <template v-else-if="hasDividendData">
          <!-- 预测方法切换 -->
          <div class="flex bg-card-alt rounded-lg p-[3px] mb-lg">
            <button @click="forecastMethod='ex_date'" :class="forecastMethod==='ex_date' ? 'bg-card-bg text-text-primary shadow-sm' : 'text-text-secondary'" class="flex-1 py-[7px] rounded font-body font-medium text-sm transition-all">按除权日</button>
            <button @click="forecastMethod='report_period'" :class="forecastMethod==='report_period' ? 'bg-card-bg text-text-primary shadow-sm' : 'text-text-secondary'" class="flex-1 py-[7px] rounded font-body font-medium text-sm transition-all">按报告期</button>
            <button @click="forecastMethod='custom'" :class="forecastMethod==='custom' ? 'bg-card-bg text-text-primary shadow-sm' : 'text-text-secondary'" class="flex-1 py-[7px] rounded font-body font-medium text-sm transition-all">自定义</button>
          </div>

          <!-- 年限选择 -->
          <div class="grid grid-cols-3 gap-2 mb-lg">
            <button @click="forecastYears='1y'" :class="forecastYears==='1y' ? 'border-brand bg-brand-light text-brand font-medium' : 'border-border-light text-text-secondary'" class="py-2 border rounded-lg font-body text-sm transition-all">近1年</button>
            <button @click="forecastYears='3y'" :class="forecastYears==='3y' ? 'border-brand bg-brand-light text-brand font-medium' : 'border-border-light text-text-secondary'" class="py-2 border rounded-lg font-body text-sm transition-all">近3年</button>
            <button @click="forecastYears='5y'" :class="forecastYears==='5y' ? 'border-brand bg-brand-light text-brand font-medium' : 'border-border-light text-text-secondary'" class="py-2 border rounded-lg font-body text-sm transition-all">近5年</button>
          </div>

          <!-- 分红结果 -->
          <div class="flex items-center justify-between border-t border-border-light pt-lg">
            <span class="font-body text-xs text-text-secondary">
              年均每份分红
              <span v-if="dividendInfo" class="text-text-tertiary">（{{ dividendInfo.unitText }}）</span>
            </span>
            <span class="font-display text-xl font-semibold text-text-primary">
              ¥{{ dividendInfo!.annualDividendPerShare.toFixed(4) }}
            </span>
          </div>

          <!-- 提示信息 -->
          <div class="mt-lg p-sm bg-brand-light/60 rounded-lg flex gap-2 items-start">
            <span class="text-brand text-[16px] mt-[2px]">💡</span>
            <p class="font-body text-xs text-text-secondary">
              取最近{{ { '1y': '1', '3y': '3', '5y': '5' }[forecastYears] || '3' }}年{{ forecastMethod === 'ex_date' ? '内除权除息日对应的分红记录' : '的报告期净值增长' }}，计算年均{{ dividendInfo?.unitText || '每份' }}收益
            </p>
          </div>
        </template>

        <!-- 初始状态 -->
        <div v-else class="text-center py-lg">
          <span class="text-2xl block mb-1">📊</span>
          <p class="font-body text-sm text-text-tertiary">选择标的后将自动查询分红数据</p>
        </div>
      </section>

      <!-- ==================== 提交按钮 ==================== -->
      <div class="pt-sm">
        <button
          @click="handleSubmit"
          :disabled="!canSubmit || isSubmitting"
          :class="[canSubmit ? 'bg-brand text-white shadow-card' : 'bg-card-alt text-text-tertiary cursor-not-allowed', 'w-full h-[52px] rounded-xl font-body font-medium text-md transition-all active:scale-[0.98]']"
        >
          {{ isSubmitting ? '添加中...' : '确认添加' }}
        </button>
        <p v-if="error" class="text-center font-body text-xs text-error mt-md">{{ error }}</p>
      </div>
    </main>
  </div>
</template>
