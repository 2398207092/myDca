<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getCoverageData } from '@/api/expense'
import type { CoverageData } from '@/api/expense'
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
  <div class="min-h-screen bg-page-bg flex flex-col">
    <!-- 自定义 Header：后退回首页 -->
    <header class="fixed top-0 w-full z-50 bg-page-bg/90 backdrop-blur-md">
      <div class="flex items-center justify-between px-gutter h-14 w-full max-w-[600px] mx-auto">
        <div class="flex items-center gap-2">
          <button class="w-10 h-10 flex items-center justify-center -ml-2 active:opacity-80 transition-opacity" @click="goBack">
            <span class="material-symbols-outlined text-text-secondary">arrow_back</span>
          </button>
          <h1 class="font-display text-2xl text-text-primary">分红覆盖</h1>
        </div>
      </div>
    </header>

    <PageStateComp :state="pageState" @retry="loadData" />

    <main v-if="pageState === 'ready' && coverage" class="flex-1 px-gutter pt-16 pb-28 overflow-y-auto space-y-lg">
      <!-- ============================================================ -->
      <!-- Hero 卡片 — 覆盖概览（与首页 Hero 风格一致）                    -->
      <!-- ============================================================ -->
      <section class="bg-card-bg rounded-xl px-2xl py-2xl card-shadow border border-border-light/40 relative overflow-hidden">
        <!-- 装饰底纹 -->
        <div class="absolute -bottom-8 -right-8 w-40 h-40 rounded-full bg-brand-light/40 pointer-events-none"></div>
        <div class="absolute -top-4 -left-4 w-16 h-16 rounded-full bg-brand-light/20 pointer-events-none"></div>

        <div class="relative z-10">
          <!-- 左上：覆盖数 + 设置按钮 -->
          <div class="flex items-start justify-between mb-2xl">
            <div>
              <p class="font-body text-sm text-text-secondary mb-1">分红已覆盖</p>
              <div class="flex items-baseline gap-1">
                <span class="font-display text-4xl text-brand tabular-nums font-semibold">{{ coverage.coveredExpenses }}</span>
                <span class="font-body text-sm text-text-tertiary">/ {{ coverage.totalExpenses }} 项支出</span>
              </div>
            </div>
            <button
              @click="goToSettings"
              class="w-10 h-10 rounded-xl bg-brand-light flex items-center justify-center active:scale-90 transition-all hover:bg-brand-dim/20"
            >
              <span class="material-symbols-outlined text-brand text-lg">settings</span>
            </button>
          </div>

          <!-- 三列指标 -->
          <div class="grid grid-cols-3 border-t border-border-light pt-lg gap-2">
            <div class="text-center">
              <p class="font-body text-xs text-text-tertiary mb-1">固定支出总额</p>
              <p class="font-display text-md text-text-primary font-semibold tabular-nums">{{ formatMoney(coverage.totalAnnualExpense) }}</p>
            </div>
            <div class="text-center border-x border-border-light">
              <p class="font-body text-xs text-text-tertiary mb-1">预计年度分红</p>
              <p class="font-display text-md text-brand font-semibold tabular-nums">{{ formatMoney(coverage.predictedAnnualDividend) }}</p>
            </div>
            <div class="text-center">
              <p class="font-body text-xs text-text-tertiary mb-1">今年已实收</p>
              <p class="font-display text-md text-text-primary font-semibold tabular-nums">{{ formatMoney(coverage.totalDividendReceived) }}</p>
            </div>
          </div>

          <!-- 底部整体进度条 -->
          <div class="mt-lg pt-lg border-t border-border-light">
            <div class="flex items-center justify-between mb-2">
              <span class="font-body text-xs text-text-tertiary">整体覆盖进度</span>
              <span class="font-body text-xs text-brand font-medium">{{ progressPercent }}%</span>
            </div>
            <div class="h-2 rounded-full bg-progress-bg overflow-hidden">
              <div
                class="h-full rounded-full bg-brand transition-all duration-700 ease-out"
                :style="{ width: Math.min(progressPercent, 100) + '%' }"
              ></div>
            </div>
          </div>
        </div>
      </section>

      <!-- ============================================================ -->
      <!-- 里程碑卡片                                                      -->
      <!-- ============================================================ -->
      <section class="bg-card-bg rounded-xl p-xl card-shadow border border-border-light/40">
        <div class="flex items-center gap-2 mb-lg">
          <span class="text-xl">🏆</span>
          <h2 class="font-body text-sm font-medium text-text-primary tracking-wide">成长之路</h2>
        </div>

        <div class="relative">
          <!-- 连接线背景 -->
          <div class="absolute top-4 left-[14px] right-[14px] h-[3px] bg-progress-bg rounded-full z-0"></div>
          <!-- 连接线已解锁部分 -->
          <div
            class="absolute top-4 left-[14px] h-[3px] bg-brand rounded-full z-0 transition-all duration-700"
            :style="{
              width: `calc(${(coverage.currentMilestoneIndex / (milestones.length - 1)) * 100}% - ${14 * (1 - coverage.currentMilestoneIndex / (milestones.length - 1))}px)`,
              opacity: coverage.currentMilestoneIndex >= milestones.length - 1 ? 0 : 1
            }"
          ></div>
          <!-- 全解锁时满格 -->
          <div
            v-if="coverage.currentMilestoneIndex >= milestones.length - 1"
            class="absolute top-4 left-[14px] right-[14px] h-[3px] bg-brand rounded-full z-0"
          ></div>

          <div class="flex justify-between items-start relative z-10">
            <div v-for="(m, i) in milestones" :key="i" class="flex flex-col items-center gap-1.5" style="min-width:0">
              <div
                :class="[
                  'w-8 h-8 rounded-full flex items-center justify-center text-sm transition-all duration-300',
                  i <= coverage.currentMilestoneIndex
                    ? 'bg-brand text-white shadow-[0_2px_8px_rgba(26,107,86,0.25)]'
                    : 'bg-card-alt text-text-tertiary'
                ]"
              >
                <span>{{ m.icon }}</span>
              </div>
              <span
                :class="[
                  'font-body text-[11px] whitespace-nowrap',
                  i <= coverage.currentMilestoneIndex ? 'text-text-primary font-medium' : 'text-text-tertiary'
                ]"
              >{{ m.name }}</span>
            </div>
          </div>
        </div>

        <div class="mt-lg bg-card-alt rounded-lg px-md py-2 text-center">
          <p class="font-body text-xs text-text-tertiary">
            <template v-if="coverage.currentMilestoneIndex < milestones.length - 1">
              再覆盖 {{ milestones[coverage.currentMilestoneIndex + 1].requiredExpenses - coverage.coveredExpenses }} 项即可点亮「{{ milestones[coverage.currentMilestoneIndex + 1].name }}」
            </template>
            <template v-else>
              🎉 恭喜！已点亮所有成就
            </template>
          </p>
        </div>
      </section>

      <!-- ============================================================ -->
      <!-- 下一个目标                                                      -->
      <!-- ============================================================ -->
      <section v-if="nextTargetExpense" class="bg-card-bg rounded-xl p-xl card-shadow border border-border-light/40">
        <div class="flex items-start gap-lg">
          <div class="w-12 h-12 rounded-xl bg-brand-light flex items-center justify-center text-2xl shrink-0">
            <span>{{ nextTargetExpense.icon }}</span>
          </div>
          <div class="flex-1 min-w-0">
            <p class="font-body text-xs text-text-tertiary mb-0.5">下一个目标</p>
            <h3 class="font-body text-md font-medium text-text-primary truncate">
              {{ nextTargetExpense.name }}
            </h3>
            <p class="font-body text-xs text-text-tertiary mt-0.5">¥{{ nextTargetExpense.annualAmount.toLocaleString() }}/年</p>
          </div>
        </div>

        <div class="mt-lg">
          <div class="flex items-center justify-between mb-2">
            <span class="font-body text-xs text-text-secondary font-medium">覆盖进度 {{ progressPercent }}%</span>
            <span class="font-body text-xs text-text-tertiary">
              还差 <span class="text-brand font-medium">¥{{ Math.max(0, nextTargetExpense.annualAmount - coverage.predictedAnnualDividend).toLocaleString() }}</span>
            </span>
          </div>
          <div class="h-2 rounded-full bg-progress-bg overflow-hidden">
            <div
              class="h-full rounded-full bg-brand transition-all duration-700 ease-out"
              :style="{ width: Math.min(progressPercent, 100) + '%' }"
            ></div>
          </div>
        </div>
      </section>

      <!-- ============================================================ -->
      <!-- 支出列表                                                        -->
      <!-- ============================================================ -->
      <section>
        <div class="flex items-center gap-2 mb-md">
          <span class="material-symbols-outlined text-brand text-sm">receipt_long</span>
          <h2 class="font-body text-sm font-medium text-text-primary tracking-wide">支出列表</h2>
        </div>

        <div class="space-y-sm">
          <div
            v-for="exp in coverage.expenses"
            :key="exp.id"
            :class="[
              'rounded-xl p-md flex items-center justify-between card-shadow border transition-all duration-200',
              exp.covered
                ? 'bg-card-bg border-brand/20'
                : exp.inProgress
                  ? 'bg-card-bg border-amber-200/60'
                  : 'bg-card-alt/50 border-transparent'
            ]"
          >
            <div class="flex items-center gap-lg">
              <div
                :class="[
                  'w-10 h-10 rounded-xl flex items-center justify-center text-lg',
                  exp.covered ? 'bg-brand-light' : exp.inProgress ? 'bg-amber-50' : 'bg-card-alt'
                ]"
              >
                <span>{{ exp.icon }}</span>
              </div>
              <div>
                <h4
                  :class="[
                    'font-body text-sm font-medium',
                    exp.covered ? 'text-text-primary' : 'text-text-secondary'
                  ]"
                >{{ exp.name }}</h4>
                <p class="font-body text-xs text-text-tertiary">¥{{ exp.annualAmount.toLocaleString() }}/年</p>
              </div>
            </div>

            <!-- 状态标签 -->
            <span
              v-if="exp.inProgress"
              class="inline-flex items-center gap-1 px-3 py-1 bg-amber-50 text-amber-800 font-body text-xs rounded-full"
            >
              <span class="w-1.5 h-1.5 rounded-full bg-amber-500 animate-pulse"></span>
              进行中
            </span>
            <span
              v-else-if="exp.covered"
              class="inline-flex items-center gap-1 px-3 py-1 bg-brand-light text-brand font-body text-xs rounded-full"
            >
              <span class="material-symbols-outlined text-sm" style="font-size:14px">check_circle</span>
              已覆盖
            </span>
            <span
              v-else
              class="inline-flex items-center gap-1 px-3 py-1 bg-card-alt text-text-tertiary font-body text-xs rounded-full"
            >
              <span class="material-symbols-outlined text-sm" style="font-size:14px">lock</span>
              未覆盖
            </span>
          </div>
        </div>
      </section>
    </main>

    <!-- Empty state -->
    <main v-if="pageState === 'empty'" class="flex-1 flex flex-col items-center justify-center px-gutter gap-md text-center">
      <span class="text-6xl">💰</span>
      <h2 class="font-body text-md font-medium text-text-primary">还没有生活支出</h2>
      <p class="font-body text-sm text-text-tertiary">先设置你的生活支出，看看分红能覆盖多少</p>
      <button @click="goToSettings" class="px-6 py-3 bg-brand text-white rounded-xl font-body text-sm font-medium active:scale-[0.97] transition-all">
        去设置
      </button>
    </main>
  </div>
</template>
