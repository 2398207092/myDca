<script setup lang="ts">
/** 状态胶囊：颜色只是第三通道，文字永远是主角。 */
withDefaults(
  defineProps<{
    tone?: 'accent' | 'muted' | 'warn' | 'danger'
  }>(),
  { tone: 'muted' },
)
</script>

<template>
  <span class="pill" :class="`pill--${tone}`"><slot /></span>
</template>

<style scoped>
.pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 9px;
  border-radius: var(--radius-pill);
  font-size: var(--text-micro);
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
  border: 1px solid var(--edge);
  color: var(--ink-2);
  background: transparent;
}
.pill--accent {
  border-color: rgba(46, 75, 198, 0.35);
  background: var(--accent-soft);
  color: var(--accent);
}
.pill--warn {
  border-color: rgba(138, 90, 0, 0.35);
  background: transparent;
  color: var(--warn);
}
.pill--danger {
  border-color: rgba(166, 27, 27, 0.35);
  background: transparent;
  color: var(--danger);
}
</style>
