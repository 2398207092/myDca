<script setup lang="ts">
/**
 * 分红日历（四层信息骨架 · 2026-11-08 v3）
 * 章 01 MONTH 本月：月份切换 + 主角数（当月预计派息）+ 1 行（N 笔 · 涉及 M 只基金）
 * 章 02 CALENDAR 分红日历：本章唯一形状 = 发丝线月历格 + 磷光点；图例压成 1 行 10px
 * 章 03 EVENTS 当日事件：登记 / 除息 / 派息 三组，每组 ≤5 行 + AurosDisclosure
 * 业务逻辑与接口不变；「年度总览」按骨架要求移出本页（待第四步承接）。
 */
import { ref, computed, watch, onActivated, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import type { PageState, DividendEvent, DividendEventType } from '@/types'
import { listEvents } from '@/api/event'
import { getMonthlyInsight, getMonthlyDetail, getAnnualInsight } from '@/api/insight'
import type { MonthlyInsight, MonthlyDetail, AnnualInsight } from '@/api/insight'
import { refreshAllFundDividends } from '@/api/fund'
import { syncAllEvents } from '@/api/event'
import AurosNavBar from '@/auros-skin/components/auros/AurosNavBar.vue'
import AurosState from '@/auros-skin/components/auros/AurosState.vue'
import AurosChapter from '@/auros-skin/components/auros/AurosChapter.vue'
import AurosStat from '@/auros-skin/components/auros/AurosStat.vue'
import AurosGroup from '@/auros-skin/components/auros/AurosGroup.vue'
import AurosRow from '@/auros-skin/components/auros/AurosRow.vue'
import AurosDisclosure from '@/auros-skin/components/auros/AurosDisclosure.vue'
import AurosSheet from '@/auros-skin/components/auros/AurosSheet.vue'
import { formatMoney, formatPercent } from '@/auros-skin/utils/format'

const router = useRouter()
const pageState = ref<PageState>('loading')
const currentYear = ref(new Date().getFullYear())
const currentMonth = ref(new Date().getMonth()) // 0-indexed
const selectedDate = ref(formatDate(new Date()))

// 本地时区的"今天"字符串，用于日历"今天"标记
const now = new Date()
const todayStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`

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

onActivated(async () => {
  try {
    // 同步、月份数据、年度读数没有依赖关系，并行执行
    // syncAllEvents 失败（如权限不足）不影响日历数据显示
    await Promise.all([
      syncAllEvents().catch(() => {}),
      loadMonthData(),
      loadAnnualData(),
    ])
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

// === 事件类型 → 章节呈现映射（语义、顺序不变，只换色相/分组） ===
const eventTypeMeta: Record<DividendEventType, { group: string; mark: 'lit' | 'pink' | 'dim' }> = {
  registration: { group: '权益登记', mark: 'dim' },
  ex_dividend: { group: '除权除息', mark: 'pink' },
  payout: { group: '派息发放', mark: 'lit' },
  announcement: { group: '公告', mark: 'dim' },
}

// 日历格子上事件小圆点色相（announcement 不显示圆点）
const dotToneForType: Record<DividendEventType, string | null> = {
  registration: 'reg',
  ex_dividend: 'ex',
  payout: 'pay',
  announcement: null,
}

function getDotTones(date: string): string[] {
  return getEventsForDate(date)
    .map(e => dotToneForType[e.type])
    .filter((c): c is string => c !== null)
}

// === 当日事件分组：登记 / 除息 / 派息，每组最多 5 行 + 折叠（密度预算） ===
const eventGroupOrder: DividendEventType[] = ['registration', 'ex_dividend', 'payout']

const eventGroups = computed(() =>
  eventGroupOrder
    .map((type) => {
      const rows = selectedEvents.value.filter(e => e.type === type)
      return {
        type,
        title: eventTypeMeta[type].group,
        mark: eventTypeMeta[type].mark,
        count: rows.length,
        shown: rows.slice(0, 5),
        rest: rows.slice(5),
      }
    })
    .filter(g => g.count > 0)
)

// === 月度洞察（章 1 的 1 行读数） ===
const monthlyActivity = computed(() => {
  const d = monthlyInsightData.value
  if (!d || !d.monthlyActivity) return { payoutCount: 0, fundCount: 0 }
  return d.monthlyActivity
})

// === 年度总览（章 4：年度数据只在这一章出现） ===
const annualData = ref<AnnualInsight | null>(null)
const annualLoading = ref(false)

async function loadAnnualData(force = false) {
  if (annualData.value && !force) return
  annualLoading.value = true
  try {
    annualData.value = await getAnnualInsight(currentYear.value)
  } catch {
    // 年度数据失败不阻断页面
  } finally {
    annualLoading.value = false
  }
}

// 年份切换（含月份导航跨年）时重取年度读数
watch(currentYear, () => loadAnnualData(true))

/** 4 段季度条：每段长度 = 当季分红 / 全年峰值 */
const quarters = computed(() => {
  if (!annualData.value) return []
  const labels = ['Q1', 'Q2', 'Q3', 'Q4']
  const raw = labels.map((label, i) => ({
    label,
    amount: annualData.value!.monthlyBars.slice(i * 3, i * 3 + 3).reduce((sum, m) => sum + m.amount, 0),
  }))
  const max = Math.max(...raw.map(q => q.amount), 1)
  return raw.map(q => ({ ...q, hPercent: q.amount > 0 ? Math.max(4, Math.round((q.amount / max) * 100)) : 0 }))
})

/** 派息最多的基金（年度排行第 1） */
const topFund = computed(() => {
  const ranks = annualData.value?.fundRanks ?? []
  const top = ranks.find(r => r.rank === 1) ?? ranks[0]
  return top ? { name: top.holdingName, amount: top.amount } : { name: '暂无排行', amount: 0 }
})

/** 头名占全年派息的比例（避免与年度合计重复显示同一金额） */
const topFundShare = computed(() => {
  const total = annualData.value?.summary.totalDividend ?? 0
  if (!topFund.value.amount || total <= 0) return '--'
  return `占比 ${formatPercent((topFund.value.amount / total) * 100, 0, false)}`
})

/** 下次分红（月度洞察；只在本章出现，不在章 1 重复） */
const nextDividend = computed(() => {
  const d = monthlyInsightData.value?.nextDividend
  if (!d || d.holdingName === '--') return { holdingName: '--', amount: 0, daysRemaining: 0 }
  return d
})

const nextDividendSub = computed(() => {
  const d = nextDividend.value
  if (d.holdingName === '--') return '暂无计划'
  return `${d.daysRemaining > 0 ? d.daysRemaining + ' 天后' : '今日'} · ${d.holdingName}`
})

// === 月度分红明细弹层 ===
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

/** 弹层也遵守密度预算：前 5 行 + 折叠 */
const detailRows = computed(() => monthlyDetailData.value?.details ?? [])
const shownDetails = computed(() => detailRows.value.slice(0, 5))
const restDetails = computed(() => detailRows.value.slice(5))

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

// === 年份选择器（点击年号展开条带） ===
const yearPickerOpen = ref(false)
const navCardRef = ref<HTMLElement | null>(null)
const yearRange = computed(() => {
  const y = new Date().getFullYear()
  const start = y - 3
  const end = y + 3
  const list: number[] = []
  for (let i = start; i <= end; i++) list.push(i)
  return list
})
function pickYear(y: number) {
  currentYear.value = y
  yearPickerOpen.value = false
  loadMonthData()
}

// 点击导航卡外部时关闭年份选择器
function onDocClick(e: MouseEvent) {
  if (yearPickerOpen.value && navCardRef.value && !navCardRef.value.contains(e.target as Node)) {
    yearPickerOpen.value = false
  }
}
onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))

// 回到今天
function goToToday() {
  yearPickerOpen.value = false
  const n = new Date()
  currentYear.value = n.getFullYear()
  currentMonth.value = n.getMonth()
  selectedDate.value = formatDate(n)
  loadMonthData()
}

// 是否正在查看当前真实月份（用于显示/隐藏「今月」按钮）
const isCurrentMonthView = computed(() => {
  const n = new Date()
  return currentYear.value === n.getFullYear() && currentMonth.value === n.getMonth()
})

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
  <div class="page">
    <AurosNavBar title="分红日历" :back="false" />

    <main class="body">
      <AurosState
        v-if="pageState !== 'ready'"
        :state="pageState === 'loading' ? 'loading' : 'error'"
        :rows="6"
        :text="pageState === 'error' ? '月度分红数据没有取到' : ''"
      />

      <template v-else>
        <!-- 章 01 MONTH 本月 -->
        <AurosChapter label="MONTH" title="本月" :gap="false">
          <div ref="navCardRef" class="switch">
            <button type="button" class="switch__nav" aria-label="上个月" @click="prevMonth">
              <svg viewBox="0 0 12 12" width="12" height="12" fill="none">
                <path d="M7.4 2.6 4 6l3.4 3.4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>
            <button type="button" class="switch__title" @click="yearPickerOpen = !yearPickerOpen">
              <span class="switch__y">{{ currentYear }}<i>年</i>{{ currentMonth + 1 }}<i>月</i></span>
              <svg class="switch__chev" :class="{ 'is-open': yearPickerOpen }" viewBox="0 0 12 12" width="10" height="10" fill="none">
                <path d="M2.8 4.6 6 7.8l3.2-3.2" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>
            <button type="button" class="switch__nav" aria-label="下个月" @click="nextMonth">
              <svg viewBox="0 0 12 12" width="12" height="12" fill="none">
                <path d="M4.6 2.6 8 6l-3.4 3.4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>
            <span class="switch__util">
              <button type="button" class="switch__act" :disabled="refreshing" @click="refreshDividendData">
                {{ refreshing ? '更新中' : '更新' }}
              </button>
              <button
                type="button"
                class="switch__act"
                :class="{ 'is-hidden': isCurrentMonthView }"
                @click="goToToday"
              >
                今月
              </button>
            </span>
          </div>

          <!-- 年份条带（展开式，不做浮层） -->
          <div v-if="yearPickerOpen" class="years">
            <button
              v-for="y in yearRange"
              :key="y"
              type="button"
              class="years__btn au-num"
              :class="{ 'is-active': y === currentYear }"
              @click="pickYear(y)"
            >
              {{ y }}
            </button>
          </div>

          <div class="month-main">
            <AurosStat
              :value="formatMoney(monthlyPredictedDividend)"
              caption="当月预计派息"
              pink
              size="lg"
            />
            <AurosRow
              label="派息事件"
              :value="`${monthlyActivity.payoutCount} 笔 · 涉及 ${monthlyActivity.fundCount} 只基金`"
              clickable
              chevron
              @click="openMonthlyDetail"
            />
          </div>
        </AurosChapter>

        <!-- 章 02 CALENDAR 分红日历（本章唯一形状） -->
        <AurosChapter label="CALENDAR" title="分红日历" meta="磷光点 = 分红日">
          <div class="cal">
            <div class="cal__week">
              <span v-for="(w, i) in ['日', '一', '二', '三', '四', '五', '六']" :key="i">{{ w }}</span>
            </div>
            <div :key="`${currentYear}-${currentMonth}`" class="cal__grid">
              <div
                v-for="(cell, idx) in calendarCells"
                :key="idx"
                class="cal__cell"
                :class="{ 'is-col': idx % 7 !== 0 }"
                :style="{ '--d': `${Math.floor(idx / 7) * 36}ms` }"
              >
                <button
                  v-if="cell.isCurrentMonth"
                  type="button"
                  class="day"
                  :class="{ 'is-sel': cell.date === selectedDate, 'is-today': cell.date === todayStr }"
                  @click="selectDate(cell.date)"
                >
                  <span class="day__n au-num">{{ cell.day }}</span>
                  <span v-if="getDotTones(cell.date).length" class="day__dots" aria-hidden="true">
                    <i
                      v-for="(tone, ci) in getDotTones(cell.date).slice(0, 3)"
                      :key="ci"
                      class="dot"
                      :class="`is-${tone}`"
                    ></i>
                    <b v-if="getDotTones(cell.date).length > 3" class="day__more">
                      +{{ getDotTones(cell.date).length - 3 }}
                    </b>
                  </span>
                </button>
              </div>
            </div>
          </div>
          <div class="legend">
            <span class="legend__item"><i class="dot is-reg"></i>股权登记</span>
            <span class="legend__item"><i class="dot is-ex"></i>除权除息</span>
            <span class="legend__item"><i class="dot is-pay"></i>派息日</span>
          </div>
        </AurosChapter>

        <!-- 章 03 EVENTS 当日事件（三组，每组 ≤5 行 + 折叠） -->
        <AurosChapter label="EVENTS" title="当日事件" :meta="selectedDateDisplay">
          <template v-if="eventGroups.length">
            <AurosGroup
              v-for="(g, gi) in eventGroups"
              :key="g.type"
              :title="g.title"
              :count="`${g.count} 条`"
            >
              <AurosRow
                v-for="(ev, ei) in g.shown"
                :key="ev.id"
                class="anim"
                :style="{ '--d': `${(gi * 5 + ei) * 32}ms` }"
                :label="ev.holdingName"
                :sub="ev.description"
                :value="ev.type === 'payout' ? (ev.participated ? formatMoney(ev.amount) : '未参与') : ''"
                :mark="g.mark"
                :tone="ev.participated ? 'default' : 'muted'"
                clickable
                chevron
                @click="goToHolding(ev.holdingId)"
              />
              <AurosDisclosure v-if="g.rest.length" :count="g.rest.length">
                <AurosRow
                  v-for="ev in g.rest"
                  :key="ev.id"
                  :label="ev.holdingName"
                  :sub="ev.description"
                  :value="ev.type === 'payout' ? (ev.participated ? formatMoney(ev.amount) : '未参与') : ''"
                  :mark="g.mark"
                  :tone="ev.participated ? 'default' : 'muted'"
                  clickable
                  chevron
                  @click="goToHolding(ev.holdingId)"
                />
              </AurosDisclosure>
            </AurosGroup>
          </template>
          <p v-else class="blank">当日无事件</p>
        </AurosChapter>

        <!-- 章 04 YEAR 年度总览（年度数据只在本章出现） -->
        <AurosChapter
          label="YEAR"
          title="年度总览"
          :meta="annualData ? `${annualData.summary.totalPayoutCount} 笔` : ''"
        >
          <AurosState v-if="annualLoading && !annualData" state="loading" :rows="3" />

          <template v-else-if="annualData">
            <AurosStat
              :value="formatMoney(annualData.summary.totalDividend)"
              caption="年度派息合计"
              size="lg"
            />

            <!-- 本章形状：4 段季度条 -->
            <div class="qbar-wrap">
              <div class="qbar">
                <span v-for="q in quarters" :key="q.label" class="qbar__seg">
                  <i
                    class="qbar__fill"
                    :class="{ 'is-empty': q.amount <= 0 }"
                    :style="{ width: `${q.hPercent}%` }"
                  ></i>
                </span>
              </div>
              <div class="qbar__labels">
                <span v-for="q in quarters" :key="q.label">{{ q.label }}</span>
              </div>
            </div>

            <div class="year-rows">
              <AurosRow
                label="派息最多的基金"
                :sub="topFund.name"
                :value="topFundShare"
              />
              <AurosRow
                label="下次分红"
                :sub="nextDividendSub"
                :value="nextDividend.holdingName !== '--' ? formatMoney(nextDividend.amount) : '--'"
              />
            </div>
          </template>

          <AurosState v-else state="empty" text="年度数据未取到" />
        </AurosChapter>
      </template>
    </main>

    <!-- 月度分红明细（章 01 那行点开；弹层仍是 AurosSheet） -->
    <AurosSheet v-model="showMonthlyDetail" :title="`${currentYear}年${currentMonth + 1}月 分红明细`">
      <AurosState v-if="loadingDetail" state="loading" :rows="4" />

      <template v-else-if="detailRows.length">
        <AurosRow
          v-for="(item, i) in shownDetails"
          :key="i"
          :label="item.holdingName"
          :sub="item.eventTypes.join(' / ')"
          :value="formatMoney(item.amount)"
        />
        <AurosDisclosure v-if="restDetails.length" :count="restDetails.length">
          <AurosRow
            v-for="(item, i) in restDetails"
            :key="i"
            :label="item.holdingName"
            :sub="item.eventTypes.join(' / ')"
            :value="formatMoney(item.amount)"
          />
        </AurosDisclosure>
      </template>

      <AurosState v-else state="empty" text="本月无分红事件" />
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

/* ---------- 章 01：月份切换 ---------- */
.switch {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 12px;
}
.switch__nav {
  flex: none;
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: none;
  color: var(--au-silver);
  transition: color 160ms var(--au-ease), background 160ms var(--au-ease);
}
.switch__nav:active {
  color: var(--au-mist);
  background: rgba(237, 255, 254, 0.06);
}
.switch__title {
  flex: 1;
  min-width: 0;
  display: inline-flex;
  align-items: baseline;
  justify-content: center;
  gap: 6px;
  border: 0;
  background: none;
  padding: 2px 0;
  font-family: inherit;
}
.switch__y {
  font-size: 20px;
  font-weight: 500;
  line-height: 1;
  letter-spacing: -0.01em;
  color: var(--au-white);
}
.switch__y i {
  font-style: normal;
  font-size: 10px;
  font-weight: 400;
  letter-spacing: 0.08em;
  color: var(--au-silver);
  margin: 0 2px 0 1px;
}
.switch__chev {
  color: var(--au-silver);
  transition: transform 240ms var(--au-ease);
}
.switch__chev.is-open {
  transform: rotate(180deg);
}
.switch__util {
  flex: none;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: 6px;
}
.switch__act {
  border: 0;
  background: none;
  padding: 4px 0;
  font-family: inherit;
  font-size: 10px;
  letter-spacing: 0.08em;
  color: var(--au-silver);
  transition: color 160ms var(--au-ease), opacity 160ms var(--au-ease);
}
.switch__act:active {
  color: var(--au-mist);
}
.switch__act:disabled {
  opacity: 0.4;
}
.switch__act.is-hidden {
  visibility: hidden;
}

/* 年份条带（卡内胶囊，无通栏分隔线） */
.years {
  display: flex;
  gap: 4px;
  margin: -4px 0 14px;
  animation: fade 220ms var(--au-ease) both;
}
.years__btn {
  flex: 1;
  min-width: 0;
  height: 30px;
  border: 0;
  border-radius: 6px;
  background: rgba(1, 29, 28, 0.35);
  font-family: inherit;
  font-size: 13px;
  color: rgba(187, 199, 198, 0.75);
  transition: background 160ms var(--au-ease), color 160ms var(--au-ease);
}
.years__btn.is-active {
  background: rgba(203, 255, 252, 0.14);
  color: var(--au-mist);
}

.month-main {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ---------- 章 02：发丝线月历格 ---------- */
.cal {
  margin-top: 2px;
}
.cal__week {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  padding-bottom: 8px;
}
.cal__week span {
  text-align: center;
  font-size: 10px;
  letter-spacing: 0.16em;
  color: rgba(187, 199, 198, 0.6);
}
.cal__grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  border-bottom: 1px solid var(--au-hair);
}
.cal__cell {
  min-width: 0;
  height: 46px;
  border-top: 1px solid var(--au-hair);
  animation: fade 260ms var(--au-ease) var(--d, 0ms) both;
}
.cal__cell.is-col {
  border-left: 1px solid var(--au-hair);
}
.day {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: 0;
  border-radius: 6px;
  background: none;
  padding: 0;
  font-family: inherit;
  transition: background 180ms var(--au-ease);
}
.day__n {
  font-size: 13px;
  line-height: 1;
  color: var(--au-silver);
}
.day.is-today .day__n {
  color: var(--au-pink);
  border-bottom: 1px solid rgba(253, 233, 255, 0.55);
  padding-bottom: 1px;
}
.day.is-sel {
  background: rgba(203, 255, 252, 0.12);
}
.day.is-sel .day__n {
  color: var(--au-white);
  border-bottom-color: transparent;
}
.day__dots {
  display: flex;
  align-items: center;
  gap: 3px;
  height: 5px;
}
.dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(187, 199, 198, 0.4);
}
.dot.is-pay {
  background: var(--au-lit);
  box-shadow: 0 0 8px rgba(203, 255, 252, 0.85);
}
.dot.is-ex {
  background: var(--au-pink);
  box-shadow: 0 0 8px rgba(253, 233, 255, 0.8);
}
.dot.is-reg {
  background: rgba(237, 255, 254, 0.45);
}
.day__more {
  font-size: 10px;
  font-weight: 400;
  line-height: 1;
  color: rgba(187, 199, 198, 0.6);
}
.legend {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-top: 10px;
  font-size: 10px;
  letter-spacing: 0.04em;
  color: rgba(187, 199, 198, 0.62);
}
.legend__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

/* ---------- 章 04：4 段季度条 + 年度读数 ---------- */
.qbar-wrap {
  margin-top: 18px;
}
.qbar {
  display: flex;
  gap: 3px;
  height: 8px;
}
.qbar__seg {
  position: relative;
  flex: 1;
  min-width: 0;
  border-radius: 2px;
  background: rgba(237, 255, 254, 0.07);
  overflow: hidden;
}
.qbar__fill {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  border-radius: 2px;
  background: linear-gradient(90deg, rgba(203, 255, 252, 0.35), var(--au-lit));
  box-shadow: 0 0 8px rgba(203, 255, 252, 0.35);
  transition: width 700ms var(--au-ease);
}
.qbar__fill.is-empty {
  background: transparent;
  box-shadow: none;
}
.qbar__labels {
  display: flex;
  margin-top: 8px;
  font-size: 10px;
  letter-spacing: 0.08em;
  color: rgba(187, 199, 198, 0.62);
}
.qbar__labels span {
  flex: 1;
  text-align: center;
}
.qbar__labels span:first-child {
  text-align: left;
}
.qbar__labels span:last-child {
  text-align: right;
}
.year-rows {
  margin-top: 16px;
}

/* ---------- 章 03：空态 / 行入场 ---------- */
.blank {
  margin: 0;
  padding: 12px 0;
  font-size: 13px;
  letter-spacing: 0.02em;
  color: rgba(187, 199, 198, 0.6);
}
.anim {
  animation: rise 440ms var(--au-ease) var(--d, 0ms) both;
}

/* ---------- 动效 ---------- */
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
@keyframes fade {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}
@media (prefers-reduced-motion: reduce) {
  .cal__cell,
  .anim,
  .years {
    animation: none;
  }
}
</style>
