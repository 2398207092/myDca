<script setup lang="ts">
import IconBase from './IconBase.vue'
import type { IconName } from '@/shared/icons'

withDefaults(
  defineProps<{
    icon: IconName
    label: string
    size?: number
  }>(),
  { size: 44 },
)
</script>

<template>
  <button class="icon-key" type="button" :aria-label="label" :title="label" :style="{ width: `${size}px`, height: `${size}px` }">
    <IconBase :name="icon" :size="20" />
  </button>
</template>

<style scoped>
.icon-key {
  border-radius: var(--radius-key);
  border: 1px solid transparent;
  background: transparent;
  color: var(--ink-2);
  display: grid;
  place-items: center;
  transition: background 120ms ease, box-shadow 90ms ease, color 120ms ease;
}
.icon-key:hover {
  background: rgba(31, 35, 41, 0.04);
}
.icon-key:active {
  box-shadow: var(--press);
  color: var(--ink);
}
</style>
