<script setup lang="ts">
/**
 * 筛选 chips：全部 / 交易 / 分红 / 定投。
 * 未选 = 凸起（可按），选中 = 凹陷 + 强调色 + 字重变化 + 对勾（状态不只靠颜色）。
 * 44px 高，可横向滚动；aria-label 里带条数，读屏也能知道筛选结果量。
 */
import type { PastFilter, PastFilterOption } from '../usePastData'

const props = defineProps<{
  modelValue: PastFilter
  options: PastFilterOption[]
  counts?: Record<PastFilter, number>
}>()

defineEmits<{ (e: 'update:modelValue', value: PastFilter): void }>()

function countOf(value: PastFilter): number {
  return props.counts?.[value] ?? 0
}
</script>

<template>
  <div class="chips" role="group" aria-label="事件类型筛选">
    <button
      v-for="opt in options"
      :key="opt.value"
      class="chip"
      :class="{ 'chip--on': modelValue === opt.value }"
      type="button"
      :aria-pressed="modelValue === opt.value"
      :aria-label="`${opt.label}，${countOf(opt.value)} 条`"
      @click="$emit('update:modelValue', opt.value)"
    >
      <span v-if="modelValue === opt.value" class="check" aria-hidden="true">✓</span>
      {{ opt.label }}
    </button>
  </div>
</template>

<style scoped>
.chips {
  display: flex;
  gap: var(--space-2);
  overflow-x: auto;
  padding: 6px 2px var(--space-2);
  margin: 0 -2px;
  scrollbar-width: none;
}
.chips::-webkit-scrollbar {
  display: none;
}
.chip {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: 44px;
  padding: 0 18px;
  border-radius: var(--radius-key);
  border: 1px solid var(--edge);
  background: var(--canvas);
  box-shadow: var(--el-key);
  color: var(--ink-2);
  font-size: var(--text-label);
  font-weight: 500;
  white-space: nowrap;
  transition: box-shadow 90ms ease, color 120ms ease, font-weight 120ms ease;
}
.chip:active {
  box-shadow: var(--press);
}
.chip--on {
  background: var(--surface-inset);
  box-shadow: var(--in-slot);
  color: var(--accent);
  font-weight: 600;
}
.check {
  font-size: var(--text-micro);
  line-height: 1;
}
</style>
