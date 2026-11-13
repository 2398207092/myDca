<script setup lang="ts">
/**
 * Sheet 宿主：全局只有一个 Sheet（ui.sheet），按名字懒加载对应组件。
 * 动作成功后各 Sheet 自己 closeSheet() 并 refreshData()。
 */
import { computed, defineAsyncComponent } from 'vue'
import { ui, closeSheet, type SheetName } from '@/next/shared/ui'

const ActionSheet = defineAsyncComponent(() => import('./ActionSheet.vue'))
const TransactionSheet = defineAsyncComponent(() => import('./TransactionSheet.vue'))
const DividendSheet = defineAsyncComponent(() => import('./DividendSheet.vue'))
const SnapshotSheet = defineAsyncComponent(() => import('./SnapshotSheet.vue'))
const DcaSheet = defineAsyncComponent(() => import('./DcaSheet.vue'))
const ExpenseSheet = defineAsyncComponent(() => import('./ExpenseSheet.vue'))
const HoldingSheet = defineAsyncComponent(() => import('./HoldingSheet.vue'))
const SettingsSheet = defineAsyncComponent(() => import('./SettingsSheet.vue'))
const CompositionSheet = defineAsyncComponent(() => import('@/next/features/now/CompositionSheet.vue'))
const EventSheet = defineAsyncComponent(() => import('@/next/features/past/EventSheet.vue'))
const HoldingCreateSheet = defineAsyncComponent(() => import('./HoldingCreateSheet.vue'))
const AccountCreateSheet = defineAsyncComponent(() => import('./AccountCreateSheet.vue'))
const CashCreateSheet = defineAsyncComponent(() => import('./CashCreateSheet.vue'))

const registry: Record<SheetName, unknown> = {
  action: ActionSheet,
  transaction: TransactionSheet,
  dividend: DividendSheet,
  snapshot: SnapshotSheet,
  dca: DcaSheet,
  expense: ExpenseSheet,
  holding: HoldingSheet,
  settings: SettingsSheet,
  composition: CompositionSheet,
  event: EventSheet,
  'holding-create': HoldingCreateSheet,
  'account-create': AccountCreateSheet,
  'cash-create': CashCreateSheet,
}

const current = computed(() => (ui.sheet ? registry[ui.sheet.name] : null))

function onClose(): void {
  closeSheet()
}
</script>

<template>
  <component
    :is="current"
    v-if="ui.sheet && current"
    v-bind="ui.sheet.props"
    @close="onClose"
  />
</template>
