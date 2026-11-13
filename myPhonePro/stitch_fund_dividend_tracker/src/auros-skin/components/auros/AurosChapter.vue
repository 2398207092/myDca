<script setup lang="ts">
/**
 * AurosChapter —— 章卡（信息骨架 L1）
 * 一章 = 一张卡：Liquid Kelp 抬升面、16px 圆角、无阴影无描边。
 * 卡头：10px 拉丁眉标 + 20px 中文标题 + 右侧一句结论；卡身 14px 后开始内容。
 * 动效：进入视口时整卡淡入上浮 10px（--au-dur-3 / --au-ease），只播一次。
 */
import { ref } from 'vue'
import { useReveal } from '@/auros-skin/utils/useReveal'

withDefaults(
  defineProps<{
    label?: string
    title: string
    meta?: string
    /** plain = 实心抬升面（主数据章，默认）；band = 半透明次级卡（操作/设置/历史章） */
    tone?: 'plain' | 'band'
    /** 章与章之间固定 12px；首章传 false */
    gap?: boolean
  }>(),
  { label: '', meta: '', tone: 'plain', gap: true },
)

const root = ref<HTMLElement | null>(null)
const isIn = useReveal(root)
</script>

<template>
  <section
    ref="root"
    class="chapter au-card au-reveal"
    :class="[
      { 'au-card--soft': tone === 'band', 'chapter--gap': gap, 'is-in': isIn },
    ]"
  >
    <header class="chapter__head">
      <span v-if="label" class="chapter__label">{{ label }}</span>
      <h2 class="chapter__title">{{ title }}</h2>
      <span v-if="meta" class="chapter__meta">{{ meta }}</span>
    </header>
    <div class="chapter__body"><slot /></div>
  </section>
</template>

<style scoped>
.chapter--gap {
  margin-top: var(--au-card-gap);
}
.chapter__head {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.chapter__label {
  flex: none;
  font-size: 10px;
  letter-spacing: 0.16em;
  color: var(--au-silver);
}
.chapter__title {
  margin: 0;
  font-size: 20px;
  font-weight: 500;
  line-height: 1.3;
  letter-spacing: 0.01em;
  color: var(--au-white);
}
.chapter__meta {
  margin-left: auto;
  flex: none;
  font-size: 10px;
  letter-spacing: 0.06em;
  color: rgba(187, 199, 198, 0.72);
  max-width: 45%;
  text-align: right;
}
.chapter__body {
  margin-top: 14px;
}
</style>
