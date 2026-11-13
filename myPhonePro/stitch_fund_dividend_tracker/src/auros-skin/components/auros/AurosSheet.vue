<script setup lang="ts">
/**
 * AurosSheet —— 底部抽屉（全站表单/确认共用）
 * 深海底面 + 顶部 16px 圆角 + 一条 grip；遮罩是深色半透明 + 模糊，不是黑色蒙层。
 */
import { onBeforeUnmount, watch } from 'vue'

const props = withDefaults(
  defineProps<{ modelValue: boolean; title?: string; maxHeight?: string; closeOnMask?: boolean }>(),
  { title: '', maxHeight: '86svh', closeOnMask: true },
)
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'close'): void }>()

function close() {
  emit('update:modelValue', false)
  emit('close')
}

watch(
  () => props.modelValue,
  (open) => {
    if (typeof document === 'undefined') return
    document.body.style.overflow = open ? 'hidden' : ''
  },
)

onBeforeUnmount(() => {
  if (typeof document !== 'undefined') document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition name="au-sheet">
      <div v-if="modelValue" class="mask" @click.self="closeOnMask && close()">
        <section class="sheet" :style="{ maxHeight }" role="dialog" aria-modal="true">
          <span class="sheet__grip" aria-hidden="true"></span>
          <header v-if="title || $slots.head" class="sheet__head">
            <h2 v-if="title" class="sheet__title">{{ title }}</h2>
            <slot name="head" />
            <button type="button" class="sheet__close" aria-label="关闭" @click="close">
              <svg viewBox="0 0 12 12" width="12" height="12" fill="none">
                <path d="m3 3 6 6M9 3l-6 6" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" />
              </svg>
            </button>
          </header>
          <div class="sheet__body"><slot /></div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: flex;
  align-items: flex-end;
  background: rgba(1, 19, 18, 0.72);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}
.sheet {
  width: 100%;
  display: flex;
  flex-direction: column;
  padding: 0 20px calc(env(safe-area-inset-bottom, 0px) + 20px);
  border-radius: 16px 16px 0 0;
  background: linear-gradient(180deg, var(--au-kelp) 0%, var(--au-deep) 62%);
  box-shadow: 0 0 60px rgba(0, 0, 0, 0.45);
}
.sheet__grip {
  align-self: center;
  width: 36px;
  height: 3px;
  margin: 8px 0 4px;
  border-radius: 2px;
  background: rgba(237, 255, 254, 0.18);
}
.sheet__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 0 12px;
}
.sheet__title {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 0.02em;
  color: var(--au-white);
}
.sheet__close {
  flex: none;
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: rgba(237, 255, 254, 0.06);
  color: var(--au-silver);
}
.sheet__body {
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}
.sheet__body::-webkit-scrollbar {
  display: none;
}

.au-sheet-enter-active,
.au-sheet-leave-active {
  transition: opacity 240ms var(--au-ease);
}
.au-sheet-enter-active .sheet,
.au-sheet-leave-active .sheet {
  transition: transform 320ms var(--au-ease);
}
.au-sheet-enter-from,
.au-sheet-leave-to {
  opacity: 0;
}
.au-sheet-enter-from .sheet,
.au-sheet-leave-to .sheet {
  transform: translateY(100%);
}

@media (prefers-reduced-motion: reduce) {
  .au-sheet-enter-active,
  .au-sheet-leave-active,
  .au-sheet-enter-active .sheet,
  .au-sheet-leave-active .sheet {
    transition: none;
  }
}
</style>
