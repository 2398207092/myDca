<script setup lang="ts">
/**
 * AurosButton —— 全站按钮
 * primary：极光渐变（全站唯一允许用渐变的地方），深色字；
 * ghost：蚨玻色块；text：纯文字 + 箭头。
 */
withDefaults(
  defineProps<{
    variant?: 'primary' | 'ghost' | 'text'
    block?: boolean
    disabled?: boolean
    size?: 'md' | 'sm'
  }>(),
  { variant: 'primary', block: false, disabled: false, size: 'md' },
)
</script>

<template>
  <button
    type="button"
    class="btn"
    :class="[`btn--${variant}`, { 'btn--block': block, 'btn--sm': size === 'sm' }]"
    :disabled="disabled"
  >
    <slot />
  </button>
</template>

<style scoped>
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 44px;
  padding: 0 20px;
  border: 0;
  border-radius: 6px;
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.12em;
  text-indent: 0.12em;
  transition:
    transform 160ms var(--au-ease),
    filter 200ms var(--au-ease),
    background 200ms var(--au-ease);
}
.btn--block {
  width: 100%;
}
.btn--sm {
  height: 34px;
  padding: 0 14px;
  font-size: 13px;
}
.btn--primary {
  background: var(--au-aurora);
  color: var(--au-ink);
}
.btn--ghost {
  background: rgba(0, 55, 52, 0.9);
  color: var(--au-mist);
}
.btn--text {
  height: auto;
  padding: 0;
  background: none;
  color: var(--au-mist);
  letter-spacing: 0.02em;
  text-indent: 0;
}
.btn:active:not(:disabled) {
  transform: scale(0.97);
}
.btn--primary:active:not(:disabled) {
  filter: brightness(0.96);
}
.btn:disabled {
  opacity: 0.4;
}
</style>
