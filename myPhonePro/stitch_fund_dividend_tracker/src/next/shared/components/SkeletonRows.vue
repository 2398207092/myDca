<script setup lang="ts">
withDefaults(defineProps<{ rows?: number }>(), { rows: 4 })
</script>

<template>
  <div class="skeleton" aria-hidden="true">
    <div v-for="i in rows" :key="i" class="sk-row" :style="{ opacity: 1 - i * 0.12 }">
      <span class="sk-bar sk-bar--w60" />
      <span class="sk-bar sk-bar--w90" />
    </div>
  </div>
</template>

<style scoped>
.skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4) 0;
}
.sk-row {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.sk-bar {
  display: block;
  height: 12px;
  border-radius: 6px;
  background: var(--surface-inset);
  box-shadow: var(--in-slot);
}
.sk-bar--w60 {
  width: 60%;
}
.sk-bar--w90 {
  width: 90%;
  height: 9px;
}
</style>
