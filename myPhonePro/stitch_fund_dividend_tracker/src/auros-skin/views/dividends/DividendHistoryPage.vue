<script setup lang="ts">
/**
 * 分红记录 · 四层信息骨架 v3（2026-11-08）
 * 章 01 DIVIDEND 累计已收：AurosStat（56 / pink）+ AurosRail（到账进度）+ 筛选胶囊
 * 章 02 RECORDS 发放流水：按年份分组（≤3 组），每组 ≤5 行，超出用 AurosDisclosure
 * 业务逻辑与接口不变，只重排信息结构。
 */
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { listEvents, markDistributed, convertEventToReinvest, type DividendEventItem } from '@/api/event'
import { formatMoney } from '@/auros-skin/utils/format'
import AurosNavBar from '@/auros-skin/components/auros/AurosNavBar.vue'
import AurosChapter from '@/auros-skin/components/auros/AurosChapter.vue'
import AurosStat from '@/auros-skin/components/auros/AurosStat.vue'
import AurosRail from '@/auros-skin/components/auros/AurosRail.vue'
import AurosGroup from '@/auros-skin/components/auros/AurosGroup.vue'
import AurosRow from '@/auros-skin/components/auros/AurosRow.vue'
import AurosDisclosure from '@/auros-skin/components/auros/AurosDisclosure.vue'
import AurosButton from '@/auros-skin/components/auros/AurosButton.vue'
import AurosSheet from '@/auros-skin/components/auros/AurosSheet.vue'
import AurosState from '@/auros-skin/components/auros/AurosState.vue'

const route = useRoute()
const holdingId = route.params.id as string

const loadState = ref<'loading' | 'ready' | 'error'>('loading')
const events = ref<DividendEventItem[]>([])

// 只展示 payout（分红发放）事件
const payoutEvents = computed(() =>
  events.value
    .filter(e => e.type === 'payout')
    .sort((a, b) => b.date.localeCompare(a.date))
)

// 查找同一分红的权益登记日和除权除息日
function findRelatedDates(payoutEvent: DividendEventItem): { registration?: string; exDividend?: string } {
  // 用 description 中"每份 X 元"的尾部匹配
  const suffix = payoutEvent.description.replace(/^(分红发放|除权除息|权益登记日)\s*·\s*/, '· ')
  const related = events.value.filter(e =>
    e.holdingId === payoutEvent.holdingId &&
    e.description.endsWith(suffix) &&
    e.id !== payoutEvent.id
  )
  return {
    registration: related.find(e => e.type === 'registration')?.date,
    exDividend: related.find(e => e.type === 'ex_dividend')?.date,
  }
}

const convertingId = ref<string | null>(null)
const distributingId = ref<string | null>(null)

const relatedDates = ref<{ registration?: string; exDividend?: string }>({})

async function handleConvertToReinvest(eventId: string) {
  convertingId.value = eventId
  try {
    await convertEventToReinvest(eventId)
    events.value = await listEvents({ holdingId })
    closeActionSheet()
  } catch (e) {
    console.error('转为复投失败', e)
  } finally {
    convertingId.value = null
  }
}

async function handleMarkDistributed(eventId: string) {
  distributingId.value = eventId
  try {
    await markDistributed(eventId)
    events.value = await listEvents({ holdingId })
    closeActionSheet()
  } catch (e) {
    console.error('标记到账失败', e)
  } finally {
    distributingId.value = null
  }
}

const totalDividend = computed(() =>
  events.value
    .filter(e => e.participated && e.status === 'distributed')
    .reduce((sum, e) => sum + (e.amount || 0), 0)
)

const distributedCount = computed(() => payoutEvents.value.filter(e => e.status === 'distributed').length)

/** 到账进度：已到账笔数 / 发放笔数 */
const settledPct = computed(() =>
  payoutEvents.value.length === 0 ? 0 : Math.round((distributedCount.value / payoutEvents.value.length) * 100)
)

// === 筛选（纯 UI 态） ===
const filterYear = ref('all')
const filterStatus = ref('all')

const yearOptions = computed(() => {
  const years = Array.from(new Set(payoutEvents.value.map(e => e.date.slice(0, 4))))
  return ['all', ...years.sort((a, b) => b.localeCompare(a))]
})

const statusOptions = [
  { value: 'all', label: '全部' },
  { value: 'distributed', label: '已到账' },
  { value: 'pending', label: '待处理' },
  { value: 'cancelled', label: '已取消' },
]

const filteredEvents = computed(() =>
  payoutEvents.value.filter(e =>
    (filterYear.value === 'all' || e.date.startsWith(filterYear.value)) &&
    (filterStatus.value === 'all' || e.status === filterStatus.value)
  )
)

function resetFilters() {
  filterYear.value = 'all'
  filterStatus.value = 'all'
}

