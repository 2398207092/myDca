<script setup lang="ts">
/**
 * AurosCard —— 自定义内容卡（非"章"结构时使用）
 * 全站信息只用两种容器：章卡（AurosChapter）和本组件。卡内不允许再套卡。
 * 抬升面 = Liquid Kelp（默认）；次级面 = 半透明抬升面（soft，操作/工具/说明块）。
 * 注：不存在"下沉卡"——页面底部画布本身就是 --au-deep，下沉卡会隐形。
 */
import { ref } from 'vue'
import { useReveal } from '@/auros-skin/utils/useReveal'

withDefaults(
  defineProps<{ tone?: 'raised' | 'soft'; flush?: boolean; reveal?: boolean }>(),
  { tone: 'raised', flush: false, reveal: true },
)

const root = ref<HTMLElement | null>(null)
const isIn = useReveal(root)
</script>

<template>
  <section
    ref="root"
    class="au-card"
    :class="{
      'au-card--soft': tone === 'soft',
      'au-card--flush': flush,
      'au-reveal': reveal,
      'is-in': reveal && isIn,
    }"
  >
    <slot />
  </section>
</template>
