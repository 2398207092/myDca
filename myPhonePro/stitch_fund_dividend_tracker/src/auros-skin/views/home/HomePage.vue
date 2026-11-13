<script setup lang="ts">
/**
 * 首页 · 一屏版（2026-11-08 第二轮）
 * 叙事：一句可读完的话——「你有 ¥47,366 → 它今年替你付了 4.6 天生活 → 钱压在红利上」
 * 结构：Banner（左文右球）+ 分红覆盖（一年刻度）+ 持仓（类目舱板），一屏不滚
 * 规格：字号只用 10/12/13/16/20/26/40/56/96；字重 400/500；圆角 6/16；阴影只允许光
 */
import { computed, nextTick, onActivated, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboard } from '@/api/dashboard'
import { listHoldings } from '@/api/holding'
import { getCoverageData } from '@/api/expense'
import type { CoverageData } from '@/api/expense'
import type { DashboardData } from '@/api/dashboard'
import type { HoldingItem } from '@/api/holding'
import AssetOrb from '@/auros-skin/components/home/AssetOrb.vue'
import CategoryDeck from '@/auros-skin/components/home/CategoryDeck.vue'
import AurosChapter from '@/auros-skin/components/auros/AurosChapter.vue'
import { buildCategories } from '@/auros-skin/components/home/deck'
import { animateValue, prefersReducedMotion } from '@/auros-skin/utils/auros-motion'

const router = useRouter()

const pageState = ref<'loading' | 'ready' | 'error'>('loading')
const dashboard = ref<DashboardData | null>(null)
const holdings = ref<HoldingItem[]>([])
const coverage = ref<CoverageData | null>(null)

/* ---------------- 数字滚动 ---------------- */
const totalDisp = ref(0)
const daysDisp = ref(0)
const covIn = ref(false)
const divisors: Array<() => void> = []
const push = (fn: () => void) => divisors.push(fn)

/* ---------------- 派生 ---------------- */
const totalMV = computed(() => dashboard.value?.totalMarketValue ?? 0)
const totalCost = computed(() => dashboard.value?.totalCost ?? 0)
const pnl = computed(() => totalMV.value - totalCost.value)
const received = computed(() => dashboard.value?.totalDividendReceived ?? 0)
const streak = computed(() => dashboard.value?.consecutiveDays ?? 0)

const annualDiv = computed(
  () => coverage.value?.predictedAnnualDividend ?? dashboard.value?.predictedAnnualDividend ?? 0,
)
const annualExpense = computed(() => coverage.value?.totalAnnualExpense ?? 0)
const hasBudget = computed(() => annualExpense.value > 0)
const coveredDays = computed(() =>
  hasBudget.value ? Math.min(365, (annualDiv.value / annualExpense.value) * 365) : 0,
)
const gap = computed(() => Math.max(0, annualExpense.value - annualDiv.value))
const coveredCount = computed(() => coverage.value?.coveredExpenses ?? 0)
const totalCats = computed(() => coverage.value?.totalExpenses ?? 0)

const nextExpense = computed(() => {
  const list = coverage.value?.expenses ?? []
  if (!list.length) return null
  const target = list.find((e) => e.inProgress && !e.covered) || list.find((e) => !e.covered) || null
  if (!target) return null
  return { ...target, need: Math.max(0, target.annualAmount - annualDiv.value) }
})

/** 未进视口也要是真值：截图 / 无障碍都拿到终态 */
const daysShown = computed(() => (covIn.value ? daysDisp.value : coveredDays.value))
const daysWhole = computed(() => Math.floor(daysShown.value))
const daysFrac = computed(() => (daysShown.value - Math.floor(daysShown.value)).toFixed(1).slice(1))
const rulerPct = computed(() => Math.min(100, Math.max(0.55, (daysShown.value / 365) * 100)))

const cats = computed(() => buildCategories(holdings.value))

/** 资产球：聚焦类别下标（-1 = 不聚焦）+ 刷新时的"散开再聚拢"计数 */
const focusCat = ref(-1)
const orbBurst = ref(0)
function onDeckFocus(key: string | null) {
  if (!key) {
    focusCat.value = -1
    return
  }
  focusCat.value = cats.value.findIndex((c) => c.key === key)
}

