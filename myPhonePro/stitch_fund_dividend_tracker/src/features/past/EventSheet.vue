<script setup lang="ts">
/**
 * 事件详情 Sheet：交易（编辑 / 删除）与分红（标记到账 / 转为复投 / 取消）。
 *
 * 钱的口径：
 * - 交易：买入 = 数量×净值 + 手续费（现金流出）；卖出 = 数量×净值 − 手续费（现金流入）；
 *   复投/送股只改份额，不动现金。
 * - 分红：只有「派息 payout + 已到账 distributed + 未转复投」计入现金；待确认、已复投、已取消不计现金。
 * - 后端约束（EventService）：markDistributed 只适用于 pending payout；convertToReinvest 只适用于已到账。
 *   因此「待确认 → 转为复投」按「先入账、再转复投」两步执行，任一步失败都明确提示，不留含糊状态。
 *
 * 所有资金动作必须二次确认，确认文案重复金额与份额（TEAM-CONTRACT §6）。
 */
import { computed, ref } from 'vue'
import SheetFrame from '@/shared/components/SheetFrame.vue'
import DataRow from '@/shared/components/DataRow.vue'
import StatusPill from '@/shared/components/StatusPill.vue'
import BtnKey from '@/shared/components/BtnKey.vue'
import { closeSheet, openSheet, refreshData, toast } from '@/shared/ui'
import { fmtMoney, fmtNav, fmtShares, fmtSignedMoney, toNumber } from '@/shared/money'
import { formatShortDate } from '@/shared/date'
import { EVENT_STATUS_LABEL, EVENT_TYPE_LABEL, TX_TYPE_LABEL, labelOf } from '@/shared/dict'
import { cancelEvent, convertEventToReinvest, markDistributed } from '@/api/event'
import { deleteTransaction } from '@/api/transaction'
import type { MoneyEvent } from '@/shared/stream'

const props = defineProps<{ event: MoneyEvent }>()

const tx = computed(() => props.event.tx ?? null)
const ev = computed(() => props.event.ev ?? null)

const title = computed(() => props.event.title || '事件详情')
const subtitle = computed(() => {
  const name = props.event.holdingName
  const date = formatShortDate(props.event.date)
  return name ? `${name} · ${date}` : date
})

// ---------- 交易 ----------
const isDca = computed(() => {
  const t = tx.value
  if (!t) return false
  return Boolean(t.dcaPlanId) || t.source === 'dca' || t.source === 'scheduler'
})

const txTypeText = computed(() => {
  const t = tx.value
  if (!t) return ''
  const base = labelOf(TX_TYPE_LABEL, t.type) || t.type
  return isDca.value ? `${base} · 定投执行` : base
})

const txSourceText = computed(() => {
  const t = tx.value
  if (!t) return '—'
  if (isDca.value) return '定投计划执行'
  switch (t.source) {
    case 'manual':
      return '手动录入'
    case 'dividend_reinvest':
      return '分红复投'
    default:
      return t.source || '—'
  }
})

const gross = computed(() => Math.abs(toNumber(tx.value?.quantity) * toNumber(tx.value?.price)))
const fee = computed(() => toNumber(tx.value?.fee))

/** 买入现金流出（含手续费），与后端 createTransaction 的 total 同口径 */
const cashOut = computed(() => {
  const t = tx.value
  if (!t) return 0
  const value = gross.value + fee.value
  return value > 0 ? value : Math.abs(toNumber(t.total))
})

/** 卖出现金流入（已扣手续费），与后端 deleteTransaction 的 netProceeds 同口径 */
const cashIn = computed(() => {
  const t = tx.value
  if (!t) return 0
  const base = gross.value > 0 ? gross.value : Math.abs(toNumber(t.total))
  return Math.max(0, base - fee.value)
})

const txCashText = computed(() => {
  const t = tx.value
  if (!t) return '—'
  if (t.type === 'buy') return fmtSignedMoney(-cashOut.value)
  if (t.type === 'sell') return fmtSignedMoney(cashIn.value)
  return '不动现金'
})

const txHeroLabel = computed(() => {
  const t = tx.value
  if (!t) return ''
  if (t.type === 'buy') return '现金流出 · 含手续费'
  if (t.type === 'sell') return '现金流入 · 已扣手续费'
  return '份额变动'
})