// === 章 02：按年份分组（≤3 组，每组 ≤5 行） ===
interface YearGroup {
  key: string
  title: string
  count: string
  total: string
  shown: DividendEventItem[]
  rest: DividendEventItem[]
}

const yearKeys = computed(() => {
  const set = new Set(filteredEvents.value.map(e => e.date.slice(0, 4)))
  return Array.from(set).sort((a, b) => b.localeCompare(a))
})

function eventsOfYear(year: string): DividendEventItem[] {
  return filteredEvents.value.filter(e => e.date.startsWith(year))
}

function makeYearGroup(year: string, list: DividendEventItem[]): YearGroup {
  return {
    key: year,
    title: `${year} 年`,
    count: `${list.length} 笔`,
    total: formatMoney(list.reduce((s, e) => s + (e.amount || 0), 0)),
    shown: list.slice(0, 5),
    rest: list.slice(5),
  }
}

const visibleYearGroups = computed(() => yearKeys.value.slice(0, 3).map(y => makeYearGroup(y, eventsOfYear(y))))
/** 第 4 年及更早：超出组预算，统一折叠 */
const olderEvents = computed(() => yearKeys.value.slice(3).flatMap(y => eventsOfYear(y)))

async function loadEvents() {
  loadState.value = 'loading'
  try {
    events.value = await listEvents({ holdingId })
    loadState.value = 'ready'
  } catch (e) {
    console.error('加载分红记录失败', e)
    loadState.value = 'error'
  }
}

onMounted(loadEvents)

// === 操作弹窗 ===
const selectedEvent = ref<DividendEventItem | null>(null)
const showActionSheet = ref(false)

function showEventActions(ev: DividendEventItem) {
  selectedEvent.value = ev
  relatedDates.value = findRelatedDates(ev)
  showActionSheet.value = true
}

function closeActionSheet() {
  showActionSheet.value = false
  selectedEvent.value = null
}

function statusText(status: string): string {
  switch (status) {
    case 'distributed': return '已到账'
    case 'pending': return '待处理'
    case 'cancelled': return '已取消'
    default: return status
  }
}

/** 状态点：lit=已到账 / pink=待处理 / dim=已取消 */
function statusMark(status: string): 'lit' | 'pink' | 'dim' {
  if (status === 'distributed') return 'lit'
  if (status === 'pending') return 'pink'
  return 'dim'
}

/** 行副行：日期（已复投追加说明） */
function rowSub(ev: DividendEventItem): string {
  return ev.converted ? `${ev.date} · 已复投` : ev.date
}

const totalText = computed(() => formatMoney(totalDividend.value))

const statusDetail = computed(() => {
  const ev = selectedEvent.value
  if (!ev) return ''
  return [
    statusText(ev.status),
    ev.participated ? '已参与' : '未参与',
    ev.converted ? '已复投' : '',
  ].filter(Boolean).join(' · ')
})
</script>

