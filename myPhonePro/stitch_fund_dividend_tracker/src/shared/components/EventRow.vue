<script setup lang="ts">
/**
 * 统一事件行（现在面今日事件 / 过去面时间轴共用）。
 * 钱的口径：现金流入/流出用墨色 + 符号，只有盈亏才用红绿。
 */
import type { MoneyEvent } from '@/shared/stream'
import StatusPill from './StatusPill.vue'

withDefaults(
  defineProps<{
    event: MoneyEvent
    tap?: boolean
  }>(),
  { tap: false },
)
defineEmits<{ (e: 'click'): void }>()

function toneOf(status: string): 'accent' | 'muted' | 'warn' | 'danger' {
  if (status === '已到账') return 'accent'
  if (status === '待确认') return 'warn'
  if (status === '已取消') return 'danger'
  return 'muted'
}
</script>

<template>
  <div
    class="event"
    :class="{ 'event--tap': tap }"
    :role="tap ? 'button' : undefined"
    :tabindex="tap ? 0 : undefined"
    @click="tap && $emit('click')"
    @keydown.enter="tap && $emit('click')"
    @keydown.space.prevent="tap && $emit('click')"
  >
    <span class="mark" :class="`mark--${event.direction}`" aria-hidden="true" />
    <div class="ev-main">
      <p class="ev-title">
        {{ event.title }}
        <em v-if="event.holdingName">{{ event.holdingName }}</em>
      </p>
      <p v-if="event.meta" class="ev-meta">{{ event.meta }}</p>
    </div>
    <div class="ev-right">
      <b class="ev-amount num">{{ event.amountText || '—' }}</b>
      <StatusPill v-if="event.statusText" :tone="toneOf(event.statusText)">{{ event.statusText }}</StatusPill>
    </div>
    <span v-if="tap" class="ev-chev" aria-hidden="true">›</span>
  </div>
</template>

<style scoped>
.event {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  gap: var(--space-3);
  align-items: start;
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--hairline);
  width: 100%;
  text-align: left;
  background: transparent;
}
.event:last-child {
  border-bottom: 0;
}
/* 台阶内的可点事件行：抬回一级（+1），按下回弹；chevron 强调色 */
.event.event--tap {
  cursor: pointer;
  grid-template-columns: 10px minmax(0, 1fr) auto 10px;
  margin: var(--space-2) 0;
  padding: var(--space-3);
  border: 1px solid var(--edge);
  border-bottom: 1px solid var(--edge);
  border-radius: var(--radius-key);
  background: var(--canvas);
  box-shadow: var(--el-key);
  transition: box-shadow 90ms ease, transform 90ms ease;
}
.event.event--tap:active {
  box-shadow: var(--press);
  transform: translateY(1px);
}
.ev-chev {
  color: var(--accent);
  font-size: 20px;
  line-height: 1;
  align-self: center;
}
.mark {
  margin-top: 6px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--ink-3);
  justify-self: center;
}
.mark--in {
  background: var(--ink);
}
.mark--out {
  background: transparent;
  border: 2px solid var(--ink-3);
}
.ev-main {
  min-width: 0;
}
.ev-title {
  font-size: var(--text-body-sm);
  font-weight: 500;
  line-height: 1.4;
  color: var(--ink);
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: var(--space-1);
}
.ev-title em {
  font-style: normal;
  font-weight: 400;
  font-size: var(--text-micro);
  color: var(--ink-3);
}
.ev-meta {
  margin-top: 3px;
  font-size: var(--text-micro);
  line-height: 1.4;
  color: var(--ink-3);
}
.ev-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--space-1);
}
.ev-amount {
  font-size: var(--text-body-sm);
  font-weight: 500;
  white-space: nowrap;
}
</style>
