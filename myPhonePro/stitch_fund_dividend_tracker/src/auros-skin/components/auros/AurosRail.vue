<script setup lang="ts">
/**
 * AurosRail —— 章里的"形状"：占比条 / 刻度 / 进度
 * 一根 4px 轨道 + 一道点亮的进度 + 可选 11 根月份刻度；下方左右两端标签。
 * 动效：挂载后从 0 生长到目标值（--au-dur-5），数值变化时平滑跟随。
 */
import { onMounted, ref, watch } from 'vue'
import { prefersReducedMotion } from '@/auros-skin/utils/auros-motion'

const props = withDefaults(
  defineProps<{
    percent: number
    labelLeft?: string
    labelRight?: string
    ticks?: number
    tone?: 'lit' | 'pink'
    caption?: string
  }>(),
  { labelLeft: '', labelRight: '', ticks: 0, tone: 'lit', caption: '' },
)

const shown = ref(prefersReducedMotion() ? props.percent : 0)

const clamp = (v: number) => Math.min(100, Math.max(0, v))

onMounted(() => {
  if (prefersReducedMotion()) return
  requestAnimationFrame(() => requestAnimationFrame(() => (shown.value = clamp(props.percent))))
})

watch(
  () => props.percent,
  (v) => {
    shown.value = clamp(v)
  },
)
</script>

<template>
  <div class="rail">
    <div class="rail__track">
      <i class="rail__fill" :class="`is-${tone}`" :style="{ width: `${shown}%` }"></i>
      <i class="rail__tip" :class="`is-${tone}`" :style="{ left: `${shown}%` }"></i>
      <b
        v-for="i in ticks"
        :key="i"
        class="rail__tick"
        :style="{ left: `${(i * 100) / (ticks + 1)}%` }"
      ></b>
    </div>
    <div v-if="labelLeft || labelRight || caption" class="rail__labels">
      <span>{{ labelLeft }}</span>
      <span v-if="caption" class="rail__cap">{{ caption }}</span>
      <span>{{ labelRight }}</span>
    </div>
  </div>
</template>

<style scoped>
.rail {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.rail__track {
  position: relative;
  height: 4px;
}
.rail__track::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 2px;
  background: rgba(237, 255, 254, 0.09);
}
.rail__fill {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  border-radius: 2px;
  transition: width 700ms var(--au-ease);
}
.rail__fill.is-lit {
  background: linear-gradient(90deg, rgba(203, 255, 252, 0.5), var(--au-lit));
  box-shadow: 0 0 10px rgba(139, 245, 232, 0.4);
}
.rail__fill.is-pink {
  background: linear-gradient(90deg, rgba(253, 233, 255, 0.45), var(--au-pink));
  box-shadow: 0 0 10px rgba(253, 233, 255, 0.35);
}
.rail__tip {
  position: absolute;
  top: 50%;
  width: 6px;
  height: 6px;
  margin: -3px 0 0 -3px;
  border-radius: 50%;
  transition: left 700ms var(--au-ease);
}
.rail__tip.is-lit {
  background: var(--au-lit);
  box-shadow: 0 0 8px rgba(203, 255, 252, 0.85);
}
.rail__tip.is-pink {
  background: var(--au-pink);
  box-shadow: 0 0 8px rgba(253, 233, 255, 0.85);
}
.rail__tick {
  position: absolute;
  top: -3px;
  width: 1px;
  height: 10px;
  background: rgba(237, 255, 254, 0.12);
}
.rail__labels {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  font-size: 10px;
  letter-spacing: 0.04em;
  color: rgba(187, 199, 198, 0.72);
}
.rail__cap {
  margin-left: auto;
  margin-right: auto;
  color: var(--au-silver);
}
</style>