<template>
  <div class="page">
    <AurosNavBar title="分红记录" />

    <main class="body">
      <AurosState v-if="loadState === 'loading'" state="loading" :rows="5" />

      <AurosState
        v-else-if="loadState === 'error'"
        state="error"
        text="分红记录没有取到"
        action-label="重试"
        @action="loadEvents"
      />

      <AurosState
        v-else-if="payoutEvents.length === 0"
        state="empty"
        text="暂无分红记录。等这只持仓派息后，每一笔都会出现在这里。"
      />

      <template v-else>
        <!-- 01 累计已收：主角数 + 形状 + 筛选 -->
        <AurosChapter label="DIVIDEND" title="累计已收" :gap="false">
          <AurosStat :value="totalText" pink caption="全部到账分红合计" />
          <div class="rail-wrap">
            <AurosRail
              :percent="settledPct"
              :label-left="`已到账 ${distributedCount} 笔`"
              :label-right="`共 ${payoutEvents.length} 笔`"
            />
          </div>

          <div class="filters">
            <div class="filters__row">
              <span class="filters__k">YEAR</span>
              <div class="pills">
                <button
                  v-for="y in yearOptions"
                  :key="y"
                  type="button"
                  class="pill"
                  :class="{ 'is-on': filterYear === y }"
                  :aria-pressed="filterYear === y"
                  @click="filterYear = y"
                >
                  {{ y === 'all' ? '全部' : y }}
                </button>
              </div>
            </div>
            <div class="filters__row">
              <span class="filters__k">STATUS</span>
              <div class="pills">
                <button
                  v-for="s in statusOptions"
                  :key="s.value"
                  type="button"
                  class="pill"
                  :class="{ 'is-on': filterStatus === s.value }"
                  :aria-pressed="filterStatus === s.value"
                  @click="filterStatus = s.value"
                >
                  {{ s.label }}
                </button>
              </div>
            </div>
          </div>
        </AurosChapter>

        <!-- 02 发放流水：按年份分组 -->
        <AurosChapter label="RECORDS" title="发放流水" meta="按年份">
          <AurosGroup
            v-for="g in visibleYearGroups"
            :key="g.key"
            :title="g.title"
            :count="g.count"
            :total="g.total"
          >
            <AurosRow
              v-for="ev in g.shown"
              :key="ev.id"
              :label="ev.description"
              :sub="rowSub(ev)"
              :value="ev.participated ? formatMoney(ev.amount) : '未参与'"
              :mark="statusMark(ev.status)"
              :tone="ev.participated ? 'default' : 'muted'"
              clickable
              chevron
              @click="showEventActions(ev)"
            />
            <AurosDisclosure v-if="g.rest.length" :count="g.rest.length">
              <AurosRow
                v-for="ev in g.rest"
                :key="ev.id"
                :label="ev.description"
                :sub="rowSub(ev)"
                :value="ev.participated ? formatMoney(ev.amount) : '未参与'"
                :mark="statusMark(ev.status)"
                :tone="ev.participated ? 'default' : 'muted'"
                clickable
                chevron
                @click="showEventActions(ev)"
              />
            </AurosDisclosure>
          </AurosGroup>

          <AurosDisclosure v-if="olderEvents.length" :count="olderEvents.length">
            <AurosRow
              v-for="ev in olderEvents"
              :key="ev.id"
              :label="ev.description"
              :sub="rowSub(ev)"
              :value="ev.participated ? formatMoney(ev.amount) : '未参与'"
              :mark="statusMark(ev.status)"
              :tone="ev.participated ? 'default' : 'muted'"
              clickable
              chevron
              @click="showEventActions(ev)"
            />
          </AurosDisclosure>

          <AurosState
            v-if="filteredEvents.length === 0"
            state="empty"
            text="当前筛选下没有分红记录"
            action-label="显示全部"
            @action="resetFilters"
          />
        </AurosChapter>
      </template>
    </main>

    <!-- 分红详情 / 操作 -->
    <AurosSheet v-model="showActionSheet" title="分红详情" @close="selectedEvent = null">
      <div v-if="selectedEvent" class="detail">
        <div class="detail__rows">
          <AurosRow label="状态" :value="statusDetail" :mark="statusMark(selectedEvent.status)" />
          <AurosRow label="发放日" :sub="selectedEvent.description" :value="selectedEvent.date" />
          <AurosRow label="金额" :value="formatMoney(selectedEvent.amount)" />
          <AurosRow
            v-if="relatedDates.registration"
            label="权益登记日"
            :value="relatedDates.registration"
          />
          <AurosRow
            v-if="relatedDates.exDividend"
            label="除权除息日"
            :value="relatedDates.exDividend"
          />
        </div>

        <div class="detail__acts">
          <AurosButton
            v-if="selectedEvent.participated && selectedEvent.status === 'pending'"
            variant="primary"
            block
            :disabled="distributingId === selectedEvent.id"
            @click="handleMarkDistributed(selectedEvent.id)"
          >
            {{ distributingId === selectedEvent.id ? '标记中…' : '标记为已到账' }}
          </AurosButton>

          <AurosButton
            v-else-if="selectedEvent.participated && selectedEvent.status === 'distributed' && !selectedEvent.converted"
            variant="primary"
            block
            :disabled="convertingId === selectedEvent.id"
            @click="handleConvertToReinvest(selectedEvent.id)"
          >
            {{ convertingId === selectedEvent.id ? '转换中…' : '转为复投' }}
          </AurosButton>

          <AurosButton variant="ghost" block @click="closeActionSheet">关闭</AurosButton>
        </div>
      </div>
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
  padding: 8px 16px calc(env(safe-area-inset-bottom, 0px) + 40px);
}

/* 章 01：形状与筛选 */
.rail-wrap {
  margin-top: 18px;
}
.filters {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.filters__row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.filters__k {
  flex: none;
  width: 44px;
  padding-top: 6px;
  font-size: 10px;
  letter-spacing: 0.14em;
  color: rgba(187, 199, 198, 0.55);
}
.pills {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.pill {
  height: 28px;
  padding: 0 10px;
  border: 0;
  border-radius: 6px;
  background: rgba(0, 55, 52, 0.55);
  color: rgba(187, 199, 198, 0.8);
  font-family: inherit;
  font-size: 13px;
  letter-spacing: 0.02em;
  transition: background 200ms var(--au-ease), color 200ms var(--au-ease);
}
.pill.is-on {
  background: rgba(203, 255, 252, 0.14);
  color: var(--au-mist);
}
.pill:active {
  transform: scale(0.97);
}

/* 弹层 */
.detail {
  padding: 2px 0 6px;
}
.detail__rows + .detail__acts {
  margin-top: 18px;
}
.detail__acts {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

@media (prefers-reduced-motion: reduce) {
  .pill {
    transition: none;
  }
}
</style>
