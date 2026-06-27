<script setup lang="ts">
import type { Holding } from '@/types'

interface Props {
  holding: Holding
  onClick?: () => void
}

defineProps<Props>()
</script>

<template>
  <div
    class="bg-card-bg rounded-xl p-lg card-shadow border border-border-light/40 transition-all duration-200 active:scale-[0.98] cursor-pointer"
    :style="{ borderLeft: `3px solid ${holding.color}` }"
    @click="onClick"
  >
    <div class="flex items-center gap-2 mb-3">
      <div class="w-3 h-3 rounded-full shrink-0" :style="{ backgroundColor: holding.color }"></div>
      <div class="flex-1 min-w-0">
        <h4 class="font-display text-md text-text-primary truncate">{{ holding.name }}</h4>
        <span class="font-body text-sm text-text-tertiary">{{ holding.code }}</span>
      </div>
      <div class="text-right shrink-0 ml-2">
        <template v-if="holding.predictedDividend > 0">
          <p class="text-brand font-display text-xl leading-none whitespace-nowrap tabular-nums">
            ¥{{ holding.predictedDividend >= 10000
              ? (holding.predictedDividend / 10000).toFixed(2) + '万'
              : holding.predictedDividend.toFixed(2) }}
          </p>
          <p class="text-text-tertiary font-body text-sm mt-0.5">预测分红</p>
        </template>
        <template v-else>
          <p class="text-text-tertiary font-display text-xl leading-none">--</p>
          <p class="text-text-tertiary font-body text-sm mt-0.5">暂无分红</p>
        </template>
      </div>
    </div>

    <!-- 4×1 指标网格 -->
    <div class="bg-card-alt rounded-lg p-lg mt-3">
      <div class="grid grid-cols-4 divide-x divide-border-light/10">
        <div class="text-center first:pl-0 last:pr-0">
          <p class="font-body text-sm text-text-tertiary/60">市值</p>
          <p class="font-body text-base text-text-primary mt-0.5 tabular-nums">¥{{ Math.round(holding.marketValue).toLocaleString() }}</p>
        </div>
        <div class="text-center">
          <p class="font-body text-sm text-text-tertiary/60">成本</p>
          <p class="font-body text-base text-text-primary mt-0.5 tabular-nums">¥{{ Math.round(holding.cost).toLocaleString() }}</p>
        </div>
        <div class="text-center">
          <p class="font-body text-sm text-text-tertiary/60">份额</p>
          <p class="font-body text-base text-text-primary mt-0.5 tabular-nums">{{ Math.round(holding.shares).toLocaleString() }} 份</p>
        </div>
        <div class="text-center">
          <p class="font-body text-sm text-text-tertiary/60">股息率</p>
          <p class="font-body text-base mt-0.5" :style="{ color: holding.color }">{{ holding.dividendRate }}%</p>
        </div>
      </div>
    </div>
  </div>
</template>
