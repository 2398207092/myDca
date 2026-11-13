<script setup lang="ts">
/**
 * 台阶（Terrace）：板块容器，负向层级。
 * 满宽横带（左右不留边、无圆角），靠 inset 光影 + 2% 明度差形成「地面下沉一阶」。
 * 它是结构：不可点、无回弹；内部的可点元素才允许 +1 凸起（按钮）或平面 + chevron（导航行）。
 */
import SectionLabel from './SectionLabel.vue'

withDefaults(
  defineProps<{
    /** 板块标题；为空时只作为纯容器 */
    label?: string
  }>(),
  { label: '' },
)
defineEmits<{ (e: 'action'): void }>()
</script>

<template>
  <section class="terrace">
    <SectionLabel v-if="label || $slots.action" :text="label" class="terrace-head">
      <template #action>
        <slot name="action" />
      </template>
    </SectionLabel>
    <div class="terrace-body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.terrace {
  /* 满宽：吃掉页面的左右 gutter，边缘贴住容器两侧，避免变成「有边距的卡片」 */
  margin: var(--space-5) calc(var(--gutter) * -1) 0;
  padding: var(--space-4) var(--gutter) var(--space-4);
  background: var(--surface-bay);
  box-shadow: var(--terrace-shadow);
  border-radius: 0;
}
.terrace-head {
  margin-bottom: var(--space-2);
}
</style>
