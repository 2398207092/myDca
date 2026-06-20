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
    case 'distributed': return { text: '已到账', class: 'text-success bg-success/10' }
    case 'pending': return { text: '待处理', class: 'text-warning bg-warning/10' }
    case 'cancelled': return { text: '已取消', class: 'text-on-surface-variant bg-surface-container-high' }
    default: return { text: status, class: 'text-on-surface-variant bg-surface-container-high' }
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
  <div class="page-container flex flex-col min-h-screen bg-surface">
    <!-- Header -->
    <header class="fixed top-0 left-0 right-0 z-40 bg-surface px-gutter">
      <div class="max-w-[600px] mx-auto flex items-center justify-between h-14">
        <button class="flex items-center gap-1 text-on-surface" @click="goBack">
          <span class="material-symbols-outlined text-2xl">arrow_back</span>
          <span class="text-title-medium font-title-medium">分红记录</span>
        </button>
        <button class="w-9 h-9 flex items-center justify-center rounded-full hover:bg-surface-container-high transition-colors" @click="goHome">
          <span class="material-symbols-outlined text-2xl text-on-surface-variant">home</span>
        </button>
      </div>
    </header>

    <!-- Content -->
    <main class="flex-1 pt-14 pb-8 px-gutter">
      <div class="max-w-[600px] mx-auto space-y-md">
        <!-- Loading -->
        <div v-if="loading" class="flex justify-center items-center py-32">
          <span class="material-symbols-outlined animate-spin text-on-surface-variant text-3xl">progress_activity</span>
        </div>

        <!-- Empty -->
        <div v-else-if="events.length === 0" class="flex flex-col items-center justify-center py-32 gap-md">
          <span class="material-symbols-outlined text-5xl text-on-surface-variant/40">history</span>
          <p class="text-body-medium font-body-medium text-on-surface-variant">暂无分红记录</p>
        </div>

        <!-- Dividend List -->
        <template v-else>
          <!-- Summary -->
          <div class="bg-surface-container-lowest rounded-xl px-lg py-md">
            <p class="text-body-small font-body-small text-on-surface-variant">累计已收分红</p>
            <p class="text-title-large font-title-large text-primary mt-xs">{{ formatAmount(totalDividend) }}</p>
          </div>

          <div class="space-y-xs">
            <div v-for="ev in events" :key="ev.id"
                 class="flex items-center justify-between bg-surface-container-lowest rounded-xl px-lg py-md transition-colors hover:bg-surface-container-high">
              <!-- Left -->
              <div class="flex items-center gap-md">
                <div class="w-10 h-10 rounded-full flex items-center justify-center"
                     :class="ev.status === 'distributed' ? 'bg-success/10' : 'bg-surface-container-high'">
                  <span class="material-symbols-outlined text-xl"
                        :class="ev.status === 'distributed' ? 'text-success' : 'text-on-surface-variant'">
                    {{ ev.status === 'distributed' ? 'check_circle' : 'schedule' }}
                  </span>
                </div>
                <div>
                  <p class="text-label-large font-label-large text-on-surface">{{ ev.date }}</p>
                  <span class="text-body-small font-body-small"
                        v-if="ev.description">{{ ev.description }}</span>
                </div>
              </div>

              <!-- Right -->
              <div class="text-right">
                <p class="text-label-large font-label-large" :class="ev.status === 'distributed' ? 'text-on-surface' : 'text-on-surface-variant'">
                  {{ formatAmount(ev.amount) }}
                </p>
                <span class="inline-block px-sm py-0.5 rounded-full text-caption font-caption mt-xs"
                      :class="statusLabel(ev.status).class">
                  {{ statusLabel(ev.status).text }}
                </span>
              </div>
            </div>
          </div>
        </template>
      </div>
    </main>
  </div>
</template>
