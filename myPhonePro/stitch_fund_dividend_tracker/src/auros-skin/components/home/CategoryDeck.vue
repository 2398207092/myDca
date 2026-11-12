<script setup lang="ts">
/**
 * CategoryDeck —— 类目舱板（2026-11-08 单屏版）
 * 收起态：面积 ∝ 占比的舱格（slice-and-dice，面积真实）
 * 打开态：点开的舱格形变吃满整块（FLIP），顶部结构脊切换类别，内部是持仓读数
 * 规则：零描边、零偏移阴影；只有「深水色阶 + 发丝线 + 一盏磷光点」
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { DeckCategory } from './deck'
import type { HoldingItem } from '@/api/holding'
import { prefersReducedMotion } from '@/auros-skin/utils/auros-motion'

const props = defineProps<{ categories: DeckCategory[] }>()
const emit = defineEmits<{
  (e: 'open-holding', id: string): void
  /** 打开/切换/收起某个类别时通知父层（用于资产球聚焦） */
  (e: 'focus', key: string | null): void
}>()

interface Rect {
  x: number
  y: number
  w: number
  h: number
}

const deckEl = ref<HTMLElement | null>(null)
const panelEl = ref<HTMLElement | null>(null)
const spineEl = ref<HTMLElement | null>(null)
const openKey = ref<string | null>(null)
const cellEls: Record<string, HTMLElement | null> = {}
const chipEls: Record<string, HTMLElement | null> = {}
/** 结构脊上那块"会走"的选中底片 */
const glow = ref({ x: 0, w: 0 })

function syncGlow() {
  const el = openKey.value ? chipEls[openKey.value] : null
  const spine = spineEl.value
  if (!el || !spine) return
  const s = spine.getBoundingClientRect()
  const c = el.getBoundingClientRect()
  glow.value = { x: c.left - s.left, w: c.width }
}

watch(openKey, async () => {
  await nextTick()
  syncGlow()
})

/* 尺寸变化（旋转 / 字体加载）后把选中底片对准 */
let spineRo: ResizeObserver | null = null
onMounted(() => {
  if (!spineEl.value) return
  spineRo = new ResizeObserver(() => syncGlow())
  spineRo.observe(spineEl.value)
})
onBeforeUnmount(() => spineRo?.disconnect())

const rects = computed<Record<string, Rect>>(() => {
  const list = props.categories
  const out: Record<string, Rect> = {}
  if (!list.length) return out
  if (list.length === 1) {
    out[list[0].key] = { x: 0, y: 0, w: 100, h: 100 }
    return out
  }
  const first = list[0]
  if (first.pct >= 40) {
    out[first.key] = { x: 0, y: 0, w: first.pct, h: 100 }
    const rest = list.slice(1)
    const restSum = rest.reduce((s, c) => s + c.pct, 0) || 1
    let y = 0
    for (const c of rest) {
      const h = (c.pct / restSum) * 100
      out[c.key] = { x: first.pct, y, w: 100 - first.pct, h }
      y += h
    }
  } else {
    const sum = list.reduce((s, c) => s + c.pct, 0) || 1
    let x = 0
    for (const c of list) {
      const w = (c.pct / sum) * 100
      out[c.key] = { x, y: 0, w, h: 100 }
      x += w
    }
  }
  return out
})

const openCat = computed(() => props.categories.find((c) => c.key === openKey.value) || null)

function styleOf(c: DeckCategory) {
  const r = rects.value[c.key]
  if (!r) return {}
  return {
    left: `calc(${r.x}% + 1px)`,
    top: `calc(${r.y}% + 1px)`,
    width: `calc(${r.w}% - 2px)`,
    height: `calc(${r.h}% - 2px)`,
    background: c.tint,
  }
}

