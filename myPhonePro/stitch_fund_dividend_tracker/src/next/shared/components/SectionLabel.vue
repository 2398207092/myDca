<script setup lang="ts">
/**
 * 小标题 / 区块标签：12px 字距 + 刻痕标记 + 右侧可放动作。
 * 刻痕（notch）是「结构」的记号：不用抬升，也能让阅读者知道这里开始新的一段。
 */
defineProps<{
  text: string
}>()
</script>

<template>
  <div class="section-label">
    <span class="label-wrap">
      <i class="notch" aria-hidden="true" />
      <span class="micro">{{ text }}</span>
    </span>
    <slot name="action" />
  </div>
</template>

<style scoped>
.section-label {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  min-height: 20px;
  margin-bottom: var(--space-2);
}
.label-wrap {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}
.notch {
  flex: none;
  width: 10px;
  height: 2px;
  border-radius: 1px;
  background: linear-gradient(to bottom, var(--seam-dark) 0, var(--seam-dark) 1px, var(--seam-light) 1px);
}
</style>