function group(n: number): string {
  return Math.round(n)
    .toString()
    .replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

const amountParts = computed(() => {
  const [i, d] = totalDisp.value.toFixed(2).split('.')
  return { i: Number(i).toLocaleString('en-US'), d }
})
const amountLen = computed(() => amountParts.value.i.length)
const amountSize = computed(() => (amountLen.value > 9 ? 'sm' : amountLen.value > 7 ? 'md' : 'lg'))

/* ---------------- 加载 ---------------- */
let revealTimer: ReturnType<typeof setTimeout> | null = null

async function loadData() {
  if (pageState.value !== 'ready') pageState.value = 'loading'
  try {
    const [dash, list, cover] = await Promise.all([getDashboard(), listHoldings(), getCoverageData()])
    dashboard.value = dash
    holdings.value = list
    coverage.value = cover
    pageState.value = 'ready'
    playEntrance()
    // 数据刷新 → 资产球播放一次"散开再聚拢"
    orbBurst.value++
  } catch (e) {
    console.error('[home] 加载失败', e)
    pageState.value = 'error'
  }
}

function playEntrance() {
  divisors.forEach((fn) => fn())
  divisors.length = 0
  totalDisp.value = prefersReducedMotion() ? totalMV.value : 0
  daysDisp.value = 0
  push(animateValue(0, totalMV.value, 900, (v) => (totalDisp.value = v)))
  if (revealTimer) clearTimeout(revealTimer)
  revealTimer = setTimeout(() => {
    covIn.value = true
    push(animateValue(0, coveredDays.value, 1000, (v) => (daysDisp.value = v)))
  }, prefersReducedMotion() ? 0 : 260)
}

onActivated(loadData)

onBeforeUnmount(() => {
  divisors.forEach((fn) => fn())
  if (revealTimer) clearTimeout(revealTimer)
})

/* ---------------- 交互 ---------------- */
const goHolding = (id: string) => router.push(`/holding/${id}`)
const goCoverage = () => router.push('/coverage')
const goDiscover = () => router.push('/discover')
const goTrade = () => router.push('/trade/add')
const goAdd = () => router.push('/holding/add')
</script>

<template>
  <div class="auros screen">
    <!-- 状态行 -->
    <header class="topbar" :style="{ '--d': '0ms' }">
      <span class="brand">种树</span>
      <span class="streak">
        <i class="streak__dot" aria-hidden="true"></i>
        连续收息 {{ streak }} 天
      </span>
    </header>

    <!-- 三格舱位：一屏三块，每块一张卡（卡 = 一个信息单位） -->
    <!-- ① Banner 卡：左文右球（球被卡片右缘裁开） -->
    <section class="hero au-card">
      <div class="hero__orb" aria-hidden="true">
        <AssetOrb :weights="cats.map((c) => c.pct / 100)" :focus="focusCat" :burst="orbBurst" />
      </div>
      <div class="hero__text">
        <p class="hero__cap">总资产</p>
        <p class="hero__amount au-num-xl" :class="`is-${amountSize}`">
          <span class="hero__sym">¥</span><span>{{ amountParts.i }}</span
          ><span class="hero__dec">.{{ amountParts.d }}</span>
        </p>
        <p class="hero__meta">
          <span>累计收益 <em :class="pnl >= 0 ? 'is-up' : 'is-down'">{{ pnl >= 0 ? '+' : '−' }}¥{{ group(Math.abs(pnl)) }}</em></span>
          <span class="hero__sep" aria-hidden="true"></span>
          <span>累计分红 ¥{{ group(received) }}</span>
        </p>
        <button v-if="holdings.length" type="button" class="cta" @click="goTrade">记一笔</button>
        <button v-else type="button" class="cta" @click="goAdd">添加第一只持仓</button>
      </div>
    </section>

    <!-- ② 分红覆盖卡：一句结论 + 一年刻度 -->
    <AurosChapter
      class="cov-card"
      label="COVERAGE"
      title="分红覆盖"
      :meta="totalCats ? `已覆盖 ${coveredCount} / ${totalCats} 项` : '未设置开支'"
    >
      <p class="cov__say">
        <template v-if="hasBudget">分红替你付了 <b class="cov__num au-num">{{ daysShown.toFixed(1) }}</b><i>天</i></template>
        <template v-else>还没有生活开支账本</template>
      </p>

      <div class="ruler" aria-hidden="true">
        <i class="ruler__track"></i>
        <i class="ruler__fill" :style="{ width: `${rulerPct}%` }"></i>
        <i class="ruler__tip" :style="{ left: `${rulerPct}%` }"></i>
        <b v-for="m in 11" :key="m" class="ruler__tick" :style="{ left: `${(m * 100) / 12}%` }"></b>
      </div>
      <div class="ruler__labels">
        <span>年分红 <b class="au-num">¥{{ group(annualDiv) }}</b></span>
        <span>年开支 <b class="au-num">¥{{ group(annualExpense) }}</b></span>
      </div>

      <div class="cov__foot">
        <span class="cov__gap">还缺 <b class="au-num">¥{{ group(gap) }}</b></span>
        <button v-if="nextExpense" type="button" class="cov__next" @click="goCoverage">
          {{ nextExpense.name }} · 差 ¥{{ group(nextExpense.need) }}
          <svg viewBox="0 0 12 12" width="10" height="10" fill="none">
            <path d="M3 9 9 3M4.4 3H9v4.6" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
        <button v-else type="button" class="cov__next" @click="goCoverage">
          全部覆盖
          <svg viewBox="0 0 12 12" width="10" height="10" fill="none">
            <path d="M3 9 9 3M4.4 3H9v4.6" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
      </div>
    </AurosChapter>

    <!-- ③ 持仓：类目舱板（舱格本身就是卡片，不再套卡） -->
    <section class="hold">
      <div class="head">
        <span class="head__left">
          <b class="head__label">HOLDINGS</b><b class="head__cn">持仓</b>
        </span>
        <button type="button" class="head__meta head__link" @click="goDiscover">
          {{ cats.length }} 类 · {{ holdings.length }} 只
          <svg viewBox="0 0 12 12" width="10" height="10" fill="none">
            <path d="M3 9 9 3M4.4 3H9v4.6" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
      </div>

      <CategoryDeck
        v-if="pageState === 'ready' && cats.length"
        :categories="cats"
        @open-holding="goHolding"
        @focus="onDeckFocus"
      />
      <p v-if="pageState === 'ready' && cats.length" class="hold__cap">面积 = 市值占比 · 点舱格看明细</p>

      <div v-else-if="pageState === 'ready'" class="empty">
        <p>还没有持仓。加一只基金，这里会长出一块舱板。</p>
        <button type="button" class="empty__btn" @click="goAdd">添加持仓</button>
      </div>

      <div v-else class="deck-skel" aria-hidden="true">
        <span class="deck-skel__cell"></span>
        <span class="deck-skel__cell"></span>
      </div>
    </section>

    <footer class="foot">净值与分红为估算 · 仅供自用</footer>

  </div>
</template>

<style scoped>
.screen {
  position: relative;
  /* 常规手机：正好一屏（min-height 下 flex 把余量给舱板）；
     超短屏（SE/窄屏）：内容自然长出一屏，允许滚动，绝不裁切 */
  min-height: 100svh;
  padding: calc(env(safe-area-inset-top, 0px) + 6px) 16px
    calc(env(safe-area-inset-bottom, 0px) + 62px);
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}
.screen::before {
  content: '';
  position: fixed;
  inset: 0;
  z-index: -1;
  background:
    radial-gradient(120% 42% at 72% 8%, rgba(0, 130, 124, 0.32) 0%, rgba(1, 38, 36, 0) 60%),
    linear-gradient(180deg, var(--au-abyss) 0%, var(--au-abyss) 62%, var(--au-deep) 100%);
}

/* ---------- 状态行 ---------- */
.topbar {
  flex: none;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.brand {
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 0.12em;
  color: var(--au-white);
}
.streak {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 10px;
  letter-spacing: 0.14em;
  color: var(--au-silver);
}
.streak__dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--au-lit);
  box-shadow: 0 0 8px rgba(203, 255, 252, 0.9);
  animation: breathe 3.2s ease-in-out infinite;
}

