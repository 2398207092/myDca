<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import type { PageState, DividendEvent, DividendEventType } from '@/types'
import { listEvents } from '@/api/event'
import { getMonthlyInsight, getMonthlyDetail, getAnnualInsight } from '@/api/insight'
import type { MonthlyInsight, MonthlyDetail, AnnualInsight } from '@/api/insight'
import { refreshAllFundDividends } from '@/api/fund'
import { syncAllEvents } from '@/api/event'
import AppHeader from '@/components/shared/AppHeader.vue'
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
    // 先同步已有分红数据到日历事件
    await syncAllEvents()
    await loadMonthData()
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

// === 事件类型样式映射 ===
const eventTypeStyles: Record<DividendEventType, {
  badgeClass: string
  badgeText: string
  icon: string
  borderClass: string
  iconBgClass: string
}> = {
  registration: {
    badgeClass: 'bg-blue-100 text-blue-700',
    badgeText: '登记',
    icon: 'how_to_reg',
    borderClass: 'border-blue-500',
    iconBgClass: 'bg-blue-500/10 text-blue-600',
  },
  ex_dividend: {
    badgeClass: 'bg-red-100 text-red-700',
    badgeText: '除息',
    icon: 'swap_horiz',
    borderClass: 'border-error',
    iconBgClass: 'bg-error/10 text-error',
  },
  payout: {
    badgeClass: 'bg-green-100 text-green-700',
    badgeText: '派息',
    icon: 'payments',
    borderClass: 'border-green-500',
    iconBgClass: 'bg-green-500/10 text-green-600',
  },
  announcement: {
    badgeClass: 'bg-surface-container-high text-on-surface-variant',
    badgeText: '公告',
    icon: 'description',
    borderClass: 'border-outline',
    iconBgClass: 'bg-surface-container text-outline',
  },
}

