<script setup lang="ts">
/**
 * AssetOrb —— 资产星座球
 * 700 粒尘埃构成球体（Auros 的粒子球母题），每只持仓是一颗信标：
 * 信标大小 ∝ sqrt(权重)，最大持仓带粉色磷光。滚动时球体下沉、变暗由父层控制。
 * 只在可见时跑 rAF；prefers-reduced-motion 时静态单帧。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { easeOutQuart, prefersReducedMotion } from '@/auros-skin/utils/auros-motion'

const props = withDefaults(
  defineProps<{
    /** 持仓权重（0..1，从大到小） */
    weights?: number[]
    /** 聚焦的类别下标（-1 = 不聚焦）：该类粒子聚拢变亮，其余变暗 */
    focus?: number
    /** 每次 +1 触发一次"散开再聚拢"（数据刷新时用） */
    burst?: number
  }>(),
  { weights: () => [], focus: -1, burst: 0 },
)

const host = ref<HTMLDivElement | null>(null)

interface Dust {
  x: number
  y: number
  z: number
  r: number
  color: string
  a: number
  /** 归属类别下标（按权重分配，用于"聚焦"时聚拢） */
  cat: number
}

const DUST = 3200
const COLORS = ['#cbfffc', '#cbfffc', '#8fe3d9', '#8fe3d9', '#edfffe', '#5fb8ae']
const PINK = '#fde9ff'

let canvas: HTMLCanvasElement | null = null
let ctx: CanvasRenderingContext2D | null = null
let dust: Dust[] = []
let beacons: { x: number; y: number; z: number; size: number }[] = []
let raf = 0
let start = 0
let running = false
let cssW = 0
let cssH = 0
let reduced = false
let io: IntersectionObserver | null = null
let ro: ResizeObserver | null = null
let glowSprite: HTMLCanvasElement | null = null

function makeGlow() {
  const c = document.createElement('canvas')
  c.width = 64
  c.height = 64
  const g = c.getContext('2d')
  if (!g) return
  const rg = g.createRadialGradient(32, 32, 0, 32, 32, 32)
  rg.addColorStop(0, 'rgba(255, 255, 255, 0.95)')
  rg.addColorStop(0.16, 'rgba(253, 233, 255, 0.6)')
  rg.addColorStop(0.42, 'rgba(253, 233, 255, 0.17)')
  rg.addColorStop(1, 'rgba(253, 233, 255, 0)')
  g.fillStyle = rg
  g.fillRect(0, 0, 64, 64)
  glowSprite = c
}

function build() {
  dust = []
  const ga = Math.PI * (3 - Math.sqrt(5))
  for (let i = 0; i < DUST; i++) {
    const y = 1 - (i / (DUST - 1)) * 2
    const r = Math.sqrt(Math.max(0, 1 - y * y))
    const th = ga * i
    dust.push({
      x: Math.cos(th) * r,
      y,
      z: Math.sin(th) * r,
      r: 0.92 + Math.random() * 0.1,
      color: COLORS[Math.floor(Math.random() * COLORS.length)],
      a: 0.45 + Math.random() * 0.55,
      cat: 0,
    })
  }
  assignCats()

  // 信标：从尘埃点里均匀抽一组，保证落在球面上且分布散开
  buildBeacons()
}

/** 按权重把粒子分给各类别：聚焦某一类时，这一类的粒子会聚拢变亮 */
function assignCats() {
  const n = Math.max(props.weights.length, 1)
  const w = props.weights.length ? props.weights : [1]
  const total = w.reduce((s, x) => s + Math.max(x, 0), 0) || 1
  const acc: number[] = []
  let sum = 0
  for (const x of w) {
    sum += Math.max(x, 0) / total
    acc.push(sum)
  }
  for (const p of dust) {
    const rnd = Math.random()
    let idx = acc.findIndex((v) => rnd <= v)
    if (idx < 0) idx = n - 1
    p.cat = idx
  }
}