const txHeroAmount = computed(() => {
  const t = tx.value
  if (!t) return '—'
  if (t.type === 'buy') return fmtSignedMoney(-cashOut.value)
  if (t.type === 'sell') return fmtSignedMoney(cashIn.value)
  return fmtMoney(toNumber(t.total))
})

// ---------- 分红 ----------
const evAmount = computed(() => Math.abs(toNumber(ev.value?.amount)))
const evStatusText = computed(() => labelOf(EVENT_STATUS_LABEL, ev.value?.status))
const evTypeText = computed(() => labelOf(EVENT_TYPE_LABEL, ev.value?.type) || '分红')
const isPayout = computed(() => ev.value?.type === 'payout')
const isConverted = computed(() => Boolean(ev.value?.converted))
const isCashIn = computed(
  () => isPayout.value && ev.value?.status === 'distributed' && !isConverted.value && evAmount.value > 0,
)
const statusTone = computed<'accent' | 'muted' | 'warn' | 'danger'>(() => {
  const status = ev.value?.status
  if (status === 'distributed') return 'accent'
  if (status === 'pending') return 'warn'
  if (status === 'cancelled') return 'danger'
  return 'muted'
})

const evHeroLabel = computed(() => {
  if (!isPayout.value) return '日程金额 · 不产生现金'
  const status = ev.value?.status
  if (status === 'cancelled') return '已取消 · 不计入现金'
  if (isConverted.value) return '分红金额 · 已复投为份额'
  if (status === 'distributed') return '现金到账'
  return '待确认金额 · 未入账'
})

/** 金额文案跟随 shared/stream.ts 的统一口径：待确认带「预计」、复投/取消不带 + 号 */
const evHeroAmount = computed(() => {
  if (isCashIn.value) return fmtSignedMoney(evAmount.value)
  if (ev.value?.status === 'pending' && evAmount.value > 0) {
    return `预计 ${fmtSignedMoney(evAmount.value)}`
  }
  return fmtMoney(evAmount.value)
})

const evReinvestText = computed(() => {
  if (isConverted.value) return '已转为份额'
  const status = ev.value?.status
  if (status === 'distributed') return evAmount.value > 0 ? '未转复投' : '金额为 0，无需复投'
  if (status === 'pending') return '到账后可转复投'
  return '—'
})

const evNote = computed(() => {
  if (!isPayout.value) {
    return '这是分红日程事件（登记 / 除权 / 公告），不产生现金；只有「派息」事件可以标记到账。'
  }
  if (isConverted.value) return '该分红已按净值转为持仓份额，不再计入现金。'
  if (ev.value?.status === 'cancelled') return '该分红已取消，不计入现金与累计已收分红。'
  if (ev.value?.status === 'distributed') {
    return '已到账现金已计入现金账户与累计已收分红；转复投会扣回等额现金并增加份额。'
  }
  return '待确认：预计金额尚未计入现金与累计已收分红，确认到账后才会入账。'
})

// ---------- 动作（严格按后端状态机） ----------
/**
 * 后端约束（EventService）：
 * - markDistributed：只接受 pending 的 payout（重复入账 / 已取消 / 非 payout 都会被拒）；
 * - convertToReinvest：只接受 distributed 且未 converted（pending 会报「只有已到账的分红才能转为复投」）；
 * - cancelEvent：已到账（distributed）会被拒（「已到账的事件不可取消」）。
 * UI 只在这些前提下启用按钮，其余显式禁用并说明原因，不靠报错教用户。
 */
const showMark = computed(() => isPayout.value && ev.value?.status === 'pending')
const showConvert = computed(
  () =>
    isPayout.value &&
    evAmount.value > 0 &&
    !isConverted.value &&
    (ev.value?.status === 'pending' || ev.value?.status === 'distributed'),
)
const canConvert = computed(
  () => isPayout.value && ev.value?.status === 'distributed' && !isConverted.value && evAmount.value > 0,
)
const showCancel = computed(
  () =>
    isPayout.value &&
    !isConverted.value &&
    (ev.value?.status === 'pending' || ev.value?.status === 'distributed'),
)
const canCancel = computed(() => isPayout.value && ev.value?.status === 'pending')
const hasActions = computed(
  () =>
    tx.value !== null ||
    showMark.value ||
    showConvert.value ||
    showCancel.value,
)