/* ---------- Banner 卡 ---------- */
.hero {
  position: relative;
  flex: none;
  overflow: hidden;
  padding: 14px 16px 16px;
}
.hero__text {
  position: relative;
  z-index: 1;
}
.hero__cap {
  font-size: 12px;
  letter-spacing: 0.16em;
  color: var(--au-silver);
}
.hero__amount {
  margin-top: 3px;
  display: inline-flex;
  align-items: baseline;
  color: var(--au-mist);
  font-size: 56px;
  line-height: 1;
  letter-spacing: -0.04em;
  animation: rise 720ms var(--au-ease) 60ms both;
}
.hero__amount.is-md {
  font-size: 40px;
}
.hero__amount.is-sm {
  font-size: 26px;
}
.hero__sym {
  font-size: 0.36em;
  font-weight: 400;
  margin-right: 0.08em;
  color: rgba(237, 255, 254, 0.6);
  letter-spacing: 0;
}
.hero__dec {
  font-size: 0.36em;
  font-weight: 400;
  color: var(--au-silver);
}
.hero__meta {
  margin-top: 7px;
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 12px;
  color: var(--au-silver);
  animation: rise 720ms var(--au-ease) 140ms both;
}
.hero__meta em {
  font-style: normal;
  font-variant-numeric: tabular-nums;
  color: var(--au-mist);
}
.hero__meta em.is-down {
  color: rgba(187, 199, 198, 0.8);
}
.hero__sep {
  width: 1px;
  height: 9px;
  background: rgba(237, 255, 254, 0.16);
}
.cta {
  margin-top: 12px;
  height: 34px;
  padding: 0 20px;
  border: 0;
  border-radius: 6px;
  background: var(--au-aurora);
  color: var(--au-ink);
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.12em;
  text-indent: 0.12em;
  animation: rise 720ms var(--au-ease) 220ms both;
  transition: transform 160ms var(--au-ease), filter 200ms var(--au-ease);
}
.cta:active {
  transform: scale(0.97);
  filter: brightness(0.96);
}
.hero__orb {
  position: absolute;
  right: -16px;
  top: -14px;
  width: 176px;
  height: 150px;
  z-index: 0;
  pointer-events: none;
  animation: fade 1200ms var(--au-ease) 80ms both;
  -webkit-mask-image: linear-gradient(90deg, transparent 0%, rgba(0, 0, 0, 0.5) 26%, var(--au-mask) 58%);
  mask-image: linear-gradient(90deg, transparent 0%, rgba(0, 0, 0, 0.5) 26%, var(--au-mask) 58%);
}

