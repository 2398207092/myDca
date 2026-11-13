<script setup lang="ts">
/**
 * 主按钮 / 次按钮 / 危险按钮：新拟态「凸起可按」，按下变「凹陷」。
 * 尺寸遵守 44px 触摸目标；primary 用强调色实底，其余用材质 + 1px 补偿边。
 */
withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'danger' | 'ghost'
    block?: boolean
    disabled?: boolean
    type?: 'button' | 'submit'
  }>(),
  { variant: 'secondary', block: false, disabled: false, type: 'button' },
)
</script>

<template>
  <button
    class="btn"
    :class="[`btn--${variant}`, { 'btn--block': block, 'btn--disabled': disabled }]"
    :type="type"
    :disabled="disabled"
  >
    <slot />
  </button>
</template>

<style scoped>
.btn {
  min-height: 44px;
  padding: 0 18px;
  border-radius: var(--radius-key);
  border: 1px solid var(--edge);
  background: var(--canvas);
  box-shadow: var(--el-key);
  color: var(--ink);
  font-size: var(--text-label);
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: box-shadow 90ms ease, transform 90ms ease, background 120ms ease;
}
.btn:active {
  box-shadow: var(--press);
  transform: translateY(1px);
}
.btn--block {
  width: 100%;
}
.btn--primary {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--on-accent);
}
.btn--primary:active {
  background: var(--accent-strong);
}
.btn--danger {
  background: var(--canvas);
  border-color: rgba(166, 27, 27, 0.45);
  color: var(--danger);
}
.btn--ghost {
  border-color: transparent;
  background: transparent;
  box-shadow: none;
  color: var(--accent);
  padding: 0 10px;
}
.btn--ghost:active {
  box-shadow: var(--in-slot);
  transform: none;
}
.btn--disabled {
  box-shadow: none;
  border-color: transparent;
  background: var(--surface-inset);
  color: var(--ink-3);
  cursor: not-allowed;
  transform: none;
}
.btn:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
</style>
