<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listEvents, type DividendEventItem } from '@/api/event'

const route = useRoute()
const router = useRouter()
const holdingId = route.params.id as string

const loading = ref(true)
const events = ref<DividendEventItem[]>([])

const totalDividend = computed(() =>
  events.value
    .filter(e => e.status === 'distributed')
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
          <div v-for="ev in events" :key="ev.id"
               class="flex items-center justify-between bg-card-bg rounded-xl p-lg card-shadow border border-border-light/40 transition-colors">
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
              <p class="font-body text-sm font-medium" :class="ev.status === 'distributed' ? 'text-text-primary' : 'text-text-secondary'">
                {{ formatAmount(ev.amount) }}
              </p>
              <span class="inline-block px-sm py-0.5 rounded-full font-body text-xs mt-xs"
                    :class="statusLabel(ev.status).class">
                {{ statusLabel(ev.status).text }}
              </span>
            </div>
          </div>
        </div>
      </template>
    </main>
  </div>
</template>
