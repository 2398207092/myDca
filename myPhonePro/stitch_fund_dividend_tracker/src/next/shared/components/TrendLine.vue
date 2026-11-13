<script setup lang="ts">
/** 走势线：SVG 刻在凹槽里的曲线（不引图表库）。数据表以文本行另行呈现。 */
import { computed } from 'vue'

export interface TrendPoint {
  label: string
  value: number
}

const props = withDefaults(
  defineProps<{
    points: TrendPoint[]
    height?: number
    ariaLabel?: string
  }>(),
  { height: 64, ariaLabel: '资产走势' },
)

const WIDTH = 300
const PAD = 4

const geom = computed(() => {
  const pts = props.points.filter((p) => Number.isFinite(p.value))
  if (pts.length < 2) return null
  const values = pts.map((p) => p.value)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const span = max - min || 1
  const stepX = (WIDTH - PAD * 2) / (pts.length - 1)
  const coords = pts.map((p, i) => {
    const x = PAD + i * stepX
    const y = PAD + (1 - (p.value - min) / span) * (props.height - PAD * 2)
    return { x, y }
  })
  const line = coords.map((c, i) => `${i === 0 ? 'M' : 'L'}${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(' ')
  const area = `${line} L${coords[coords.length - 1].x.toFixed(1)},${props.height - PAD} L${PAD},${props.height - PAD} Z`
  const last = coords[coords.length - 1]
  return { line, area, last }
})
</script>

<template>
  <div class="trend" :style="{ height: `${height}px` }">
    <svg
      v-if="geom"
      :viewBox="`0 0 ${WIDTH} ${height}`"
      preserveAspectRatio="none"
      class="trend-svg"
      role="img"
      :aria-label="ariaLabel"
    >
      <path :d="geom.area" class="area" />
      <path :d="geom.line" class="line" vector-effect="non-scaling-stroke" />
      <circle :cx="geom.last.x" :cy="geom.last.y" r="3" class="dot" />
    </svg>
    <div v-else class="trend-empty micro">暂无走势数据</div>
  </div>
</template>

<style scoped>
.trend {
  width: 100%;
  border-radius: var(--radius-key);
  box-shadow: var(--in-slot);
  background: var(--canvas);
  padding: 0;
  overflow: hidden;
  display: grid;
  place-items: stretch;
}
.trend-svg {
  width: 100%;
  height: 100%;
  display: block;
}
.area {
  fill: rgba(46, 75, 198, 0.1);
  stroke: none;
}
.line {
  fill: none;
  stroke: var(--accent);
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.dot {
  fill: var(--accent);
  stroke: var(--canvas);
  stroke-width: 2;
}
.trend-empty {
  display: grid;
  place-items: center;
  height: 100%;
}
</style>
