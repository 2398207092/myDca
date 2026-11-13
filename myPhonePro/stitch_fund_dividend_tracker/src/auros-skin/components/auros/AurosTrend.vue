<script setup lang="ts">
/**
 * AurosTrend —— 迷你走势（章的"形状"之一）
 * 无坐标轴、无网格噪音：一条 lit 折线 + 竖向渐变淡出 + 末点光点；可选一条 0 轴。
 */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    points: number[]
    height?: number
    zero?: boolean
    label?: string
    caption?: string
  }>(),
  { height: 72, zero: false, label: '', caption: '' },
)

const view = computed(() => {
  const pts = props.points.length ? props.points : [0, 0]
  const min = Math.min(...pts)
  const max = Math.max(...pts)
  const span = max - min || 1
  // 等值序列（如 12 个月等额分红）归一化后会把平线贴到底部；居中画才读得出"持平"
  const flat = max === min
  const W = 100
  const H = 100
  const xy = (v: number, i: number) => {
    const x = pts.length === 1 ? 0 : (i / (pts.length - 1)) * W
    const y = flat ? H * 0.5 : H - ((v - min) / span) * H * 0.86 - H * 0.07
    return [x, y] as const
  }
  const coords = pts.map(xy)
  const line = coords.map(([x, y], i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(2)} ${y.toFixed(2)}`).join(' ')
  const area = `${line} L100 100 L0 100 Z`
  const last = coords[coords.length - 1]
  const zeroY = props.zero ? H - ((0 - min) / span) * H * 0.86 - H * 0.07 : null
  return { line, area, last, zeroY }
})
const uid = `au-trend-${Math.random().toString(36).slice(2, 8)}`
</script>

<template>
  <div class="trend">
    <div class="trend__chart" :style="{ height: `${height}px` }">
      <svg viewBox="0 0 100 100" preserveAspectRatio="none" class="trend__svg">
        <defs>
          <linearGradient :id="uid" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="var(--au-lit)" stop-opacity="0.24" />
            <stop offset="100%" stop-color="var(--au-lit)" stop-opacity="0" />
          </linearGradient>
        </defs>
        <line
          v-if="view.zeroY !== null"
          x1="0"
          :y1="view.zeroY"
          x2="100"
          :y2="view.zeroY"
          stroke="rgba(237,255,254,0.12)"
          stroke-width="1"
          vector-effect="non-scaling-stroke"
        />
        <path :d="view.area" :fill="`url(#${uid})`" class="trend__area" />
        <path
          :d="view.line"
          class="trend__line"
          fill="none"
          stroke="var(--au-lit)"
          stroke-width="1.4"
          stroke-linejoin="round"
          stroke-linecap="round"
          vector-effect="non-scaling-stroke"
          style="filter: drop-shadow(0 0 5px rgba(203, 255, 252, 0.45))"
        />
      </svg>
      <i class="trend__dot" :style="{ left: '100%', top: `${(view.last[1] / 100) * 100}%` }"></i>
    </div>
    <div v-if="label || caption" class="trend__labels">
      <span>{{ label }}</span>
      <span>{{ caption }}</span>
    </div>
  </div>
</template>

<style scoped>
.trend {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.trend__chart {
  position: relative;
  width: 100%;
  overflow: hidden;
  /* 折线"画出来"：整幅从左向右擦亮（clip 不打断折线本身；
     旧方案 stroke-dasharray 在 Chromium 会在中段留下断口） */
  animation: trend-wipe var(--au-dur-5) var(--au-ease) both;
}
.trend__svg {
  display: block;
  width: 100%;
  height: 100%;
}
@keyframes trend-wipe {
  from {
    clip-path: inset(0 100% 0 0);
    opacity: 0.5;
  }
  to {
    clip-path: inset(0 0 0 0);
    opacity: 1;
  }
}
/* 面积随后淡入 */
.trend__area {
  animation: au-reveal var(--au-dur-4) var(--au-ease) 180ms both;
}
@media (prefers-reduced-motion: reduce) {
  .trend__chart,
  .trend__area {
    animation: none;
  }
}
.trend__dot {
  position: absolute;
  width: 5px;
  height: 5px;
  margin: -2.5px 0 0 -5px;
  border-radius: 50%;
  background: var(--au-light, var(--au-lit));
  box-shadow: 0 0 8px rgba(203, 255, 252, 0.9);
}
.trend__labels {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  font-size: 10px;
  letter-spacing: 0.04em;
  color: rgba(187, 199, 198, 0.66);
}
</style>
