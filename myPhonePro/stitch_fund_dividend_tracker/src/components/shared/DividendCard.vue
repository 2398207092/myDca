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
    class="bg-surface-container-lowest rounded-xl p-md shadow-md transition-all active:scale-[0.98] cursor-pointer"
    :style="{ borderTop: `2px solid ${holding.color}` }"
    @click="onClick"
  >
    <div class="flex items-center gap-2 mb-3">
      <span class="w-3 h-3 rounded-full shrink-0" :style="{ backgroundColor: holding.color }"></span>
      <div class="flex-1 min-w-0">
        <h4 class="font-label-bold text-label-bold text-on-surface truncate">{{ holding.name }}</h4>
        <span class="font-caption text-caption text-on-surface-variant">{{ holding.code }}</span>
      </div>
      <div class="text-right shrink-0 ml-2">
        <template v-if="holding.predictedDividend > 0">
          <p class="text-primary font-headline-md text-headline-md leading-none whitespace-nowrap">
            ¥{{ holding.predictedDividend >= 10000
              ? (holding.predictedDividend / 10000).toFixed(2) + '万'
              : holding.predictedDividend.toFixed(2) }}
          </p>
          <p class="text-on-surface-variant font-caption text-caption mt-0.5">预测分红</p>
        </template>
        <template v-else>
          <p class="text-on-surface-variant font-headline-md text-headline-md leading-none">--</p>
          <p class="text-on-surface-variant font-caption text-caption mt-0.5">暂无分红</p>
        </template>
      </div>
    </div>

    <!-- 4×1 指标网格 -->
    <div class="bg-surface-container rounded-lg p-2.5 mt-3">
      <div class="grid grid-cols-4">
        <div class="text-center border-r border-outline-variant/20">
          <p class="font-caption text-caption text-on-surface-variant/60">市值</p>
          <p class="font-label-bold text-label-bold text-on-surface mt-0.5">¥{{ Math.round(holding.marketValue).toLocaleString() }}</p>
        </div>
        <div class="text-center border-r border-outline-variant/20">
          <p class="font-caption text-caption text-on-surface-variant/60">成本</p>
          <p class="font-label-bold text-label-bold text-on-surface mt-0.5">¥{{ Math.round(holding.cost).toLocaleString() }}</p>
        </div>
        <div class="text-center border-r border-outline-variant/20">
          <p class="font-caption text-caption text-on-surface-variant/60">份额</p>
          <p class="font-label-bold text-label-bold text-on-surface mt-0.5">{{ Math.round(holding.shares).toLocaleString() }} 份</p>
        </div>
        <div class="text-center">
          <p class="font-caption text-caption text-on-surface-variant/60">股息率</p>
          <p class="font-label-bold text-label-bold mt-0.5" :style="{ color: holding.color }">{{ holding.dividendRate }}%</p>
        </div>
      </div>
    </div>
  </div>
</template>
