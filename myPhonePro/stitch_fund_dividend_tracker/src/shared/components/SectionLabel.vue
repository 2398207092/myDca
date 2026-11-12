<script setup lang="ts">
/** 小标题 / 区块标签：12px 字距 + 右侧可放动作。 */
defineProps<{
  text: string
}>()
</script>

<template>
  <div class="section-label">
    <span class="micro">{{ text }}</span>
    <slot name="action" />
  </div>
</template>

<style scoped>
.section-label {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  min-height: 20px;
  margin-bottom: var(--space-2);
}
</style>
