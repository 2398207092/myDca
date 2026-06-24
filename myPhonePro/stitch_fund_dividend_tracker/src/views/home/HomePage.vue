<script setup lang="ts">
import { ref, onMounted, onActivated, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboard } from '@/api/dashboard'
import { ALL_METRICS, loadEnabledKeys } from '@/api/metrics'
import { listHoldings } from '@/api/holding'
import { getCoverageData } from '@/api/expense'
import type { CoverageData } from '@/api/expense'
import type { DashboardData } from '@/api/dashboard'
import type { HoldingItem } from '@/api/holding'
import AppHeader from '@/components/shared/AppHeader.vue'
import DividendCard from '@/components/shared/DividendCard.vue'
import PageStateComp from '@/components/shared/PageState.vue'

function formatMoney(value: number | undefined | null): string {
  if (value == null || value === 0) return '¥ 0.00'
  if (value >= 1_0000_0000) return `¥ ${(value / 1_0000_0000).toFixed(2)}亿`
  if (value >= 1_0000) return `¥ ${(value / 1_0000).toFixed(2)}万`
  return `¥ ${value.toFixed(2)}`
}

const router = useRouter()
const pageState = ref<'loading' | 'ready' | 'error'>('loading')
const dashboard = ref<DashboardData | null>(null)
const holdings = ref<HoldingItem[]>([])
const coverageSummary = ref<CoverageData | null>(null)
const showMoreMetrics = ref(false)
const enabledMetricKeys = ref<string[]>([])

function goToHolding(id: string) {
  router.push(`/holding/${id}`)
}

function goToCoverage() {
  router.push('/coverage')
}

function goToAddHolding() {
  router.push({ name: 'holding-add' })
}

function goToMetricSettings() {
  router.push('/metrics/settings')
}

// 计算启用的指标列表（每次展开时读取最新配置）
const enabledMetrics = computed(() => {
  const keys = enabledMetricKeys.value
  return ALL_METRICS.filter((m) => keys.includes(m.key))
})

async function loadData() {
  pageState.value = 'loading'
  try {
    enabledMetricKeys.value = loadEnabledKeys()
    const [dashData, holdingData, coverData] = await Promise.all([
      getDashboard(),
      listHoldings(),
      getCoverageData(),
    ])
    dashboard.value = dashData
    const colorPalette = ["#FF7A45", "#4CAF50", "#2196F3", "#9C27B0", "#FF9800", "#E91E63", "#00BCD4"]
    holdings.value = holdingData.map((h, i) => ({
      ...h,
      color: colorPalette[i % colorPalette.length],
    }))
    coverageSummary.value = coverData
    pageState.value = 'ready'
  } catch (e) {
    console.error('加载首页数据失败:', e)
    pageState.value = 'error'
  }
}

onMounted(loadData)
onActivated(loadData)
</script>

