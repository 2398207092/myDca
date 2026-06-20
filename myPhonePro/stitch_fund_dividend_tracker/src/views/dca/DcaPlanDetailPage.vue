<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getDcaPlan, updateDcaPlan, deleteDcaPlan, executeDcaPlan, type DcaPlanVO } from '@/api/dca'
import { listTransactions, type TransactionItem } from '@/api/transaction'
import PageStateComp from '@/components/shared/PageState.vue'
import DcaExecuteSheet from '@/components/dca/DcaExecuteSheet.vue'

const route = useRoute()
const router = useRouter()

const pageState = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const plan = ref<DcaPlanVO | null>(null)
const dcaTransactions = ref<TransactionItem[]>([])

// Sheet state
const showExecuteSheet = ref(false)
const showDeleteConfirm = ref(false)
const updating = ref(false)

const planId = computed(() => route.params.id as string)

async function loadData() {
  pageState.value = 'loading'
  try {
    const p = await getDcaPlan(planId.value)
    plan.value = p

    // Fetch transactions for this holding and filter by source === 'dca'
    const allTxs = await listTransactions(p.holdingId)
    // Filter by dca plan
    dcaTransactions.value = allTxs.filter(tx => tx.source === 'dca' && tx.dcaPlanId === p.id)
  } catch (e) {
    console.error('加载定投计划详情失败:', e)
    pageState.value = 'error'
    return
  }
  pageState.value = 'ready'
}

watch(() => route.params.id, (newId) => {
  if (newId && route.name === 'dca-plan-detail') loadData()
})

onMounted(loadData)

function goBack() {
  router.back()
}

function goHome() {
  router.push({ name: 'home' })
}

// === Status helpers ===
const statusInfo = computed(() => {
  if (!plan.value) return { label: '', icon: '' }
  switch (plan.value.status) {
    case 'active':
      return { label: '进行中', icon: 'play_arrow' }
    case 'paused':
      return { label: '已暂停', icon: 'pause' }
    case 'ended':
      return { label: '已终止', icon: 'stop' }
    default:
      return { label: plan.value.status, icon: 'circle' }
  }
})

const frequencyLabel = computed(() => {
  if (!plan.value) return ''
  switch (plan.value.frequency) {
    case 'daily': return '每日'
    case 'weekly': return '每周'
    case 'biweekly': return '双周'
    case 'monthly': return '每月'
    default: return plan.value.frequency
  }
})

const dayLabel = computed(() => {
  if (!plan.value || plan.value.day == null) return '--'
  if (plan.value.frequency === 'weekly' || plan.value.frequency === 'biweekly') {
    const labels = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']
    return labels[plan.value.day] || `周${plan.value.day}`
  }
  return `每月${plan.value.day}日`
})

// === Actions ===
const avgCost = computed(() => {
  if (!plan.value || plan.value.totalShares === 0) return '0.0000'
  return (plan.value.totalInvested / plan.value.totalShares).toLocaleString('zh-CN', { minimumFractionDigits: 4, maximumFractionDigits: 4 })
})

function handleExecute() {
  showExecuteSheet.value = true
}

function handleExecuted() {
  showExecuteSheet.value = false
  loadData()
}

async function handlePauseResume() {
  if (!plan.value) return
  updating.value = true
  try {
    const newStatus = plan.value.status === 'active' ? 'paused' : 'active'
    await updateDcaPlan(plan.value.id, { status: newStatus })
    await loadData()
  } catch (e) {
    console.error('操作失败:', e)
  } finally {
    updating.value = false
  }
}

async function handleEnd() {
  if (!plan.value) return
  updating.value = true
  try {
    await updateDcaPlan(plan.value.id, { status: 'ended' })
    await loadData()
  } catch (e) {
    console.error('终止失败:', e)
  } finally {
    updating.value = false
  }
}