/* ---------- 分红覆盖卡 ---------- */
.cov-card {
  flex: none;
  padding: 14px 16px 16px;
}
.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.head__left {
  display: inline-flex;
  align-items: baseline;
  gap: 7px;
}
.head__label {
  font-size: 10px;
  font-weight: 400;
  letter-spacing: 0.16em;
  color: var(--au-silver);
}
.head__cn {
  font-size: 20px;
  font-weight: 500;
  line-height: 1.3;
  letter-spacing: 0.01em;
  color: var(--au-white);
}
.head__meta {
  font-size: 10px;
  letter-spacing: 0.08em;
  color: rgba(187, 199, 198, 0.8);
}
.head__link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 0;
  background: none;
  font-family: inherit;
  padding: 0;
}
.cov__say {
  margin-top: 0;
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 13px;
  letter-spacing: 0.02em;
  color: var(--au-silver);
}
.cov__num {
  font-size: 40px;
  line-height: 0.9;
  font-weight: 500;
  letter-spacing: -0.03em;
  color: var(--au-pink);
}
.cov__say i {
  font-style: normal;
  font-size: 13px;
  letter-spacing: 0.1em;
  color: var(--au-silver);
}
.ruler {
  position: relative;
  height: 6px;
  margin-top: 12px;
}
.ruler__track {
  position: absolute;
  inset: 0;
  border-radius: 3px;
  background: rgba(237, 255, 254, 0.09);
}
.ruler__fill {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  border-radius: 3px;
  background: linear-gradient(90deg, rgba(203, 255, 252, 0.6), var(--au-lit) 70%, var(--au-pink-deep));
  box-shadow: 0 0 10px rgba(139, 245, 232, 0.45);
  transition: width 1000ms var(--au-ease);
}
.ruler__tip {
  position: absolute;
  top: 50%;
  width: 6px;
  height: 6px;
  margin: -3px 0 0 -3px;
  border-radius: 50%;
  background: var(--au-pink);
  box-shadow: 0 0 10px rgba(253, 233, 255, 0.9);
  transition: left 1000ms var(--au-ease);
}
.ruler__tick {
  position: absolute;
  top: -3px;
  width: 1px;
  height: 12px;
  background: rgba(237, 255, 254, 0.12);
}
.ruler__labels {
  display: flex;
  justify-content: space-between;
  margin-top: 7px;
  font-size: 10px;
  letter-spacing: 0.04em;
  color: rgba(187, 199, 198, 0.72);
}
.ruler__labels b {
  font-weight: 500;
  color: var(--au-mist);
  margin-left: 3px;
}
.cov__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 10px;
  font-size: 12px;
  color: var(--au-silver);
}
.cov__gap b {
  font-weight: 500;
  color: rgba(253, 233, 255, 0.9);
  margin-left: 2px;
}
.cov__next {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 0;
  background: none;
  padding: 6px 0;
  font-family: inherit;
  font-size: 12px;
  color: var(--au-mist);
  white-space: nowrap;
}
.cov__next:active {
  opacity: 0.7;
}

