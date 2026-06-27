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
import PageStateComp from '@/components/shared/PageState.vue'

function formatMoney(value: number | undefined | null): string {
  if (value == null || value === 0) return '¥0.00'
  if (value >= 1_0000_0000) return `¥${(value / 1_0000_0000).toFixed(2)}亿`
  if (value >= 1_0000) return `¥${(value / 1_0000).toFixed(2)}万`
  return `¥${value.toFixed(2)}`
}

function formatShares(value: number | undefined | null): string {
  if (value == null || value === 0) return '--'
  if (value >= 10000) return `${(value / 10000).toFixed(2)}万份`
  return `${value.toFixed(2)}份`
}

const router = useRouter()
const pageState = ref<'loading' | 'ready' | 'error'>('loading')
const dashboard = ref<DashboardData | null>(null)
const holdings = ref<HoldingItem[]>([])
const coverageSummary = ref<CoverageData | null>(null)
const showMoreMetrics = ref(false)
const enabledMetricKeys = ref<string[]>([])

const colorPalette = ["#1A6B56", "#5B8C7A", "#8DB8A4", "#B8D5C8"]

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

onActivated(loadData)
</script>

<template>
  <div class="min-h-screen bg-page-bg">
    <AppHeader title="种树" :show-logo="true" right-icon="search" />

    <main class="pt-14 pb-24 px-gutter max-w-[600px] mx-auto space-y-md">
      <PageStateComp :state="pageState" />

      <template v-if="pageState === 'ready'">
        <!-- ============================================================ -->
        <!-- 公告横幅 — 卡片样式                                           -->
        <!-- ============================================================ -->
        <div v-if="(dashboard?.monthlyPredictedDividend ?? 0) > 0" class="bg-brand-light/60 rounded-xl px-lg py-sm card-shadow border border-brand/10 flex items-center gap-lg">
          <span class="material-symbols-outlined text-brand text-lg">campaign</span>
          <p class="font-body text-sm text-text-primary">
            稳稳的幸福，本月预计收息 <span class="font-semibold text-brand tabular-nums">{{ dashboard?.monthlyPredictedDividend ?? 0 }}</span> 元
          </p>
        </div>

        <!-- Hero 卡片 -->
        <div class="bg-card-bg rounded-xl px-lg py-md card-shadow border border-border-light/40 relative overflow-hidden">
          <!-- 装饰底纹 -->
          <div class="absolute -bottom-6 -right-6 w-28 h-28 rounded-full bg-brand-light/40 pointer-events-none"></div>
          <div class="absolute -top-3 -left-3 w-12 h-12 rounded-full bg-brand-light/20 pointer-events-none"></div>

          <div class="relative z-10">
            <!-- 主区域：预测年度分红 — 品牌色块 + 两侧装饰线 -->
            <div class="text-center">
              <p class="font-body text-xs text-text-tertiary mb-2">预测年度分红</p>
              <div class="flex items-center justify-center gap-0">
                <!-- 左侧装饰竖线 -->
                <div class="flex items-center gap-[3px] mr-3">
                  <div class="w-[3px] h-[3px] rounded-full bg-border-light"></div>
                  <div class="w-[2px] h-[10px] rounded-[1px] bg-border-light"></div>
                  <div class="w-[2px] h-[18px] rounded-[1px] bg-text-tertiary/30"></div>
                </div>
                <!-- 品牌色背景块 -->
                <div class="bg-brand-light/60 rounded-lg px-5 py-1 inline-block">
                  <p class="font-display text-3xl text-brand font-semibold tabular-nums">{{ formatMoney(dashboard?.predictedAnnualDividend) }}</p>
                </div>
                <!-- 右侧装饰竖线 -->
                <div class="flex items-center gap-[3px] ml-3">
                  <div class="w-[2px] h-[18px] rounded-[1px] bg-text-tertiary/30"></div>
                  <div class="w-[2px] h-[10px] rounded-[1px] bg-border-light"></div>
                  <div class="w-[3px] h-[3px] rounded-full bg-border-light"></div>
                </div>
              </div>
            </div>

            <!-- 副区域：连续收息 + 10年预期收益 — 轻量 chip 标签 -->
            <div class="flex items-center justify-center gap-2 mt-3">
              <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-brand-light/60 text-brand font-body text-xs font-medium">
                <span class="tabular-nums">{{ dashboard?.consecutiveDays ?? 0 }}</span>
                <span class="opacity-70">天</span>
              </span>
              <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-brand-light/60 text-brand font-body text-xs font-medium">
                <span class="tabular-nums">{{ dashboard?.tenYearExpectedReturn ?? 0 }}×</span>
                <span class="opacity-70">10年</span>
              </span>
            </div>

            <!-- 操作按钮行 -->
            <div class="flex items-center justify-center gap-lg pt-2.5 pb-0 mt-3 border-t border-border-light">
              <button
                @click="showMoreMetrics = !showMoreMetrics"
                class="flex items-center gap-1 text-text-secondary/60 hover:text-text-secondary transition-colors px-md py-1 rounded-full hover:bg-card-alt text-sm"
              >
                <span>{{ showMoreMetrics ? '收起指标' : '查看指标' }}</span>
                <span class="material-symbols-outlined text-xs transition-transform" :class="{ 'rotate-180': showMoreMetrics }">expand_more</span>
              </button>
              <button class="flex items-center gap-1 text-text-secondary/60 hover:text-text-secondary transition-colors px-md py-1 rounded-full hover:bg-card-alt text-sm" @click="goToMetricSettings">
                设置指标
              </button>
            </div>

            <!-- 展开指标区域 — 卡片组 3列（无额外分割线，避免视觉抖动） -->
            <div v-if="showMoreMetrics" class="mt-3">
              <div class="grid grid-cols-3 gap-sm">
                <template v-for="(metric, idx) in enabledMetrics" :key="metric.key">
                  <div class="bg-card-alt/50 rounded-lg px-md py-3 text-center">
                    <p class="font-body text-xs text-text-tertiary mb-1">{{ metric.label }}</p>
                    <template v-if="metric.formatter === 'percent'">
                      <p class="font-display text-md text-brand font-semibold tabular-nums">{{ metric.getValue(dashboard!).toFixed(1) }}<span class="text-xs text-text-tertiary">%</span></p>
                    </template>
                    <template v-else-if="metric.formatter === 'money'">
                      <p class="font-display text-md text-brand font-semibold tabular-nums">{{ formatMoney(metric.getValue(dashboard!)) }}</p>
                    </template>
                    <template v-else>
                      <p class="font-display text-md text-brand font-semibold tabular-nums">{{ metric.getValue(dashboard!) }}</p>
                    </template>
                  </div>
                </template>
              </div>
            </div>
          </div>
        </div>

        <!-- ============================================================ -->
        <!-- 分红覆盖                                                       -->
        <!-- ============================================================ -->
        <section v-if="coverageSummary && coverageSummary.totalExpenses > 0" class="cursor-pointer" @click="goToCoverage">
          <div class="flex items-center justify-between mb-sm">
            <div class="flex items-center gap-2">
              <span class="material-symbols-outlined text-brand text-sm">account_balance_wallet</span>
              <h3 class="font-body text-sm font-medium text-text-primary tracking-wide">分红覆盖</h3>
            </div>
            <span class="text-text-tertiary text-xs">{{ coverageSummary.coveredExpenses }}/{{ coverageSummary.totalExpenses }} 项已覆盖</span>
          </div>
          <div class="bg-card-bg rounded-xl p-md card-shadow border border-border-light/40">
            <div class="flex items-center gap-lg overflow-x-auto hide-scrollbar">
              <div v-for="exp in coverageSummary.expenses" :key="exp.id" class="flex flex-col items-center gap-1 min-w-[52px]">
                <div class="w-9 h-9 rounded-full flex items-center justify-center text-sm"
                  :class="exp.covered ? 'bg-brand-light text-brand' : exp.inProgress ? 'bg-amber-50 text-amber-700' : 'bg-card-alt text-text-tertiary'">
                  {{ exp.icon }}
                </div>
                <span class="text-[11px] text-text-tertiary whitespace-nowrap">{{ exp.name }}</span>
                <span class="text-[11px] font-medium"
                  :class="exp.covered ? 'text-brand' : exp.inProgress ? 'text-amber-700' : 'text-text-tertiary'">
                  {{ exp.covered ? '已覆盖' : exp.inProgress ? '进行中' : '未覆盖' }}
                </span>
              </div>
            </div>
          </div>
        </section>

        <!-- ============================================================ -->
        <!-- 持仓列表 — 含市值/成本/份额/股息率四列详情                     -->
        <!-- ============================================================ -->
        <section class="space-y-sm">
          <div class="flex items-center gap-2">
            <span class="material-symbols-outlined text-brand text-sm">account_balance</span>
            <h3 class="font-body text-sm font-medium text-text-primary tracking-wide">持仓</h3>
          </div>
          <div v-if="holdings.length > 0" class="space-y-sm">
            <div
              v-for="holding in holdings"
              :key="holding.id"
              class="bg-card-bg rounded-xl p-md card-shadow border border-border-light/40 cursor-pointer hover:shadow-elevated transition-all duration-200"
              @click="goToHolding(holding.id)"
            >
              <!-- 第一行：圆点 + 名称 + 分红 -->
              <div class="flex items-center gap-md mb-sm">
                <span class="w-2.5 h-2.5 rounded-full shrink-0" :style="{ backgroundColor: holding.color }"></span>
                <div class="flex-1 min-w-0">
                  <p class="font-body text-sm font-medium text-text-primary truncate">{{ holding.name }}</p>
                  <p class="font-body text-xs text-text-tertiary">{{ holding.code }}</p>
                </div>
                <div class="text-right shrink-0">
                  <template v-if="holding.predictedDividend > 0">
                    <p class="font-display text-sm font-semibold text-brand tabular-nums">
                      ¥{{ holding.predictedDividend >= 10000 ? (holding.predictedDividend / 10000).toFixed(2) + '万' : holding.predictedDividend.toFixed(0) }}
                    </p>
                    <p class="font-body text-[11px] text-text-tertiary">预测分红/年</p>
                  </template>
                  <template v-else>
                    <p class="font-body text-sm text-text-tertiary">--</p>
                  </template>
                </div>
              </div>

              <!-- 第二行：四列详情数据（市值/成本/份额/股息率） -->
              <div class="bg-card-alt/60 rounded-lg px-md py-2 grid grid-cols-4 gap-1 text-center">
                <div>
                  <p class="font-body text-[11px] text-text-tertiary">市值</p>
                  <p class="font-body text-xs font-medium text-text-primary tabular-nums">{{ formatMoney(holding.marketValue) }}</p>
                </div>
                <div>
                  <p class="font-body text-[11px] text-text-tertiary">成本</p>
                  <p class="font-body text-xs font-medium text-text-primary tabular-nums">{{ formatMoney(holding.cost) }}</p>
                </div>
                <div>
                  <p class="font-body text-[11px] text-text-tertiary">份额</p>
                  <p class="font-body text-xs font-medium text-text-primary tabular-nums">{{ formatShares(holding.shares) }}</p>
                </div>
                <div>
                  <p class="font-body text-[11px] text-text-tertiary">股息率</p>
                  <p class="font-body text-xs font-medium text-brand tabular-nums">{{ holding.dividendRate ? holding.dividendRate.toFixed(2) + '%' : '--' }}</p>
                </div>
              </div>

              <!-- 第三行：回本进度条 -->
              <div v-if="holding.dividendRecoveryRate > 0" class="flex items-center gap-md mt-sm">
                <div class="flex-1 h-1.5 rounded-full bg-progress-bg overflow-hidden">
                  <div class="h-full rounded-full bg-brand transition-all duration-500" :style="{ width: Math.min(holding.dividendRecoveryRate, 100) + '%' }"></div>
                </div>
                <span class="font-body text-[11px] text-text-tertiary tabular-nums whitespace-nowrap">回本 {{ holding.dividendRecoveryRate.toFixed(1) }}%</span>
              </div>
            </div>
          </div>
          <div v-else class="flex flex-col items-center justify-center py-xl gap-md text-center">
            <span class="text-5xl">📦</span>
            <div>
              <p class="font-body text-md font-medium text-text-primary">还没有持仓</p>
              <p class="font-body text-sm text-text-tertiary mt-1">点击右下角 + 添加第一笔投资</p>
            </div>
          </div>
        </section>
      </template>

      <!-- FAB -->
      <button
        class="fixed right-6 bottom-24 w-14 h-14 bg-brand text-white rounded-xl flex items-center justify-center shadow-elevated active:scale-90 hover:shadow-overlay transition-all duration-200 z-50"
        @click="goToAddHolding"
      >
        <span class="material-symbols-outlined text-[32px]">add</span>
      </button>
    </main>
  </div>
</template>
