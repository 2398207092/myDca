<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    description?: string
    compact?: boolean
  }>(),
  { description: '', compact: false },
)
</script>

<template>
  <div class="empty" :class="{ 'empty--compact': compact }">
    <div class="empty-mark" aria-hidden="true" />
    <p class="empty-title">{{ title }}</p>
    <p v-if="description" class="empty-desc">{{ description }}</p>
    <slot />
  </div>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: var(--space-2);
  padding: var(--space-8) var(--space-4);
}
.empty--compact {
  padding: var(--space-5) var(--space-3);
}
.empty-mark {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  box-shadow: var(--in-slot);
  margin-bottom: var(--space-2);
}
.empty-title {
  font-size: var(--text-body);
  font-weight: 600;
  color: var(--ink-2);
}
.empty-desc {
  font-size: var(--text-label);
  color: var(--ink-3);
  line-height: 1.6;
  max-width: 260px;
}
</style>
