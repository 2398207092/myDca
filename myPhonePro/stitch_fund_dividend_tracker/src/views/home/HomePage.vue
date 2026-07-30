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
import { changelog } from '@/data/changelog'
import { formatMoney } from '@/utils/format'

function formatShares(value: number | undefined | null): string {
  if (value == null || value === 0) return '--'
  if (value >= 10000) return `${(value / 10000).toFixed(2)}万份`
  return `${value.toFixed(2)}份`
}

function dividendRateText(rate: number | undefined | null): string {
  if (rate == null) return '--'
  if (rate === -1) return '已收回'
  return rate.toFixed(2) + '%'
}

function formatRelativeDate(dateStr: string): string {
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const date = new Date(dateStr)
  const diffDays = Math.round((today.getTime() - date.getTime()) / 86400000)
  if (diffDays === 0) return '今天'
  if (diffDays === 1) return '昨天'
  if (diffDays <= 7) return `${diffDays}天前`
  if (diffDays <= 14) return '1周前'
  if (diffDays <= 30) return `${Math.floor(diffDays / 7)}周前`
  if (diffDays <= 60) return '1个月前'
  if (diffDays <= 365) return `${Math.floor(diffDays / 30)}个月前`
  return `${Math.floor(diffDays / 365)}年前`
}

const router = useRouter()
const pageState = ref<'loading' | 'ready' | 'error'>('loading')
const dashboard = ref<DashboardData | null>(null)
const holdings = ref<HoldingItem[]>([])
const coverageSummary = ref<CoverageData | null>(null)
const showMoreMetrics = ref(false)
const enabledMetricKeys = ref<string[]>([])
const showChangelog = ref(false)

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
    <AppHeader title="种树" :show-logo="true" />

    <main class="pt-14 pb-24 px-gutter max-w-[600px] mx-auto space-y-md">
      <PageStateComp :state="pageState" />

      <template v-if="pageState === 'ready'">
        <!-- ============================================================ -->
        <!-- 更新公告 — 品牌色横幅                                          -->
        <!-- ============================================================ -->
        <div
          class="bg-brand-light/60 rounded-xl px-lg py-sm card-shadow border border-brand/10 flex items-center justify-between cursor-pointer active:scale-[0.98] transition-transform"
          @click="showChangelog = true"
        >
          <div class="flex items-center gap-lg">
            <span class="material-symbols-outlined text-brand text-lg">newsmode</span>
            <div>
              <p class="font-body text-sm text-text-primary font-medium">查看更新日志</p>
              <p class="font-body text-[11px] text-brand/70 mt-[1px]">{{ changelog[0].version }} · {{ changelog[0].title }} · {{ formatRelativeDate(changelog[0].date) }}</p>
            </div>
          </div>
          <span class="material-symbols-outlined text-brand/50 text-lg">chevron_right</span>
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
                  <p class="font-display text-[40px] text-brand font-semibold tabular-nums leading-none">{{ formatMoney(dashboard?.predictedAnnualDividend) }}</p>
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
        <section class="cursor-pointer" @click="goToCoverage">
          <div class="flex items-center justify-between mb-sm">
            <div class="flex items-center gap-2">
              <span class="material-symbols-outlined text-brand text-sm">account_balance_wallet</span>
              <h3 class="font-body text-sm font-medium text-text-primary tracking-wide">分红覆盖</h3>
            </div>
            <span v-if="coverageSummary && coverageSummary.totalExpenses > 0" class="text-text-tertiary text-xs">{{ coverageSummary.coveredExpenses }}/{{ coverageSummary.totalExpenses }} 项已覆盖</span>
          </div>

          <!-- 有支出时显示列表 -->
          <div v-if="coverageSummary && coverageSummary.totalExpenses > 0" class="bg-card-bg rounded-xl p-md card-shadow border border-border-light/40">
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

          <!-- 无支出时显示添加引导 -->
          <div v-else class="bg-card-bg rounded-xl p-md card-shadow border border-border-light/40 flex items-center justify-between">
            <div class="flex items-center gap-3">
              <span class="material-symbols-outlined text-text-tertiary">receipt_long</span>
              <span class="font-body text-sm text-text-tertiary">尚未设置生活支出</span>
            </div>
            <span class="inline-flex items-center gap-1 px-3 py-1.5 bg-brand text-white rounded-lg font-body text-xs font-medium active:scale-95 transition-all"
                  @click.stop="router.push('/coverage/settings')">
              <span class="material-symbols-outlined text-sm">add</span>
              添加支出
            </span>
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
                  <p class="font-body text-lg font-medium text-text-primary truncate">{{ holding.name }}</p>
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
                  <p class="font-body text-xs font-medium tabular-nums"
                     :class="holding.dividendRate === -1 ? 'text-success' : 'text-brand'">
                    {{ dividendRateText(holding.dividendRate) }}
                  </p>
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

  <!-- ==================== Changelog Modal ==================== -->
  <Teleport to="body">
    <div
      v-if="showChangelog"
      class="fixed inset-0 z-[100] flex items-end justify-center bg-black/40"
      @click.self="showChangelog = false"
    >
      <div class="bg-card-bg rounded-t-2xl w-full max-w-lg px-gutter pt-lg pb-8 animate-slide-up max-h-[80vh] flex flex-col">
        <div class="flex items-center justify-between mb-md shrink-0">
          <div class="flex items-center gap-sm">
            <span class="material-symbols-outlined text-brand">newsmode</span>
            <h3 class="font-body text-base font-medium text-text-primary">更新日志</h3>
          </div>
          <button
            class="w-8 h-8 flex items-center justify-center text-text-tertiary hover:bg-card-alt rounded-lg transition-colors"
            @click="showChangelog = false"
          >
            <span class="material-symbols-outlined">close</span>
          </button>
        </div>
        <div class="overflow-y-auto flex-1 -mx-gutter px-gutter space-y-lg">
          <div v-for="entry in changelog" :key="entry.version" class="relative pl-lg border-l-2 border-border-light pb-lg">
            <!-- 版本圆点 -->
            <div class="absolute -left-[9px] top-1 w-4 h-4 rounded-full bg-brand border-2 border-card-bg"></div>
            <!-- 版本标题 -->
            <div class="flex items-baseline gap-sm mb-1">
              <span class="font-display text-sm font-semibold text-text-primary">{{ entry.version }}</span>
              <span class="font-body text-[11px] text-text-tertiary">{{ entry.date }}</span>
            </div>
            <p class="font-body text-xs text-text-secondary mb-2">{{ entry.title }}</p>
            <ul class="space-y-1">
              <li v-for="(item, i) in entry.items" :key="i" class="font-body text-xs text-text-tertiary flex items-start gap-1.5">
                <span class="text-brand shrink-0 mt-0.5">·</span>
                {{ item }}
              </li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.animate-slide-up {
  animation: slideUp 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes slideUp {
  from {
    transform: translateY(100%);
    opacity: 0.5;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}
</style>
