<script setup lang="ts">
/** Toast：动作结果反馈，固定在上方（避开底坞与安全区）。 */
import { ui, dismissToast } from '@/shared/ui'
import IconBase from './IconBase.vue'
</script>

<template>
  <div class="toast-host" aria-live="polite" aria-atomic="false">
    <TransitionGroup name="toast">
      <button
        v-for="t in ui.toasts"
        :key="t.id"
        class="toast"
        :class="`toast--${t.kind}`"
        type="button"
        @click="dismissToast(t.id)"
      >
        <IconBase :name="t.kind === 'ok' ? 'check' : 'alert'" :size="18" />
        <span class="toast-text">{{ t.message }}</span>
      </button>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  z-index: var(--z-toast);
  left: 50%;
  transform: translateX(-50%);
  top: calc(var(--safe-top) + var(--space-2));
  width: min(420px, calc(100vw - 32px));
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  pointer-events: none;
}
.toast {
  pointer-events: auto;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 44px;
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-key);
  background: var(--canvas);
  box-shadow: var(--el-panel);
  border: 1px solid var(--edge);
  color: var(--ink);
  font-size: var(--text-label);
  text-align: left;
}
.toast--ok :deep(svg) {
  color: var(--accent);
}
.toast--error {
  border-color: rgba(166, 27, 27, 0.4);
}
.toast--error :deep(svg) {
  color: var(--danger);
}
.toast-text {
  line-height: 1.4;
}
.toast-enter-active,
.toast-leave-active {
  transition: opacity 160ms ease, transform 160ms ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
