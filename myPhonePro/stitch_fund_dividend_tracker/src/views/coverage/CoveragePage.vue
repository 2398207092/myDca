<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getCoverageData } from '@/api/expense'
import type { CoverageData } from '@/api/expense'
import AppHeader from '@/components/shared/AppHeader.vue'
import PageStateComp from '@/components/shared/PageState.vue'

const router = useRouter()
const pageState = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const coverage = ref<CoverageData | null>(null)

const milestones = [
  { name: '初出茅庐', icon: '🌱', requiredExpenses: 1 },
  { name: '小有所成', icon: '🌿', requiredExpenses: 2 },
  { name: '渐入佳境', icon: '🌳', requiredExpenses: 3 },
  { name: '收益达人', icon: '🏆', requiredExpenses: 4 },
  { name: '财务自由', icon: '👑', requiredExpenses: 5 },
]

const progressPercent = computed(() => {
  if (!coverage.value || coverage.value.totalExpenses === 0) return 0
  return Math.round((coverage.value.coveredExpenses / coverage.value.totalExpenses) * 100)
})

const nextTargetExpense = computed(() => {
  if (!coverage.value) return null
  return coverage.value.expenses.find(e => e.inProgress) ?? null
})

function formatMoney(value: number | undefined | null): string {
  if (value == null || value === 0) return '¥0.00'
  if (value >= 1_0000_0000) return `¥${(value / 1_0000_0000).toFixed(2)}亿`
  if (value >= 1_0000) return `¥${(value / 1_0000).toFixed(2)}万`
  return `¥${value.toFixed(2)}`
}

async function loadData() {
  pageState.value = 'loading'
  try {
    const data = await getCoverageData()
    coverage.value = data
    pageState.value = data.totalExpenses === 0 ? 'empty' : 'ready'
  } catch (e) {
    pageState.value = 'error'
  }
}

function goBack() {
  router.push('/')
}

function goToSettings() {
  router.push('/coverage/settings')
}

onMounted(loadData)
</script>