function sizeClass(c: DeckCategory) {
  const r = rects.value[c.key]
  if (!r) return ''
  if (r.h < 22) return 'is-slim'
  if (r.w < 30) return 'is-narrow'
  if (r.w > 50 && r.h > 45) return 'is-hero'
  return ''
}

async function open(key: string) {
  if (openKey.value === key) return
  // 已经是打开态 → 只是切换类别：不再重放"从舱格长出来"的入场，只做选中底片位移 + 列表切换
  const isEntrance = openKey.value === null
  openKey.value = key
  emit('focus', key)
  await nextTick()
  syncGlow()
  if (!isEntrance) return
  const deck = deckEl.value
  const panel = panelEl.value
  const cell = cellEls[key]
  if (!deck || !panel || !cell) return
  if (prefersReducedMotion()) return
  const d = deck.getBoundingClientRect()
  const c = cell.getBoundingClientRect()
  panel.style.transition = 'none'
  panel.style.transformOrigin = 'top left'
  panel.style.transform = `translate(${c.left - d.left}px, ${c.top - d.top}px) scale(${
    c.width / d.width
  }, ${c.height / d.height})`
  panel.style.opacity = '0.35'
  panel.getBoundingClientRect()
  panel.style.transition = 'transform 460ms cubic-bezier(0.22, 1, 0.36, 1), opacity 320ms ease'
  panel.style.transform = 'none'
  panel.style.opacity = '1'
}

async function close() {
  const key = openKey.value
  if (!key) return
  const deck = deckEl.value
  const panel = panelEl.value
  const cell = cellEls[key]
  if (deck && panel && cell && !prefersReducedMotion()) {
    const d = deck.getBoundingClientRect()
    const c = cell.getBoundingClientRect()
    panel.style.transition = 'transform 380ms cubic-bezier(0.4, 0, 0.2, 1), opacity 260ms ease'
    panel.style.transformOrigin = 'top left'
    panel.style.transform = `translate(${c.left - d.left}px, ${c.top - d.top}px) scale(${
      c.width / d.width
    }, ${c.height / d.height})`
    panel.style.opacity = '0'
    await new Promise((r) => setTimeout(r, 380))
  }
  openKey.value = null
  emit('focus', null)
}

const onHold = (h: HoldingItem) => emit('open-holding', h.id)

/** 该笔在本类中的占比（行内背景条长度） */
function shareOf(h: HoldingItem): number {
  const cat = openCat.value
  if (!cat || !cat.value) return 0
  return Math.max(2, ((h.marketValue || 0) / cat.value) * 100)
}

function money(v: number): string {
  return Math.round(v)
    .toString()
    .replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}
function pctText(v: number): string {
  return v < 0.1 ? '<0.1' : v.toFixed(1)
}
const yearDiv = (list: HoldingItem[]) => list.reduce((s, h) => s + (h.predictedDividend || 0), 0)
const receivedOf = (list: HoldingItem[]) =>
  list.reduce((s, h) => s + (h.totalDividendReceived || 0), 0)
/** 该类目今年已收 / 预计 的进度（0..100） */
function progressOf(list: HoldingItem[]): number {
  const year = yearDiv(list)
  if (year <= 0) return 0
  return Math.min(100, Math.max(1.5, (receivedOf(list) / year) * 100))
}
</script>

