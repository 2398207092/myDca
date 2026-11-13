<script setup lang="ts">
/** 底部坞：三档物理滑钮（过去/现在/未来）+ 动作键。拇指区固定，不随内容滚动。 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import IconBase from './IconBase.vue'
import { openSheet } from '@/next/shared/ui'

const route = useRoute()
const router = useRouter()

const faces = [
  { name: 'past', label: '过去' },
  { name: 'now', label: '现在' },
  { name: 'future', label: '未来' },
] as const

const active = computed(() => String(route.name ?? ''))

function go(name: string): void {
  if (route.name !== name) void router.push({ name })
}
</script>

<template>
  <nav class="dock" aria-label="主视图切换">
    <div class="dock-inner">
      <div class="rocker" role="tablist" aria-label="时态视图">
        <button
          v-for="f in faces"
          :key="f.name"
          class="rock"
          :class="{ 'rock--active': active === f.name }"
          type="button"
          role="tab"
          :aria-selected="active === f.name"
          @click="go(f.name)"
        >
          {{ f.label }}
        </button>
      </div>
      <button class="action-key" type="button" aria-label="记一笔" @click="openSheet('action')">
        <IconBase name="plus" :size="24" :stroke="2.4" />
      </button>
    </div>
  </nav>
</template>

<style scoped>
.dock {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: 0;
  width: 100%;
  max-width: 520px;
  z-index: var(--z-dock);
  padding: var(--space-2) var(--gutter) calc(var(--space-2) + var(--safe-bottom));
  background: linear-gradient(to bottom, rgba(232, 234, 238, 0), var(--canvas) 18%);
}
.dock-inner {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}
.rocker {
  flex: 1;
  height: 48px;
  padding: 3px;
  display: flex;
  gap: 2px;
  border-radius: var(--radius-key);
  box-shadow: var(--in-slot);
}
.rock {
  flex: 1;
  height: 40px;
  border-radius: var(--radius-well);
  font-size: var(--text-label);
  font-weight: 500;
  color: var(--ink-2);
  display: grid;
  place-items: center;
  transition: box-shadow 90ms ease, color 120ms ease;
}
.rock:active {
  box-shadow: var(--press);
}
.rock--active {
  background: var(--canvas);
  box-shadow: var(--el-key);
  color: var(--accent);
  font-weight: 600;
}
.action-key {
  width: 56px;
  height: 56px;
  flex: none;
  border-radius: 50%;
  background: var(--accent);
  color: var(--on-accent);
  display: grid;
  place-items: center;
  box-shadow: var(--el-key);
  transition: box-shadow 90ms ease, background 120ms ease;
}
.action-key:active {
  background: var(--accent-strong);
  box-shadow: var(--press);
}
.action-key:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 3px;
}
</style>
