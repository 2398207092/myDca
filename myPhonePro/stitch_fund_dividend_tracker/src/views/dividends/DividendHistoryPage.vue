<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listEvents, markDistributed, convertEventToReinvest, type DividendEventItem } from '@/api/event'

const route = useRoute()
const router = useRouter()
const holdingId = route.params.id as string

const loading = ref(true)
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

onMounted(async () => {
  try {
    events.value = await listEvents({ holdingId })
  } catch (e) {
    console.error('加载分红记录失败', e)
  } finally {
    loading.value = false
  }
})

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

function formatAmount(n: number): string {
  return `¥ ${n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function statusLabel(status: string): { text: string; class: string } {
  switch (status) {
    case 'distributed': return { text: '已到账', class: 'bg-brand-light text-brand' }
    case 'pending': return { text: '待处理', class: 'bg-yellow-500/10 text-yellow-500' }
    case 'cancelled': return { text: '已取消', class: 'bg-card-alt text-text-secondary' }
    default: return { text: status, class: 'bg-card-alt text-text-secondary' }
  }
}

function goBack() {
  router.back()
}

function goHome() {
  router.push({ name: 'home' })
}
</script>

<template>
  <div class="min-h-screen bg-page-bg flex flex-col">
    <!-- Header — 统一 -->
    <header class="flex items-center justify-between px-gutter h-14 sticky top-0 z-50 bg-card-bg border-b border-border-light/40">
      <button @click="goBack" class="w-10 h-10 flex items-center justify-center -ml-2 active:opacity-80">
        <span class="material-symbols-outlined text-text-secondary">arrow_back</span>
      </button>
      <div class="flex-1 text-center">
        <h1 class="font-body text-md font-medium text-text-primary">分红记录</h1>
      </div>
      <button @click="goHome" class="w-10 h-10 flex items-center justify-center active:opacity-80 transition-opacity">
        <span class="material-symbols-outlined text-text-secondary">home</span>
      </button>
    </header>

    <!-- Content -->
    <main class="flex-1 px-gutter pb-8 space-y-md">
      <!-- Loading -->
      <div v-if="loading" class="flex justify-center items-center py-32">
        <span class="material-symbols-outlined animate-spin text-text-tertiary text-3xl">progress_activity</span>
      </div>

      <!-- Empty -->
      <div v-else-if="events.length === 0" class="flex flex-col items-center justify-center py-32 gap-md">
        <span class="text-5xl block text-text-tertiary/40">📋</span>
        <p class="font-body text-sm text-text-secondary">暂无分红记录</p>
      </div>

      <!-- Dividend List -->
      <template v-else>
        <!-- Summary -->
        <div class="bg-card-bg rounded-xl p-lg card-shadow border border-border-light/40">
          <p class="font-body text-xs text-text-tertiary">累计已收分红</p>
          <p class="font-display text-xl font-semibold text-brand mt-xs">{{ formatAmount(totalDividend) }}</p>
        </div>

        <div class="space-y-xs">
          <div v-for="ev in payoutEvents" :key="ev.id"
               class="flex items-center justify-between bg-card-bg rounded-xl p-lg card-shadow border border-border-light/40 transition-colors cursor-pointer active:scale-[0.98]"
               @click="showEventActions(ev)">
            <!-- Left -->
            <div class="flex items-center gap-md">
              <div class="w-10 h-10 rounded-full flex items-center justify-center"
                   :class="ev.status === 'distributed' ? 'bg-brand-light' : 'bg-card-alt'">
                <span class="material-symbols-outlined text-xl"
                      :class="ev.status === 'distributed' ? 'text-brand' : 'text-text-secondary'">
                  {{ ev.status === 'distributed' ? 'check_circle' : 'schedule' }}
                </span>
              </div>
              <div>
                <p class="font-body text-sm font-medium text-text-primary">{{ ev.date }}</p>
                <span class="font-body text-xs text-text-tertiary"
                      v-if="ev.description">{{ ev.description }}</span>
              </div>
            </div>

            <!-- Right -->
            <div class="text-right">
              <template v-if="ev.participated">
                <p class="font-body text-sm font-medium" :class="ev.status === 'distributed' ? 'text-text-primary' : 'text-text-secondary'">
                  {{ formatAmount(ev.amount) }}
                </p>
              </template>
              <p v-else class="font-body text-xs text-text-tertiary/50 mt-1">未参与</p>
              <div class="flex items-center gap-1 justify-end mt-xs">
                <span class="inline-block px-sm py-0.5 rounded-full font-body text-xs"
                      :class="statusLabel(ev.status).class">
                  {{ statusLabel(ev.status).text }}
                </span>
                <span v-if="ev.status === 'distributed' && ev.converted"
                      class="inline-block px-sm py-0.5 rounded-full font-body text-xs bg-brand-light text-brand">
                  已复投
                </span>
              </div>
            </div>
          </div>
        </div>
      </template>
    </main>

    <!-- === Action Sheet === -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showActionSheet" class="fixed inset-0 z-[100] bg-black/40" @click="closeActionSheet"></div>
      </Transition>
      <Transition name="slide-up">
        <div v-if="showActionSheet && selectedEvent"
             class="fixed bottom-0 left-0 right-0 z-[110] bg-card-bg rounded-t-2xl px-gutter py-lg shadow-overlay">
          <div class="w-10 h-1 bg-border-light rounded-full mx-auto mb-lg"></div>
          <!-- Event info -->
          <div class="text-center mb-md">
            <p class="font-body text-sm font-medium text-text-primary">分红发放日</p>
            <p class="font-display text-lg font-semibold text-text-primary">{{ selectedEvent.date }}</p>
            <div class="flex items-center justify-center gap-lg mt-sm">
              <div v-if="relatedDates.registration" class="text-center">
                <p class="font-body text-[11px] text-text-tertiary">登记日</p>
                <p class="font-body text-xs text-text-secondary mt-0.5">{{ relatedDates.registration }}</p>
              </div>
              <div v-if="relatedDates.exDividend" class="text-center">
                <p class="font-body text-[11px] text-text-tertiary">除息日</p>
                <p class="font-body text-xs text-text-secondary mt-0.5">{{ relatedDates.exDividend }}</p>
              </div>
            </div>
            <p class="font-body text-xs text-text-tertiary mt-sm">{{ selectedEvent.description }}</p>
            <p class="font-display text-lg font-semibold text-text-primary mt-sm">{{ formatAmount(selectedEvent.amount) }}</p>
          </div>

          <!-- 未参与 → 仅展示 -->
          <div v-if="!selectedEvent.participated"
               class="w-full text-center px-md py-lg rounded-xl bg-card-alt">
            <span class="font-body text-sm text-text-tertiary">未参与本次分红</span>
          </div>

          <!-- 已复投 → 仅展示 -->
          <div v-else-if="selectedEvent.converted"
               class="w-full text-center px-md py-lg rounded-xl bg-card-alt">
            <span class="font-body text-sm text-brand">已转为复投份额</span>
          </div>

          <!-- pending + participated → 标记到账 -->
          <button v-if="selectedEvent.participated && selectedEvent.status === 'pending'"
                  class="w-full flex items-center gap-md px-md py-lg rounded-xl hover:bg-card-alt transition-colors"
                  :disabled="distributingId === selectedEvent.id"
                  @click="handleMarkDistributed(selectedEvent.id)">
            <span class="material-symbols-outlined text-brand">check_circle</span>
            <span class="font-body text-sm font-medium text-text-primary">
              {{ distributingId === selectedEvent.id ? '标记中...' : '标记为已到账' }}
            </span>
          </button>

          <!-- distributed + participated + 未复投 → 转为复投 -->
          <button v-if="selectedEvent.participated && selectedEvent.status === 'distributed' && !selectedEvent.converted"
                  class="w-full flex items-center gap-md px-md py-lg rounded-xl hover:bg-card-alt transition-colors"
                  :disabled="convertingId === selectedEvent.id"
                  @click="handleConvertToReinvest(selectedEvent.id)">
            <span class="material-symbols-outlined text-brand">autorenew</span>
            <span class="font-body text-sm font-medium text-text-primary">
              {{ convertingId === selectedEvent.id ? '转换中...' : '转为复投' }}
            </span>
          </button>

          <!-- 取消 -->
          <button class="w-full mt-md h-12 rounded-xl bg-card-alt text-text-secondary font-body font-medium text-md transition-colors active:scale-[0.98]"
                  @click="closeActionSheet">
            取消
          </button>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.fade-enter-active { transition: opacity 0.2s ease; }
.fade-leave-active { transition: opacity 0.15s ease; }
.fade-enter-from,
.fade-leave-to { opacity: 0; }

.slide-up-enter-active { transition: transform 0.3s cubic-bezier(0.32, 0.72, 0, 1); }
.slide-up-leave-active { transition: transform 0.2s ease; }
.slide-up-enter-from { transform: translateY(100%); }
.slide-up-leave-to { transform: translateY(100%); }
</style>
