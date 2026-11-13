<script setup lang="ts">
/**
 * AurosGroup —— 组（信息骨架 L2）
 * 组头：中文 13/500 + 数量 10 + 右侧合计 13 tabular；组头下一条更强的发丝线；
 * 组内行缩进 12px。一章最多 3 组、一组最多 5 行（超出用 AurosDisclosure）。
 */
withDefaults(defineProps<{ title: string; count?: string; total?: string }>(), {
  count: '',
  total: '',
})
</script>

<template>
  <section class="group">
    <header class="group__head">
      <span class="group__title">{{ title }}</span>
      <span v-if="count" class="group__count">{{ count }}</span>
      <span v-if="total" class="group__total au-num">{{ total }}</span>
    </header>
    <div class="group__body"><slot /></div>
  </section>
</template>

<style scoped>
.group + .group {
  margin-top: 18px;
}
.group__head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding-bottom: 9px;
  border-bottom: 1px solid rgba(237, 255, 254, 0.16);
}
.group__title {
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.02em;
  color: var(--au-mist);
}
.group__count {
  font-size: 10px;
  letter-spacing: 0.06em;
  color: rgba(187, 199, 198, 0.6);
}
.group__total {
  margin-left: auto;
  font-size: 13px;
  font-weight: 500;
  color: var(--au-mist);
}
.group__body {
  /* 卡片已经提供了 16px 内边距，卡内不再二次缩进 */
  padding-left: 0;
}
</style>
