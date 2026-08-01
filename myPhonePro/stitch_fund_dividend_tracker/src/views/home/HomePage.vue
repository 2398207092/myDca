<script setup lang="ts">
import { ref, onActivated, onBeforeUnmount, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import confetti from 'canvas-confetti'
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
import { animateNumber } from '@/utils/animate'

// ---- count-up 动画状态 ----
const heroDividend = ref(0)                      // Hero 预测分红（动画值）
const consecutiveDaysDisp = ref(0)               // 连续收息天数（动画值）
const tenYearDisp = ref(0)                       // 10年预期收益（动画值）
const holdingValues = ref<Record<string, { marketValue: number; cost: number }>>({}) // 持仓市值/成本动画值
let animateCleanup: Array<() => void> = []       // 动画取消函数集合

// ---- 今日到账庆祝横幅：常驻派息日当天，进入页面时烟花 + 向右铺开 ----

/** 今日到账金额（>0 时展示庆祝横幅） */
const todayDividend = computed(() => dashboard.value?.todayDividendReceived || 0)

/** 每次进入页面递增，重放入场动效 */
const burstTick = ref(0)

/** 从横幅左端绽放 star 形烟花（canvas-confetti，成熟动效） */
function fireCelebration() {
  // 先回到页面顶部，确保横幅在视口内，烟花位置正确
  window.scrollTo(0, 0)
  // 等骨架屏淡出（0.2s）后再测横幅坐标：过早测量会取到被骨架屏挤到屏幕中下部的过渡位置，
  // 导致烟花从错误位置绽放（曾出现"左下角向右下炸开"）
  setTimeout(() => {
    const banner = document.querySelector<HTMLElement>('.celebrate-banner')
    let origin = { x: 0.08, y: 0.1 }
    if (banner) {
      // 优先取徽章中心（图标处喷出）
      const badge = banner.querySelector<HTMLElement>('.celebrate-badge')
      if (badge) {
        const br = badge.getBoundingClientRect()
        origin = {
          x: Math.max(0.01, Math.min(0.9, (br.left + br.width / 2) / window.innerWidth)),
          y: Math.max(0.04, Math.min(0.3, (br.top + br.height / 2) / window.innerHeight)),
        }
      } else {
        const rect = banner.getBoundingClientRect()
        origin = {
          x: Math.max(0.01, Math.min(0.9, (rect.left + 16) / window.innerWidth)),
          y: Math.max(0.04, Math.min(0.3, (rect.top + rect.height * 0.4) / window.innerHeight)),
        }
      }
    }
    confetti({
      // 更小的引子烟花：粒子少、尺寸小、飞行短，喷到上方"种树"标题即可
      particleCount: 20,
      spread: 25,
      // canvas-confetti 角度约定：angle=45 → 右上方（负角度才是右下，勿用负值）
      angle: 45,
      startVelocity: 15,
      gravity: 0.4,
      origin,
      colors: ['#1A6B56', '#2E8B6E', '#5B8C7A', '#8DB8A4', '#F5C044', '#A8D5C5'],
      shapes: ['star'],
      scalar: 0.4,
      ticks: 45,
      zIndex: 300,
    })
  }, 350)
}

function formatTenYear(v: number): string {
  return v.toFixed(1).replace(/\.0$/, '') + '×'
}

/** 数据就绪后驱动数字滚动动画 */
function runAnimations() {
  // 清理上一次动画
  animateCleanup.forEach((fn) => fn())
  animateCleanup = []

  const dash = dashboard.value
  if (!dash) return

  // Hero 预测分红
  animateCleanup.push(animateNumber(0, dash.predictedAnnualDividend, 500, (v) => (heroDividend.value = v)))
  // 连续收息天数（整数跳动）
  animateCleanup.push(animateNumber(0, dash.consecutiveDays, 400, (v) => (consecutiveDaysDisp.value = Math.round(v))))
  // 10年预期收益
  animateCleanup.push(animateNumber(0, dash.tenYearExpectedReturn, 400, (v) => (tenYearDisp.value = v)))

  // 持仓卡片：市值/成本 count-up，按索引错落 60ms
  holdings.value.forEach((h, idx) => {
    holdingValues.value[h.id] = { marketValue: 0, cost: 0 }
    const mv = h.marketValue || 0
    const cost = h.cost || 0
    setTimeout(() => {
      animateCleanup.push(
        animateNumber(0, mv, 350, (v) => {
          const cur = holdingValues.value[h.id] || { marketValue: 0, cost: 0 }
          holdingValues.value[h.id] = { ...cur, marketValue: v }
        }),
        animateNumber(0, cost, 350, (v) => {
          const cur = holdingValues.value[h.id] || { marketValue: 0, cost: 0 }
          holdingValues.value[h.id] = { ...cur, cost: v }
        }),
      )
    }, idx * 60)
  })
}

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
/** Hero 数字弹跳触发：每次数据加载递增，重放弹跳动画 */
const heroTick = ref(0)

/**
 * 类目节点：扁平胶囊式，每个类目一个节点
 * 状态：covered（已覆盖）/ inProgress（奋斗中）/ pending（待点亮）
 */
const coverageNodes = computed(() => {
  const c = coverageSummary.value
  if (!c || !c.expenses || c.expenses.length === 0) return []
  return c.expenses.map((exp) => ({
    id: exp.id,
    icon: exp.icon,
    name: exp.name,
    annualAmount: exp.annualAmount,
    status: exp.covered ? 'covered' : exp.inProgress ? 'inProgress' : 'pending',
  }))
})

/** 胶囊内金额紧凑显示：≥1万用"万"，否则整数 */
function formatChipAmount(v: number): string {
  if (v == null || v === 0) return '¥0'
  if (v >= 10000) return `¥${(v / 10000).toFixed(1).replace(/\.0$/, '')}万`
  return `¥${Math.round(v)}`
}

/** 胶囊容器 ref + 滚动定位：让用户进入页面就看到当前焦点 */
const coverageChipContainer = ref<HTMLElement | null>(null)
const coverageChipRefs = ref<HTMLElement[]>([])

/**
 * 智能滚动定位胶囊容器：
 * 1. 有"奋斗中" → 滚到第一个奋斗中（当前焦点）
 * 2. 无奋斗中但有"待点亮" → 滚到第一个待点亮（下一目标）
 * 3. 全部点亮 → 滚到最右（成就终点）
 * 4. 只有一个/都在奋斗中 → 最左
 */
function scrollCoverageToFocus() {
  const container = coverageChipContainer.value
  const nodes = coverageNodes.value
  if (!container || nodes.length === 0) return
  // 截断 ref 数组到当前节点数（防止旧数据残留）
  coverageChipRefs.value = coverageChipRefs.value.slice(0, nodes.length)
  // 找目标索引
  let targetIdx = nodes.findIndex((n) => n.status === 'inProgress')
  if (targetIdx === -1) targetIdx = nodes.findIndex((n) => n.status === 'pending')
  if (targetIdx === -1) {
    // 全部点亮 → 滚到最右
    container.scrollLeft = container.scrollWidth
    return
  }
  const target = coverageChipRefs.value[targetIdx]
  if (target) {
    // 滚动到目标胶囊，留 8px 左边距
    container.scrollLeft = target.offsetLeft - 8
  }
}

/**
 * 底部 CTA 文案：找到第一个未覆盖类目，生成"再攒 ¥X 就能点亮 Y"激励语。
 * 全部覆盖时返回成就语。X = 该类目年度金额 - 剩余分红额度。
 */
const coverageCta = computed(() => {
  const c = coverageSummary.value
  if (!c || !c.expenses || c.expenses.length === 0) return null
  // 全部覆盖
  if (c.coveredExpenses >= c.totalExpenses) {
    return { type: 'done' as const, text: '全部生活开支已被分红点亮 🌿' }
  }
  // 找第一个未覆盖项（inProgress 优先，否则 pending）
  const target = c.expenses.find((e) => e.inProgress && !e.covered) || c.expenses.find((e) => !e.covered)
  if (!target) return null
  // 剩余可分配分红
  const remaining = c.remainingDividend || 0
  // 还需攒多少 = 该类目年金额 - 剩余分红（clamped ≥0）
  const need = Math.max(0, target.annualAmount - remaining)
  return {
    type: 'progress' as const,
    need,
    icon: target.icon,
    name: target.name,
  }
})

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
    runAnimations()
    // 重放 Hero 数字弹跳（与数字滚动完成对齐）
    heroTick.value++

    // 分红覆盖胶囊：数据就绪后智能滚动到当前焦点（奋斗中>待点亮>最右）
    nextTick(() => scrollCoverageToFocus())

    // 今日有分红到账 → 绽放烟花 + 重放入场动效（横幅本身常驻当天）
    if ((dashData.todayDividendReceived || 0) > 0) {
      burstTick.value++
      fireCelebration()
    }
  } catch (e) {
    console.error('加载首页数据失败:', e)
    pageState.value = 'error'
  }
}

