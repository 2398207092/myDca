<script setup lang="ts">
/**
 * 各类资产卡片（经典皮肤）
 * - 1 个标的：整行可点，直接进持仓详情（信息一行看完，不需要折叠）
 * - 多个标的：默认收起，只露分类名 / 总额 / 标的数；点开看明细
 * - 多张卡片互不影响，可同时展开（不是手风琴）
 */
import { computed, ref } from 'vue'
import type { HoldingItem } from '@/api/holding'
import { formatMoney } from '@/utils/format'

const props = defineProps<{
  /** 分类名（多标的时做标题） */
  title: string
  /** 分类色点 */
  color: string
  /** 色点的描边色（黄金黄点在白底上需要一圈描边） */
  dotShadow?: string
  /** 单标的下方的分类说明，如「美股 · 纳斯达克100联接」 */
  subtitle?: string
  /** 分类合计市值 */
  total: number
  holdings: HoldingItem[]
  /** 未分类卡片右侧的分类图标 */
  showCategoryIcon?: boolean
}>()

const emit = defineEmits<{
  (e: 'detail', id: string): void
  (e: 'sheet', holding: HoldingItem): void
}>()

const expanded = ref(false)
const single = computed(() => props.holdings.length === 1)
const onlyHolding = computed(() => props.holdings[0])
const dotStyle = computed(() => ({
  backgroundColor: props.color,
  ...(props.dotShadow ? { boxShadow: `inset 0 0 0 1px ${props.dotShadow}` } : {}),
}))
</script>

<template>
  <div class="bg-card-bg rounded-lg card-shadow border border-border-light/40">
    <!-- 单标的：整行可点，直接进详情 -->
    <div
      v-if="single && onlyHolding"
      class="flex items-center gap-3 cursor-pointer hover:bg-card-alt rounded-lg p-lg transition-colors"
      @click="emit('detail', onlyHolding.id)"
    >
      <span class="w-3 h-3 rounded-full shrink-0" :style="dotStyle"></span>
      <div class="flex-1 min-w-0">
        <p class="font-body text-sm font-medium text-text-primary truncate">{{ onlyHolding.name }}</p>
        <p class="font-body text-xs text-text-tertiary truncate">{{ subtitle || title }}</p>
      </div>
      <div class="flex items-center gap-2 shrink-0">
        <p class="font-body text-sm font-medium text-text-primary tabular-nums">{{ formatMoney(onlyHolding.marketValue) }}</p>
        <span v-if="showCategoryIcon" class="material-symbols-outlined text-text-tertiary text-md">category</span>
      </div>
    </div>

    <!-- 多标的：可折叠卡片 -->
    <template v-else>
      <button
        type="button"
        class="w-full p-lg flex items-start gap-3 text-left"
        :aria-expanded="expanded"
        @click="expanded = !expanded"
      >
        <span class="w-3 h-3 rounded-full mt-[5px] shrink-0" :style="dotStyle"></span>
        <div class="flex-1 min-w-0">
          <div class="flex items-center justify-between gap-2">
            <h3 class="font-body text-sm font-medium text-text-primary truncate">{{ title }}</h3>
            <span class="font-body text-sm font-medium text-text-primary shrink-0 tabular-nums">{{ formatMoney(total) }}</span>
          </div>
          <div class="flex items-center justify-between gap-2 mt-0.5">
            <span class="font-body text-xs text-text-tertiary">{{ holdings.length }} 个标的</span>
            <span class="flex items-center gap-0.5 font-body text-xs text-text-tertiary shrink-0">
              {{ expanded ? '收起' : '展开' }}
              <span class="material-symbols-outlined text-md transition-transform duration-200" :class="{ 'rotate-180': expanded }">expand_more</span>
            </span>
          </div>
        </div>
      </button>

      <div v-if="expanded" class="px-lg pb-lg">
        <div class="border-t border-border-light/40 mb-md"></div>
        <div class="space-y-0.5">
          <div
            v-for="h in holdings"
            :key="h.id"
            class="flex items-center justify-between py-2.5 px-3 rounded-lg hover:bg-card-alt cursor-pointer transition-colors"
            @click="emit('sheet', h)"
          >
            <span class="font-body text-xs text-text-primary truncate flex-1 min-w-0">{{ h.name }}</span>
            <div class="flex items-center gap-2 shrink-0 ml-2">
              <span class="font-body text-xs text-text-tertiary tabular-nums">{{ formatMoney(h.marketValue) }}</span>
              <span v-if="showCategoryIcon" class="material-symbols-outlined text-text-tertiary text-md">category</span>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