/* ---------- 持仓：舱板本身就是一组卡，不再外包一层 ---------- */
.hold {
  flex: 1;
  min-height: 0;
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  padding: 0 16px;
}
.foot {
  flex: none;
  padding-top: 10px;
  text-align: center;
  font-size: 10px;
  letter-spacing: 0.08em;
  color: rgba(187, 199, 198, 0.4);
}
.hold .head {
  flex: none;
  margin-bottom: 8px;
}
.hold__cap {
  flex: none;
  margin-top: 8px;
  font-size: 10px;
  letter-spacing: 0.06em;
  color: rgba(187, 199, 198, 0.55);
}
.empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: flex-start;
  gap: 10px;
  font-size: 13px;
  color: var(--au-silver);
}
.empty__btn {
  height: 34px;
  padding: 0 16px;
  border: 0;
  border-radius: 6px;
  background: rgba(0, 55, 52, 0.9);
  color: var(--au-mist);
  font-family: inherit;
  font-size: 13px;
}
.deck-skel {
  flex: 1;
  display: flex;
  gap: 2px;
  border-radius: 16px;
  overflow: hidden;
}
.deck-skel__cell {
  flex: 1;
  background: rgba(203, 255, 252, 0.05);
  animation: breathe 2.4s ease-in-out infinite;
}
.deck-skel__cell:first-child {
  flex: 3;
}

/* ---------- 动效 ---------- */
@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@keyframes fade {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}
@keyframes grow-x {
  from {
    transform: scaleX(0);
  }
  to {
    transform: scaleX(1);
  }
}
@keyframes breathe {
  0%,
  100% {
    opacity: 0.6;
  }
  50% {
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero__amount,
  .hero__meta,
  .cta,
  .hero__orb,
  .cov__num,
  .streak__dot,
  .deck-skel__cell {
    animation: none;
  }
  .ruler__fill,
  .ruler__tip {
    transition: none;
  }
}

/* 短屏（iPhone SE / 8）：只压缩字号与留白，不砍图形、不砍动效 */
@media (max-height: 780px) {
  .cov__num {
    font-size: 26px;
  }
  .hero__amount {
    font-size: 40px;
  }
  .hero__orb {
    width: 150px;
    height: 122px;
  }
  .ruler {
    margin-top: 9px;
  }
}

@media (max-height: 700px) {
  .cov__num {
    font-size: 20px;
  }
  .hero {
    padding: 10px 16px 12px;
  }
  .cta {
    height: 30px;
    margin-top: 8px;
  }
}

/* 窄屏（320）：只收字号与内边距，不动结构 */
@media (max-width: 340px) {
  .hero__amount {
    font-size: 40px;
  }
  .hero__orb {
    width: 150px;
    height: 122px;
  }
  .hero {
    padding-right: 56px;
  }
}
</style>
