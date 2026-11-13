<script setup lang="ts">
/**
 * 添加标的（Auros 卡片化 · 2026-11-11）
 * 结构：选择标的章卡 → 持仓信息章卡 → 分红预测口径章卡 → 影响预估章卡 → 底部固定主按钮
 * 业务逻辑与原页面逐行一致，只换外壳与呈现；金额/汇率/净值统一走 utils/format。
 */
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { searchHoldings, createHolding, getDividendInfo } from '@/api/holding'
import type { HoldingSearchResult, DividendInfo } from '@/api/holding'
import { listExchangeRates } from '@/api/exchangeRate'
import { getAccounts, type AccountItem } from '@/api/account'
import { formatMoney, formatPrice } from '@/auros-skin/utils/format'
import AurosNavBar from '@/auros-skin/components/auros/AurosNavBar.vue'
import AurosChapter from '@/auros-skin/components/auros/AurosChapter.vue'
import AurosField from '@/auros-skin/components/auros/AurosField.vue'
import AurosRow from '@/auros-skin/components/auros/AurosRow.vue'
import AurosButton from '@/auros-skin/components/auros/AurosButton.vue'

const router = useRouter()

// Step 1: Search
const keyword = ref('')
const searchResults = ref<HoldingSearchResult[]>([])
const isSearching = ref(false)
const selectedHolding = ref<HoldingSearchResult | null>(null)
const showResults = ref(false)

// 本地时区日期串（toISOString 走 UTC，北京时间 0-8 点会错标为昨天，参考 CalendarPage 的正确写法）
function localToday(): string {
  const n = new Date()
  return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')}`
}

// Step 2: Holding Info
const shares = ref<number | null>(null)
const buyDate = ref(localToday())
const buyFeeRate = ref(0.15)
const sellFeeRate = ref(0)
const costAlgorithm = ref<'diluted' | 'diluted_only' | 'weighted_avg'>('diluted')
const costInput = ref<number | null>(null)
const isNegativeCost = ref(false)

// === 加密币：基金搜索接口不覆盖，提供手动入口 ===
const CRYPTO_PRESETS = [
  { code: 'BTC', name: '比特币' },
  { code: 'ETH', name: '以太坊' },
]
const isCryptoSelected = computed(() => selectedHolding.value?.type === 'crypto')
const usdCny = ref<number | null>(null)
const accountsList = ref<AccountItem[]>([])
const selectedAccountId = ref('')

/** 账户按本位币过滤：加密资产只能挂 USDT 账户，与后端校验同一条规则 */
const availableAccounts = computed(() =>
  accountsList.value.filter((a) =>
    isCryptoSelected.value ? a.baseCurrency === 'USDT' : a.baseCurrency !== 'USDT'
  )
)

// 资产必须归属账户：账户列表到达后默认选中第一个匹配项
watch(availableAccounts, (list) => {
  if (!selectedAccountId.value && list.length) selectedAccountId.value = list[0].id
}, { immediate: true })

onMounted(async () => {
  try {
    const rates = await listExchangeRates()
    const usd = rates.find((r) => r.pair === 'USD/CNY')
    usdCny.value = usd?.rate ?? null
  } catch {
    // 拿不到汇率就不折算（不编一个默认值，避免成本算错）
    usdCny.value = null
  }
  try {
    accountsList.value = await getAccounts()
  } catch {
    /* 账户拉取失败不阻断创建 */
  }
})

function addCrypto(preset: { code: string; name: string }) {
  selectedHolding.value = { code: preset.code, name: preset.name, type: 'crypto' } as unknown as HoldingSearchResult
  shares.value = null
  costInput.value = null
  showResults.value = false
}

/** 加密币按 USDT 输入成本，提交前折算成人民币（后端只认 CNY；没有汇率则不折算并在提交处拦截） */
const costInCny = computed(() => {
  if (costInput.value == null) return null
  if (!isCryptoSelected.value) return costInput.value
  if (!usdCny.value) return null
  return Math.round(costInput.value * usdCny.value * 100) / 100
})

// Step 3: Forecast Horizon
const forecastMethod = ref<'ex_date' | 'report_period' | 'custom'>('ex_date')
const forecastYears = ref<'1y' | '3y' | '5y'>('3y')