const actionHint = computed(() => {
  if (!isPayout.value || isConverted.value) return ''
  if (ev.value?.status === 'pending') return '「转为复投」需先把这笔分红标记为已到账。'
  if (ev.value?.status === 'distributed') return '已到账的分红不可取消。'
  return ''
})

type ConfirmKind = 'deleteTx' | 'distribute' | 'convert' | 'cancel'

interface ConfirmConfig {
  title: string
  body: string
  ok: string
  danger: boolean
}

const confirming = ref<ConfirmKind | null>(null)
const busy = ref<ConfirmKind | null>(null)

const confirmConfig = computed<ConfirmConfig | null>(() => {
  const kind = confirming.value
  if (!kind) return null

  if (kind === 'deleteTx') {
    const t = tx.value
    if (!t) return null
    return {
      title: '删除这笔交易？',
      body: `将删除「${txTypeText.value}」${props.event.holdingName || '未知持仓'} · ${fmtShares(t.quantity)} · 净值 ${fmtNav(t.price)} · ${txHeroAmount.value}（${formatShortDate(t.date)}）。删除后持仓份额、成本与现金会按剩余流水重算，且不可恢复。`,
      ok: '确认删除',
      danger: true,
    }
  }

  const e = ev.value
  if (!e) return null
  const who = props.event.holdingName || '该持仓'

  if (kind === 'distribute') {
    return {
      title: '确认分红到账？',
      body: `确认「${who}」这笔 ${fmtMoney(evAmount.value)} 分红已到账？确认后计入现金与累计已收分红（若该持仓为复投模式，会自动按最新净值转为份额），不可撤销。`,
      ok: '确认到账',
      danger: false,
    }
  }

  if (kind === 'convert') {
    return {
      title: '转为复投？',
      body: `将「${who}」已到账的 ${fmtMoney(evAmount.value)} 分红按最新净值转为持仓份额：现金扣回 ${fmtMoney(evAmount.value)}，份额增加。`,
      ok: '确认转复投',
      danger: false,
    }
  }

  return {
    title: '取消这笔分红？',
    body: `取消「${who}」这笔 ${fmtMoney(evAmount.value)} 分红？取消后不会再入账，也不可恢复。`,
    ok: '确认取消',
    danger: true,
  }
})

function failText(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}

function editTx(): void {
  const t = tx.value
  if (!t) return
  openSheet('transaction', { mode: 'edit', transaction: t })
}

async function runConfirm(): Promise<void> {
  const kind = confirming.value
  if (!kind || busy.value) return
  busy.value = kind
  try {
    if (kind === 'deleteTx') await deleteTx()
    else if (kind === 'distribute') await distributeDividend()
    else if (kind === 'convert') await convertDividend()
    else await cancelDividend()
  } catch (e) {
    toast(failText(e), 'error')
  } finally {
    busy.value = null
  }
}

async function deleteTx(): Promise<void> {
  const t = tx.value
  if (!t) return
  await deleteTransaction(t.id)
  toast('已删除交易')
  refreshData()
  closeSheet()
}

async function distributeDividend(): Promise<void> {
  const e = ev.value
  if (!e) return
  const result = await markDistributed(e.id)
  toast(result.converted ? '已标记到账，并按复投模式转为份额' : '已标记到账')
  refreshData()
  closeSheet()
}

async function convertDividend(): Promise<void> {
  const e = ev.value
  if (!e) return
  // 只有已到账的分红能转复投（后端约束），pending 的按钮在 UI 上是禁用的
  if (e.status !== 'distributed') {
    toast('只有已到账的分红才能转为复投', 'error')
    return
  }
  await convertEventToReinvest(e.id)
  toast('已转为复投')
  refreshData()
  closeSheet()
}

async function cancelDividend(): Promise<void> {
  const e = ev.value
  if (!e) return
  await cancelEvent(e.id)
  toast('已取消这笔分红')
  refreshData()
  closeSheet()
}
</script>

