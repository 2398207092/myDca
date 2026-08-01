<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  message: string | null
  type?: 'success' | 'error' | 'info'
}>()

const emit = defineEmits<{ (e: 'close'): void }>()

const visible = ref(false)
let timer: ReturnType<typeof setTimeout> | null = null

watch(() => props.message, (val) => {
  if (val) {
    visible.value = true
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      visible.value = false
      setTimeout(() => emit('close'), 200)
    }, 2500)
  }
})
</script>

<template>
  <Teleport to="body">
    <Transition name="toast">
      <div
        v-if="visible && message"
        class="fixed top-16 left-1/2 -translate-x-1/2 z-[200] px-lg py-sm rounded-xl shadow-lg border font-body text-sm font-medium max-w-[90vw] whitespace-nowrap"
        :class="type === 'error'
          ? 'bg-alert text-white border-alert/20'
          : type === 'success'
            ? 'bg-green-500 text-white border-green-500/20'
            : 'bg-card-bg text-text-primary border-border-light/40'"
      >
        {{ message }}
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* D3 成功时刻：Toast 温和落下 + 缩放（ease-out-quart） */
.toast-enter-active { transition: all 0.3s cubic-bezier(0.25, 1, 0.5, 1); }
.toast-leave-active { transition: all 0.2s ease; }
.toast-enter-from { opacity: 0; transform: translate(-50%, -16px) scale(0.92); }
.toast-leave-to { opacity: 0; transform: translate(-50%, -16px) scale(0.92); }
</style>
