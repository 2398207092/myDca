<script setup lang="ts">
import { computed } from 'vue'
import { ICON_PATHS, type IconName } from '@/shared/icons'

const props = withDefaults(
  defineProps<{
    name: IconName
    size?: number
    stroke?: number
  }>(),
  { size: 20, stroke: 2 },
)

const paths = computed(() => ICON_PATHS[props.name] ?? [])
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="stroke"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-for="(d, i) in paths" :key="i" :d="d" />
  </svg>
</template>
