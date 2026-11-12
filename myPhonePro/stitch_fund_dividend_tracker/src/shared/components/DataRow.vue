<script setup lang="ts">
/** 数据行：左标签 / 右数值；可选次级说明、色点、可点。整行可点时为 64 高。 */
defineProps<{
  label: string
  value?: string
  meta?: string
  tone?: 'up' | 'down' | 'flat'
  dotColor?: string
  tap?: boolean
  tall?: boolean
}>()

defineEmits<{ (e: 'click'): void }>()
</script>

<template>
  <div
    class="row"
    :class="{ 'row--tap': tap, 'row--tall': tall }"
    :role="tap ? 'button' : undefined"
    :tabindex="tap ? 0 : undefined"
    @click="tap && $emit('click')"
    @keydown.enter="tap && $emit('click')"
    @keydown.space.prevent="tap && $emit('click')"
  >
    <span class="label">
      <i v-if="dotColor" class="dot" :style="{ background: dotColor }" />
      <span class="label-text">
        {{ label }}
        <span v-if="meta" class="row-meta">{{ meta }}</span>
      </span>
    </span>
    <span class="value num" :class="tone">{{ value }}<slot name="value" /></span>
    <span v-if="tap" class="chev" aria-hidden="true">›</span>
  </div>
</template>

<style scoped>
.row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-height: 56px;
  border-bottom: 1px solid var(--hairline);
  width: 100%;
  text-align: left;
  background: transparent;
}
.row:last-child {
  border-bottom: 0;
}
.row--tall {
  min-height: 64px;
}
.row--tap {
  cursor: pointer;
  border-radius: 6px;
}
.row--tap:active {
  background: rgba(31, 35, 41, 0.03);
}
.label {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
  flex: 1;
}
.label-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: var(--text-label);
  color: var(--ink-2);
  min-width: 0;
}
.row-meta {
  font-size: var(--text-micro);
  color: var(--ink-3);
  line-height: 1.35;
}
.value {
  font-size: var(--text-body);
  font-weight: 500;
  white-space: nowrap;
  color: var(--ink);
}
.value.up {
  color: var(--up);
}
.value.down {
  color: var(--down);
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: none;
}
.chev {
  color: var(--ink-3);
  font-size: 18px;
  line-height: 1;
  margin-left: calc(var(--space-1) * -1);
}
</style>