function buildBeacons() {
  beacons = []
  const n = Math.max(props.weights.length, 1)
  if (!dust.length) return
  for (let i = 0; i < n; i++) {
    const idx = Math.floor(((i + 0.5) / n) * dust.length) % dust.length
    const p = dust[idx]
    const w = Math.max(props.weights[i] ?? 1 / n, 0.004)
    beacons.push({
      x: p.x,
      y: p.y,
      z: p.z,
      size: 1.8 + Math.sqrt(w) * 4.8,
    })
  }
}

watch(
  () => props.weights,
  () => {
    assignCats()
    buildBeacons()
    if (!running) draw(performance.now())
  },
  { deep: true },
)

/* 聚焦切换：只重绘，混合值在 draw 里平滑逼近（不突跳） */
watch(
  () => props.focus,
  () => {
    if (!running) draw(performance.now())
  },
)

/* 数据刷新：重新播放一次"散开 → 聚拢" */
let assembleAt = 0
watch(
  () => props.burst,
  () => {
    assembleAt = performance.now()
    if (!running) play()
  },
)

function resize() {
  if (!canvas || !host.value) return
  const rect = host.value.getBoundingClientRect()
  cssW = Math.max(1, rect.width)
  cssH = Math.max(1, rect.height)
  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  canvas.width = Math.round(cssW * dpr)
  canvas.height = Math.round(cssH * dpr)
  canvas.style.width = `${cssW}px`
  canvas.style.height = `${cssH}px`
  ctx = canvas.getContext('2d')
  ctx?.setTransform(dpr, 0, 0, dpr, 0, 0)
}

let focusMix = 0

function draw(now: number) {
  if (!ctx) return
  const t = reduced ? 0.9 : (now - start) / 1000
  const assemble = reduced ? 1 : easeOutQuart(Math.min(1, (now - assembleAt) / 1150))
  // 聚焦混合：向目标逼近，260ms 左右完成（与 --au-dur-2 同量级）
  focusMix += ((!reduced && props.focus >= 0 ? 1 : 0) - focusMix) * 0.18
  const rot = reduced ? 0.55 : t * 0.155
  const breathe = reduced ? 1 : 1 + 0.009 * Math.sin(t * 0.65)
  const R = Math.min(cssW, cssH) * 0.44 * assemble * breathe
  const cx = cssW / 2
  const cy = cssH * 0.5
  const cosY = Math.cos(rot)
  const sinY = Math.sin(rot)
  const tilt = 0.34
  const cosT = Math.cos(tilt)
  const sinT = Math.sin(tilt)

  ctx.clearRect(0, 0, cssW, cssH)

  for (const p of dust) {
    // rotate Y
    const x1 = p.x * cosY - p.z * sinY
    const z1 = p.x * sinY + p.z * cosY
    // tilt X
    const y1 = p.y * cosT - z1 * sinT
    const z2 = p.y * sinT + z1 * cosT
    const persp = 4 / (4 + z2)
    const hit = p.cat === props.focus
    // 聚焦：该类粒子向内收拢成更密的核，其余散成暗尘
    const pull = 1 - focusMix * (hit ? 0.18 : 0)
    const sx = cx + x1 * R * p.r * persp * pull
    const sy = cy + y1 * R * p.r * persp * 0.94 * pull
    if (sx < -4 || sx > cssW + 4 || sy < -4 || sy > cssH + 4) continue
    const depth = (z2 + 1) / 2
    const focusAmp = 1 + focusMix * (hit ? 0.6 : -0.78)
    const a = (0.04 + 0.96 * Math.pow(depth, 1.35)) * p.a * assemble * focusAmp
    if (a <= 0.02) continue
    ctx.globalAlpha = Math.min(1, a)
    ctx.fillStyle = p.color
    const s = (0.75 + depth * 1) * (1 + focusMix * (hit ? 0.8 : 0))
    ctx.fillRect(sx, sy, s, s)
  }

  for (let bi = 0; bi < beacons.length; bi++) {
    const b = beacons[bi]
    const x1 = b.x * cosY - b.z * sinY
    const z1 = b.x * sinY + b.z * cosY
    const y1 = b.y * cosT - z1 * sinT
    const z2 = b.y * sinT + z1 * cosT
    const persp = 4 / (4 + z2)
    const sx = cx + x1 * R * persp
    const sy = cy + y1 * R * persp * 0.94
    const depth = (z2 + 1) / 2
    const twinkle = reduced ? 1 : 0.82 + 0.18 * Math.sin(t * 1.1 + bi * 1.7)
    const hitB = bi === props.focus
    const focusScale = 1 + focusMix * (hitB ? 0.5 : -0.45)
    const s = b.size * (0.72 + 0.4 * depth) * assemble * focusScale
    if (s <= 0.1) continue
    // 光晕贴图 + 白色核心
    const halo = s * 3.1
    const focusAlpha = 1 + focusMix * (hitB ? 0.35 : -0.8)
    ctx.globalAlpha = Math.min(1, (0.32 + 0.5 * depth) * twinkle * assemble * focusAlpha)
    if (glowSprite) ctx.drawImage(glowSprite, sx - halo, sy - halo, halo * 2, halo * 2)
    ctx.globalAlpha = Math.min(1, (0.72 + 0.28 * depth) * twinkle * (hitB ? 1 : 0.55 + 0.45 * (1 - focusMix)))
    ctx.fillStyle = '#ffffff'
    ctx.beginPath()
    ctx.arc(sx, sy, s * 0.46, 0, Math.PI * 2)
    ctx.fill()
    ctx.fillStyle = PINK
  }
  ctx.globalAlpha = 1
}

