<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import type { PageState, DividendEvent, DividendEventType } from '@/types'
import { listEvents } from '@/api/event'
import { getMonthlyInsight, getMonthlyDetail, getAnnualInsight } from '@/api/insight'
import type { MonthlyInsight, MonthlyDetail, AnnualInsight } from '@/api/insight'
import { refreshAllFundDividends } from '@/api/fund'
import { syncAllEvents } from '@/api/event'
import PageStateComp from '@/components/shared/PageState.vue'

const router = useRouter()
const pageState = ref<PageState>('loading')
const activeTab = ref<'calendar' | 'overview'>('calendar')
const currentYear = ref(new Date().getFullYear())
const currentMonth = ref(new Date().getMonth()) // 0-indexed
const selectedDate = ref(formatDate(new Date()))

function formatDate(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

const events = ref<DividendEvent[]>([])
const monthlyInsightData = ref<MonthlyInsight | null>(null)
const refreshing = ref(false)

async function refreshDividendData() {
  if (refreshing.value) return
  refreshing.value = true
  try {
    await refreshAllFundDividends()
    // 刷新后重新加载当前月份数据和月度洞察
    await loadMonthData()
  } catch {
    // ignore
  } finally {
    refreshing.value = false
  }
}

// 按当前 currentYear/currentMonth 加载事件和月度洞察
async function loadMonthData() {
  const monthStr = `${currentYear.value}-${String(currentMonth.value + 1).padStart(2, '0')}`
  const [eventsData, insightData] = await Promise.all([
    listEvents({ month: monthStr }),
    getMonthlyInsight(currentYear.value, currentMonth.value + 1),
  ])
  events.value = eventsData as DividendEvent[]
  monthlyInsightData.value = insightData
}

onMounted(async () => {
  try {
    // 同步与加载月份数据没有依赖关系，并行执行
    await Promise.all([syncAllEvents(), loadMonthData()])
    pageState.value = 'ready'
  } catch {
    pageState.value = 'error'
  }
})

// === 日历计算 ===
const daysInMonth = computed(() => new Date(currentYear.value, currentMonth.value + 1, 0).getDate())
const firstDayOfWeek = computed(() => new Date(currentYear.value, currentMonth.value, 1).getDay())

interface CalendarCell {
  day: number
  isCurrentMonth: boolean
  date: string
}

const calendarCells = computed<CalendarCell[]>(() => {
  const cells: CalendarCell[] = []
  const totalCells = Math.ceil((firstDayOfWeek.value + daysInMonth.value) / 7) * 7
  for (let i = 0; i < totalCells; i++) {
    const dayNum = i - firstDayOfWeek.value + 1
    const isCurrentMonth = dayNum >= 1 && dayNum <= daysInMonth.value
    const monthStr = String(currentMonth.value + 1).padStart(2, '0')
    cells.push({
      day: isCurrentMonth ? dayNum : 0,
      isCurrentMonth,
      date: isCurrentMonth ? `${currentYear.value}-${monthStr}-${String(dayNum).padStart(2, '0')}` : '',
    })
  }
  return cells
})

// === 事件相关 ===
const eventsMap = computed(() => {
  const map = new Map<string, DividendEvent[]>()
  for (const event of events.value) {
    const existing = map.get(event.date) || []
    existing.push(event)
    map.set(event.date, existing)
  }
  return map
})

function getEventsForDate(date: string): DividendEvent[] {
  return eventsMap.value.get(date) || []
}

const selectedEvents = computed(() => getEventsForDate(selectedDate.value))
const selectedDateDisplay = computed(() => {
  const parts = selectedDate.value.split('-')
  return `${parseInt(parts[1])}月${parseInt(parts[2])}日`
})

// 当月预计派息总额：当月所有 payout 事件的 amount 之和
const monthlyPredictedDividend = computed(() => {
  const prefix = `${currentYear.value}-${String(currentMonth.value + 1).padStart(2, '0')}`
  return events.value
    .filter(e => e.date.startsWith(prefix) && e.type === 'payout')
    .reduce((sum, e) => sum + e.amount, 0)
})

// === 事件类型样式映射（保留语义色，统一风格） ===
const eventTypeStyles: Record<DividendEventType, {
  badgeClass: string
  badgeText: string
  icon: string
  borderClass: string
  iconBgClass: string
}> = {
  registration: {
    badgeClass: 'bg-blue-500/10 text-blue-600',
    badgeText: '登记',
    icon: 'how_to_reg',
    borderClass: 'border-blue-500',
    iconBgClass: 'bg-blue-500/10 text-blue-600',
  },
  ex_dividend: {
    badgeClass: 'bg-error/10 text-error',
    badgeText: '除息',
    icon: 'swap_horiz',
    borderClass: 'border-error',
    iconBgClass: 'bg-error/10 text-error',
  },
  payout: {
    badgeClass: 'bg-brand-light text-brand',
    badgeText: '派息',
    icon: 'payments',
    borderClass: 'border-brand',
    iconBgClass: 'bg-brand-light text-brand',
  },
  announcement: {
    badgeClass: 'bg-card-alt text-text-secondary',
    badgeText: '公告',
    icon: 'description',
    borderClass: 'border-border-light',
    iconBgClass: 'bg-card-alt text-text-secondary',
  },
}

// 日历格子上事件小圆点颜色（announcement 不显示圆点）
const dotColorForType: Record<DividendEventType, string | null> = {
  registration: 'bg-blue-500',
  ex_dividend: 'bg-error',
  payout: 'bg-brand',
  announcement: null,
}

function getDotColor(date: string): string | null {
  const events = getEventsForDate(date)
  for (const event of events) {
    const color = dotColorForType[event.type]
    if (color) return color
  }
  return null
}

function getDotColors(date: string): string[] {
  return getEventsForDate(date)
    .map(e => dotColorForType[e.type])
    .filter((c): c is string => c !== null)
}

// === 分组事件列表 ===
const eventGroupOrder: DividendEventType[] = ['registration', 'ex_dividend', 'payout', 'announcement']

const eventGroupStyles: Record<DividendEventType, { label: string; dotClass: string }> = {
  registration: { label: '权益登记', dotClass: 'bg-blue-500' },
  ex_dividend: { label: '除权除息', dotClass: 'bg-error' },
  payout: { label: '派息发放', dotClass: 'bg-brand' },
  announcement: { label: '公告', dotClass: 'bg-text-tertiary' },
}

const groupedEvents = computed(() => {
  const groups: Record<string, DividendEvent[]> = {}
  for (const type of eventGroupOrder) {
    const items = selectedEvents.value.filter(e => e.type === type)
    if (items.length) groups[type] = items
  }
  return groups
})

// === 月度洞察 ===
const richestSource = computed(() => {
  if (!monthlyInsightData.value) return { name: '--', amount: 0 }
  return {
    name: monthlyInsightData.value.richestSource.holdingName,
    amount: monthlyInsightData.value.richestSource.amount,
  }
})

const monthlyActivity = computed(() => {
  const d = monthlyInsightData.value
  if (!d || !d.monthlyActivity) return { payoutCount: 0, fundCount: 0 }
  return d.monthlyActivity
})

const nextDividend = computed(() => {
  const d = monthlyInsightData.value
  if (!d || !d.nextDividend) return { holdingName: '--', amount: 0, daysRemaining: 0 }
  return d.nextDividend
})

// === 月度分红明细弹窗 ===
const showMonthlyDetail = ref(false)
const monthlyDetailData = ref<MonthlyDetail | null>(null)
const loadingDetail = ref(false)

async function openMonthlyDetail() {
  showMonthlyDetail.value = true
  if (monthlyDetailData.value) return
  loadingDetail.value = true
  try {
    const data = await getMonthlyDetail(currentYear.value, currentMonth.value + 1)
    monthlyDetailData.value = data
  } catch {
    // ignore
  } finally {
    loadingDetail.value = false
  }
}

// === 年度总览
const annualData = ref<AnnualInsight | null>(null)
const annualLoading = ref(false)

const quarters = computed(() => {
  if (!annualData.value) return []
  const labels = ['Q1', 'Q2', 'Q3', 'Q4']
  const q = Array.from({ length: 4 }, (_, i) => {
    const months = annualData.value!.monthlyBars.slice(i * 3, i * 3 + 3)
    const amount = months.reduce((sum, m) => sum + m.amount, 0)
    return { label: labels[i], amount, index: i }
  })
  const maxAmount = Math.max(...q.map(x => x.amount), 1)
  return q.map(x => ({ ...x, percentage: Math.round(x.amount / maxAmount * 100) }))
})

async function loadAnnualData() {
  if (annualData.value) return
  annualLoading.value = true
  try {
    annualData.value = await getAnnualInsight(currentYear.value)
  } catch {
    // ignore
  } finally {
    annualLoading.value = false
  }
}

// === 月份导航
function prevMonth() {
  if (currentMonth.value === 0) {
    currentMonth.value = 11
    currentYear.value--
  } else {
    currentMonth.value--
  }
  // 如果选中日期不在当前月，重置到当月第一天
  const prefix = `${currentYear.value}-${String(currentMonth.value + 1).padStart(2, '0')}`
  if (!selectedDate.value.startsWith(prefix)) {
    selectedDate.value = `${prefix}-01`
  }
  loadMonthData()  // 切换月份后重新加载事件和洞察
}

function nextMonth() {
  if (currentMonth.value === 11) {
    currentMonth.value = 0
    currentYear.value++
  } else {
    currentMonth.value++
  }
  const prefix = `${currentYear.value}-${String(currentMonth.value + 1).padStart(2, '0')}`
  if (!selectedDate.value.startsWith(prefix)) {
    selectedDate.value = `${prefix}-01`
  }
  loadMonthData()  // 切换月份后重新加载事件和洞察
}

// === Tab 切换 ===
const tabIndicatorStyle = computed(() => ({
  transform: activeTab.value === 'calendar' ? 'translateX(0)' : 'translateX(100%)',
}))

// === 日期选中 ===
function selectDate(date: string) {
  selectedDate.value = date
}

// === 导航 ===
function goToHolding(holdingId: string) {
  router.push(`/holding/${holdingId}`)
}
</script>

<template>
  <div class="min-h-screen bg-page-bg flex flex-col">

    <!-- Header — 统一 -->
    <header class="flex items-center justify-between px-gutter h-14 sticky top-0 z-50 bg-card-bg border-b border-border-light/40">
      <button @click="router.push('/')" class="w-10 h-10 flex items-center justify-center -ml-2 active:opacity-80">
        <span class="material-symbols-outlined text-text-secondary">arrow_back</span>
      </button>
      <div class="flex-1 text-center">
        <h1 class="font-body text-md font-medium text-text-primary">分红日历</h1>
      </div>
      <button class="w-10 h-10 flex items-center justify-center active:opacity-80">
        <span class="material-symbols-outlined text-text-secondary">notifications</span>
      </button>
    </header>

    <main class="flex-1 px-gutter pt-sm pb-24 overflow-y-auto space-y-md max-w-[600px] mx-auto w-full">
      <PageStateComp
        v-if="pageState !== 'ready'"
        :state="pageState"
      />

      <template v-if="pageState === 'ready'">
        <!-- ========== Tab Switcher ========== -->
        <div class="bg-card-alt p-[3px] rounded-xl flex relative">
          <button
            class="flex-1 py-2 font-body font-medium text-sm z-10 transition-colors"
            :class="activeTab === 'calendar' ? 'text-text-primary' : 'text-text-secondary'"
            @click="activeTab = 'calendar'"
          >
            日历
          </button>
          <button
            class="flex-1 py-2 font-body font-medium text-sm z-10 transition-colors"
            :class="activeTab === 'overview' ? 'text-text-primary' : 'text-text-secondary'"
            @click="activeTab = 'overview'; loadAnnualData()"
          >
            年度总览
          </button>
          <div
            class="active-tab-indicator absolute left-[3px] top-[3px] bottom-[3px] w-[calc(50%-6px)] bg-card-bg rounded-lg shadow-sm transition-all duration-300"
            :style="tabIndicatorStyle"
          />
        </div>

        <!-- ==================== 日历视图 ==================== -->
        <template v-if="activeTab === 'calendar'">
          <!-- Month Navigation -->
          <div class="flex items-center justify-between bg-card-bg p-lg rounded-xl card-shadow border border-border-light/40">
            <button
              class="w-8 h-8 flex items-center justify-center text-text-secondary hover:text-brand transition-colors"
              @click="prevMonth"
            >
              <span class="material-symbols-outlined">chevron_left</span>
            </button>
            <div class="text-center">
              <p class="font-display text-xl text-text-primary">
                {{ currentYear }}年 {{ currentMonth + 1 }}月
              </p>
              <p class="font-body text-xs text-text-tertiary mt-0.5">
                当月预计派息 {{ monthlyPredictedDividend.toFixed(2) }} 元
              </p>
            </div>
            <button
              class="w-8 h-8 flex items-center justify-center text-text-secondary hover:text-brand transition-colors"
              @click="nextMonth"
            >
              <span class="material-symbols-outlined">chevron_right</span>
            </button>
          </div>

          <!-- Refresh Button -->
          <button
            class="w-full flex items-center justify-center gap-2 py-2 font-body text-xs text-text-secondary hover:text-brand transition-colors rounded-lg hover:bg-card-alt"
            :disabled="refreshing"
            @click="refreshDividendData"
          >
            <span class="material-symbols-outlined text-[16px]" :class="{ 'animate-spin': refreshing }">
              {{ refreshing ? 'progress_activity' : 'refresh' }}
            </span>
            {{ refreshing ? '更新中...' : '更新分红数据' }}
          </button>

          <!-- Calendar Card -->
          <section class="bg-card-bg p-lg rounded-xl card-shadow border border-border-light/40">
            <!-- Weekday Headers -->
            <div class="grid grid-cols-7 mb-lg">
              <div
                v-for="(w, i) in ['日','一','二','三','四','五','六']"
                :key="i"
                class="text-center font-body text-xs text-text-tertiary py-1"
              >
                {{ w }}
              </div>
            </div>

            <!-- Calendar Grid -->
            <div class="grid grid-cols-7 gap-y-2">
              <div
                v-for="(cell, idx) in calendarCells"
                :key="idx"
                class="flex flex-col items-center justify-center h-10 relative"
                :class="{ 'cursor-pointer': cell.isCurrentMonth }"
                @click="cell.isCurrentMonth && selectDate(cell.date)"
              >
                <template v-if="cell.isCurrentMonth">
                  <span
                    class="flex items-center justify-center w-9 h-9 rounded-lg transition-colors font-body text-sm"
                    :class="cell.date === selectedDate
                      ? 'bg-brand text-white font-medium'
                      : 'text-text-primary hover:bg-card-alt'"
                  >
                    {{ cell.day }}
                  </span>
                  <!-- 多圆点：每个事件一个点 -->
                  <div v-if="getDotColors(cell.date).length" class="flex items-center gap-[2px] absolute bottom-0">
                    <template v-for="(color, ci) in getDotColors(cell.date).slice(0, 3)" :key="ci">
                      <div class="w-[5px] h-[5px] rounded-full" :class="color" />
                    </template>
                    <span
                      v-if="getDotColors(cell.date).length > 3"
                      class="font-body text-[8px] text-text-tertiary leading-none"
                    >+{{ getDotColors(cell.date).length - 3 }}</span>
                  </div>
                </template>
              </div>
            </div>

            <!-- Legend -->
            <div class="mt-lg pt-lg border-t border-border-light flex justify-around">
              <div class="flex items-center gap-2">
                <div class="w-2 h-2 rounded-full bg-blue-500" />
                <span class="font-body text-xs text-text-tertiary">股权登记</span>
              </div>
              <div class="flex items-center gap-2">
                <div class="w-2 h-2 rounded-full bg-error" />
                <span class="font-body text-xs text-text-tertiary">除权除息</span>
              </div>
              <div class="flex items-center gap-2">
                <div class="w-2 h-2 rounded-full bg-brand" />
                <span class="font-body text-xs text-text-tertiary">派息日</span>
              </div>
            </div>
          </section>

          <!-- Events List (only render when there are events) -->
          <section v-if="selectedEvents.length > 0" class="space-y-sm">
            <!-- Header with count -->
            <div class="flex items-center justify-between px-1">
              <h3 class="font-body text-sm font-medium text-text-primary">
                {{ selectedDateDisplay }}
              </h3>
              <span class="font-body text-xs text-text-tertiary bg-card-alt px-2 py-0.5 rounded-full">
                {{ selectedEvents.length }} 个事件
              </span>
            </div>

            <!-- Grouped Events -->
            <template v-for="type in eventGroupOrder" :key="type">
              <div v-if="groupedEvents[type]?.length" class="space-y-sm">
                <!-- Group header -->
                <div class="flex items-center gap-2 px-1 pt-1">
                  <span class="w-2 h-2 rounded-full" :class="eventGroupStyles[type].dotClass"></span>
                  <span class="font-body text-sm font-medium text-text-primary">{{ eventGroupStyles[type].label }}</span>
                  <span class="font-body text-xs text-text-tertiary/60 ml-auto">{{ groupedEvents[type].length }}</span>
                </div>

                <!-- Group items -->
                <TransitionGroup name="event-list" tag="div" class="space-y-sm">
                  <div
                    v-for="event in groupedEvents[type]"
                    :key="event.id"
                    class="bg-card-bg p-md rounded-xl card-shadow border border-border-light/40 flex items-center gap-3 cursor-pointer active:scale-[0.98] transition-all"
                    @click="goToHolding(event.holdingId)"
                  >
                    <div
                      class="w-9 h-9 rounded-lg flex items-center justify-center shrink-0"
                      :class="eventTypeStyles[event.type].iconBgClass"
                    >
                      <span class="material-symbols-outlined text-[18px]">
                        {{ eventTypeStyles[event.type].icon }}
                      </span>
                    </div>
                    <div class="flex-1 min-w-0">
                      <p class="font-body text-sm font-medium text-text-primary truncate">{{ event.holdingName }}</p>
                      <p class="font-body text-xs text-text-tertiary mt-0.5 truncate">{{ event.description }}</p>
                    </div>
                    <div class="text-right shrink-0 ml-2">
                      <p
                        class="font-body text-sm font-medium"
                        :class="event.type === 'payout' ? 'text-brand' : 'text-text-primary'"
                      >
                        ¥{{ event.amount.toFixed(2) }}
                      </p>
                      <span
                        class="font-body text-[10px] px-1.5 py-0.5 rounded mt-1 inline-block"
                        :class="eventTypeStyles[event.type].badgeClass"
                      >
                        {{ eventTypeStyles[event.type].badgeText }}
                      </span>
                    </div>
                  </div>
                </TransitionGroup>
              </div>
            </template>
          </section>

          <!-- Monthly Insights Bento -->
          <section class="grid grid-cols-3 gap-sm">
            <!-- 最丰厚来源 — 品牌色强调 -->
            <div class="col-span-1 bg-brand-light p-sm rounded-xl flex flex-col justify-between min-h-[90px]">
              <p class="font-body text-xs text-brand/60 font-medium">最丰厚来源</p>
              <div class="space-y-[2px]">
                <p class="text-[20px] font-display font-semibold text-brand leading-none">
                  ¥{{ richestSource.amount.toFixed(0) }}
                </p>
                <p class="font-body text-xs text-text-tertiary truncate">{{ richestSource.name }}</p>
              </div>
            </div>
            <!-- 本月动态 — 可点击进入明细 -->
            <div class="col-span-1 bg-card-bg p-sm rounded-xl card-shadow border border-border-light/40 flex flex-col justify-between min-h-[90px] relative cursor-pointer active:scale-[0.98] transition-transform" @click="openMonthlyDetail">
              <div class="flex items-center justify-between">
                <p class="font-body text-xs text-text-tertiary font-medium">本月动态</p>
                <span class="material-symbols-outlined text-[14px] text-text-tertiary/50">chevron_right</span>
              </div>
              <div class="space-y-[2px]">
                <p class="text-[20px] font-display font-semibold text-text-primary leading-none">{{ monthlyActivity.payoutCount }}</p>
                <p class="font-body text-xs text-text-tertiary truncate">{{ monthlyActivity.payoutCount }}笔分红 · {{ monthlyActivity.fundCount }}只基金</p>
              </div>
            </div>
            <!-- 下次分红 -->
            <div class="col-span-1 bg-card-bg p-sm rounded-xl card-shadow border border-border-light/40 flex flex-col justify-between min-h-[90px]">
              <p class="font-body text-xs text-text-tertiary font-medium">下次分红</p>
              <div class="space-y-[2px]">
                <p class="text-[20px] font-display font-semibold leading-none"
                   :class="nextDividend.holdingName !== '--' ? 'text-brand' : 'text-text-tertiary/40'">
                  {{ nextDividend.holdingName !== '--' ? (nextDividend.daysRemaining > 0 ? nextDividend.daysRemaining + '天后' : '今日') : '--' }}
                </p>
                <p class="font-body text-xs text-text-tertiary truncate">{{ nextDividend.holdingName }}</p>
                <p class="font-body text-xs text-text-tertiary/60">预计 ¥{{ nextDividend.amount.toFixed(0) }}</p>
              </div>
            </div>
          </section>
        </template>

        <!-- ==================== 年度总览 ==================== -->
        <template v-else>
          <template v-if="annualLoading && !annualData">
            <div class="flex items-center justify-center py-20">
              <span class="material-symbols-outlined animate-spin text-text-tertiary text-[32px]">progress_activity</span>
            </div>
          </template>

          <template v-else-if="annualData">
            <!-- Summary Cards -->
            <section class="grid grid-cols-4 gap-[6px]">
              <div class="bg-card-bg p-[10px] rounded-xl card-shadow border border-border-light/40 flex flex-col items-center text-center border-t-2 border-border-light">
                <p class="font-body text-[11px] text-text-tertiary/60">全年</p>
                <p class="text-[18px] font-display font-medium text-text-primary mt-1">¥{{ annualData.summary.totalDividend.toFixed(0) }}</p>
              </div>
              <div class="bg-card-bg p-[10px] rounded-xl card-shadow border border-border-light/40 flex flex-col items-center text-center border-t-2 border-border-light">
                <p class="font-body text-[11px] text-text-tertiary/60">笔数</p>
                <p class="text-[18px] font-display font-medium text-text-primary mt-1">{{ annualData.summary.totalPayoutCount }}</p>
              </div>
              <div class="bg-card-bg p-[10px] rounded-xl card-shadow border border-border-light/40 flex flex-col items-center text-center border-t-2 border-border-light">
                <p class="font-body text-[11px] text-text-tertiary/60">基金</p>
                <p class="text-[18px] font-display font-medium text-text-primary mt-1">{{ annualData.summary.fundCount }}</p>
              </div>
              <div class="bg-card-bg p-[10px] rounded-xl card-shadow border border-border-light/40 flex flex-col items-center text-center border-t-2 border-border-light">
                <p class="font-body text-[11px] text-text-tertiary/60">最多</p>
                <p class="text-[18px] font-display font-medium text-text-primary mt-1">{{ annualData.summary.peakMonth }}</p>
              </div>
            </section>

            <!-- Monthly Bar Chart -->
            <section class="bg-card-bg p-lg rounded-xl card-shadow border border-border-light/40 space-y-md">
              <h3 class="font-body text-sm font-medium text-text-primary">月度分红趋势</h3>

              <div v-for="q in quarters" :key="q.label" class="flex items-center gap-2">
                <span
                  class="font-body text-xs text-text-tertiary w-8 shrink-0"
                  :class="q.percentage === 100 ? 'text-brand font-medium' : ''"
                >{{ q.label }}</span>
                <div class="flex-1 h-6 bg-progress-bg rounded overflow-hidden">
                  <div
                    class="h-full rounded transition-all duration-500"
                    :class="q.amount > 0 ? 'bg-brand' : 'bg-progress-bg'"
                    :style="{ width: q.percentage + '%' }"
                  />
                </div>
                <span class="font-body text-xs text-text-tertiary w-20 text-right shrink-0">
                  <template v-if="q.amount > 0">¥{{ q.amount.toFixed(0) }}</template>
                  <template v-else>—</template>
                </span>
              </div>
            </section>

            <!-- Fund Ranking -->
            <section class="bg-card-bg p-lg rounded-xl card-shadow border border-border-light/40 space-y-md">
              <h3 class="font-body text-sm font-medium text-text-primary">基金分红排行</h3>

              <div
                v-for="(fund, i) in annualData.fundRanks"
                :key="i"
                class="flex items-center gap-3"
              >
                <span
                  class="w-5 h-5 rounded-full flex items-center justify-center font-body text-[10px] shrink-0"
                  :class="i === 0 ? 'bg-brand text-white' : i === 1 ? 'bg-card-alt text-text-secondary' : i === 2 ? 'bg-card-alt text-text-secondary' : 'text-text-tertiary/40'"
                >
                  {{ fund.rank }}
                </span>
                <div class="flex-1 min-w-0">
                  <p class="font-body text-[13px] font-medium text-text-primary truncate">{{ fund.holdingName }}</p>
                  <div class="h-2 bg-progress-bg rounded overflow-hidden mt-1">
                    <div
                      class="h-full rounded transition-all duration-500"
                      :class="fund.amount > 0 ? 'bg-brand-light' : 'bg-progress-bg'"
                      :style="{ width: fund.percentage + '%' }"
                    />
                  </div>
                </div>
                <span class="font-body text-[13px] font-medium text-brand shrink-0">¥{{ fund.amount.toFixed(0) }}</span>
              </div>

              <div v-if="annualData.fundRanks.length === 0" class="flex flex-col items-center py-8">
                <span class="text-3xl block mb-1">📊</span>
                <p class="font-body text-sm text-text-tertiary">暂无分红数据</p>
              </div>
            </section>
          </template>

          <template v-else>
            <div class="flex flex-col items-center justify-center py-20">
              <span class="text-4xl block mb-2">📊</span>
              <p class="font-body text-sm text-text-tertiary">暂无数据</p>
            </div>
          </template>
        </template>
      </template>
    </main>

    <!-- Monthly Detail Dialog -->
    <Teleport to="body">
      <div
        v-if="showMonthlyDetail"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
        @click.self="showMonthlyDetail = false"
      >
        <div class="bg-card-bg rounded-2xl w-[90vw] max-w-[420px] max-h-[80vh] flex flex-col shadow-overlay">
          <!-- Dialog Header -->
          <div class="flex items-center justify-between px-lg pt-lg pb-sm">
            <h3 class="font-body text-sm font-medium text-text-primary">{{ currentYear }}年{{ currentMonth + 1 }}月 分红明细</h3>
            <button class="w-7 h-7 flex items-center justify-center rounded-full hover:bg-card-alt" @click="showMonthlyDetail = false">
              <span class="material-symbols-outlined text-[18px] text-text-secondary">close</span>
            </button>
          </div>

          <!-- Dialog Body -->
          <div class="flex-1 overflow-y-auto px-lg pb-lg space-y-sm">
            <div v-if="loadingDetail" class="flex items-center justify-center py-12">
              <span class="material-symbols-outlined animate-spin text-text-tertiary">progress_activity</span>
            </div>

            <template v-else-if="monthlyDetailData?.details?.length">
              <div
                v-for="(item, i) in monthlyDetailData.details"
                :key="i"
                class="flex items-center justify-between py-2.5"
                :class="{ 'border-b border-border-light/40': i < monthlyDetailData.details.length - 1 }"
              >
                <div class="flex-1 min-w-0">
                  <p class="font-body text-[13px] font-medium text-text-primary truncate">{{ item.holdingName }}</p>
                  <div class="flex items-center gap-1.5 mt-0.5">
                    <span
                      v-for="(type, ti) in item.eventTypes"
                      :key="ti"
                      class="font-body text-[10px] text-text-tertiary/60 bg-card-alt px-1.5 py-0.5 rounded"
                    >
                      {{ type }}
                    </span>
                  </div>
                </div>
                <p class="font-body text-[13px] font-medium text-brand shrink-0 ml-3">¥{{ item.amount.toFixed(0) }}</p>
              </div>
            </template>

            <div v-else class="flex flex-col items-center py-12">
              <span class="text-3xl block mb-1">📭</span>
              <p class="font-body text-sm text-text-tertiary">本月无分红事件</p>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.event-list-enter-active,
.event-list-leave-active {
  transition: all 0.3s ease;
}
.event-list-enter-from {
  opacity: 0;
  transform: translateY(12px);
}
.event-list-leave-to {
  opacity: 0;
  transform: translateY(-12px);
}
.event-list-move {
  transition: transform 0.3s ease;
}
</style>
