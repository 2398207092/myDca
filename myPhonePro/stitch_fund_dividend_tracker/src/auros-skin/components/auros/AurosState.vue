<script setup lang="ts">
/**
 * AurosState —— 加载 / 空 / 错误 三态（全站共用）
 * 三态也遵守"信息只用卡片承载"：骨架 = 一张抬升卡里的呼吸条；
 * 空/错 = 一张下沉井卡里的一句话 + 一个动作。不用插图、不用 emoji。
 */
withDefaults(
  defineProps<{
    state: 'loading' | 'empty' | 'error'
    text?: string
    actionLabel?: string
    rows?: number
  }>(),
  { text: '', actionLabel: '', rows: 3 },
)
const emit = defineEmits<{ (e: 'action'): void }>()
</script>

<template>
  <div v-if="state === 'loading'" class="au-card state-card" aria-hidden="true">
    <span v-for="i in rows" :key="i" class="state-card__bar" :style="{ width: `${100 - i * 12}%` }"></span>
  </div>

  <div v-else class="au-card state-card">
    <p class="state-card__text">{{ text || (state === 'error' ? '加载失败' : '这里还是空的') }}</p>
    <button v-if="actionLabel" type="button" class="state-card__btn" @click="emit('action')">
      {{ actionLabel }}
      <svg viewBox="0 0 12 12" width="10" height="10" fill="none">
        <path d="M3 9 9 3M4.4 3H9v4.6" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </button>
  </div>
</template>

<style scoped>
.state-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 14px;
}
.state-card__bar {
  height: 14px;
  border-radius: 6px;
  background: rgba(237, 255, 254, 0.07);
  animation: au-breathe 2.4s ease-in-out infinite;
}
.state-card__text {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--au-silver);
}
.state-card__btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 34px;
  padding: 0 14px;
  border: 0;
  border-radius: 6px;
  background: rgba(0, 55, 52, 0.9);
  color: var(--au-mist);
  font-family: inherit;
  font-size: 13px;
}
</style>
