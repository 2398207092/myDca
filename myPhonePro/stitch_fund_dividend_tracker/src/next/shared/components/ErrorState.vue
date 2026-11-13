<script setup lang="ts">
import BtnKey from './BtnKey.vue'

defineProps<{
  message: string
}>()
defineEmits<{ (e: 'retry'): void }>()
</script>

<template>
  <div class="error-box" role="alert">
    <span class="error-mark" aria-hidden="true">!</span>
    <p class="error-text">{{ message }}</p>
    <BtnKey variant="secondary" @click="$emit('retry')">重新加载</BtnKey>
  </div>
</template>

<style scoped>
.error-box {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-5) var(--space-4);
  border-radius: var(--radius-key);
  background: var(--surface-1);
  border: 1px solid rgba(166, 27, 27, 0.28);
}
.error-mark {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 700;
  color: var(--danger);
  border: 1px solid rgba(166, 27, 27, 0.45);
}
.error-text {
  font-size: var(--text-label);
  color: var(--ink);
  line-height: 1.6;
}
</style>
