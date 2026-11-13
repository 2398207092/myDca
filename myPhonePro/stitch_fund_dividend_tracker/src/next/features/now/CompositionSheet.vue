<script setup lang="ts">
/** 构成 Sheet：分类 → 持仓明细，金额与百分比并列（不是只靠颜色）。 */
import SheetFrame from '@/next/shared/components/SheetFrame.vue'
import DataRow from '@/next/shared/components/DataRow.vue'
import { fmtMoney, fmtPercent } from '@/next/shared/money'
import { closeSheet, openSheet } from '@/next/shared/ui'
import type { CompositionGroup } from '@/next/shared/composition'

defineProps<{
  groups: CompositionGroup[]
  totalValue: number
}>()

/** 只有一项且名字与分组同名时（如「现金 / 现金」），子行不再重复显示 */
function childrenOf(group: CompositionGroup): CompositionGroup['items'] {
  if (group.items.length === 1 && group.items[0].label === group.label) return []
  return group.items
}

/** 分类 → 持仓明细行是详情入口：打开单支持仓（份额/成本/IRR/再投开关） */
function openHolding(holdingId: string): void {
  openSheet('holding', { holdingId })
}
</script>

<template>
  <SheetFrame title="资产构成" :subtitle="`合计 ${fmtMoney(totalValue)}`" @close="closeSheet">
    <div v-for="group in groups" :key="group.key" class="group">
      <DataRow
        :label="group.label"
        :dot-color="group.color"
        :meta="fmtPercent(group.percentage, 1)"
        :value="fmtMoney(group.value)"
      />
      <DataRow
        v-for="item in childrenOf(group)"
        :key="item.id"
        :label="item.label"
        class="child"
        :value="fmtMoney(item.value)"
        :tap="Boolean(item.holdingId)"
        @click="item.holdingId && openHolding(item.holdingId)"
      />
    </div>
    <p class="note micro">百分比按总资产口径计算；「其他持仓」是尚未归类的持仓市值。</p>
  </SheetFrame>
</template>

<style scoped>
.group {
  padding-bottom: var(--space-2);
}
.group :deep(.row) {
  padding-left: 0;
}
.child {
  padding-left: var(--space-5);
}
.child :deep(.label-text) {
  font-size: var(--text-micro);
  color: var(--ink-3);
}
.child :deep(.value) {
  font-size: var(--text-label);
  color: var(--ink-2);
}
.note {
  margin-top: var(--space-3);
  line-height: 1.6;
}
</style>