async function handleDelete() {
  if (!plan.value) return
  updating.value = true
  try {
    await deleteDcaPlan(plan.value.id)
    router.back()
  } catch (e) {
    console.error('删除失败:', e)
  } finally {
    updating.value = false
    showDeleteConfirm.value = false
  }
}

function formatAmount(n: number): string {
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatQuantity(n: number): string {
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 })
}
</script>

<template>
  <div class="min-h-screen bg-background">
    <!-- Header -->
    <header class="fixed top-0 w-full z-50 bg-surface shadow-sm">
      <div class="flex items-center justify-between px-gutter h-14 w-full max-w-[600px] mx-auto">
        <div class="flex items-center gap-2">
          <button
            class="w-10 h-10 flex items-center justify-center -ml-2 active:opacity-80 transition-opacity"
            @click="goBack"
          >
            <span class="material-symbols-outlined text-on-surface-variant">arrow_back</span>
          </button>
          <h1 class="font-headline-md text-headline-md text-on-surface font-bold">定投计划</h1>
        </div>
        <button
          class="w-10 h-10 flex items-center justify-center active:opacity-80 transition-opacity"
          @click="goHome"
        >
          <span class="material-symbols-outlined text-on-surface-variant">home</span>
        </button>
      </div>
    </header>

    <!-- Page State -->
    <PageStateComp v-if="pageState !== 'ready'" :state="pageState" @retry="loadData" />

    <!-- Main Content -->
    <main
      v-if="pageState === 'ready' && plan"
      class="pt-20 pb-32 px-gutter space-y-md max-w-[600px] mx-auto"
    >
      <!-- Holding Name + Status -->
      <div class="flex items-start gap-sm px-lg">
        <div class="flex-1 min-w-0">
          <h2 class="font-headline-md text-headline-md text-on-surface font-bold truncate">{{ plan.holdingName }}</h2>
          <p class="text-caption font-caption text-on-surface-variant">{{ plan.holdingCode }}</p>
        </div>
        <span
          class="shrink-0 flex items-center gap-[2px] px-sm py-0.5 rounded-full text-label-small font-label-small whitespace-nowrap"
          :class="plan?.status === 'active' ? 'bg-green-500/10 text-green-500' : plan?.status === 'paused' ? 'bg-yellow-500/10 text-yellow-500' : 'bg-surface-container-high text-on-surface-variant'"
        >
          <span class="material-symbols-outlined text-[12px]">{{ statusInfo.icon }}</span>
          {{ statusInfo.label }}
        </span>
      </div>

      <!-- Summary Cards: 2x2 Grid -->
      <section class="grid grid-cols-2 gap-md">
        <div class="bg-surface-container-lowest rounded-xl p-md card-shadow">
          <p class="text-caption font-caption text-on-surface-variant mb-1">累计投入</p>
          <p class="text-headline-md font-headline-md text-primary">
            ¥{{ formatAmount(plan.totalInvested) }}
          </p>
        </div>
        <div class="bg-surface-container-lowest rounded-xl p-md card-shadow">
          <p class="text-caption font-caption text-on-surface-variant mb-1">累计期数</p>
          <p class="text-headline-md font-headline-md text-on-surface">
            {{ plan.totalExecutions }}<span class="text-[12px] ml-1">期</span>
          </p>
        </div>
        <div class="bg-surface-container-lowest rounded-xl p-md card-shadow">
          <p class="text-caption font-caption text-on-surface-variant mb-1">平均成本</p>
          <p class="text-headline-md font-headline-md text-on-surface">
            ¥{{ avgCost }}<span class="text-[12px] ml-1">/份</span>
          </p>
        </div>
        <div class="bg-surface-container-lowest rounded-xl p-md card-shadow">
          <p class="text-caption font-caption text-on-surface-variant mb-1">累计买入份额</p>
          <p class="text-headline-md font-headline-md text-on-surface">
            {{ formatQuantity(plan.totalShares) }}<span class="text-[12px] ml-1">份</span>
          </p>
        </div>
      </section>

      <!-- Plan Info -->
      <section class="bg-surface-container-lowest rounded-xl p-md card-shadow space-y-sm">
        <h3 class="font-label-bold text-label-bold text-on-surface mb-sm">计划信息</h3>
        <div class="flex justify-between items-center border-b border-surface-variant pb-2">
          <span class="text-caption font-caption text-on-surface-variant">每期金额</span>
          <span class="text-body-sm font-body-sm text-on-surface">¥{{ formatAmount(plan.amount) }}</span>
        </div>
        <div class="flex justify-between items-center border-b border-surface-variant pb-2">
          <span class="text-caption font-caption text-on-surface-variant">频率</span>
          <span class="text-body-sm font-body-sm text-on-surface">{{ frequencyLabel }}</span>
        </div>
        <div class="flex justify-between items-center border-b border-surface-variant pb-2">
          <span class="text-caption font-caption text-on-surface-variant">扣款日</span>
          <span class="text-body-sm font-body-sm text-on-surface">{{ dayLabel }}</span>
        </div>
        <div class="flex justify-between items-center border-b border-surface-variant pb-2">
          <span class="text-caption font-caption text-on-surface-variant">下次执行</span>
          <span class="text-body-sm font-body-sm text-on-surface">{{ plan.nextExecutionDate || '--' }}</span>
        </div>
        <div class="flex justify-between items-center">
          <span class="text-caption font-caption text-on-surface-variant">开始日期</span>
          <span class="text-body-sm font-body-sm text-on-surface">{{ plan.startedAt }}</span>
        </div>
      </section>

      <!-- Execution History Table -->
      <section class="bg-surface-container-lowest rounded-xl p-md card-shadow">
        <h3 class="font-label-bold text-label-bold text-on-surface mb-md">执行记录</h3>
        <div v-if="dcaTransactions.length === 0" class="text-center py-lg">
          <span class="material-symbols-outlined text-3xl text-on-surface-variant/40 mb-sm">receipt_long</span>
          <p class="text-caption font-caption text-on-surface-variant">暂无执行记录</p>
        </div>
        <div v-else class="space-y-xs">
          <div
            v-for="tx in dcaTransactions"
            :key="tx.id"
            class="flex items-center justify-between py-sm border-b border-surface-variant last:border-b-0"
          >
            <div>
              <p class="text-body-small font-body-small text-on-surface">{{ tx.date }}</p>
              <p class="text-caption font-caption text-on-surface-variant">
                @ ¥{{ formatAmount(tx.price) }}
              </p>
            </div>
            <div class="text-right">
              <p class="text-body-small font-body-small text-on-surface">¥{{ formatAmount(tx.total) }}</p>
              <p class="text-caption font-caption text-on-surface-variant">{{ formatQuantity(tx.quantity) }} 份</p>
            </div>
          </div>
        </div>
      </section>

      <!-- Action Buttons -->
      <section class="space-y-sm">
        <button
          v-if="plan.status === 'active'"
          class="w-full h-12 rounded-xl bg-primary-container text-on-primary-container text-label-large font-label-large transition-all hover:brightness-95 active:scale-[0.98] flex items-center justify-center gap-sm"
          @click="handleExecute"
        >
          <span class="material-symbols-outlined">play_arrow</span>
          <span>执行一期</span>
        </button>

        <div class="grid grid-cols-2 gap-sm">
          <button
            class="h-12 rounded-xl bg-surface-container-high text-on-surface-variant text-label-large font-label-large transition-all hover:bg-surface-container-highest active:scale-[0.98] flex items-center justify-center gap-sm disabled:opacity-50"
            :disabled="updating"
            @click="handlePauseResume"
          >
            <span class="material-symbols-outlined">
              {{ plan.status === 'active' ? 'pause' : 'play_arrow' }}
            </span>
            <span>{{ plan.status === 'active' ? '暂停' : '恢复' }}</span>
          </button>

          <button
            v-if="plan.status !== 'ended'"
            class="h-12 rounded-xl bg-surface-container-high text-on-surface-variant text-label-large font-label-large transition-all hover:bg-surface-container-highest active:scale-[0.98] flex items-center justify-center gap-sm disabled:opacity-50"
            :disabled="updating"
            @click="handleEnd"
          >
            <span class="material-symbols-outlined">stop</span>
            <span>终止</span>
          </button>
        </div>

        <button
          class="w-full h-12 rounded-xl bg-error-container/20 text-error text-label-large font-label-large transition-all hover:bg-error-container/30 active:scale-[0.98] flex items-center justify-center gap-sm disabled:opacity-50"
          :disabled="updating"
          @click="showDeleteConfirm = true"
        >
          <span class="material-symbols-outlined">delete</span>
          <span>删除计划</span>
        </button>
      </section>

      <!-- Bottom decoration -->
      <div class="relative w-full rounded-2xl overflow-hidden py-md">
        <p class="text-on-surface-variant text-caption text-center italic">
          &ldquo;时间是最好的朋友&rdquo;
        </p>
      </div>
    </main>

    <!-- === Execute Sheet === -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showExecuteSheet" class="fixed inset-0 z-[100] bg-black/40" @click="showExecuteSheet = false"></div>
      </Transition>
      <Transition name="scale-up">
        <div v-if="showExecuteSheet" class="fixed inset-0 z-[110] flex items-center justify-center px-gutter" @click.self="showExecuteSheet = false">
          <DcaExecuteSheet
            :plan-id="planId"
            :holding-name="plan?.holdingName || ''"
            :amount="plan?.amount || 0"
            @close="showExecuteSheet = false"
            @executed="handleExecuted"
          />
        </div>
      </Transition>
    </Teleport>

    <!-- === Delete Confirm Dialog === -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showDeleteConfirm" class="fixed inset-0 z-[100] bg-black/40" @click="showDeleteConfirm = false"></div>
      </Transition>
      <Transition name="scale-up">
        <div v-if="showDeleteConfirm"
             class="fixed inset-0 z-[110] flex items-center justify-center"
             @click.self="showDeleteConfirm = false">
          <div class="bg-surface rounded-2xl px-xl py-lg mx-gutter max-w-sm w-full shadow-2xl">
            <div class="flex flex-col items-center text-center">
              <div class="w-12 h-12 rounded-full bg-error/10 flex items-center justify-center mb-md">
                <span class="material-symbols-outlined text-2xl text-error">delete_forever</span>
              </div>
              <h3 class="text-title-large font-title-large text-on-surface mb-sm">确认删除</h3>
              <p class="text-body-medium font-body-medium text-on-surface-variant mb-xl">删除后定投计划数据将不可恢复，确定要继续吗？</p>
              <div class="flex gap-md w-full">
                <button
                  class="flex-1 h-12 rounded-xl bg-surface-container-high text-on-surface-variant text-label-large font-label-large transition-colors hover:bg-surface-container-highest active:scale-[0.98]"
                  @click="showDeleteConfirm = false"
                >
                  取消
                </button>
                <button
                  class="flex-1 h-12 rounded-xl bg-error text-on-error text-label-large font-label-large transition-colors hover:brightness-95 active:scale-[0.98] flex items-center justify-center gap-sm disabled:opacity-50"
                  :disabled="updating"
                  @click="handleDelete"
                >
                  <span class="material-symbols-outlined">delete</span>
                  <span>{{ updating ? '删除中...' : '删除' }}</span>
                </button>
              </div>
            </div>
          </div>
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

.scale-up-enter-active { transition: transform 0.2s ease, opacity 0.2s ease; }
.scale-up-leave-active { transition: transform 0.15s ease, opacity 0.15s ease; }
.scale-up-enter-from { transform: scale(0.9); opacity: 0; }
.scale-up-leave-to { transform: scale(0.9); opacity: 0; }
</style>