onActivated(loadData)

onBeforeUnmount(() => {
  animateCleanup.forEach((fn) => fn())
})
</script>

<template>
  <div class="min-h-screen bg-page-bg">
    <AppHeader title="种树" :show-logo="true" />

    <main class="pt-16 pb-24 px-gutter max-w-[600px] mx-auto space-y-section">
      <PageStateComp :state="pageState" skeleton="card" :on-retry="loadData" />

      <template v-if="pageState === 'ready'">
        <!-- ============================================================ -->
        <!-- 今日到账庆祝横幅 — 仅派息日当天常驻，入场：烟花 + 向右铺开   -->
        <!-- ============================================================ -->
        <div
          v-if="todayDividend > 0"
          class="celebrate-banner stagger-item relative overflow-hidden rounded-xl card-shadow border border-brand/10 bg-[linear-gradient(120deg,#F3F9F5,#E8F3EC)]"
          style="animation-delay: 0ms"
        >
          <!-- 向右铺开的横幅主体（每次进入重放） -->
          <div :key="burstTick" class="celebrate-sheet relative">
            <!-- 柔和光晕点缀（浅色氛围，不抢眼） -->
            <div class="absolute -top-10 -left-10 w-28 h-28 rounded-full bg-brand-light/50 blur-2xl pointer-events-none"></div>
            <div class="absolute -bottom-8 right-8 w-24 h-24 rounded-full bg-[#F5C044]/10 blur-2xl pointer-events-none"></div>

            <div class="celebrate-text relative flex items-center gap-sm px-lg py-3">
              <!-- 左端徽章：品牌绿小圆角标，轻量融入文字行 -->
              <span class="celebrate-badge w-7 h-7 rounded-lg bg-brand text-white flex items-center justify-center shrink-0">
                <span class="material-symbols-outlined text-sm leading-none" style="font-variation-settings: 'FILL' 1;">auto_awesome</span>
              </span>
              <p class="font-body text-sm font-medium text-text-primary">
                今天分红到账
                <span class="celebrate-amount font-display text-base font-semibold tabular-nums">{{ formatMoney(todayDividend) }}</span>
                <span class="opacity-60">！</span>
              </p>
              <!-- 右端四角星点缀（延续欢庆气氛） -->
              <span class="ml-auto flex items-center gap-1.5 pr-0.5" aria-hidden="true">
                <span class="sparkle-icon"></span>
                <span class="sparkle-icon sparkle-sm" style="animation-delay: 0.35s"></span>
              </span>
            </div>
          </div>
        </div>

        <!-- ============================================================ -->
        <!-- 更新公告 — 品牌色横幅                                          -->
        <!-- ============================================================ -->
        <div
          class="stagger-item bg-brand-light/60 rounded-xl px-lg py-sm card-shadow border border-brand/10 flex items-center justify-between cursor-pointer interactive-card"
          style="animation-delay: 0ms"
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
        <div class="stagger-item bg-card-bg rounded-xl px-lg py-md card-shadow border border-border-light/40 relative overflow-hidden" style="animation-delay: 40ms">
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
                <!-- 品牌色背景块：数字弹跳 + 极淡光晕呼吸 -->
                <div class="amount-hl pos inline-block relative" style="padding: 4px 20px;">
                  <div class="hero-glow absolute inset-0 rounded-lg pointer-events-none"></div>
                  <p
                    :key="'hero-' + heroTick"
                    class="hero-amount font-display text-[40px] font-semibold tabular-nums leading-none transition-colors duration-300 relative"
                  >{{ formatMoney(heroDividend) }}</p>
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
                <span class="tabular-nums">{{ consecutiveDaysDisp }}</span>
                <span class="opacity-70">天</span>
              </span>
              <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-brand-light/60 text-brand font-body text-xs font-medium">
                <span class="tabular-nums">{{ formatTenYear(tenYearDisp) }}</span>
                <span class="opacity-70">10年</span>
              </span>
            </div>

            <!-- 情境提示语：月度数据一句话 -->
            <p v-if="dashboard?.monthlyMessage" class="text-center font-body text-xs text-text-tertiary mt-2">
              {{ dashboard.monthlyMessage }}
            </p>

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
                      <p class="font-display text-md text-brand font-semibold tabular-nums transition-colors duration-300">{{ metric.getValue(dashboard!).toFixed(1) }}<span class="text-xs text-text-tertiary">%</span></p>
                    </template>
                    <template v-else-if="metric.formatter === 'money'">
                      <p class="font-display text-md text-brand font-semibold tabular-nums transition-colors duration-300">{{ formatMoney(metric.getValue(dashboard!)) }}</p>
                    </template>
                    <template v-else>
                      <p class="font-display text-md text-brand font-semibold tabular-nums transition-colors duration-300">{{ metric.getValue(dashboard!) }}</p>
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
        <section class="stagger-item cursor-pointer" style="animation-delay: 80ms" @click="goToCoverage">
          <!-- 有支出时显示列表 -->
          <div v-if="coverageSummary && coverageSummary.totalExpenses > 0" class="coverage-growth-card relative bg-card-bg rounded-xl p-md card-shadow border border-border-light/40 overflow-hidden">
            <!-- 顶部渐变线：成长的天空 -->
            <div class="absolute top-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-brand/30 to-transparent pointer-events-none"></div>
            <!-- 右上角树叶水印（极淡，种树主题签名） -->
            <span class="material-symbols-outlined absolute -top-3 -right-3 text-6xl text-brand/[0.08] pointer-events-none select-none">eco</span>

            <!-- 标题行：分红覆盖 + 年度生活支出（右侧小字） -->
            <div class="relative flex items-center justify-between mb-md">
              <div class="flex items-center gap-2">
                <span class="material-symbols-outlined text-brand text-sm">account_balance_wallet</span>
                <h3 class="font-body text-sm font-medium text-text-primary tracking-wide">分红覆盖</h3>
              </div>
              <div class="flex items-baseline gap-1">
                <span class="font-body text-[11px] text-text-tertiary">年度支出</span>
                <span class="font-display text-xs font-semibold text-text-primary tabular-nums">{{ formatMoney(coverageSummary.totalAnnualExpense) }}</span>
              </div>
            </div>

            <!-- 类目节点（扁平胶囊式，无连接线） -->
            <div
              ref="coverageChipContainer"
              class="relative flex items-center gap-sm overflow-x-auto hide-scrollbar pb-xs"
            >
              <div
                v-for="(node, idx) in coverageNodes"
                :key="node.id"
                :ref="(el) => { if (el) coverageChipRefs[idx] = el as HTMLElement }"
                class="coverage-chip shrink-0 grid grid-cols-[auto_1fr] gap-x-2 gap-y-0.5 items-center rounded-full pl-2.5 pr-3 py-1.5 transition-all"
                :class="{
                  'coverage-chip--covered': node.status === 'covered',
                  'coverage-chip--active': node.status === 'inProgress',
                  'coverage-chip--pending': node.status === 'pending',
                }"
              >
                <!-- 第一行左：图标（居中对齐下方金额中线） -->
                <span class="text-base leading-none text-center">{{ node.icon }}</span>
                <!-- 第一行右：名称 -->
                <span class="font-body text-xs whitespace-nowrap leading-none">{{ node.name }}</span>
                <!-- 第二行左：金额（与右列状态同行自动对齐） -->
                <span
                  class="font-display text-[10px] font-semibold tabular-nums leading-none text-center mt-0.5"
                  :class="node.status === 'covered' ? 't-pos-strong' : node.status === 'inProgress' ? 't-gold' : 'text-text-tertiary'"
                >{{ formatChipAmount(node.annualAmount) }}</span>
                <!-- 第二行右：状态标签（与左列金额同行自动对齐） -->
                <span
                  class="font-body text-[10px] leading-none mt-1"
                  :class="node.status === 'covered' ? 'text-pos/70' : node.status === 'inProgress' ? 'text-gold/70' : 'text-text-tertiary/60'"
                >{{ node.status === 'covered' ? '已覆盖' : node.status === 'inProgress' ? '奋斗中' : '待点亮' }}</span>
              </div>
            </div>

            <!-- 底部 CTA 文案：激励语 / 成就语 -->
              <div v-if="coverageCta" class="relative mt-sm pt-sm border-t border-border-light/60 flex items-center gap-1.5">
              <template v-if="coverageCta.type === 'progress'">
                <span class="material-symbols-outlined text-brand text-sm">auto_awesome</span>
                <p class="font-body text-xs text-text-secondary">
                  再攒 <span class="t-pos-strong font-semibold tabular-nums">{{ formatMoney(coverageCta.need) }}</span> 分红就能点亮
                  <span class="text-base leading-none mx-0.5">{{ coverageCta.icon }}</span>
                  <span class="font-medium text-text-primary">{{ coverageCta.name }}</span>
                </p>
              </template>
              <template v-else>
                <span class="material-symbols-outlined text-brand text-sm">eco</span>
                <p class="font-body text-xs text-brand font-medium">{{ coverageCta.text }}</p>
              </template>
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
        <section class="stagger-item space-y-sm" style="animation-delay: 120ms">
          <!-- 区域锚点：品牌竖条 + 标题 + 持仓数 -->
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span class="w-1 h-4 rounded-full bg-brand" aria-hidden="true"></span>
              <h3 class="font-body text-md font-medium text-text-primary tracking-wide">持仓</h3>
            </div>
            <span v-if="holdings.length > 0" class="font-body text-xs text-text-tertiary tabular-nums">{{ holdings.length }} 支</span>
          </div>
          <!-- 持仓卡片：全宽与上方卡片一致（区域感由标题锚点承担） -->
          <div v-if="holdings.length > 0" class="space-y-sm">
            <div
              v-for="(holding, idx) in holdings"
              :key="holding.id"
              class="stagger-item card-brand-stroke rounded-xl p-md card-shadow cursor-pointer interactive-card relative overflow-hidden"
              :style="{ animationDelay: (120 + idx * 40) + 'ms' }"
              @click="goToHolding(holding.id)"
            >
              <!-- 顶部渐变线（与覆盖卡统一） -->
              <div class="absolute top-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-brand/25 to-transparent pointer-events-none"></div>
              <!-- 氛围光晕点缀（极淡，打破白板感） -->
              <div class="absolute -bottom-8 -right-8 w-20 h-20 rounded-full bg-brand-light/30 blur-2xl pointer-events-none"></div>
              <div class="absolute -top-4 -left-4 w-10 h-10 rounded-full bg-brand-light/20 blur-xl pointer-events-none"></div>
              <!-- 右上角树叶水印（极淡，种树主题签名） -->
              <span class="material-symbols-outlined absolute -top-3 -right-3 text-5xl text-brand/[0.06] pointer-events-none select-none">eco</span>

              <!-- 第一行：圆点 + 名称 + 代码 + 右上角主锚点（预测分红/年） -->
              <div class="flex items-start gap-md">
                <span class="w-2.5 h-2.5 rounded-full shrink-0 mt-1" :style="{ backgroundColor: holding.color }"></span>
                <div class="flex-1 min-w-0">
                  <p class="font-body text-sm font-medium text-text-primary truncate">{{ holding.name }}</p>
                  <p class="font-body text-[11px] text-text-tertiary">{{ holding.code }}</p>
                </div>
                <!-- 层级① 主锚点：预测分红/年（右上角顶对齐，唯一视觉焦点） -->
                <div class="text-right shrink-0">
                  <p v-if="holding.predictedDividend > 0" class="font-display text-[26px] font-semibold tracking-tight leading-tight t-pos-strong tabular-nums">
                    ¥{{ holding.predictedDividend >= 10000 ? (holding.predictedDividend / 10000).toFixed(2) + '万' : holding.predictedDividend.toFixed(0) }}<span class="text-xs text-text-tertiary ml-1">/年</span>
                  </p>
                  <p v-else class="font-display text-[26px] font-semibold tracking-tight leading-tight text-text-tertiary tabular-nums">--<span class="text-xs text-text-tertiary ml-1">/年</span></p>
                </div>
              </div>

              <!-- 层级② 收益叙事 + 层级③ 弱化过程量：统一浅底数据盘（1×4 四列 + 竖线分隔，标签上/数值下居中，消除中间空洞） -->
              <div class="mt-[13px] rounded-xl bg-card-alt/50 px-md py-[9px] grid grid-cols-4 gap-x-2">
                <!-- 市值 -->
                <div class="relative flex flex-col items-center gap-1 px-1 min-w-0">
                  <span class="font-body text-xs text-text-tertiary">市值</span>
                  <span class="font-body text-sm font-medium text-text-primary tabular-nums truncate">{{ formatMoney(holdingValues[holding.id]?.marketValue ?? holding.marketValue) }}</span>
                  <span class="absolute right-0 top-1/2 -translate-y-1/2 h-6 w-px bg-border-light" aria-hidden="true"></span>
                </div>
                <!-- 成本 -->
                <div class="relative flex flex-col items-center gap-1 px-1 min-w-0">
                  <span class="font-body text-xs text-text-tertiary">成本</span>
                  <span class="font-body text-sm font-medium text-text-primary tabular-nums truncate">{{ formatMoney(holdingValues[holding.id]?.cost ?? holding.cost) }}</span>
                  <span class="absolute right-0 top-1/2 -translate-y-1/2 h-6 w-px bg-border-light" aria-hidden="true"></span>
                </div>
                <!-- 股息率（收益叙事，语义色贯穿） -->
                <div class="relative flex flex-col items-center gap-1 px-1 min-w-0">
                  <span class="font-body text-xs text-text-tertiary">股息率</span>
                  <span class="font-body text-sm font-medium tabular-nums" :class="holding.dividendRate === -1 ? 'text-success' : 'text-pos'">
                    {{ dividendRateText(holding.dividendRate) }}
                  </span>
                  <span class="absolute right-0 top-1/2 -translate-y-1/2 h-6 w-px bg-border-light" aria-hidden="true"></span>
                </div>
                <!-- 份额（最后一格：无右分隔线） -->
                <div class="flex flex-col items-center gap-1 px-1 min-w-0">
                  <span class="font-body text-xs text-text-tertiary">份额</span>
                  <span class="font-body text-sm font-medium text-text-primary tabular-nums truncate">{{ formatShares(holding.shares).replace(/份$/, '') }}</span>
                </div>
              </div>

              <!-- 层级② 收益叙事：回本进度条（独立叙事区，加粗为 h-1.5） -->
              <div v-if="holding.dividendRecoveryRate > 0" class="flex items-center gap-md mt-4">
                <div class="flex-1 h-1.5 rounded-full bg-progress-bg overflow-hidden">
                  <div class="progress-shimmer h-full rounded-full bg-brand transition-all duration-500" :style="{ width: Math.min(holding.dividendRecoveryRate, 100) + '%' }"></div>
                </div>
                <span class="font-body text-xs font-medium text-success tabular-nums shrink-0">{{ holding.dividendRecoveryRate.toFixed(1) }}%</span>
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
        class="fixed right-6 bottom-24 w-14 h-14 bg-brand text-white rounded-full flex items-center justify-center shadow-overlay active:scale-90 hover:shadow-overlay hover:brightness-110 transition-all duration-200 z-sticky"
        @click="goToAddHolding"
      >
        <span class="material-symbols-outlined text-[28px]" style="font-variation-settings: 'FILL' 1, 'wght' 400;">add</span>
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