// Dividend info (fetched after holding selection)
const dividendInfo = ref<DividendInfo | null>(null)
const isFetchingDividend = ref(false)

// Submit state
const isSubmitting = ref(false)
const error = ref('')

// 算法说明气泡
const showAlgorithmHelp = ref(false)
function handleClickOutside(e: MouseEvent) {
  if (showAlgorithmHelp.value) showAlgorithmHelp.value = false
}
onMounted(() => document.addEventListener('click', handleClickOutside))
onUnmounted(() => document.removeEventListener('click', handleClickOutside))

// Step availability
const step2Active = computed(() => selectedHolding.value !== null)
const step3Active = computed(() => step2Active.value && (shares.value ?? 0) > 0 && (costInput.value ?? 0) > 0)
const canSubmit = computed(() => {
  if (!step3Active.value) return false
  // 加密币拿不到汇率就无法折算成本，必须拦住（否则 cost 为空，后端会 400）
  if (isCryptoSelected.value && costInCny.value == null) return false
  return true
})

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
      cost: costInCny.value!,
      buyFeeRate: buyFeeRate.value,
      sellFeeRate: sellFeeRate.value,
      quoteCurrency: isCryptoSelected.value ? 'USDT' : undefined,
      costEstimated: isCryptoSelected.value ? true : undefined,
      accountId: selectedAccountId.value || undefined,
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
  <div class="page">
    <AurosNavBar title="添加标的" sub="ADD HOLDING">
      <template #right>
        <button type="button" class="nav-text" @click="goBack">取消</button>
      </template>
    </AurosNavBar>

    <main class="body">
      <!-- 章 01 选择标的 -->
      <AurosChapter label="SEARCH" title="选择标的" :gap="false">
        <div class="form">
          <label class="field">
            <span class="field__box">
              <svg class="field__ico" viewBox="0 0 16 16" width="14" height="14" fill="none" aria-hidden="true">
                <circle cx="7.2" cy="7.2" r="4.6" stroke="currentColor" stroke-width="1.2" />
                <path d="m10.6 10.6 2.6 2.6" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" />
              </svg>
              <input
                v-model="keyword"
                class="field__input"
                type="text"
                placeholder="搜索基金，或用下方入口添加加密币"
                @click="handleSearchClick"
              />
              <svg
                v-if="isSearching"
                class="field__spin"
                viewBox="0 0 16 16"
                width="14"
                height="14"
                fill="none"
                aria-hidden="true"
              >
                <path d="M8 2.2a5.8 5.8 0 1 1-5.8 5.8" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" />
              </svg>
            </span>
          </label>

          <!-- 加密币手动入口（基金搜索覆盖不到） -->
          <div v-if="!selectedHolding" class="quick">
            <span class="quick__label">或手动添加</span>
            <div class="quick__btns">
              <AurosButton v-for="p in CRYPTO_PRESETS" :key="p.code" variant="ghost" size="sm" @click="addCrypto(p)">
                {{ p.name }} {{ p.code }}
              </AurosButton>
            </div>
          </div>

          <!-- 已选中的加密币 -->
          <AurosRow
            v-if="selectedHolding && isCryptoSelected"
            :label="`${selectedHolding.name}（${selectedHolding.code}）`"
            sub="加密币 · 手动录入"
            mark="pink"
          >
            <template #default>
              <span class="picked__tag">USDT 计价</span>
              <button type="button" class="picked__change" @click="clearSelection()">更换</button>
            </template>
          </AurosRow>

          <!-- 已选中的基金 / ETF -->
          <AurosRow
            v-else-if="selectedHolding"
            :label="selectedHolding.name"
            :sub="`${selectedHolding.code} · ${selectedHolding.type}`"
            mark="lit"
          >
            <template #default>
              <svg class="picked__check" viewBox="0 0 16 16" width="14" height="14" fill="none" aria-hidden="true">
                <path d="m3.4 8.4 3 3 6.2-6.8" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </template>
          </AurosRow>

          <!-- 搜索结果：卡内行，不套卡 -->
          <div v-if="showResults && searchResults.length > 0" class="results">
            <AurosRow
              v-for="item in searchResults"
              :key="item.code"
              :label="item.name"
              :sub="`${item.code} · ${item.type}`"
              clickable
              chevron
              @click="selectHolding(item)"
            />
          </div>

          <!-- 无结果 -->
          <p v-if="showResults && !isSearching && searchResults.length === 0 && keyword.trim().length >= 1" class="hint">
            <i class="hint__dot" aria-hidden="true"></i>未找到匹配的标的
          </p>
        </div>
      </AurosChapter>

      <!-- 章 02 持仓信息 -->
      <AurosChapter
        label="HOLDING"
        title="持仓信息"
        :meta="step2Active ? '' : '选股后自动展开'"
        :class="{ 'is-locked': !step2Active }"
      >
        <div class="form">
          <div class="grid2">
            <AurosField
              :model-value="shares"
              label="*持仓数量"
              type="number"
              inputmode="decimal"
              :placeholder="isCryptoSelected ? '数量（枚，最多 8 位小数）' : '股数/份数'"
              :disabled="!step2Active"
              mono
              @update:model-value="(v) => (shares = v === '' || Number.isNaN(Number(v)) ? null : Number(v))"
            />
            <label class="field">
              <span class="field__label">*买入日期</span>
              <span class="field__box">
                <input v-model="buyDate" class="field__input is-date" type="date" :disabled="!step2Active" />
              </span>
            </label>
          </div>

          <!-- 归属账户（可选）：加密资产只能挂 USDT 本位币账户 -->
          <div v-if="accountsList.length > 0" class="accts">
            <span class="field__label">归属账户</span>
            <div class="accts__row">
              <button
                v-for="acc in availableAccounts"
                :key="acc.id"
                type="button"
                class="chip"
                :class="{ 'is-on': selectedAccountId === acc.id }"
                :disabled="!step2Active"
                @click="selectedAccountId = selectedAccountId === acc.id ? '' : acc.id"
              >
                {{ acc.name }}{{ acc.isDefault ? ' ·默认' : '' }}
              </button>
            </div>
            <p class="hint">
              <i class="hint__dot" aria-hidden="true"></i>
              {{ isCryptoSelected ? '只显示 USDT 本位币账户（如交易所）' : '人民币资产挂在 CNY 本位币账户下' }}；资产必须归属账户，否则现金账会失真
            </p>
          </div>

          <div class="grid2">
            <AurosField
              :model-value="buyFeeRate"
              label="买入费率 (%)"
              type="number"
              inputmode="decimal"
              :disabled="!step2Active"
              mono
              @update:model-value="(v) => (buyFeeRate = v === '' || Number.isNaN(Number(v)) ? 0 : Number(v))"
            />
            <AurosField
              :model-value="sellFeeRate"
              label="卖出费率 (%)"
              type="number"
              inputmode="decimal"
              :disabled="!step2Active"
              mono
              @update:model-value="(v) => (sellFeeRate = v === '' || Number.isNaN(Number(v)) ? 0 : Number(v))"
            />
          </div>

          <!-- 成本口径 + 成本 -->
          <div class="cost">
            <div class="cost__head">
              <span class="field__label">*当前成本</span>
              <span v-if="isCryptoSelected" class="cost__note">加密币无分红，不需要选成本算法</span>
              <button
                v-if="!isCryptoSelected"
                type="button"
                class="ico"
                aria-label="成本算法说明"
                @click.stop="showAlgorithmHelp = !showAlgorithmHelp"
              >
                <svg viewBox="0 0 16 16" width="14" height="14" fill="none" aria-hidden="true">
                  <circle cx="8" cy="8" r="6.2" stroke="currentColor" stroke-width="1.2" />
                  <path d="M6.3 6.2a1.75 1.75 0 1 1 2.5 1.58c-.5.25-.8.62-.8 1.12v.34" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" />
                  <circle cx="8" cy="11.5" r="0.7" fill="currentColor" />
                </svg>
              </button>
              <Transition name="fade">
                <div v-if="showAlgorithmHelp && !isCryptoSelected" class="help" @click.stop>
                  <div class="help__item">
                    <p class="help__k">分红摊薄</p>
                    <p class="help__v">收到的分红会冲减持仓成本，成本越来越低，息率越来越高。适合长期收息视角。</p>
                  </div>
                  <div class="help__item">
                    <p class="help__k">摊薄成本</p>
                    <p class="help__v">只看买入卖出净额，分红不参与成本计算。折中方案，息率相对保守。</p>
                  </div>
                  <div class="help__item">
                    <p class="help__k">加权平均</p>
                    <p class="help__v">只看买入总金额和总份额，不考虑卖出和分红。最保守，成本永远为正。</p>
                  </div>
                  <p class="help__foot">默认使用「分红摊薄」，可在持仓详情页随时切换</p>
                </div>
              </Transition>
            </div>

            <div v-if="!isCryptoSelected" class="seg">
              <button
                type="button"
                class="seg__item"
                :class="{ 'is-active': costAlgorithm === 'diluted' }"
                :disabled="!step2Active"
                @click="costAlgorithm = 'diluted'"
              >分红摊薄</button>
              <button
                type="button"
                class="seg__item"
                :class="{ 'is-active': costAlgorithm === 'diluted_only' }"
                :disabled="!step2Active"
                @click="costAlgorithm = 'diluted_only'"
              >摊薄成本</button>
              <button
                type="button"
                class="seg__item"
                :class="{ 'is-active': costAlgorithm === 'weighted_avg' }"
                :disabled="!step2Active"
                @click="costAlgorithm = 'weighted_avg'"
              >加权平均</button>
            </div>

            <div class="cost__field">
              <AurosField
                :model-value="costInput"
                type="number"
                inputmode="decimal"
                :placeholder="isCryptoSelected ? '每枚成本（USDT）' : '可输入正数、零、或者负数（人民币）'"
                :disabled="!step2Active"
                mono
                @update:model-value="(v) => (costInput = v === '' || Number.isNaN(Number(v)) ? null : Number(v))"
              />
              <p v-if="isCryptoSelected && costInCny && usdCny" class="hint">
                <i class="hint__dot is-on" aria-hidden="true"></i>
                ≈ {{ formatMoney(costInCny) }}/枚（1 USDT ≈ {{ formatPrice(usdCny, 4) }} CNY，保存后按此折算）
              </p>
              <p v-else-if="isCryptoSelected && !usdCny" class="hint is-warn">
                <i class="hint__dot is-warn" aria-hidden="true"></i>
                汇率获取失败，无法折算成本，请稍后重试
              </p>
            </div>

            <!-- 负成本勾选（加密币首次录入不涉及"卖出收回成本"，隐藏） -->
            <div v-if="!isCryptoSelected" class="check">
              <span class="check__box" :class="{ 'is-on': isNegativeCost }" @click="isNegativeCost = !isNegativeCost">
                <svg viewBox="0 0 16 16" width="16" height="16" fill="none" aria-hidden="true">
                  <rect class="check__rect" x="0.6" y="0.6" width="14.8" height="14.8" rx="5" />
                  <path
                    v-if="isNegativeCost"
                    class="check__tick"
                    d="m4 8.3 2.8 2.8 5.2-6"
                  />
                </svg>
              </span>
              <span class="check__label">标记为负成本（成本已通过卖出全部收回）</span>
            </div>
          </div>
        </div>
      </AurosChapter>

      <!-- 章 03 分红预测口径 -->
      <AurosChapter
        v-if="!isCryptoSelected"
        label="DIVIDEND"
        title="分红预测口径"
        :meta="step3Active ? '' : '选股后自动展开'"
        :class="{ 'is-locked': !step3Active }"
      >
        <div class="form">
          <!-- 加载中 -->
          <p v-if="isFetchingDividend" class="hint is-on">
            <i class="hint__dot is-warn" aria-hidden="true"></i>查询分红数据中…
          </p>

          <!-- 无分红数据 -->
          <p v-else-if="dividendInfo && !hasDividendData" class="hint">
            <i class="hint__dot" aria-hidden="true"></i>该基金暂无分红记录，已跳过分红预测
          </p>

          <!-- 有分红数据：完整预测 UI -->
          <template v-else-if="hasDividendData">
            <div class="pred">
              <span class="field__label">预测口径</span>
              <div class="seg">
                <button
                  type="button"
                  class="seg__item"
                  :class="{ 'is-active': forecastMethod === 'ex_date' }"
                  :disabled="!step3Active"
                  @click="forecastMethod = 'ex_date'"
                >按除权日</button>
                <button
                  type="button"
                  class="seg__item"
                  :class="{ 'is-active': forecastMethod === 'report_period' }"
                  :disabled="!step3Active"
                  @click="forecastMethod = 'report_period'"
                >按报告期</button>
                <button
                  type="button"
                  class="seg__item"
                  :class="{ 'is-active': forecastMethod === 'custom' }"
                  :disabled="!step3Active"
                  @click="forecastMethod = 'custom'"
                >自定义</button>
              </div>
            </div>

            <div class="pred">
              <span class="field__label">回溯年限</span>
              <div class="seg seg--sm">
                <button
                  type="button"
                  class="seg__item"
                  :class="{ 'is-active': forecastYears === '1y' }"
                  :disabled="!step3Active"
                  @click="forecastYears = '1y'"
                >近1年</button>
                <button
                  type="button"
                  class="seg__item"
                  :class="{ 'is-active': forecastYears === '3y' }"
                  :disabled="!step3Active"
                  @click="forecastYears = '3y'"
                >近3年</button>
                <button
                  type="button"
                  class="seg__item"
                  :class="{ 'is-active': forecastYears === '5y' }"
                  :disabled="!step3Active"
                  @click="forecastYears = '5y'"
                >近5年</button>
              </div>
            </div>

            <!-- 分红结果 -->
            <AurosRow
              label="年均每份分红"
              :sub="dividendInfo!.unitText"
              :value="formatPrice(dividendInfo!.annualDividendPerShare, 4)"
              mark="lit"
            />

            <!-- 提示信息 -->
            <p class="note">
              <svg viewBox="0 0 16 16" width="13" height="13" fill="none" aria-hidden="true">
                <path d="M8 2.4a3.9 3.9 0 0 0-2.2 7.1c.5.36.8.9.8 1.5v.6h2.8v-.6c0-.6.3-1.14.8-1.5A3.9 3.9 0 0 0 8 2.4Z" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round" />
                <path d="M6.9 13.4h2.2" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" />
              </svg>
              取最近{{ { '1y': '1', '3y': '3', '5y': '5' }[forecastYears] || '3' }}年{{ forecastMethod === 'ex_date' ? '内除权除息日对应的分红记录' : '的报告期净值增长' }}，计算年均{{ dividendInfo?.unitText || '每份' }}收益
            </p>
          </template>

          <!-- 初始状态 -->
          <p v-else class="hint">
            <i class="hint__dot" aria-hidden="true"></i>选择标的后将自动查询分红数据
          </p>
        </div>
      </AurosChapter>

      <!-- 章 04 影响预估 -->
      <AurosChapter v-if="!isCryptoSelected" label="IMPACT" title="影响预估">
        <AurosRow
          label="预估年分红"
          :sub="estimatedAnnualDividend === '--' ? '填写数量与成本后计算' : '按 3% 股息率粗估 · 仅供自用'"
          :value="estimatedAnnualDividend === '--' ? '--' : formatMoney(Number(estimatedAnnualDividend))"
          :mark="estimatedAnnualDividend === '--' ? 'dim' : 'lit'"
        />
        <p v-if="error" class="error">{{ error }}</p>
      </AurosChapter>
    </main>

    <footer class="bar">
      <AurosButton variant="primary" block :disabled="!canSubmit || isSubmitting" @click="handleSubmit">
        {{ isSubmitting ? '添加中...' : '确认添加' }}
      </AurosButton>
    </footer>
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
  padding: 8px 16px calc(env(safe-area-inset-bottom, 0px) + 96px);
}
.nav-text {
  border: 0;
  background: none;
  padding: 0;
  font-family: inherit;
  font-size: 13px;
  color: var(--au-silver);
}