function loop(now: number) {
  draw(now)
  if (running) raf = requestAnimationFrame(loop)
}

function play() {
  if (running) return
  running = true
  start = performance.now()
  assembleAt = performance.now()
  if (reduced) {
    draw(performance.now())
    running = false
    return
  }
  raf = requestAnimationFrame(loop)
}

function stop() {
  running = false
  if (raf) cancelAnimationFrame(raf)
  raf = 0
}

onMounted(() => {
  canvas = document.createElement('canvas')
  reduced = prefersReducedMotion()
  makeGlow()
  if (host.value) {
    host.value.appendChild(canvas)
    canvas.style.display = 'block'
  }
  build()
  resize()
  ro = new ResizeObserver(() => {
    resize()
    if (!running) draw(performance.now())
  })
  if (host.value) ro.observe(host.value)
  io = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting)) play()
      else stop()
    },
    { threshold: 0.05 },
  )
  if (host.value) io.observe(host.value)
})

onBeforeUnmount(() => {
  stop()
  io?.disconnect()
  ro?.disconnect()
  canvas?.remove()
  canvas = null
})
</script>

<template>
  <div ref="host" class="au-orb" aria-hidden="true">
    <div class="au-orb__bloom"></div>
  </div>
</template>

<style scoped>
.au-orb {
  position: relative;
  width: 100%;
  height: 100%;
}
.au-orb canvas {
  position: relative;
  z-index: 1;
}
.au-orb__bloom {
  position: absolute;
  inset: 0;
  z-index: 0;
  background:
    radial-gradient(circle closest-side at 50% 50%, rgba(0, 156, 144, 0.62) 0%, rgba(0, 130, 124, 0.24) 45%, rgba(1, 38, 36, 0) 100%),
    radial-gradient(circle closest-side at 62% 58%, rgba(253, 233, 255, 0.16) 0%, rgba(1, 38, 36, 0) 72%);
  filter: blur(4px);
  animation: au-breathe 12s ease-in-out infinite;
}

@media (prefers-reduced-motion: reduce) {
  .au-orb__bloom {
    animation: none;
  }
}
</style>
