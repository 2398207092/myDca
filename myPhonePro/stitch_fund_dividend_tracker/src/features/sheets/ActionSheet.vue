<script setup lang="ts">
/**
 * 操作面板：底部 ＋ 打开，九个键（3 列 × 3 行）。
 * 这里只做分发：资金动作进入各自的 Sheet/确认层，不在面板里记账；
 * 最后一排是「搭账户」：新建持仓 / 新建账户 / 加一笔现金。
 */
import SheetFrame from '@/shared/components/SheetFrame.vue'
import BtnKey from '@/shared/components/BtnKey.vue'
import { closeSheet, openSheet, type SheetName } from '@/shared/ui'

interface ActionItem {
  key: string
  label: string
  sheet: SheetName
  props: Record<string, unknown>
}

const actions: ActionItem[] = [
  { key: 'buy', label: '买入', sheet: 'transaction', props: { mode: 'create', type: 'buy' } },
  { key: 'sell', label: '卖出', sheet: 'transaction', props: { mode: 'create', type: 'sell' } },
  { key: 'dividend', label: '记分红', sheet: 'dividend', props: {} },
  { key: 'snapshot', label: '生成快照', sheet: 'snapshot', props: {} },
  { key: 'dca', label: '新建定投', sheet: 'dca', props: {} },
  { key: 'expense', label: '添加支出', sheet: 'expense', props: {} },
  { key: 'holding', label: '新建持仓', sheet: 'holding-create', props: {} },
  { key: 'account', label: '新建账户', sheet: 'account-create', props: {} },
  { key: 'cash', label: '加一笔现金', sheet: 'cash-create', props: {} },
]

function pick(item: ActionItem): void {
  openSheet(item.sheet, { ...item.props })
}
</script>

<template>
  <SheetFrame
    title="操作"
    subtitle="资金动作会先进入确认层；最后一排用来搭账户与持仓"
    @close="closeSheet"
  >
    <div class="grid">
      <button v-for="item in actions" :key="item.key" class="key" type="button" @click="pick(item)">
        {{ item.label }}
      </button>
    </div>

    <p class="note micro">
      快照会写明「当前总资产」口径；交易与分红可在「过去」面查看、编辑或标记到账。
    </p>

    <template #footer>
      <div class="cancel-wrap">
        <BtnKey variant="ghost" block @click="closeSheet">取消</BtnKey>
      </div>
    </template>
  </SheetFrame>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  padding-top: var(--space-2);
}
.key {
  min-height: 64px;
  padding: 0 4px;
  border-radius: var(--radius-key);
  border: 1px solid var(--edge);
  background: var(--canvas);
  box-shadow: var(--el-key);
  font-size: var(--text-label);
  font-weight: 600;
  color: var(--ink);
  transition: box-shadow 90ms ease, transform 90ms ease;
}
.key:active {
  box-shadow: var(--press);
  transform: translateY(1px);
}
.key:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.note {
  margin-top: var(--space-3);
  line-height: 1.6;
}
.cancel-wrap {
  width: 100%;
}
.cancel-wrap :deep(.btn) {
  color: var(--ink-2);
}
</style>
