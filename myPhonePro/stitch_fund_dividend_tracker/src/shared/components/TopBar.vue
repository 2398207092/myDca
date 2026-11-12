<script setup lang="ts">
/** 顶栏：面名 + 数据日期 + 刷新 + 设置。低频入口放这里，高频操作在底坞。 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import IconKey from './IconKey.vue'
import { formatShortDate, todayStr } from '@/shared/date'
import { openSheet, refreshData, toast } from '@/shared/ui'

const route = useRoute()
const face = computed(() => String(route.meta.face ?? ''))
const today = computed(() => formatShortDate(todayStr()))

function refresh(): void {
  refreshData()
  toast('已刷新')
}
</script>

<template>
  <header class="topbar">
    <div class="tb-left">
      <b class="face">{{ face }}</b>
      <span class="date micro">{{ today }}</span>
    </div>
    <div class="tb-right">
      <IconKey icon="refresh" label="刷新数据" @click="refresh" />
      <IconKey icon="settings" label="账户与设置" @click="openSheet('settings')" />
    </div>
  </header>
</template>

<style scoped>
.topbar {
  position: sticky;
  top: 0;
  z-index: var(--z-topbar);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  height: calc(var(--topbar-h) + var(--safe-top));
  padding: var(--safe-top) var(--gutter) 0;
  background: var(--canvas);
}
.tb-left {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  min-width: 0;
}
.face {
  font-size: var(--text-heading);
  font-weight: 600;
  letter-spacing: -0.01em;
}
.date {
  color: var(--ink-3);
}
.tb-right {
  display: flex;
  gap: var(--space-1);
}
</style>