<template>
  <div class="min-h-screen bg-background">
    <AppHeader title="种树" :show-logo="true" right-icon="search" />

    <main
      class="pt-20 pb-24 px-gutter max-w-[600px] mx-auto space-y-md"
    >
      <PageStateComp :state="pageState" />

      <template v-if="pageState === 'ready'">
        <!-- Notice Banner -->
        <div class="bg-primary-container/10 border border-primary/10 rounded-xl px-4 py-3 flex items-center gap-3">
          <span class="material-symbols-outlined text-primary text-[20px] fill">campaign</span>
          <span v-if="(dashboard?.monthlyPredictedDividend ?? 0) > 0" class="text-on-surface-variant font-label-bold text-label-bold">稳稳的幸福，本月预计收息{{ dashboard?.monthlyPredictedDividend ?? 0 }}元</span>
          <span v-else class="text-on-surface-variant font-label-bold text-label-bold">当前持仓暂无可预测的分红收入</span>
        </div>

        <!-- Hero Stats Card -->
        <div class="bg-surface-container-lowest rounded-xl p-lg card-shadow relative overflow-hidden transition-all duration-300 border-t-2 border-primary/20">
          <div class="relative z-10">
            <p class="text-on-surface-variant font-caption text-caption mb-4 text-center">让财富在时间中生根发芽</p>
            
            <!-- 三栏均衡布局 -->
            <div class="grid grid-cols-3 gap-md">
              <!-- 连续收息 -->
              <div class="text-center">
                <div class="flex items-baseline justify-center gap-xs">
                  <span class="font-headline-xl text-primary">{{ dashboard?.consecutiveDays ?? 0 }}</span>
                  <span class="text-body-md text-primary">天</span>
                </div>
                <p class="text-on-surface-variant font-caption text-caption mt-1">连续收息</p>
              </div>
              
              <!-- 预测年度分红 -->
              <div class="text-center">
                <div class="text-primary-container font-headline-xl whitespace-nowrap">{{ formatMoney(dashboard?.predictedAnnualDividend) }}</div>
                <p class="text-on-surface-variant font-caption text-caption mt-1">🎯 预测年度分红</p>
              </div>
              
              <!-- 10年预期收益 -->
              <div class="text-center">
                <div class="text-on-surface font-headline-xl">{{ dashboard?.tenYearExpectedReturn ?? 0 }}<span class="text-body-md ml-0.5">倍</span></div>
                <p class="text-on-surface-variant font-caption text-caption mt-1">10年预期收益</p>
              </div>
            </div>
            
            <!-- Actions Bar -->
            <div class="mt-lg pt-lg border-t border-outline-variant/20 flex items-center justify-center gap-lg">
              <button 
                @click="showMoreMetrics = !showMoreMetrics"
                class="flex items-center gap-1 text-on-surface-variant/60 hover:text-on-surface-variant transition-colors"
              >
                <span class="font-caption text-caption">{{ showMoreMetrics ? '收起指标' : '查看指标' }}</span>
                <span class="material-symbols-outlined text-[14px]" :class="{ 'rotate-180': showMoreMetrics }">expand_more</span>
              </button>
              <span class="w-px h-3 bg-outline-variant/30"></span>
              <button class="flex items-center gap-1 text-on-surface-variant/60 hover:text-on-surface-variant transition-colors" @click="goToMetricSettings">
                <span class="font-caption text-caption">设置指标</span>
              </button>
            </div>
            
            <!-- Expanded Metrics Section -->
            <div
              v-if="showMoreMetrics"
              class="mt-md pt-md border-t border-outline-variant/30"
            >
              <div class="grid grid-cols-3 gap-md">
                <template v-for="metric in enabledMetrics" :key="metric.key">
                  <!-- 百分比类型 -->
                  <div v-if="metric.formatter === 'percent'" class="text-center">
                    <div class="flex items-baseline justify-center gap-xs whitespace-nowrap">
                      <span class="text-primary font-headline-xl">{{ metric.getValue(dashboard!).toFixed(2) }}</span>
                      <span class="text-primary text-body-md">%</span>
                    </div>
                    <p class="text-on-surface-variant font-caption text-caption mt-1">{{ metric.label }}</p>
                  </div>
                  <!-- 金额类型 -->
                  <div v-else-if="metric.formatter === 'money'" class="text-center">
                    <div class="text-primary-container font-headline-xl whitespace-nowrap">{{ formatMoney(metric.getValue(dashboard!)) }}</div>
                    <p class="text-on-surface-variant font-caption text-caption mt-1">{{ metric.label }}</p>
                  </div>
                  <!-- 纯数值类型 -->
                  <div v-else class="text-center">
                    <div class="text-on-surface font-headline-xl whitespace-nowrap">{{ metric.getValue(dashboard!) }}</div>
                    <p class="text-on-surface-variant font-caption text-caption mt-1">{{ metric.label }}</p>
                  </div>
                </template>
                <!-- 占位空单元格（保持 grid-cols-3 对齐） -->
                <div v-for="n in (3 - enabledMetrics.length % 3) % 3" :key="'empty-' + n" />
              </div>
            </div>
          </div>
        </div>

        <!-- Coverage Section -->
        <section v-if="coverageSummary && coverageSummary.totalExpenses > 0" class="space-y-sm cursor-pointer" @click="goToCoverage">
          <div class="flex items-center justify-between">
            <h3 class="font-headline-md text-headline-md">分红覆盖</h3>
            <span class="text-primary font-caption text-caption">已覆盖 {{ coverageSummary.coveredExpenses }}/{{ coverageSummary.totalExpenses }} 项支出</span>
          </div>
          <div class="bg-surface-container-lowest rounded-xl p-md card-shadow">
            <div class="flex justify-between items-center hide-scrollbar overflow-x-auto pb-2 gap-md">
              <div
                v-for="exp in coverageSummary.expenses"
                :key="exp.id"
                class="flex flex-col items-center min-w-[64px] gap-2"
                :class="{ 'opacity-50': !exp.covered && !exp.inProgress }"
              >
                <template v-if="exp.covered">
                  <div class="relative w-14 h-14 flex items-center justify-center">
                    <svg class="absolute inset-0 w-full h-full -rotate-90">
                      <circle cx="28" cy="28" r="24" fill="transparent" stroke="#E8E8E8" stroke-width="4" />
                      <circle cx="28" cy="28" r="24" fill="transparent" stroke="#FF7A45" stroke-dasharray="150.8" stroke-dashoffset="0" stroke-linecap="round" stroke-width="4" />
                    </svg>
                    <span class="text-xl">{{ exp.icon }}</span>
                  </div>
                  <span class="font-caption text-caption text-primary">已覆盖</span>
                </template>
                <template v-else-if="exp.inProgress">
                  <div class="relative w-14 h-14 flex items-center justify-center">
                    <svg class="absolute inset-0 w-full h-full -rotate-90">
                      <circle cx="28" cy="28" r="24" fill="transparent" stroke="#E8E8E8" stroke-width="4" />
                      <circle cx="28" cy="28" r="24" fill="transparent" stroke="#FF7A45" stroke-dasharray="150.8" stroke-dashoffset="75" stroke-linecap="round" stroke-width="4" />
                    </svg>
                    <span class="text-xl">{{ exp.icon }}</span>
                  </div>
                  <span class="font-caption text-caption text-primary">进行中</span>
                </template>
                <template v-else>
                  <div class="w-14 h-14 bg-surface-container rounded-full flex items-center justify-center">
                    <span class="text-xl">{{ exp.icon }}</span>
                  </div>
                  <span class="font-caption text-caption text-on-surface-variant">{{ exp.name }}</span>
                </template>
              </div>
            </div>
          </div>
        </section>

        <!-- Holding List -->
        <section class="space-y-sm pb-20">
          <h3 class="font-headline-md text-headline-md">持仓详情</h3>
          <div v-if="holdings.length > 0" class="space-y-md">
            <DividendCard
              v-for="holding in holdings"
              :key="holding.id"
              :holding="holding"
              :on-click="() => goToHolding(holding.id)"
            />
          </div>
          <div v-else class="bg-surface-container-lowest rounded-xl p-lg card-shadow text-center">
            <span class="material-symbols-outlined text-[48px] text-outline-variant mb-3" style="font-variation-settings: 'FILL' 1">inventory_2</span>
            <p class="text-on-surface-variant font-label-bold text-label-bold mb-1">还没有持仓</p>
            <p class="text-outline-variant font-caption text-caption">点击右下角 + 添加你的第一笔投资</p>
          </div>
        </section>
      </template>

      <!-- FAB -->
      <button
        class="fixed right-6 bottom-24 w-14 h-14 bg-primary-container text-on-primary-container rounded-full flex items-center justify-center shadow-lg active:scale-95 transition-transform z-50"
        @click="goToAddHolding"
      >
        <span class="material-symbols-outlined text-[32px]">add</span>
      </button>
    </main>
  </div>
</template>