/* 卡内表单：字段之间 14px，不需要发丝线 */
.form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.grid2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.grid2 > * {
  min-width: 0;
}

/* 未就绪的章：整卡变淡但仍是卡，逻辑仍由 step2Active/step3Active 控制 */
.is-locked {
  opacity: 0.6;
  pointer-events: none;
}

/* AurosField 在抬升卡上改用下沉井，避免与卡面同色 */
.form :deep(.field__box) {
  background: rgba(1, 29, 28, 0.55);
}
.form :deep(.field:focus-within .field__box) {
  background: rgba(0, 67, 63, 0.8);
}

/* ---------- 自写字段（搜索 / 日期）：与 AurosField 同一口井 ---------- */
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
  gap: 9px;
  height: 46px;
  padding: 0 12px;
  border-radius: 6px;
  background: rgba(1, 29, 28, 0.55);
  transition: background 200ms var(--au-ease);
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
.field:focus-within .field__box {
  background: rgba(0, 67, 63, 0.8);
}
.field:focus-within .field__box::before {
  background: rgba(203, 255, 252, 0.75);
}
.field__ico {
  flex: none;
  color: var(--au-silver);
}
.field__input {
  flex: 1;
  min-width: 0;
  width: 100%;
  border: 0;
  background: none;
  outline: none;
  color: var(--au-mist);
  font-family: inherit;
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 0.01em;
}
.field__input::placeholder {
  color: rgba(187, 199, 198, 0.45);
  font-weight: 400;
  font-size: 13px;
}
.field__input.is-date {
  color-scheme: dark;
  font-weight: 400;
}
.field__spin {
  flex: none;
  color: var(--au-silver);
  animation: spin 900ms linear infinite;
}