<template>
  <div ref="deckEl" class="deck" :class="{ 'is-open': !!openKey }">
    <!-- 收起态：面积真实的舱格 -->
    <div class="deck__cells">
      <button
        v-for="c in categories"
        :key="c.key"
        :ref="(el) => (cellEls[c.key] = el as HTMLElement)"
        type="button"
        class="cell"
        :class="sizeClass(c)"
        :style="styleOf(c)"
        @click.stop="open(c.key)"
      >
        <span class="cell__glow" :style="{ background: c.tone }"></span>
        <span class="cell__edge" :style="{ background: c.tone }"></span>
        <span class="cell__top">
          <span class="cell__label">
            {{ c.label }}
            <em v-if="sizeClass(c) === 'is-hero'">{{ c.count }} 只</em>
          </span>
          <span class="cell__num">
            <span class="cell__pct au-num">{{ pctText(c.pct) }}<em>%</em></span>
            <span class="cell__meta">{{ c.count }} 只 · ¥{{ money(c.value) }}</span>
          </span>
        </span>
        <!-- 最大的一格：把里面的标的直接写在面积里，面积大就该装得下更多信息 -->
        <span v-if="sizeClass(c) === 'is-hero'" class="cell__rows">
          <span v-for="h in c.holdings.slice(0, 3)" :key="h.id" class="cell__row">
            <span class="cell__rowmain">
              <span class="cell__rowname">{{ h.name }}</span>
              <span class="cell__rowsub">{{ h.code }}</span>
            </span>
            <span class="cell__rowval au-num">¥{{ money(h.marketValue || 0) }}</span>
          </span>
          <span v-if="c.holdings.length > 3" class="cell__more">还有 {{ c.holdings.length - 3 }} 只</span>
        </span>
        <span v-if="sizeClass(c) === 'is-hero'" class="cell__prog">
          <span class="cell__prog-label">
            <span>已收 <b class="au-num">¥{{ money(receivedOf(c.holdings)) }}</b></span>
            <span>预计 <b class="au-num">¥{{ money(yearDiv(c.holdings)) }}</b></span>
          </span>
          <i class="cell__prog-bar">
            <b :style="{ width: `${progressOf(c.holdings)}%`, background: c.tone }"></b>
          </i>
        </span>
      </button>
    </div>

    <!-- 打开态：舱格形变吃满 -->
    <div v-if="openCat" ref="panelEl" class="panel">
      <div ref="spineEl" class="panel__spine">
        <i
          class="spine__glow"
          :style="{ transform: `translateX(${glow.x}px)`, width: `${glow.w}px` }"
        ></i>
        <button
          v-for="c in categories"
          :key="c.key"
          :ref="(el) => (chipEls[c.key] = el as HTMLElement)"
          type="button"
          class="chip"
          :class="{ 'is-active': c.key === openCat.key }"
          @click.stop="open(c.key)"
        >
          <span class="chip__label">{{ c.label }}</span>
          <span class="chip__pct au-num">{{ pctText(c.pct) }}%</span>
          <i class="chip__dot" :style="{ background: c.tone }"></i>
        </button>
        <button type="button" class="chip chip--close" aria-label="收起" @click.stop="close">
          <svg viewBox="0 0 12 12" width="12" height="12" fill="none">
            <path d="M2.6 4.6 6 8l3.4-3.4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
      </div>

      <ul :key="openCat.key" class="panel__rows" :class="{ 'is-short': openCat.holdings.length <= 3 }">
        <li v-for="(h, i) in openCat.holdings" :key="h.id" class="row" :style="{ '--i': i }">
          <button type="button" class="row__hit" @click.stop="onHold(h)">
            <span class="row__tick" :style="{ background: openCat.tone }" aria-hidden="true"></span>
            <span class="row__main">
              <span class="row__name">{{ h.name }}</span>
              <span class="row__sub">
                {{ h.code }}
                <i></i>
                年分红 <b class="au-num">¥{{ money(h.predictedDividend || 0) }}</b>
              </span>
            </span>
            <span class="row__right">
              <span class="row__val au-num">¥{{ money(h.marketValue || 0) }}</span>
              <span class="row__share au-num">本类 {{ shareOf(h).toFixed(0) }}%</span>
            </span>
          </button>
        </li>
      </ul>

      <div class="panel__foot">
        <span class="panel__share">
          本类在总资产中占
          <b class="au-num">{{ pctText(openCat.pct) }}%</b>
          · 年分红 ¥{{ money(yearDiv(openCat.holdings)) }}
        </span>
        <i class="panel__bar"><b :style="{ width: `${Math.max(1.5, openCat.pct)}%`, background: openCat.tone }"></b></i>
      </div>
    </div>
  </div>
