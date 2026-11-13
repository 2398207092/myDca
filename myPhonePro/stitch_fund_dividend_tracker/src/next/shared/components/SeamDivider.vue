<script setup lang="ts">
/**
 * 接缝（Seam）：2px 双向刻线，上沿受阴影、下沿受光。
 * 用于同一台面上的小分组分区；它只是「结构」，不可点、无回弹。
 */
withDefaults(
  defineProps<{
    /** 更紧的上下间距，用于板块内部的行组之间 */
    tight?: boolean
  }>(),
  { tight: false },
)
</script>

<template>
  <div class="seam" :class="{ 'seam--tight': tight }" role="presentation" aria-hidden="true" />
</template>

<style scoped>
.seam {
  height: 2px;
  border-radius: 1px;
  background: linear-gradient(
    to bottom,
    var(--seam-dark) 0,
    var(--seam-dark) 1px,
    var(--seam-light) 1px,
    var(--seam-light) 100%
  );
  margin: var(--space-5) 0 var(--space-4);
}
.seam--tight {
  margin: var(--space-3) 0;
}
</style>