/* ---- 氛围感：Hero 数字弹跳 + 光晕呼吸 ---- */
.hero-glow {
  background: radial-gradient(circle at 50% 55%, rgba(26, 107, 86, 0.16), transparent 72%);
  animation: glow-breathe 3.2s ease-in-out infinite;
}
@keyframes glow-breathe {
  0%, 100% { opacity: 0.35; }
  50% { opacity: 0.75; }
}
.hero-amount {
  animation: hero-pop 0.45s cubic-bezier(0.34, 1.56, 0.64, 1) 0.5s both;
}
@keyframes hero-pop {
  0% { transform: scale(1); }
  40% { transform: scale(1.05); }
  100% { transform: scale(1); }
}

/* ---- 氛围感：进度条光点流动（收息涓流） ---- */
.progress-shimmer {
  position: relative;
  overflow: hidden;
}
.progress-shimmer::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: -40px;
  width: 28px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.6), transparent);
  animation: shimmer-slide 2.4s ease-in-out infinite;
}
@keyframes shimmer-slide {
  0% { left: -40px; }
  100% { left: calc(100% + 40px); }
}

/* 今日到账庆祝横幅：canvas-confetti 星形烟花（外部库）+ 向右铺开（clip-path 揭示，文字不变形） */
.celebrate-sheet {
  transform-origin: left center;
  animation: sheetUnroll 0.55s cubic-bezier(0.25, 1, 0.5, 1) 0.25s both;
}
@keyframes sheetUnroll {
  from { clip-path: inset(0 100% 0 0); }
  to { clip-path: inset(0 0 0 0); }
}
.celebrate-text {
  animation: sheetTextIn 0.4s ease 0.7s both;
}
@keyframes sheetTextIn {
  from { opacity: 0; transform: translateX(-6px); }
  to { opacity: 1; transform: translateX(0); }
}
/* 愉悦细节：左端徽章弹跳入场 + 右端小星星交错闪烁 */
.celebrate-badge {
  animation: badgePop 0.5s cubic-bezier(0.34, 1.56, 0.64, 1) 0.85s both;
}
@keyframes badgePop {
  0% { transform: scale(0.4) rotate(-8deg); opacity: 0; }
  100% { transform: scale(1) rotate(0); opacity: 1; }
}
/* 金额品牌深绿强调（浅色卡片上更协调） */
.celebrate-amount {
  color: #0F4F40;
  margin: 0 2px;
}
.sparkle-icon {
  width: 10px;
  height: 10px;
  background: #2E8B6E;
  clip-path: polygon(50% 0%, 61% 39%, 100% 50%, 61% 61%, 50% 100%, 39% 61%, 0% 50%, 39% 39%);
  animation: twinkle 1.6s ease-in-out 1.2s infinite;
}
.sparkle-sm {
  width: 7px;
  height: 7px;
  opacity: 0.75;
  animation-duration: 1.9s;
  animation-delay: 1.5s;
}
@keyframes twinkle {
  0%, 100% { transform: scale(1); opacity: 0.9; }
  50% { transform: scale(1.35); opacity: 1; }
}

/* ---- 分红覆盖卡：扁平胶囊 + CTA 文案 ---- */
/* 类目胶囊三态：已覆盖/奋斗中/待点亮 — 使用语义色 */
.coverage-chip--covered {
  background: #E8F5F0;
  color: #0F4F40;
}
.coverage-chip--active {
  background: #F7F0DC;
  border: 1px solid #8A6B08;
  color: #6B5306;
  animation: coverage-chip-pulse 1.8s ease-in-out infinite;
}
@keyframes coverage-chip-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(138, 107, 8, 0.35); }
  50% { box-shadow: 0 0 0 4px rgba(138, 107, 8, 0); }
}
.coverage-chip--pending {
  background: #EFEFEE;
  color: #6F6F6E;
  border: 1px dashed #D3D1C7;
}

@media (prefers-reduced-motion: reduce) {
  .celebrate-sheet {
    animation: none;
    clip-path: none;
  }
  .celebrate-text {
    animation: none;
    opacity: 1;
  }
  .celebrate-badge,
  .sparkle-icon {
    animation: none;
  }
  .coverage-chip--active {
    animation: none;
  }
}
</style>
