<script setup lang="ts">
/** 月份切换：44px 凸起键（可按），中间月份标签本身就是「回到本月」按钮。 */
import IconBase from '@/shared/components/IconBase.vue'
import { monthLabel } from '@/shared/date'

defineProps<{
  month: string
  canNext: boolean
  isCurrent: boolean
}>()

defineEmits<{
  (e: 'prev'): void
  (e: 'next'): void
  (e: 'current'): void
}>()
</script>

<template>
  <div class="month">
    <button class="nav" type="button" aria-label="上一个月" @click="$emit('prev')">
      <span class="flip" aria-hidden="true"><IconBase name="chevron" :size="20" /></span>
    </button>

    <button
      class="label-key"
      type="button"
      :disabled="isCurrent"
      :aria-label="isCurrent ? `${monthLabel(month)}（本月）` : `正在查看 ${monthLabel(month)}，回到本月`"
      @click="$emit('current')"
    >
      <b class="label-text num">{{ monthLabel(month) }}</b>
      <span v-if="!isCurrent" class="hint micro">轻触回到本月</span>
      <span v-else class="hint micro">本月</span>
    </button>

    <!-- 已到本月：不渲染「可点但禁用」的空方块，用等宽占位保持左右对称 -->
    <span v-if="!canNext" class="nav nav--slot" aria-hidden="true" />
    <button v-else class="nav" type="button" aria-label="下一个月" @click="$emit('next')">
      <IconBase name="chevron" :size="20" />
    </button>
  </div>
</template>

<style scoped>
.month {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) 44px;
  align-items: center;
  gap: var(--space-2);
  min-height: 56px;
  padding: var(--space-1) 0 var(--space-2);
}
.nav {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-key);
  border: 1px solid var(--edge);
  background: var(--canvas);
  box-shadow: var(--el-key);
  color: var(--ink-2);
  display: grid;
  place-items: center;
  transition: box-shadow 90ms ease, color 120ms ease;
}
.nav:active {
  box-shadow: var(--press);
  color: var(--ink);
}
.nav--slot {
  box-shadow: none;
  border-color: transparent;
  background: transparent;
  pointer-events: none;
}
.flip {
  display: grid;
  place-items: center;
  transform: rotate(180deg);
}
.label-key {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  min-height: 44px;
  padding: 2px var(--space-2);
  border-radius: var(--radius-key);
}
.label-key:disabled {
  cursor: default;
}
.label-key:not(:disabled):active {
  box-shadow: var(--in-slot);
}
.label-text {
  font-size: var(--text-body);
  font-weight: 600;
  color: var(--ink);
  white-space: nowrap;
}
.hint {
  color: var(--ink-3);
}
.label-key:not(:disabled) .hint {
  color: var(--accent);
}
</style>
