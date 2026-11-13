<script setup lang="ts">
/**
 * 构成条：凹槽里的分类分段。永远配「文本行 + 百分比」一起出现，
 * 颜色只是辅助（无障碍要求：不能只靠颜色传达）。
 */
import { computed } from 'vue'

export interface CompositionSegment {
  key: string
  label: string
  value: number
  color: string
}

const props = withDefaults(
  defineProps<{
    segments: CompositionSegment[]
    height?: number
  }>(),
  { height: 14 },
)

const total = computed(() => props.segments.reduce((sum, s) => sum + Math.max(0, s.value), 0))
const fills = computed(() =>
  props.segments
    .filter((s) => s.value > 0 && total.value > 0)
    .map((s) => ({ ...s, width: (Math.max(0, s.value) / total.value) * 100 })),
)
</script>

<template>
  <div
    class="bar"
    :style="{ height: `${height}px` }"
    role="img"
    :aria-label="`构成：${fills.map((f) => `${f.label} ${f.width.toFixed(1)}%`).join('，') || '暂无数据'}`"
  >
    <i
      v-for="(f, idx) in fills"
      :key="f.key"
      class="seg"
      :style="{ width: `${f.width}%`, background: f.color }"
      :class="{ 'seg--first': idx === 0 }"
    />
  </div>
</template>

<style scoped>
.bar {
  display: flex;
  align-items: stretch;
  border-radius: var(--radius-pill);
  box-shadow: var(--in-slot);
  background: var(--surface-inset);
  overflow: hidden;
  width: 100%;
}
.seg {
  display: block;
  height: 100%;
  min-width: 2px;
}
.seg + .seg {
  border-left: 2px solid var(--canvas);
}
</style>
