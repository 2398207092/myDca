<script setup lang="ts">
import { computed } from 'vue'
import { deltaArrow, deltaTone, type DeltaTone } from '@/next/shared/money'

/**
 * 大读数：标签 + 金额（tabular）+ 可选涨跌。
 * 金额字号自适应：字符数越多越小，保证 390 宽不换行、不溢出。
 */
const props = withDefaults(
  defineProps<{
    label: string
    amount: string
    size?: 'xl' | 'lg' | 'md'
    deltaText?: string
    caption?: string
    tone?: DeltaTone
    showArrow?: boolean
  }>(),
  { size: 'xl', deltaText: '', caption: '', tone: 'flat', showArrow: true },
)

const amountClass = computed(() => {
  if (props.size !== 'xl') return `amount--${props.size}`
  // 按整串字符数降档（含 ¥、千分位、小数点），不能只数数字：
  // 320 宽下 ¥1,234,567.89（13 字符）在 44px 会横向溢出。
  const len = props.amount.length
  if (len > 12) return 'amount--sm'
  if (len > 11) return 'amount--md'
  return 'amount--xl'
})
</script>

<template>
  <div class="readout">
    <div class="micro">{{ label }}</div>
    <div class="amount num" :class="amountClass">{{ amount }}</div>
    <div v-if="deltaText" class="delta" :class="tone">
      <span v-if="showArrow" class="arrow">{{ deltaArrow(deltaText) }}</span>
      <span class="num">{{ deltaText }}</span>
      <span v-if="caption" class="cap">{{ caption }}</span>
    </div>
    <slot />
  </div>
</template>

<style scoped>
.readout {
  padding-top: var(--space-3);
}
.amount {
  letter-spacing: -0.02em;
  font-weight: 600;
  white-space: nowrap;
  margin-top: var(--space-1);
}
.amount--xl {
  font-size: var(--text-readout-xl);
  line-height: 1.05;
}
.amount--md {
  font-size: 38px;
  line-height: 1.08;
}
.amount--sm {
  font-size: 32px;
  line-height: 1.1;
}
.amount--lg {
  font-size: var(--text-readout-lg);
  line-height: 1.1;
}
.amount--md.amount--lg {
  font-size: var(--text-readout-md);
  line-height: 1.2;
}
.delta {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: var(--space-2);
  font-size: var(--text-body-sm);
  font-weight: 500;
}
.delta.up {
  color: var(--up);
}
.delta.down {
  color: var(--down);
}
.delta.flat {
  color: var(--ink-2);
}
.arrow {
  font-size: 12px;
}
.cap {
  color: var(--ink-3);
  font-size: var(--text-micro);
  font-weight: 400;
}
</style>