</template>

<style scoped>
.deck {
  position: relative;
  flex: 1;
  min-height: 170px;
  max-height: 320px;
  border-radius: 16px;
}
@media (max-height: 780px) {
  .deck {
    min-height: 158px;
  }
}

/* ---------- 舱格 ---------- */
.deck__cells {
  position: absolute;
  inset: 0;
  transition: opacity 240ms ease;
}
.deck.is-open .deck__cells {
  opacity: 0;
  pointer-events: none;
}
.cell {
  position: absolute;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 12px 12px 11px;
  border: 0;
  border-radius: 6px;
  overflow: hidden;
  text-align: left;
  font-family: inherit;
  color: var(--au-mist);
  transition: transform 160ms var(--au-ease);
  animation: cell-in 620ms var(--au-ease) both;
}
.cell:active {
  transform: scale(0.993);
}
/* 顶面受光：一道极淡的顶部高光，让舱格像"被光打到的石板"，而不是刷上去的色块 */
.cell__glow {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 46%;
  opacity: 0.08;
  -webkit-mask-image: linear-gradient(180deg, var(--au-mask) 0%, transparent 100%);
  mask-image: linear-gradient(180deg, var(--au-mask) 0%, transparent 100%);
  pointer-events: none;
}
/* 只有上缘一道发丝亮线：不封口 */
.cell__edge {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 1px;
  opacity: 0.55;
  pointer-events: none;
}
.cell__top {
  position: relative;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}
