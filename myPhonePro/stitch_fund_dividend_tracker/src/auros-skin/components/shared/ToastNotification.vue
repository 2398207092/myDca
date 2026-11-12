<script setup lang="ts">
/**
 * AurosToast（原 ToastNotification 的深色版）
 * 顶部落下的读数提示：凯玻底 + 一根状态光点；成功用磷光青，错误用淡粉，信息用银。
 */
import { ref, watch } from 'vue'

const props = defineProps<{
  message: string | null
  type?: 'success' | 'error' | 'info'
}>()

const emit = defineEmits<{ (e: 'close'): void }>()

const visible = ref(false)
let timer: ReturnType<typeof setTimeout> | null = null

watch(
  () => props.message,
  (val) => {
    if (val) {
      visible.value = true
      if (timer) clearTimeout(timer)
      timer = setTimeout(() => {
        visible.value = false
        setTimeout(() => emit('close'), 200)
      }, 2500)
    }
  },
)
</script>

<template>
  <Teleport to="body">
    <Transition name="toast">
      <div v-if="visible && message" class="toast" :class="`is-${type || 'info'}`" role="status">
        <i class="toast__dot"></i>
        <span class="toast__text">{{ message }}</span>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.toast {
  position: fixed;
  top: calc(env(safe-area-inset-top, 0px) + 14px);
  left: 50%;
  z-index: 3000;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 90vw;
  padding: 10px 14px;
  border-radius: 6px;
  background: rgba(0, 55, 52, 0.94);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  color: var(--au-mist);
  font-family: 'InterAuros', 'PingFang SC', 'Noto Sans SC', system-ui, sans-serif;
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.01em;
  box-shadow: 0 0 24px rgba(0, 0, 0, 0.35);
}
.toast__dot {
  flex: none;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--au-silver);
}
.toast.is-success .toast__dot {
  background: var(--au-lit);
  box-shadow: 0 0 8px rgba(203, 255, 252, 0.9);
}
.toast.is-error {
  color: rgba(253, 233, 255, 0.95);
}
.toast.is-error .toast__dot {
  background: var(--au-pink);
  box-shadow: 0 0 8px rgba(253, 233, 255, 0.85);
}
.toast__text {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.toast-enter-active {
  transition: opacity 260ms cubic-bezier(0.22, 1, 0.36, 1), transform 260ms cubic-bezier(0.22, 1, 0.36, 1);
}
.toast-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translate(-50%, -10px);
}
.toast-enter-to,
.toast-leave-from {
  transform: translate(-50%, 0);
}
</style>
