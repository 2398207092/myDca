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
  <div class="min-h-screen bg-background font-['Work_Sans']">
    <!-- Header -->
    <header class="bg-white border-b border-surface-variant flex items-center justify-between w-full px-container-padding h-14 sticky top-0 z-50">
      <div class="flex items-center gap-4">
        <button @click="goBack" class="active:scale-95 duration-200 hover:bg-surface-container-high transition-colors p-1 rounded-full">
          <span class="material-symbols-outlined text-primary">arrow_back</span>
        </button>
        <h1 class="text-[20px] font-bold text-primary font-['Plus_Jakarta_Sans']">添加标的</h1>
      </div>
      <button @click="router.push('/')" class="w-10 h-10 flex items-center justify-center active:opacity-80 transition-opacity">
        <span class="material-symbols-outlined text-on-surface-variant">home</span>
      </button>
    </header>

    <!-- Main Content -->
    <main class="max-w-[600px] mx-auto p-gutter space-y-md pb-40">

      <!-- Step 1: 选择标的 -->
      <section class="bg-white rounded-xl p-md shadow-[0_4px_12px_rgba(0,0,0,0.05)]">
        <div class="flex items-center gap-3 mb-md">
          <span class="flex items-center justify-center w-6 h-6 rounded-full bg-[#1b1c1c] text-white text-[12px] font-bold">1</span>
          <h2 class="text-[20px] font-semibold text-[#1b1c1c] font-['Plus_Jakarta_Sans']">选择标的</h2>
        </div>

        <!-- Search input -->
        <div class="border-t border-[#e3e2e2] pt-md">
          <div class="relative">
            <div class="flex items-center border border-[#dfc0b5] rounded-lg px-3 py-2 bg-white focus-within:border-[#ff7a45] transition-colors">
              <span class="material-symbols-outlined text-[#8b7168] mr-2">search</span>
              <input
                v-model="keyword"
                type="text"
                placeholder="输入基金名称或代码搜索..."
                class="flex-1 bg-transparent border-none outline-none text-sm placeholder:text-[#c6c6c7]"
                @click="handleSearchClick"
              />
              <span v-if="isSearching" class="material-symbols-outlined text-[#8b7168] animate-spin">refresh</span>
            </div>
          </div>

          <!-- Search results dropdown -->
          <div v-if="showResults && searchResults.length > 0" class="mt-2 border border-[#e3e2e2] rounded-lg max-h-48 overflow-y-auto">
            <div
              v-for="item in searchResults"
              :key="item.code"
              @click="selectHolding(item)"
              class="flex items-center justify-between px-3 py-3 border-b border-[#e3e2e2] last:border-b-0 hover:bg-[#f4f3f3] cursor-pointer active:scale-[0.99] transition-all"
            >
              <div>
                <div class="text-sm font-medium text-[#1b1c1c]">{{ item.name }}</div>
                <div class="text-xs text-[#8b7168]">{{ item.code }} · {{ item.type }}</div>
              </div>
              <span class="material-symbols-outlined text-[#8b7168]">chevron_right</span>
            </div>
          </div>

          <!-- No results -->
          <div v-if="showResults && !isSearching && searchResults.length === 0 && keyword.trim().length >= 1" class="mt-2 text-center py-4 text-sm text-[#8b7168]">
            未找到匹配的标的
          </div>

          <!-- Selected holding display -->
          <div v-if="selectedHolding" class="mt-2 flex items-center justify-between p-3 bg-[#ff7a45]/5 rounded-lg border border-[#ff7a45]/20">
            <div>
              <div class="text-sm font-medium text-[#1b1c1c]">{{ selectedHolding.name }}</div>
              <div class="text-xs text-[#8b7168]">{{ selectedHolding.code }} · {{ selectedHolding.type }}</div>
            </div>
            <span class="material-symbols-outlined text-[#ff7a45]" style="font-variation-settings:'FILL' 1">check_circle</span>
          </div>
        </div>
      </section>

      <!-- Step 2: 持仓信息 -->
      <section
        :class="['bg-white rounded-xl p-md shadow-[0_4px_12px_rgba(0,0,0,0.05)]', !step2Active ? 'opacity-40 pointer-events-none' : '']"
      >
        <div class="flex items-center justify-between mb-md">
          <div class="flex items-center gap-3">
            <span :class="['flex items-center justify-center w-6 h-6 rounded-full text-white text-[12px] font-bold', step2Active ? 'bg-[#1b1c1c]' : 'bg-[#a2a1a1]']">2</span>
            <h2 :class="['text-[20px] font-semibold font-[\'Plus_Jakarta_Sans\']', step2Active ? 'text-[#1b1c1c]' : 'text-[#5f5e5e]']">持仓信息</h2>
          </div>
          <span class="text-[12px] text-[#5f5e5e]" v-if="!step2Active">选股后自动展开</span>
        </div>

        <div class="grid grid-cols-2 gap-md mb-md">
          <div class="space-y-1">
            <label class="text-[12px] font-medium text-[#ba1a1a]">*持仓数量</label>
            <input v-model.number="shares" type="number" placeholder="股数/份数" :disabled="!step2Active" class="w-full bg-transparent border-b border-[#dfc0b5] py-2 focus:outline-none focus:border-[#ff7a45] placeholder:text-[#c6c6c7] text-sm" />
          </div>
          <div class="space-y-1">
            <label class="text-[12px] font-medium text-[#ba1a1a]">*买入日期</label>
            <div class="flex items-center border-b border-[#dfc0b5] py-2">
              <input v-model="buyDate" type="date" :disabled="!step2Active" class="flex-1 bg-transparent border-none outline-none text-sm text-[#5d5f5f]" />
            </div>
          </div>
        </div>

        <div class="mb-md">
          <label class="text-[12px] font-medium text-[#5f5e5e] block mb-1">交易费用</label>
          <input v-model.number="fee" type="number" placeholder="交易费用（可选）" :disabled="!step2Active" class="w-full bg-transparent border-b border-[#dfc0b5] py-2 focus:outline-none focus:border-[#ff7a45] placeholder:text-[#c6c6c7] text-sm" />
        </div>

        <div class="space-y-3">
          <!-- *当前成本 [selector] ? 同一行 -->
          <div class="flex items-center gap-2">
            <label class="text-[12px] font-medium text-[#ba1a1a] whitespace-nowrap">*当前成本</label>
            <div class="flex-1 flex bg-[#efeded] rounded-lg p-1 max-w-[260px]">
              <button @click="costAlgorithm='diluted'" :class="costAlgorithm==='diluted' ? 'bg-white text-[#1b1c1c] shadow-sm' : 'text-[#5f5e5e]'" class="flex-1 py-1 rounded font-semibold text-xs transition-all">分红摊薄</button>
              <button @click="costAlgorithm='diluted_only'" :class="costAlgorithm==='diluted_only' ? 'bg-white text-[#1b1c1c] shadow-sm' : 'text-[#5f5e5e]'" class="flex-1 py-1 rounded font-semibold text-xs transition-all">摊薄成本</button>
              <button @click="costAlgorithm='weighted_avg'" :class="costAlgorithm==='weighted_avg' ? 'bg-white text-[#1b1c1c] shadow-sm' : 'text-[#5f5e5e]'" class="flex-1 py-1 rounded font-semibold text-xs transition-all">加权平均</button>
            </div>
            <span class="material-symbols-outlined text-[#8b7168] text-[18px]">help</span>
          </div>
          <div>
            <input v-model.number="costInput" type="number" placeholder="可输入正数、零、或者负数（人民币）" :disabled="!step2Active" class="w-full bg-transparent border-b border-[#dfc0b5] py-2 focus:outline-none focus:border-[#ff7a45] placeholder:text-[#c6c6c7] text-sm" />
          </div>
          <div class="flex items-center gap-2 pt-1">
            <div
              @click="isNegativeCost = !isNegativeCost"
              :class="['w-4 h-4 border rounded', isNegativeCost ? 'bg-[#ff7a45] border-[#ff7a45]' : 'border-[#dfc0b5] bg-white']"
            ></div>
            <span class="text-xs text-[#5f5e5e]">标记为负成本（成本已通过卖出全部收回）</span>
          </div>
        </div>
      </section>

      <!-- Step 3: 分红预测口径 -->
      <section
        :class="['bg-white rounded-xl p-md shadow-[0_4px_12px_rgba(0,0,0,0.05)]', !step3Active ? 'opacity-40 pointer-events-none' : '']"
      >
        <div class="flex items-center justify-between mb-md">
          <div class="flex items-center gap-3">
            <span :class="['flex items-center justify-center w-6 h-6 rounded-full text-white text-[12px] font-bold', step3Active ? 'bg-[#1b1c1c]' : 'bg-[#a2a1a1]']">3</span>
            <h2 :class="['text-[20px] font-semibold flex items-center gap-1 font-[\'Plus_Jakarta_Sans\']', step3Active ? 'text-[#1b1c1c]' : 'text-[#5f5e5e]']">
              分红预测口径
              <span class="material-symbols-outlined text-[18px] text-[#8b7168]">help</span>
            </h2>
          </div>
          <span class="text-[12px] text-[#5f5e5e]" v-if="!step3Active">选股后自动展开</span>
        </div>

        <!-- 加载中 -->
        <div v-if="isFetchingDividend" class="text-center py-4 text-sm text-[#8b7168]">
          查询分红数据中...
        </div>

        <!-- 无分红数据：简化提示 -->
        <div v-else-if="dividendInfo && !hasDividendData" class="text-center py-4">
          <span class="material-symbols-outlined text-[#8b7168] text-[32px]">info</span>
          <p class="text-sm text-[#5f5e5e] mt-2">该基金暂无分红记录，已跳过分红预测</p>
        </div>

        <!-- 有分红数据：完整预测 UI -->
        <template v-else-if="hasDividendData">
          <div class="flex bg-[#efeded] rounded-lg p-1 mb-md">
            <button @click="forecastMethod='ex_date'" :class="forecastMethod==='ex_date' ? 'bg-white text-[#1b1c1c] shadow-sm' : 'text-[#5f5e5e]'" class="flex-1 py-2 rounded font-semibold text-sm transition-all">按除权日</button>
            <button @click="forecastMethod='report_period'" :class="forecastMethod==='report_period' ? 'bg-white text-[#1b1c1c] shadow-sm' : 'text-[#5f5e5e]'" class="flex-1 py-2 rounded font-semibold text-sm transition-all">按报告期</button>
            <button @click="forecastMethod='custom'" :class="forecastMethod==='custom' ? 'bg-white text-[#1b1c1c] shadow-sm' : 'text-[#5f5e5e]'" class="flex-1 py-2 rounded font-semibold text-sm transition-all">自定义</button>
          </div>

          <div class="grid grid-cols-3 gap-2 mb-md">
            <button @click="forecastYears='1y'" :class="forecastYears==='1y' ? 'border-[#ff7a45] bg-[#ff7a45]/10 text-[#672000] font-semibold' : 'border-[#dfc0b5] text-[#5f5e5e]'" class="py-2 border rounded-lg text-sm transition-all">近1年</button>
            <button @click="forecastYears='3y'" :class="forecastYears==='3y' ? 'border-[#ff7a45] bg-[#ff7a45]/10 text-[#672000] font-semibold' : 'border-[#dfc0b5] text-[#5f5e5e]'" class="py-2 border rounded-lg text-sm transition-all">近3年</button>
            <button @click="forecastYears='5y'" :class="forecastYears==='5y' ? 'border-[#ff7a45] bg-[#ff7a45]/10 text-[#672000] font-semibold' : 'border-[#dfc0b5] text-[#5f5e5e]'" class="py-2 border rounded-lg text-sm transition-all">近5年</button>
          </div>

          <div class="flex items-center justify-between border-t border-[#e3e2e2] pt-md">
            <span class="text-[12px] text-[#5f5e5e]">
              年均每份分红
              <span v-if="dividendInfo" class="text-[#8b7168]">（{{ dividendInfo.unitText }}）</span>
            </span>
            <span class="text-[20px] font-semibold text-[#5f5e5e]">
              ¥{{ dividendInfo!.annualDividendPerShare.toFixed(4) }}
            </span>
          </div>

          <div class="mt-md p-sm bg-[#ff7a45]/5 rounded flex gap-2 items-start">
            <span class="material-symbols-outlined text-[#a73a05] text-[18px] mt-0.5" style="font-variation-settings:'FILL' 1">info</span>
            <p class="text-xs text-[#a73a05]">
              取最近{{ { '1y': '1', '3y': '3', '5y': '5' }[forecastYears] || '3' }}年{{ forecastMethod === 'ex_date' ? '内除权除息日对应的分红记录' : '的报告期净值增长' }}，计算年均{{ dividendInfo?.unitText || '每份' }}收益
            </p>
          </div>
        </template>

        <!-- 初始状态（未查询） -->
        <div v-else class="text-center py-4 text-sm text-[#8b7168]">
          选择标的后将自动查询分红数据
        </div>
      </section>

      <!-- Bottom Button (in flow) -->
      <div class="pt-md">
        <button
          @click="handleSubmit"
          :disabled="!canSubmit || isSubmitting"
          :class="[canSubmit ? 'bg-[#ff7a45] text-white shadow-md' : 'bg-[#efeded] text-[#5f5e5e] cursor-not-allowed', 'w-full h-14 rounded-full font-bold text-lg transition-all active:scale-[0.98]']"
        >
          {{ isSubmitting ? '添加中...' : '确认添加' }}
        </button>
        <p v-if="error" class="text-center text-[#ba1a1a] text-xs mt-2">{{ error }}</p>
      </div>
    </main>
  </div>
</template>