// 日历格子上事件小圆点颜色（announcement 不显示圆点）
const dotColorForType: Record<DividendEventType, string | null> = {
  registration: 'bg-blue-500',
  ex_dividend: 'bg-error',
  payout: 'bg-green-500',
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
  payout: { label: '派息发放', dotClass: 'bg-green-500' },
  announcement: { label: '公告', dotClass: 'bg-outline-variant' },
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
  <div class="min-h-screen bg-background">
    <AppHeader title="分红日历" right-icon="notifications" />

    <main class="mt-14 pt-sm pb-24 px-gutter max-w-[600px] mx-auto space-y-md">
      <PageStateComp
        v-if="pageState !== 'ready'"
        :state="pageState"
      />

      <template v-if="pageState === 'ready'">
        <!-- Tab Switcher -->
        <div class="bg-surface-container-low p-1 rounded-xl flex relative">
          <button
            class="flex-1 py-2 text-label-bold font-label-bold z-10 transition-colors"
            :class="activeTab === 'calendar' ? 'text-on-surface' : 'text-on-surface-variant'"
            @click="activeTab = 'calendar'"
          >
            日历
          </button>
          <button
            class="flex-1 py-2 text-label-bold font-label-bold z-10 transition-colors"
            :class="activeTab === 'overview' ? 'text-on-surface' : 'text-on-surface-variant'"
            @click="activeTab = 'overview'; loadAnnualData()"
          >
            年度总览
          </button>
          <div
            class="active-tab-indicator absolute left-1 top-1 bottom-1 w-[calc(50%-4px)] bg-surface-container-lowest rounded-lg shadow-sm"
            :style="tabIndicatorStyle"
          />
        </div>

        <!-- 日历视图 -->
        <template v-if="activeTab === 'calendar'">
          <!-- Month Navigation -->
          <div class="flex items-center justify-between bg-surface-container-lowest p-md rounded-xl card-shadow">
            <button
              class="w-8 h-8 flex items-center justify-center text-outline hover:text-primary transition-colors"
              @click="prevMonth"
            >
              <span class="material-symbols-outlined">chevron_left</span>
            </button>
            <div class="text-center">
              <p class="font-headline-md text-headline-md text-on-surface">
                {{ currentYear }}年 {{ currentMonth + 1 }}月
              </p>
              <p class="font-caption text-caption text-on-surface-variant mt-0.5">
                当月预计派息 {{ monthlyPredictedDividend.toFixed(2) }} 元
              </p>
            </div>
            <button
              class="w-8 h-8 flex items-center justify-center text-outline hover:text-primary transition-colors"
              @click="nextMonth"
            >
              <span class="material-symbols-outlined">chevron_right</span>
            </button>
          </div>

          <!-- Refresh Button -->
          <button
            class="w-full flex items-center justify-center gap-2 py-2 text-caption font-caption text-on-surface-variant hover:text-primary transition-colors rounded-lg hover:bg-surface-container-high"
            :disabled="refreshing"
            @click="refreshDividendData"
          >
            <span class="material-symbols-outlined text-[16px]" :class="{ 'animate-spin': refreshing }">
              {{ refreshing ? 'progress_activity' : 'refresh' }}
            </span>
            {{ refreshing ? '更新中...' : '更新分红数据' }}
          </button>

          <!-- Calendar Card -->
          <section class="bg-surface-container-lowest p-md rounded-xl card-shadow">
            <!-- Weekday Headers -->
            <div class="grid grid-cols-7 mb-4">
              <div
                v-for="(w, i) in ['日','一','二','三','四','五','六']"
                :key="i"
                class="text-center font-caption text-caption text-on-surface-variant py-1"
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
                :class="{
                  'cursor-pointer': cell.isCurrentMonth,
                }"
                @click="cell.isCurrentMonth && selectDate(cell.date)"
              >
                <template v-if="cell.isCurrentMonth">
                  <span
                    class="flex items-center justify-center w-9 h-9 rounded-lg transition-colors font-body-md text-body-md"
                    :class="cell.date === selectedDate
                      ? 'bg-primary-container text-on-primary-container font-label-bold text-label-bold'
                      : 'text-on-surface hover:bg-surface-container-high'"
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
                      class="font-caption text-[8px] text-on-surface-variant leading-none"
                    >+{{ getDotColors(cell.date).length - 3 }}</span>
                  </div>
                </template>
              </div>
            </div>

            <!-- Legend -->
            <div class="mt-6 pt-4 border-t border-outline-variant flex justify-around">
              <div class="flex items-center gap-2">
                <div class="w-2 h-2 rounded-full bg-blue-500" />
                <span class="font-caption text-caption text-on-surface-variant">股权登记</span>
              </div>
              <div class="flex items-center gap-2">
                <div class="w-2 h-2 rounded-full bg-error" />
                <span class="font-caption text-caption text-on-surface-variant">除权除息</span>
              </div>
              <div class="flex items-center gap-2">
                <div class="w-2 h-2 rounded-full bg-green-500" />
                <span class="font-caption text-caption text-on-surface-variant">派息日</span>
              </div>
            </div>
          </section>

          <!-- Events List -->
          <section class="space-y-sm">
            <!-- Header with count -->
            <div v-if="selectedEvents.length > 0" class="flex items-center justify-between px-1">
              <h3 class="font-label-bold text-label-bold text-on-surface">
                {{ selectedDateDisplay }}
              </h3>
              <span class="font-caption text-caption text-on-surface-variant bg-surface-container px-2 py-0.5 rounded-full">
                {{ selectedEvents.length }} 个事件
              </span>
            </div>

            <!-- Empty state -->
            <div
              v-else
              class="bg-surface-container-lowest rounded-xl p-xl flex flex-col items-center justify-center"
            >
              <span class="material-symbols-outlined text-[36px] text-outline-variant">event_busy</span>
              <p class="font-caption text-caption text-on-surface-variant mt-2">当天无分红事件</p>
            </div>

            <!-- Grouped Events -->
            <template v-for="type in eventGroupOrder" :key="type">
              <div v-if="groupedEvents[type]?.length" class="space-y-sm">
                <!-- Group header -->
                <div class="flex items-center gap-2 px-1 pt-1">
                  <span class="w-2 h-2 rounded-full" :class="eventGroupStyles[type].dotClass"></span>
                  <span class="font-label-bold text-label-bold text-on-surface">{{ eventGroupStyles[type].label }}</span>
                  <span class="font-caption text-caption text-on-surface-variant/60 ml-auto">{{ groupedEvents[type].length }}</span>
                </div>

                <!-- Group items -->
                <TransitionGroup name="event-list" tag="div" class="space-y-sm">
                  <div
                    v-for="event in groupedEvents[type]"
                    :key="event.id"
                    class="bg-surface-container-lowest p-md rounded-xl shadow-sm flex items-center gap-3 cursor-pointer active:scale-[0.98] transition-all"
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
                      <p class="font-label-bold text-label-bold text-on-surface truncate">{{ event.holdingName }}</p>
                      <p class="font-caption text-caption text-on-surface-variant mt-0.5 truncate">{{ event.description }}</p>
                    </div>
                    <div class="text-right shrink-0 ml-2">
                      <p
                        class="font-label-bold text-label-bold"
                        :class="event.type === 'payout' ? 'text-primary' : 'text-on-surface'"
                      >
                        ¥{{ event.amount.toFixed(2) }}
                      </p>
                      <span
                        class="font-caption text-[10px] px-1.5 py-0.5 rounded mt-1 inline-block"
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
            <div class="col-span-1 bg-primary-container p-sm rounded-xl flex flex-col justify-between min-h-[84px]">
              <p class="font-caption text-[10px] text-on-primary-container/70">最丰厚来源</p>
              <div>
                <p class="font-label-bold text-[13px] text-on-primary-container truncate">
                  {{ richestSource.name }}
                </p>
                <p class="font-headline-sm text-headline-sm text-primary mt-0.5">
                  ¥{{ richestSource.amount.toFixed(0) }}
                </p>
              </div>
            </div>
            <div class="col-span-1 bg-surface-container-high p-sm rounded-xl flex flex-col justify-between min-h-[84px] relative cursor-pointer hover:bg-surface-container-high/80 transition-colors" @click="openMonthlyDetail">
              <div class="flex items-center justify-between">
                <p class="font-caption text-[10px] text-on-surface-variant">本月动态</p>
                <span class="material-symbols-outlined text-[12px] text-on-surface-variant/40">chevron_right</span>
              </div>
              <div>
                <p class="text-[22px] font-label-bold text-on-surface leading-tight">{{ monthlyActivity.payoutCount }}</p>
                <p class="font-caption text-[11px] text-on-surface-variant">笔分红</p>
                <p class="font-caption text-[10px] text-on-surface-variant/60">{{ monthlyActivity.fundCount }} 只基金参与</p>
              </div>
            </div>
            <div class="col-span-1 bg-surface-container-high p-sm rounded-xl flex flex-col justify-between min-h-[84px]">
              <p class="font-caption text-[10px] text-on-surface-variant">下次分红</p>
              <div>
                <p class="text-[22px] font-label-bold leading-tight"
                   :class="nextDividend.holdingName !== '--' ? 'text-primary' : 'text-on-surface-variant/40'">
                  {{ nextDividend.holdingName !== '--' ? (nextDividend.daysRemaining > 0 ? nextDividend.daysRemaining + '天后' : '今日') : '--' }}
                </p>
                <p class="font-caption text-[11px] text-on-surface-variant truncate">{{ nextDividend.holdingName }}</p>
                <p class="font-caption text-[10px] text-on-surface-variant/60">预计 ¥{{ nextDividend.amount.toFixed(0) }}</p>
              </div>
            </div>
          </section>
        </template>

        <!-- 年度总览 -->
        <template v-else>
          <template v-if="annualLoading && !annualData">
            <div class="flex items-center justify-center py-20">
              <span class="material-symbols-outlined animate-spin text-outline text-[32px]">progress_activity</span>
            </div>
          </template>

          <template v-else-if="annualData">
            <!-- Summary Cards -->
            <section class="grid grid-cols-4 gap-[6px]">
              <div class="bg-surface-container-lowest p-[10px] rounded-xl card-shadow flex flex-col items-center text-center border-t-2 border-outline-variant">
                <p class="font-caption text-[11px] text-on-surface-variant/60">全年</p>
                <p class="text-[18px] font-label-bold text-on-surface mt-1">¥{{ annualData.summary.totalDividend.toFixed(0) }}</p>
              </div>
              <div class="bg-surface-container-lowest p-[10px] rounded-xl card-shadow flex flex-col items-center text-center border-t-2 border-outline-variant">
                <p class="font-caption text-[11px] text-on-surface-variant/60">笔数</p>
                <p class="text-[18px] font-label-bold text-on-surface mt-1">{{ annualData.summary.totalPayoutCount }}</p>
              </div>
              <div class="bg-surface-container-lowest p-[10px] rounded-xl card-shadow flex flex-col items-center text-center border-t-2 border-outline-variant">
                <p class="font-caption text-[11px] text-on-surface-variant/60">基金</p>
                <p class="text-[18px] font-label-bold text-on-surface mt-1">{{ annualData.summary.fundCount }}</p>
              </div>
              <div class="bg-surface-container-lowest p-[10px] rounded-xl card-shadow flex flex-col items-center text-center border-t-2 border-outline-variant">
                <p class="font-caption text-[11px] text-on-surface-variant/60">最多</p>
                <p class="text-[18px] font-label-bold text-on-surface mt-1">{{ annualData.summary.peakMonth }}</p>
              </div>
            </section>

            <!-- Monthly Bar Chart -->
            <section class="bg-surface-container-lowest p-md rounded-xl card-shadow space-y-md">
              <h3 class="font-label-bold text-label-bold text-on-surface">月度分红趋势</h3>

              <div
                  v-for="q in quarters"
                  :key="q.label"
                  class="flex items-center gap-2"
                >
                  <span
                    class="font-caption text-caption text-on-surface-variant w-8 shrink-0"
                    :class="q.percentage === 100 ? 'text-primary font-label-bold' : ''"
                  >{{ q.label }}</span>
                  <div class="flex-1 h-6 bg-surface-container rounded overflow-hidden">
                    <div
                      class="h-full rounded transition-all duration-500"
                      :class="q.amount > 0 ? 'bg-primary' : 'bg-surface-container'"
                      :style="{ width: q.percentage + '%' }"
                    />
                  </div>
                  <span class="font-caption text-caption text-on-surface-variant w-20 text-right shrink-0">
                    <template v-if="q.amount > 0">¥{{ q.amount.toFixed(0) }}</template>
                    <template v-else>—</template>
                  </span>
                </div>
            </section>

            <!-- Fund Ranking -->
            <section class="bg-surface-container-lowest p-md rounded-xl card-shadow space-y-md">
              <h3 class="font-label-bold text-label-bold text-on-surface">基金分红排行</h3>

              <div
                v-for="(fund, i) in annualData.fundRanks"
                :key="i"
                class="flex items-center gap-3"
              >
                <span
                  class="w-5 h-5 rounded-full flex items-center justify-center font-caption text-[10px] shrink-0"
                  :class="i === 0 ? 'bg-primary text-on-primary' : i === 1 ? 'bg-surface-container-high text-on-surface-variant' : i === 2 ? 'bg-surface-container-high text-on-surface-variant' : 'text-on-surface-variant/40'"
                >
                  {{ fund.rank }}
                </span>
                <div class="flex-1 min-w-0">
                  <p class="font-label-bold text-[13px] text-on-surface truncate">{{ fund.holdingName }}</p>
                  <div class="h-2 bg-surface-container rounded overflow-hidden mt-1">
                    <div
                      class="h-full rounded bg-primary-container transition-all duration-500"
                      :style="{ width: fund.percentage + '%' }"
                    />
                  </div>
                </div>
                <span class="font-label-bold text-[13px] text-primary shrink-0">¥{{ fund.amount.toFixed(0) }}</span>
              </div>

              <div v-if="annualData.fundRanks.length === 0" class="flex flex-col items-center py-8">
                <span class="material-symbols-outlined text-[32px] text-outline-variant">bar_chart</span>
                <p class="font-caption text-caption text-on-surface-variant mt-2">暂无分红数据</p>
              </div>
            </section>
          </template>

          <template v-else>
            <div class="flex flex-col items-center justify-center py-20">
              <span class="material-symbols-outlined text-[48px] text-outline-variant">bar_chart</span>
              <p class="text-on-surface-variant font-caption mt-4">暂无数据</p>
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
        <div class="bg-surface-container-lowest rounded-2xl w-[90vw] max-w-[420px] max-h-[80vh] flex flex-col shadow-xl">
          <!-- Dialog Header -->
          <div class="flex items-center justify-between px-md pt-md pb-sm">
            <h3 class="font-label-bold text-label-bold text-on-surface">{{ currentYear }}年{{ currentMonth + 1 }}月 分红明细</h3>
            <button class="w-7 h-7 flex items-center justify-center rounded-full hover:bg-surface-container" @click="showMonthlyDetail = false">
              <span class="material-symbols-outlined text-[18px]">close</span>
            </button>
          </div>

          <!-- Dialog Body -->
          <div class="flex-1 overflow-y-auto px-md pb-md space-y-sm">
            <div v-if="loadingDetail" class="flex items-center justify-center py-12">
              <span class="material-symbols-outlined animate-spin text-outline">progress_activity</span>
            </div>

            <template v-else-if="monthlyDetailData?.details?.length">
              <div
                v-for="(item, i) in monthlyDetailData.details"
                :key="i"
                class="flex items-center justify-between py-2.5"
                :class="{ 'border-b border-outline-variant/20': i < monthlyDetailData.details.length - 1 }"
              >
                <div class="flex-1 min-w-0">
                  <p class="font-label-bold text-[13px] text-on-surface truncate">{{ item.holdingName }}</p>
                  <div class="flex items-center gap-1.5 mt-0.5">
                    <span
                      v-for="(type, ti) in item.eventTypes"
                      :key="ti"
                      class="font-caption text-[10px] text-on-surface-variant/60 bg-surface-container px-1.5 py-0.5 rounded"
                    >
                      {{ type }}
                    </span>
                  </div>
                </div>
                <p class="font-label-bold text-[13px] text-primary shrink-0 ml-3">¥{{ item.amount.toFixed(0) }}</p>
              </div>
            </template>

            <div v-else class="flex flex-col items-center py-12">
              <span class="material-symbols-outlined text-[32px] text-outline-variant">inbox</span>
              <p class="font-caption text-caption text-on-surface-variant mt-2">本月无分红事件</p>
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