.cell__label {
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.02em;
  padding-top: 2px;
}
.cell__label em {
  font-style: normal;
  font-size: 10px;
  font-weight: 400;
  letter-spacing: 0.04em;
  color: rgba(187, 199, 198, 0.7);
  margin-left: 6px;
}
.cell__num {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 3px;
  flex: none;
}
.cell__pct {
  flex: none;
  font-size: 20px;
  font-weight: 500;
  line-height: 1.1;
  color: var(--au-white);
  letter-spacing: -0.01em;
}
.cell__pct em {
  font-style: normal;
  font-size: 0.55em;
  font-weight: 400;
  color: var(--au-silver);
  margin-left: 1px;
}
.cell__meta {
  position: relative;
  font-size: 10px;
  letter-spacing: 0.04em;
  color: rgba(187, 199, 198, 0.72);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 主舱格：标签在上，标的清单紧随其后，年分红压在下缘（Auros 统计卡构图） */
.cell.is-hero {
  padding: 14px 14px 13px;
  justify-content: flex-start;
  gap: 12px;
}
.cell.is-hero .cell__pct {
  font-size: 26px;
}
.cell.is-hero .cell__label {
  font-size: 13px;
}
.cell.is-hero .cell__meta {
  display: none;
}
.cell__foot {
  position: relative;
  margin-top: auto;
  font-size: 10px;
  letter-spacing: 0.06em;
  color: rgba(187, 199, 198, 0.72);
}
.cell__foot b {
  font-weight: 500;
  color: var(--au-mist);
  margin-left: 2px;
}

/* 主舱格底部的"已收 / 预计"迷你进度 */
.cell__prog {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid rgba(237, 255, 254, 0.07);
}
.cell__prog-label {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  font-size: 10px;
  letter-spacing: 0.04em;
  color: rgba(187, 199, 198, 0.72);
}
.cell__prog-label b {
  font-weight: 500;
  color: var(--au-mist);
  margin-left: 2px;
}
.cell__prog-bar {
  position: relative;
  display: block;
  height: 2px;
  border-radius: 1px;
  background: rgba(237, 255, 254, 0.1);
}
.cell__prog-bar b {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  border-radius: 1px;
  box-shadow: 0 0 8px rgba(203, 255, 252, 0.45);
  transition: width 700ms var(--au-ease);
}
/* 窄舱格：标签与数字竖排，垂直居中 */
.cell.is-narrow {
  padding: 11px 10px 10px;
  justify-content: center;
}
.cell.is-narrow .cell__top {
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}
.cell.is-narrow .cell__num {
  align-items: flex-start;
}
.cell.is-narrow .cell__label {
  font-size: 12px;
}
.cell.is-narrow .cell__pct {
  font-size: 20px;
}
.cell.is-narrow .cell__meta {
  display: none;
}
/* 极扁舱格：一行横排 */
.cell.is-slim {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  padding: 0 9px;
  line-height: 1;
}
.cell.is-slim .cell__top {
  flex-direction: row;
  align-items: center;
  gap: 6px;
  width: 100%;
  justify-content: space-between;
}
.cell.is-slim .cell__label {
  font-size: 10px;
  line-height: 1;
  letter-spacing: 0.04em;
  white-space: nowrap;
}
.cell.is-slim .cell__pct {
  font-size: 10px;
  line-height: 1;
  color: var(--au-mist);
  white-space: nowrap;
}
.cell.is-slim .cell__meta {
  display: none;
}

/* 主舱格里的标的清单：让面积大的格子装得下信息 */
.cell__rows {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 2px 0;
  padding-top: 12px;
  border-top: 1px solid rgba(237, 255, 254, 0.08);
}
.cell.is-hero .cell__rows {
  flex: 1;
  justify-content: center;
}
.cell__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 7px 0;
  border-bottom: 1px solid rgba(237, 255, 254, 0.05);
}
.cell__row:last-of-type {
  border-bottom: 0;
}
.cell__rowmain {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.cell__rowsub {
  font-size: 10px;
  letter-spacing: 0.06em;
  color: rgba(187, 199, 198, 0.55);
}
.cell__rowname {
  font-size: 12px;
  letter-spacing: 0.01em;
  color: rgba(237, 255, 254, 0.88);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cell__rowval {
  flex: none;
  font-size: 10px;
  color: rgba(187, 199, 198, 0.75);
}
.cell__more {
  font-size: 10px;
  letter-spacing: 0.04em;
  color: rgba(187, 199, 198, 0.55);
}

@keyframes cell-in {
  from {
    opacity: 0;
    transform: scale(0.975);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

/* ---------- 打开态 ---------- */
.panel {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(0, 55, 52, 0.94), rgba(1, 29, 28, 0.97));
  will-change: transform, opacity;
}
.panel__spine {
  flex: none;
  position: relative;
  display: flex;
  gap: 5px;
  padding: 8px 8px 0;
}
/* 会走的选中底片：切换类别时只负责移动，不重放入场 */
.spine__glow {
  position: absolute;
  left: 0;
  top: 8px;
  height: 44px;
  border-radius: 6px;
  background: rgba(203, 255, 252, 0.13);
  transition:
    transform 320ms var(--au-ease),
    width 320ms var(--au-ease);
  pointer-events: none;
}
.chip {
  position: relative;
  z-index: 1;
  flex: 1 1 0;
  min-width: 0;
  height: 44px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 3px;
  padding: 0 9px;
  border: 0;
  border-radius: 6px;
  background: rgba(237, 255, 254, 0.04);
  font-family: inherit;
  text-align: left;
  transition: background 200ms var(--au-ease);
}
.chip.is-active {
  background: transparent;
}
.chip__label {
  font-size: 12px;
  letter-spacing: 0.02em;
  color: var(--au-silver);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}
.chip__pct {
  font-size: 13px;
  color: var(--au-mist);
}
.chip__dot {
  position: absolute;
  right: 8px;
  top: 10px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  opacity: 0.3;
}
.chip.is-active {
  background: rgba(203, 255, 252, 0.13);
}
.chip.is-active .chip__label {
  color: var(--au-mist);
}
.chip.is-active .chip__dot {
  opacity: 1;
  box-shadow: 0 0 8px rgba(203, 255, 252, 0.85);
}
.chip--close {
  flex: 0 0 32px;
  align-items: center;
  justify-content: center;
  color: var(--au-silver);
  background: transparent;
}

.panel__rows {
  flex: 1;
  min-height: 0;
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
  mask-image: linear-gradient(180deg, transparent 0, var(--au-mask) 8px, var(--au-mask) calc(100% - 10px), transparent 100%);
  -webkit-mask-image: linear-gradient(180deg, transparent 0, var(--au-mask) 8px, var(--au-mask) calc(100% - 10px), transparent 100%);
}
.panel__rows.is-short {
  justify-content: center;
}
.panel__rows::-webkit-scrollbar {
  display: none;
}
.row {
  position: relative;
  border-bottom: 1px solid rgba(237, 255, 254, 0.06);
  animation: row-in 420ms var(--au-ease) both;
  animation-delay: calc(var(--i) * 34ms);
}
.row:last-child {
  border-bottom: 0;
}
.row__hit {
  position: relative;
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  min-height: 56px;
  border: 0;
  background: none;
  font-family: inherit;
  text-align: left;
  overflow: hidden;
  transition: background 160ms ease;
}
/* 行内背景条 = 该笔在本类中的占比（列表即柱图） */
.row__right {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 3px;
}
.row__share {
  font-size: 10px;
  letter-spacing: 0.04em;
  color: rgba(187, 199, 198, 0.6);
}
.row__hit:active {
  background: rgba(237, 255, 254, 0.05);
}
.row__tick {
  flex: none;
  width: 2px;
  height: 24px;
  border-radius: 1px;
  opacity: 0.9;
}
.row__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.row__name {
  font-size: 13px;
  font-weight: 500;
  color: var(--au-mist);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.row__sub {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 10px;
  letter-spacing: 0.06em;
  color: rgba(187, 199, 198, 0.66);
}
.row__sub b {
  font-weight: 500;
  color: rgba(203, 255, 252, 0.85);
}
.row__sub i {
  width: 2px;
  height: 2px;
  border-radius: 50%;
  background: rgba(187, 199, 198, 0.45);
}
.row__val {
  flex: none;
  font-size: 13px;
  font-weight: 500;
  color: var(--au-mist);
}

.panel__foot {
  flex: none;
  padding: 10px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.panel__share {
  font-size: 10px;
  letter-spacing: 0.04em;
  color: rgba(187, 199, 198, 0.7);
}
.panel__share b {
  font-weight: 500;
  color: var(--au-mist);
}
.panel__bar {
  position: relative;
  display: block;
  height: 3px;
  border-radius: 2px;
  background: rgba(237, 255, 254, 0.08);
}
.panel__bar b {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  border-radius: 2px;
  box-shadow: 0 0 8px rgba(203, 255, 252, 0.5);
}

@keyframes row-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .cell,
  .row {
    animation: none;
  }
  .deck__cells,
  .panel {
    transition: none;
  }
}

/* 窄屏（320）：只收字号，不改构图 */
@media (max-width: 340px) {
  .cell.is-narrow .cell__pct {
    font-size: 13px;
  }
  .cell.is-slim .cell__label,
  .cell.is-slim .cell__pct {
    font-size: 10px;
    letter-spacing: 0;
  }
}

/* 短屏：舱格放不下内嵌清单时，只保留结构（清单在抽屉里） */
@media (max-height: 700px) {
  .cell__rows {
    display: none;
  }
  .deck {
    min-height: 132px;
  }
  .cell.is-hero .cell__pct {
    font-size: 20px;
  }
  /* 极扁舱格在短屏里只剩一道色条，文字留给结构脊 */
  .cell.is-slim .cell__top,
  .cell.is-slim .cell__pct {
    display: none;
  }
}
</style>
