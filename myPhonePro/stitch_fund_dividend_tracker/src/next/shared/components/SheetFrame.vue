<script setup lang="ts">
/**
 * 底部 Sheet 骨架：遮罩 + 圆角面板 + 抓手 + 标题 + 关闭键 + Esc/遮罩关闭。
 * 焦点：打开时聚焦面板，关闭时还给触发元素；body 滚动锁定。
 */
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import IconBase from './IconBase.vue'

const props = defineProps<{
  title: string
  subtitle?: string
}>()
const emit = defineEmits<{ (e: 'close'): void }>()

const panel = ref<HTMLElement | null>(null)
let lastActive: HTMLElement | null = null

function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape') {
    e.preventDefault()
    emit('close')
  }
}

onMounted(async () => {
  lastActive = document.activeElement instanceof HTMLElement ? document.activeElement : null
  document.addEventListener('keydown', onKeydown)
  document.body.style.overflow = 'hidden'
  await nextTick()
  panel.value?.focus()
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
  lastActive?.focus?.()
})
</script>

<template>
  <Teleport to="body">
    <div class="sheet-layer" role="dialog" aria-modal="true" :aria-label="props.title">
      <button class="scrim" type="button" aria-label="关闭" tabindex="-1" @click="$emit('close')" />
      <div ref="panel" class="sheet-panel" tabindex="-1">
        <div class="grabber" aria-hidden="true" />
        <header class="sheet-head">
          <div class="head-text">
            <h2 class="sheet-title">{{ title }}</h2>
            <p v-if="subtitle" class="sheet-subtitle">{{ subtitle }}</p>
          </div>
          <button class="close-key" type="button" aria-label="关闭" @click="$emit('close')">
            <IconBase name="close" :size="20" />
          </button>
        </header>
        <div class="sheet-body">
          <slot />
        </div>
        <footer v-if="$slots.footer" class="sheet-footer">
          <slot name="footer" />
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.sheet-layer {
  position: fixed;
  inset: 0;
  z-index: var(--z-sheet);
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}
.scrim {
  position: absolute;
  inset: 0;
  background: rgba(31, 35, 41, 0.32);
  border-radius: 0;
  cursor: default;
}
.sheet-panel {
  position: relative;
  background: var(--canvas);
  border-radius: var(--radius-sheet) var(--radius-sheet) 0 0;
  box-shadow: 0 -12px 30px rgba(31, 35, 41, 0.18);
  max-height: 88dvh;
  display: flex;
  flex-direction: column;
  padding-bottom: calc(var(--space-4) + var(--safe-bottom));
  animation: sheet-in 220ms cubic-bezier(0.16, 1, 0.3, 1);
  outline: none;
}
@keyframes sheet-in {
  from {
    transform: translateY(24px);
    opacity: 0.6;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}
.grabber {
  width: 36px;
  height: 4px;
  border-radius: var(--radius-pill);
  background: var(--shade);
  margin: var(--space-2) auto var(--space-1);
  box-shadow: var(--in-slot);
}
.sheet-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2) var(--gutter) var(--space-3);
}
.head-text {
  min-width: 0;
}
.sheet-title {
  font-size: var(--text-title);
  line-height: 1.3;
  font-weight: 600;
}
.sheet-subtitle {
  margin-top: var(--space-1);
  font-size: var(--text-micro);
  color: var(--ink-3);
  line-height: 1.5;
}
.close-key {
  width: 44px;
  height: 44px;
  flex: none;
  border-radius: var(--radius-key);
  color: var(--ink-2);
  display: grid;
  place-items: center;
}
.close-key:active {
  box-shadow: var(--press);
}
.sheet-body {
  padding: 0 var(--gutter);
  overflow-y: auto;
  overscroll-behavior: contain;
  min-height: 0;
}
.sheet-footer {
  padding: var(--space-4) var(--gutter) 0;
  display: flex;
  gap: var(--space-2);
}
</style>
