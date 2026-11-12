<script setup lang="ts">
/**
 * AurosStat —— 一章的主角数（L4）
 * 一章只允许一个；粉色仍只用于统计强调，不是装饰。
 */
withDefaults(
  defineProps<{
    value: string
    unit?: string
    caption?: string
    pink?: boolean
    size?: 'xl' | 'lg'
    delta?: string
    deltaTone?: 'up' | 'down' | 'flat'
  }>(),
  { unit: '', caption: '', pink: false, size: 'xl', delta: '', deltaTone: 'flat' },
)
</script>

<template>
  <div class="stat">
    <p class="stat__line">
      <span class="stat__num au-num" :class="[`is-${size}`, { 'is-pink': pink }]">{{ value }}</span>
      <span v-if="unit" class="stat__unit">{{ unit }}</span>
      <span v-if="delta" class="stat__delta" :class="`is-${deltaTone}`">
        <svg v-if="deltaTone !== 'flat'" viewBox="0 0 10 10" width="9" height="9" fill="none">
          <path
            :d="deltaTone === 'up' ? 'M5 8V2M2.4 4.6 5 2l2.6 2.6' : 'M5 2v6M2.4 5.4 5 8l2.6-2.6'"
            stroke="currentColor"
            stroke-width="1.2"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
        {{ delta }}
      </span>
    </p>
    <p v-if="caption" class="stat__cap">{{ caption }}</p>
  </div>
</template>

<style scoped>
.stat {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.stat__line {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 0;
  animation: au-settle var(--au-dur-4) var(--au-ease) both;
}
.stat__num {
  font-weight: 500;
  line-height: 1;
  letter-spacing: -0.04em;
  color: var(--au-mist);
}
.stat__num.is-xl {
  font-size: 56px;
}
.stat__num.is-lg {
  font-size: 40px;
  letter-spacing: -0.03em;
}
.stat__num.is-pink {
  color: var(--au-pink);
}
/* 320/360 窄屏：主数从 56 收到 40，避免金额横向溢出（≤390 不降级） */
@media (max-width: 360px) {
  .stat__num.is-xl {
    font-size: 40px;
  }
}
.stat__unit {
  font-size: 13px;
  letter-spacing: 0.06em;
  color: var(--au-silver);
}
.stat__delta {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  color: rgba(187, 199, 198, 0.72);
}
.stat__delta.is-up {
  color: var(--au-lit);
}
.stat__cap {
  margin: 0;
  font-size: 10px;
  letter-spacing: 0.06em;
  color: rgba(187, 199, 198, 0.72);
}
</style>