<template>
  <div class="min-h-screen bg-surface flex flex-col">
    <!-- Header -->
    <header class="flex items-center justify-between px-container-padding h-16 bg-surface sticky top-0 z-50">
      <div class="flex items-center gap-4">
        <button @click="goBack" class="material-symbols-outlined text-primary">arrow_back</button>
        <h1 class="font-headline-md text-headline-md font-bold text-primary">分红覆盖</h1>
      </div>
      <button @click="router.push('/')" class="w-10 h-10 flex items-center justify-center active:opacity-80 transition-opacity">
        <span class="material-symbols-outlined text-on-surface-variant">home</span>
      </button>
    </header>

    <PageStateComp :state="pageState" @retry="loadData" />

    <main v-if="pageState === 'ready' && coverage" class="flex-1 px-container-padding pb-24 overflow-y-auto flex flex-col gap-md">
      <!-- Summary Dashboard Card -->
      <section class="bg-inverse-surface rounded-xl p-lg text-white relative overflow-hidden">
        <div class="flex justify-between items-center mb-4">
          <div>
            <p class="text-secondary-fixed-dim font-caption text-caption mb-1">分红已覆盖</p>
            <div class="flex items-baseline gap-1">
              <span class="text-4xl font-headline-lg font-bold">{{ coverage.coveredExpenses }}</span>
              <span class="text-secondary-fixed-dim text-body-md">/ {{ coverage.totalExpenses }} 项支出</span>
            </div>
          </div>
          <button @click="goToSettings" class="w-12 h-12 rounded-full bg-gradient-to-br from-white/20 to-primary-container/20 flex items-center justify-center active:scale-90 transition-all shadow-[0_0_10px_rgba(255,122,69,0.2)] hover:shadow-[0_0_14px_rgba(255,122,69,0.35)]">
            <span class="material-symbols-outlined text-white/90 text-xl">settings</span>
          </button>
        </div>
        <div class="grid grid-cols-3 border-t border-white/10 pt-4 gap-2">
          <div class="text-center">
            <p class="text-secondary-fixed-dim text-body-sm mb-1">固定支出总额</p>
            <p class="font-label-bold text-label-bold">{{ formatMoney(coverage.totalAnnualExpense) }}</p>
          </div>
          <div class="text-center border-x border-white/10">
            <p class="text-secondary-fixed-dim text-body-sm mb-1">预计年度分红</p>
            <p class="font-label-bold text-label-bold text-[#4ADE80]">{{ formatMoney(coverage.predictedAnnualDividend) }}</p>
          </div>
          <div class="text-center">
            <p class="text-secondary-fixed-dim text-body-sm mb-1">今年已实收</p>
            <p class="font-label-bold text-label-bold text-[#FACC15]">{{ formatMoney(coverage.totalDividendReceived) }}</p>
          </div>
        </div>
      </section>

      <!-- Milestone -->
      <section class="bg-surface-container-lowest rounded-xl p-md">
        <div class="flex items-center gap-2 mb-4">
          <span class="text-lg">🏆</span>
          <h2 class="font-headline-md text-headline-md font-bold text-on-surface">成长之路</h2>
        </div>
        <div class="relative flex justify-between items-center mb-4 px-2">
          <div v-for="(m, i) in milestones" :key="i" class="flex flex-col items-center gap-2 z-10 relative">
            <div :class="[
              'w-8 h-8 rounded-full flex items-center justify-center text-sm',
              i <= coverage.currentMilestoneIndex
                ? 'bg-primary-container text-white'
                : 'bg-surface-container text-on-surface-variant'
            ]">
              <span>{{ m.icon }}</span>
            </div>
            <span class="text-body-sm text-on-surface-variant whitespace-nowrap">{{ m.name }}</span>
          </div>
        </div>
        <p class="text-body-sm text-on-surface-variant text-center bg-surface-container-low py-2 rounded-lg">
          再覆盖剩余支出即可点亮更多成就
        </p>
      </section>

      <!-- Next Target -->
      <section v-if="nextTargetExpense" class="bg-primary-fixed/30 rounded-xl p-md border border-primary/10">
        <div class="flex gap-4 mb-3">
          <div class="w-12 h-12 rounded-xl bg-white flex items-center justify-center text-2xl">
            <span>{{ nextTargetExpense.icon }}</span>
          </div>
          <div>
            <p class="text-primary font-caption text-caption mb-0.5">下一个目标</p>
            <h3 class="font-headline-md text-headline-md font-bold text-on-surface">
              {{ nextTargetExpense.name }} · ¥{{ nextTargetExpense.annualAmount }}/年
            </h3>
          </div>
        </div>
        <div class="mb-2">
          <div class="flex justify-between text-body-sm mb-1.5">
            <span class="text-primary font-bold">进度 {{ progressPercent }}%</span>
            <span class="text-primary">还差 ¥{{ Math.max(0, nextTargetExpense.annualAmount - coverage.predictedAnnualDividend).toFixed(2) }} 分红</span>
          </div>
          <div class="h-2 w-full bg-white rounded-full overflow-hidden">
            <div class="h-full bg-primary-container rounded-full" :style="{ width: Math.min(progressPercent, 100) + '%' }"></div>
          </div>
        </div>
      </section>

      <!-- Expense List -->
      <section class="flex flex-col gap-3">
        <div
          v-for="exp in coverage.expenses"
          :key="exp.id"
          :class="[
            'rounded-xl p-md flex items-center justify-between',
            exp.covered ? 'bg-surface-container-lowest border-l-4 border-primary' : 'bg-white/60 opacity-70'
          ]"
        >
          <div class="flex items-center gap-4">
            <span class="text-xl">{{ exp.icon }}</span>
            <div>
              <h4 class="font-label-bold text-label-bold">{{ exp.name }}</h4>
              <p class="text-caption text-on-surface-variant">¥{{ exp.annualAmount }}/年</p>
            </div>
          </div>
          <span v-if="exp.inProgress" class="px-3 py-1 bg-primary-fixed text-primary text-caption rounded-full font-bold">进行中</span>
          <span v-else-if="exp.covered" class="px-3 py-1 bg-[#4ADE80]/10 text-[#166534] text-caption rounded-full font-bold">已覆盖</span>
          <span v-else class="material-symbols-outlined text-secondary-fixed-dim">lock</span>
        </div>
      </section>
    </main>

    <!-- Empty state -->
    <main v-if="pageState === 'empty'" class="flex-1 flex flex-col items-center justify-center px-container-padding gap-md text-center">
      <span class="text-6xl">💰</span>
      <h2 class="font-headline-md text-headline-md text-on-surface">还没有生活支出</h2>
      <p class="text-body-md text-on-surface-variant">先设置你的生活支出，看看分红能覆盖多少</p>
      <button @click="goToSettings" class="px-6 py-3 bg-primary-container text-white rounded-2xl font-semibold">
        去设置
      </button>
    </main>
  </div>
</template>