<template>
  <SheetFrame :title="title" :subtitle="subtitle" @close="closeSheet">
    <template v-if="tx">
      <div class="hero">
        <span class="micro">{{ txHeroLabel }}</span>
        <div class="hero-amount num">{{ txHeroAmount }}</div>
      </div>

      <div class="rows">
        <DataRow label="类型" :value="txTypeText" />
        <DataRow label="日期" :value="formatShortDate(tx.date)" />
        <DataRow label="份额" :value="fmtShares(tx.quantity)" />
        <DataRow label="净值" :value="fmtNav(tx.price)" />
        <DataRow label="金额（数量×净值）" :value="fmtMoney(gross)" />
        <DataRow label="手续费" :value="fmtMoney(fee)" />
        <DataRow label="现金变动" :value="txCashText" />
        <DataRow label="来源" :value="txSourceText" />
      </div>

      <p class="note micro">
        删除交易会按剩余流水重算份额、成本与现金；编辑金额以后端计算为准。
      </p>
    </template>

    <template v-else-if="ev">
      <div class="hero">
        <span class="micro">{{ evHeroLabel }}</span>
        <div class="hero-amount num">{{ evHeroAmount }}</div>
      </div>

      <div class="rows">
        <DataRow label="状态">
          <template #value>
            <StatusPill :tone="statusTone">{{ evStatusText }}</StatusPill>
          </template>
        </DataRow>
        <DataRow label="事件类型" :value="evTypeText" />
        <DataRow label="日期" :value="formatShortDate(ev.date)" />
        <DataRow v-if="isPayout" label="复投" :value="evReinvestText" />
      </div>

      <p v-if="ev.description" class="desc">{{ ev.description }}</p>
      <p class="note micro">{{ evNote }}</p>
    </template>

    <div
      v-if="confirmConfig"
      class="confirm"
      role="alertdialog"
      aria-modal="true"
      :aria-label="confirmConfig.title"
    >
      <p class="confirm-title">{{ confirmConfig.title }}</p>
      <p class="confirm-body">{{ confirmConfig.body }}</p>
      <div class="confirm-actions">
        <BtnKey variant="secondary" block :disabled="busy !== null" @click="confirming = null">
          再想想
        </BtnKey>
        <BtnKey
          :variant="confirmConfig.danger ? 'danger' : 'primary'"
          block
          :disabled="busy !== null"
          @click="runConfirm"
        >
          {{ busy === confirming ? '处理中…' : confirmConfig.ok }}
        </BtnKey>
      </div>
    </div>

    <div v-else-if="hasActions" class="actions">
      <template v-if="tx">
        <div class="actions-pair">
          <BtnKey variant="secondary" block @click="editTx">编辑</BtnKey>
          <BtnKey variant="danger" block @click="confirming = 'deleteTx'">删除</BtnKey>
        </div>
      </template>
      <template v-else>
        <BtnKey v-if="showMark" variant="primary" block @click="confirming = 'distribute'">
          标记到账
        </BtnKey>
        <div v-if="showConvert || showCancel" class="actions-pair">
          <BtnKey
            v-if="showConvert"
            variant="secondary"
            block
            :disabled="!canConvert"
            @click="confirming = 'convert'"
          >
            转为复投
          </BtnKey>
          <BtnKey
            v-if="showCancel"
            variant="danger"
            block
            :disabled="!canCancel"
            @click="confirming = 'cancel'"
          >
            取消
          </BtnKey>
        </div>
        <p v-if="actionHint" class="action-hint micro">{{ actionHint }}</p>
      </template>
    </div>
  </SheetFrame>
</template>

<style scoped>
.hero {
  padding: var(--space-2) 0 var(--space-4);
}
.hero-amount {
  font-size: var(--text-readout-lg);
  line-height: 1.1;
  font-weight: 600;
  letter-spacing: -0.01em;
  margin-top: var(--space-1);
}
.rows {
  margin-top: var(--space-1);
}
.desc {
  margin-top: var(--space-3);
  font-size: var(--text-label);
  color: var(--ink-2);
  line-height: 1.6;
}
.note {
  margin-top: var(--space-3);
  line-height: 1.6;
}
.actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-top: var(--space-5);
  padding-bottom: var(--space-2);
}
.actions-pair {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2);
}
.action-hint {
  line-height: 1.55;
}
.confirm {
  margin-top: var(--space-5);
  padding: var(--space-4);
  border-radius: var(--radius-key);
  border: 1px solid var(--edge);
  background: var(--surface-1);
}
.confirm-title {
  font-size: var(--text-body);
  font-weight: 600;
  color: var(--ink);
}
.confirm-body {
  margin-top: var(--space-2);
  font-size: var(--text-label);
  color: var(--ink-2);
  line-height: 1.6;
}
.confirm-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2);
  margin-top: var(--space-4);
}
</style>
