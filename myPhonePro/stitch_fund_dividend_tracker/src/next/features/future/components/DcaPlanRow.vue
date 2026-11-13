<script setup lang="ts">
/**
 * 定投计划行（未来面）：名称 / 每期金额 / 状态 / 启停拨杆 / 节奏 / 下次执行 / 已投期数 / 扣款账户。
 * 状态与拨杆放在金额同一行（开关前），避免状态文字悬在开关下方；整行可点进入编辑 Sheet。
 * 状态三通道：文字（进行中/已暂停/已结束）+ 拨杆形状 + 颜色。
 */
import { computed } from 'vue'
import type { DcaPlanVO } from '@/api/dca'
import SwitchKey from '@/next/shared/components/SwitchKey.vue'
import StatusPill from '@/next/shared/components/StatusPill.vue'
import { fmtMoney, fmtNumber } from '@/next/shared/money'
import { formatMonthDay } from '@/next/shared/date'
import { scheduleText } from '../dcaText'

const props = defineProps<{
  plan: DcaPlanVO
  accountName?: string
}>()

const emit = defineEmits<{
  (e: 'edit'): void
  (e: 'toggle'): void
}>()

const isActive = computed(() => props.plan.status === 'active')
const isEnded = computed(() => props.plan.status === 'ended')

/** crypto 计划的 amount 单位是 USDT，不能套 ¥ */
const amountText = computed(() =>
  props.plan.tradingMarket === 'crypto'
    ? `${fmtNumber(props.plan.amount, 2)} USDT`
    : fmtMoney(props.plan.amount),
)

const stateText = computed(() => (isEnded.value ? '已结束' : isActive.value ? '进行中' : '已暂停'))

const nextText = computed(() => {
  if (isEnded.value) {
    return props.plan.endedAt ? `结束于 ${formatMonthDay(props.plan.endedAt)}` : '已结束'
  }
  if (!isActive.value) return '恢复后重算下次执行日'
  return props.plan.nextExecutionDate
    ? `下次 ${formatMonthDay(props.plan.nextExecutionDate)}`
    : '下次执行日待定'
})

const executionsText = computed(() => `已投 ${props.plan.totalExecutions ?? 0} 期`)
const accountText = computed(() => (props.accountName ? `扣款 ${props.accountName}` : '未关联扣款账户'))
</script>

<template>
  <div
    class="plan-row"
    role="button"
    tabindex="0"
    :aria-label="`${plan.holdingName} 定投计划，${stateText}`"
    @click="emit('edit')"
    @keydown.enter="emit('edit')"
    @keydown.space.prevent="emit('edit')"
  >
    <div class="head-line">
      <b class="name">{{ plan.holdingName || plan.holdingCode }}</b>
      <span class="amount num">{{ amountText }}</span>
      <span class="state micro">{{ stateText }}</span>
      <span v-if="!isEnded" class="switch-slot" @click.stop @keydown.stop>
        <SwitchKey
          :model-value="isActive"
          :label="`${plan.holdingName} 定投计划开关`"
          @update:model-value="emit('toggle')"
        />
      </span>
      <StatusPill v-else tone="muted">已结束</StatusPill>
    </div>
    <p class="meta micro">{{ scheduleText(plan.frequency, plan.day) }} · {{ accountText }}</p>
    <p class="meta micro">{{ nextText }} · {{ executionsText }}</p>
  </div>
</template>

<style scoped>
.plan-row {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-height: 76px;
  padding: 8px 0;
  border-bottom: 1px solid var(--hairline);
  cursor: pointer;
  border-radius: 6px;
}
.plan-row:last-child {
  border-bottom: 0;
}
.plan-row:active {
  background: rgba(31, 35, 41, 0.03);
}
.plan-row:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.head-line {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
  min-height: 28px;
}
.name {
  flex: 1;
  min-width: 0;
  font-size: var(--text-body-sm);
  font-weight: 600;
  color: var(--ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.amount {
  flex: none;
  font-size: var(--text-num-sm);
  font-weight: 500;
  color: var(--ink);
  white-space: nowrap;
}
.state {
  flex: none;
  color: var(--ink-3);
  white-space: nowrap;
}
.switch-slot {
  flex: none;
  display: inline-flex;
  align-items: center;
}
.meta {
  color: var(--ink-2);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