/* ---------- 加密入口 / 已选 ---------- */
.quick {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.quick__btns {
  display: flex;
  gap: 8px;
}
.quick__label {
  font-size: 10px;
  letter-spacing: 0.06em;
  color: rgba(187, 199, 198, 0.6);
}
/* 抬升卡上的幽灵按钮改用下沉井，避免与卡面同色 */
.quick :deep(.btn--ghost) {
  background: rgba(1, 29, 28, 0.55);
}
.picked__tag {
  flex: none;
  font-size: 10px;
  letter-spacing: 0.04em;
  color: var(--au-pink);
}
.picked__check {
  flex: none;
  color: var(--au-lit);
}
.picked__change {
  flex: none;
  border: 0;
  background: none;
  padding: 4px 0;
  font-family: inherit;
  font-size: 12px;
  color: var(--au-silver);
}
.picked__change:active {
  color: var(--au-mist);
}

/* ---------- 搜索结果（卡内行） ---------- */
.results {
  max-height: 246px;
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}
.results::-webkit-scrollbar {
  display: none;
}
.results :deep(.row:first-child) {
  border-top: 1px solid rgba(237, 255, 254, 0.1);
}

/* ---------- 账户 chips ---------- */
.accts {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.accts__row {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  overscroll-behavior-x: contain;
  scrollbar-width: none;
  padding-bottom: 2px;
}
.accts__row::-webkit-scrollbar {
  display: none;
}
.chip {
  flex: none;
  height: 34px;
  padding: 0 14px;
  border: 0;
  border-radius: 6px;
  background: rgba(1, 29, 28, 0.55);
  color: rgba(187, 199, 198, 0.85);
  font-family: inherit;
  font-size: 13px;
  letter-spacing: 0.04em;
  white-space: nowrap;
  transition: background 200ms var(--au-ease), color 200ms var(--au-ease);
}
.chip.is-on {
  background: rgba(203, 255, 252, 0.14);
  color: var(--au-mist);
}
.chip:disabled {
  opacity: 0.4;
}

/* ---------- 成本口径 ---------- */
.cost {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.cost__head {
  display: flex;
  align-items: center;
  gap: 8px;
  position: relative;
}
.cost__note {
  font-size: 10px;
  letter-spacing: 0.04em;
  color: rgba(187, 199, 198, 0.6);
}
.ico {
  margin-left: auto;
  width: 28px;
  height: 28px;
  margin-right: -6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: none;
  color: rgba(187, 199, 198, 0.55);
  transition: color 160ms var(--au-ease);
}
.ico:active {
  color: var(--au-mist);
}
.help {
  position: absolute;
  right: -6px;
  top: 32px;
  z-index: 10;
  width: 264px;
  padding: 14px;
  border-radius: 16px;
  background: linear-gradient(180deg, var(--au-kelp) 0%, var(--au-deep) 70%);
  box-shadow: 0 0 40px rgba(0, 0, 0, 0.5);
}
.help__item + .help__item {
  margin-top: 12px;
}
.help__k {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 500;
  color: var(--au-mist);
}
.help__v {
  margin: 0;
  font-size: 10px;
  line-height: 1.6;
  letter-spacing: 0.02em;
  color: rgba(187, 199, 198, 0.72);
}
.help__foot {
  margin: 12px 0 0;
  padding-top: 10px;
  border-top: 1px solid var(--au-hair);
  font-size: 10px;
  letter-spacing: 0.02em;
  color: rgba(187, 199, 198, 0.6);
}
.cost__field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 200ms var(--au-ease), transform 200ms var(--au-ease);
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* ---------- 分段 ---------- */
.seg {
  display: flex;
  gap: 4px;
  padding: 3px;
  border-radius: 6px;
  background: rgba(1, 29, 28, 0.5);
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
  transition: background 200ms var(--au-ease), color 200ms var(--au-ease);
}
.seg__item.is-active {
  background: rgba(203, 255, 252, 0.14);
  color: var(--au-mist);
}
.seg--sm .seg__item {
  height: 30px;
  font-size: 12px;
}
.pred {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* ---------- 提示（放在所属卡内） ---------- */
.hint {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin: 0;
  font-size: 10px;
  line-height: 1.6;
  letter-spacing: 0.02em;
  color: rgba(187, 199, 198, 0.65);
}
.hint__dot {
  flex: none;
  width: 4px;
  height: 4px;
  margin-top: 6px;
  border-radius: 50%;
  background: rgba(187, 199, 198, 0.4);
}
.hint__dot.is-on {
  background: var(--au-lit);
  box-shadow: 0 0 8px rgba(203, 255, 252, 0.8);
}
.hint__dot.is-warn {
  background: var(--au-pink);
  box-shadow: 0 0 8px rgba(253, 233, 255, 0.8);
  animation: au-breathe 2.4s ease-in-out infinite;
}
.hint.is-warn {
  color: rgba(253, 233, 255, 0.85);
}
.hint.is-on {
  color: var(--au-lit);
}
.note {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin: 0;
  font-size: 10px;
  line-height: 1.6;
  letter-spacing: 0.02em;
  color: rgba(187, 199, 198, 0.72);
}
.note svg {
  flex: none;
  margin-top: 1px;
  color: var(--au-lit);
}

/* ---------- 负成本勾选 ---------- */
.check {
  display: flex;
  align-items: center;
  gap: 9px;
}
.check__box {
  flex: none;
  width: 16px;
  height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: rgba(237, 255, 254, 0.32);
  transition: color 200ms var(--au-ease);
}
.check__rect {
  stroke: currentColor;
  stroke-width: 1.2;
  fill: transparent;
  transition: fill 200ms var(--au-ease), stroke 200ms var(--au-ease);
}
.check__tick {
  stroke: var(--au-ink);
  stroke-width: 1.4;
  fill: none;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.check__box.is-on {
  color: var(--au-lit);
}
.check__box.is-on .check__rect {
  fill: var(--au-lit);
  stroke: var(--au-lit);
}
.check__label {
  font-size: 12px;
  line-height: 1.5;
  letter-spacing: 0.02em;
  color: var(--au-silver);
}

.error {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: rgba(253, 233, 255, 0.9);
}

/* ---------- 底部固定提交条（全页唯一渐变） ---------- */
.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 12px 16px calc(env(safe-area-inset-bottom, 0px) + 16px);
  background: linear-gradient(180deg, rgba(1, 38, 36, 0) 0%, rgba(1, 29, 28, 0.92) 38%);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .hint__dot.is-warn {
    animation: none;
  }
  .field__spin {
    animation: none;
  }
  .field__box,
  .field__box::before,
  .chip,
  .seg__item,
  .picked__change,
  .ico,
  .check__box,
  .fade-enter-active,
  .fade-leave-active {
    transition: none;
  }
}
</style>
