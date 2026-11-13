<script setup lang="ts">
/**
 * 交易明细 · 四层信息骨架 v3（2026-11-08）
 * 章 01 SUMMARY 汇总：AurosStat（区间净买入）+ 一组区间读数（笔数 / 最近一笔）
 * 章 02 FLOW 流水：按月份分组（≤3 组），每组 ≤5 行，超出用 AurosDisclosure
 * 业务逻辑、金额口径、接口与上一版逐行一致，只重排信息结构。
 */
import { ref, computed, watch, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listTransactions, updateTransaction, deleteTransaction, type TransactionItem, type UpdateTransactionReq } from '@/api/transaction'
import { getHolding } from '@/api/holding'
import ToastNotification from '@/auros-skin/components/shared/ToastNotification.vue'
import { formatMoney } from '@/auros-skin/utils/format'
import AurosNavBar from '@/auros-skin/components/auros/AurosNavBar.vue'
import AurosState from '@/auros-skin/components/auros/AurosState.vue'
import AurosChapter from '@/auros-skin/components/auros/AurosChapter.vue'
import AurosStat from '@/auros-skin/components/auros/AurosStat.vue'
import AurosGroup from '@/auros-skin/components/auros/AurosGroup.vue'
import AurosRow from '@/auros-skin/components/auros/AurosRow.vue'
import AurosDisclosure from '@/auros-skin/components/auros/AurosDisclosure.vue'
import AurosSheet from '@/auros-skin/components/auros/AurosSheet.vue'
import AurosField from '@/auros-skin/components/auros/AurosField.vue'
import AurosButton from '@/auros-skin/components/auros/AurosButton.vue'

const route = useRoute()
const router = useRouter()

// Toast 通知（写操作失败必须可见，禁止静默失败）
const toastMsg = ref<string | null>(null)
const toastType = ref<'success' | 'error' | 'info'>('error')
function showToast(msg: string, type: 'success' | 'error' | 'info' = 'error') {
  toastMsg.value = msg
  toastType.value = type
}

const loading = ref(true)
const transactions = ref<TransactionItem[]>([])
// 当前持仓是否为加密币（quoteCurrency=USDT）：影响份额单位（枚）与单价单位（USDT）
const isCrypto = ref(false)

async function loadTransactions(holdingId: string) {
  if (!holdingId) return
  loading.value = true
  try {
    transactions.value = await listTransactions(holdingId)
  } catch (e) {
    console.error('加载交易明细失败', e)
  } finally {
    loading.value = false
  }
  // 持仓币种信息（失败不阻断列表展示，回退为基金口径）
  getHolding(holdingId).then((h) => { isCrypto.value = h.quoteCurrency === 'USDT' }).catch(() => { isCrypto.value = false })
}

// 最稳健的模式：监听路由参数变化加载数据（与 HoldingDetailPage 一致）
// 覆盖：全新挂载（immediate）、路由参数切换、KeepAlive 重新进入
watch(() => route.params.id as string | undefined, (newId) => {
  if (newId) loadTransactions(newId)
}, { immediate: true })

// KeepAlive 缓存重新激活时的兜底
onActivated(() => {
  const id = route.params.id as string || route.query.holdingId as string
  if (id) loadTransactions(id)
})

// === 交易操作弹窗 ===
const selectedTx = ref<TransactionItem | null>(null)
const showActionSheet = ref(false)
const showEditSheet = ref(false)
const showDeleteConfirm = ref(false)
const editSaving = ref(false)
const error = ref('')

// 编辑表单
const editType = ref<'buy' | 'sell' | 'bonus_share' | 'reinvest'>('buy')
const editDate = ref('')
const editQuantity = ref(0)
const editPrice = ref(0)
const editFee = ref(0)

function showTxAction(tx: TransactionItem) {
  selectedTx.value = tx
  showActionSheet.value = true
}

function closeActionSheet() {
  showActionSheet.value = false
  selectedTx.value = null
}

function openEdit() {
  const tx = selectedTx.value
  if (!tx) return
  showActionSheet.value = false
  // 回填表单
  editType.value = tx.type as any
  editDate.value = tx.date
  editQuantity.value = tx.quantity
  editPrice.value = tx.price
  editFee.value = tx.fee
  showEditSheet.value = true
}

function openDelete() {
  showActionSheet.value = false
  showDeleteConfirm.value = true
}

async function doEdit() {
  const tx = selectedTx.value
  if (!tx) return
  if (editQuantity.value <= 0) {
    error.value = '请填写完整信息'
    return
  }
  editSaving.value = true
  error.value = ''
  try {
    const req: UpdateTransactionReq = {
      type: editType.value,
      date: editDate.value,
      quantity: editQuantity.value,
      price: editPrice.value,
      fee: editFee.value,
    }
    await updateTransaction(tx.id, req)
    showEditSheet.value = false
    // 重新加载列表
    transactions.value = await listTransactions(route.params.id as string)
  } catch (e: any) {
    error.value = e.message || '保存失败'
  } finally {
    editSaving.value = false
  }
}

async function doDeleteTx() {
  const tx = selectedTx.value
  if (!tx) return
  try {
    await deleteTransaction(tx.id)
    showDeleteConfirm.value = false
    selectedTx.value = null
    transactions.value = await listTransactions(route.params.id as string)
  } catch (e: any) {
    console.error('删除失败:', e)
    showToast(e?.message || '删除交易失败，请重试', 'error')
    showDeleteConfirm.value = false
  }
}

function txLabel(type: string): { text: string; color: string } {
  switch (type) {
    case 'buy': return { text: '买入', color: 'text-brand' }
    case 'sell': return { text: '卖出', color: 'text-error' }
    case 'bonus_share': return { text: '送股', color: 'text-brand' }
    case 'reinvest': return { text: '复投', color: 'text-brand' }
    case 'dividend': return { text: '分红', color: 'text-brand' }
    default: return { text: type, color: 'text-text-primary' }
  }
}

/* ---------------- 展示层派生（纯 UI 聚合，不改口径） ---------------- */
/** 类型符号：卖出为负，买入类（买入/送股/复投）与分红为正 */
function signOf(type: string): string {
  return type === 'sell' ? '−' : '+'
}

/** 金额文本：人民币 ¥+千分位两位；加密币 USDT（不带 ¥，单位在行尾） */
function moneyText(n: number): string {
  return isCrypto.value ? `${formatMoney(n, { symbol: false })} USDT` : formatMoney(n)
}

/** 带方向金额（组头净额）：+¥1,234.56 / -¥1,234.56 */
function netText(n: number): string {
  return isCrypto.value
    ? `${formatMoney(n, { sign: true, symbol: false })} USDT`
    : formatMoney(n, { sign: true })
}

/** 净买入：买入 / 送股 为正、卖出为负（复投是分红转份额、分红是收益入账，都不计入净买入） */
function netBuyOf(list: TransactionItem[]): number {
  return list.reduce((s, tx) => {
    if (tx.type === 'buy' || tx.type === 'bonus_share') return s + (tx.total || 0)
    if (tx.type === 'sell') return s - (tx.total || 0)
    return s
  }, 0)
}

const netBuy = computed(() => netBuyOf(transactions.value))
const netBuyText = computed(() =>
  isCrypto.value ? formatMoney(netBuy.value, { symbol: false }) : formatMoney(netBuy.value)
)

/** 章 01 区间读数：笔数与最近一笔 */
const summary = computed(() => {
  const dates = transactions.value.map((t) => t.date).filter(Boolean).sort()
  return {
    count: transactions.value.length,
    latest: dates.length ? dates[dates.length - 1] : '',
  }
})

/* ---------------- 章 02：按月份分组（≤3 组，每组 ≤5 行） ---------------- */
interface MonthGroup {
  key: string
  title: string
  count: string
  total: string
  rows: TransactionItem[]
  rest: TransactionItem[]
}

const monthKeys = computed(() => {
  const set = new Set(transactions.value.map((t) => (t.date || '').slice(0, 7)).filter(Boolean))
  return Array.from(set).sort((a, b) => b.localeCompare(a))
})

function rowsOfMonth(key: string): TransactionItem[] {
  return transactions.value.filter((t) => (t.date || '').slice(0, 7) === key)
}

function makeMonthGroup(key: string): MonthGroup {
  const list = rowsOfMonth(key)
  return {
    key,
    title: `${key.slice(0, 4)} 年 ${Number(key.slice(5, 7))} 月`,
    count: `${list.length} 笔`,
    total: `净 ${netText(netBuyOf(list))}`,
    rows: list.slice(0, 5),
    rest: list.slice(5),
  }
}

const visibleMonthGroups = computed(() => monthKeys.value.slice(0, 3).map(makeMonthGroup))
/** 第 4 个月及更早：超出组预算，统一折叠 */
const olderRows = computed(() => monthKeys.value.slice(3).flatMap(rowsOfMonth))

/* ---------------- 编辑表单：AurosField 只发字符串，这里还原为数字 ---------------- */
function setEditQuantity(v: string) {
  editQuantity.value = v === '' ? 0 : Number(v)
}
function setEditPrice(v: string) {
  editPrice.value = v === '' ? 0 : Number(v)
}
function setEditFee(v: string) {
  editFee.value = v === '' ? 0 : Number(v)
}

const goHome = () => router.push({ name: 'home' })

function goAddTrade() {
  router.push({ name: 'trade-add', query: { holdingId: route.params.id as string } })
}
</script>

<template>
  <div class="page">
    <ToastNotification :message="toastMsg" :type="toastType" @close="toastMsg = null" />

    <AurosNavBar title="交易明细">
      <template #right>
        <button type="button" class="nav-icon" aria-label="返回首页" @click="goHome">
          <svg viewBox="0 0 16 16" width="14" height="14" fill="none">
            <path
              d="M2.8 7.4 8 2.9l5.2 4.5M4.1 6.8v6.3h7.8V6.8"
              stroke="currentColor"
              stroke-width="1.2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </button>
      </template>
    </AurosNavBar>

    <main class="body">
      <AurosState v-if="loading" state="loading" :rows="5" />

      <AurosState
        v-else-if="transactions.length === 0"
        state="empty"
        text="还没有交易记录"
        action-label="添加第一笔交易"
        @action="goAddTrade"
      />

      <template v-else>
        <!-- 章 01 SUMMARY 汇总：主角数 + 一组区间读数 -->
        <AurosChapter label="SUMMARY" title="汇总" :gap="false">
          <AurosStat
            :value="netBuyText"
            :unit="isCrypto ? 'USDT' : ''"
            caption="区间净买入"
            pink
          />
          <div class="sec-gap">
            <AurosGroup title="区间读数" count="2 项">
              <AurosRow label="笔数" :value="`${summary.count} 笔`" />
              <AurosRow label="最近一笔" :value="summary.latest || '--'" />
            </AurosGroup>
          </div>
        </AurosChapter>

        <!-- 章 02 FLOW 流水：按月份分组（≤3 组，每组 ≤5 行 + 折叠） -->
        <AurosChapter label="FLOW" title="流水" meta="按月份">
          <AurosGroup
            v-for="g in visibleMonthGroups"
            :key="g.key"
            :title="g.title"
            :count="g.count"
            :total="g.total"
          >
            <AurosRow
              v-for="tx in g.rows"
              :key="tx.id"
              :label="`${signOf(tx.type)} ${txLabel(tx.type).text}`"
              :sub="tx.date"
              :value="moneyText(tx.total)"
              clickable
              chevron
              @click="showTxAction(tx)"
            />
            <AurosDisclosure v-if="g.rest.length" :count="g.rest.length">
              <AurosRow
                v-for="tx in g.rest"
                :key="tx.id"
                :label="`${signOf(tx.type)} ${txLabel(tx.type).text}`"
                :sub="tx.date"
                :value="moneyText(tx.total)"
                clickable
                chevron
                @click="showTxAction(tx)"
              />
            </AurosDisclosure>
          </AurosGroup>

          <!-- 第 4 个月及更早：组预算外，统一折叠 -->
          <AurosDisclosure v-if="olderRows.length" :count="olderRows.length">
            <AurosRow
              v-for="tx in olderRows"
              :key="tx.id"
              :label="`${signOf(tx.type)} ${txLabel(tx.type).text}`"
              :sub="tx.date"
              :value="moneyText(tx.total)"
              clickable
              chevron
              @click="showTxAction(tx)"
            />
          </AurosDisclosure>
        </AurosChapter>
      </template>
    </main>

    <!-- 底部主按钮（本页唯一极光渐变） -->
    <footer class="bar">
      <AurosButton block @click="goAddTrade">添加交易</AurosButton>
    </footer>

    <!-- 交易操作 -->
    <AurosSheet v-model="showActionSheet" title="这笔交易" @close="closeActionSheet">
      <p class="sheet__meta">
        {{ selectedTx?.date }}<i>·</i>{{ selectedTx ? txLabel(selectedTx.type).text : '' }}
      </p>
      <div class="sheet__acts">
        <AurosButton variant="ghost" block @click="openEdit">编辑</AurosButton>
        <AurosButton variant="ghost" block @click="openDelete">删除</AurosButton>
        <AurosButton variant="text" block @click="closeActionSheet">取消</AurosButton>
      </div>
    </AurosSheet>

    <!-- 编辑交易 -->
    <AurosSheet v-model="showEditSheet" title="编辑交易">
      <div class="form">
        <p class="cap">交易类型</p>
        <div class="seg">
          <button
            v-for="t in (['buy', 'sell', 'bonus_share', 'reinvest'] as const)"
            :key="t"
            type="button"
            class="seg__item"
            :class="{ 'is-active': editType === t }"
            @click="editType = t"
          >
            {{ { buy: '买入', sell: '卖出', bonus_share: '送股', reinvest: '复投' }[t] }}
          </button>
        </div>

        <AurosField
          class="fld"
          v-model="editDate"
          label="交易日期"
          type="date"
        />

        <div class="grid2">
          <AurosField
            :model-value="editQuantity"
            label="数量"
            :suffix="isCrypto ? '枚' : '份'"
            type="number"
            inputmode="decimal"
            mono
            @update:model-value="setEditQuantity"
          />
          <AurosField
            :model-value="editPrice"
            label="单价"
            :suffix="isCrypto ? 'USDT' : 'CNY'"
            type="number"
            inputmode="decimal"
            mono
            @update:model-value="setEditPrice"
          />
        </div>

        <AurosField
          class="fld"
          :model-value="editFee"
          label="手续费"
          :suffix="isCrypto ? 'USDT' : 'CNY'"
          type="number"
          inputmode="decimal"
          mono
          @update:model-value="setEditFee"
        />

        <p v-if="error" class="err">{{ error }}</p>

        <div class="sheet__acts">
          <AurosButton variant="ghost" block @click="showEditSheet = false">取消</AurosButton>
          <AurosButton block :disabled="editSaving" @click="doEdit">
            {{ editSaving ? '保存中...' : '保存' }}
          </AurosButton>
        </div>
      </div>
    </AurosSheet>

    <!-- 删除确认 -->
    <AurosSheet v-model="showDeleteConfirm" title="确认删除">
      <p class="confirm__text">删除后数据将不可恢复，确定要继续吗？</p>
      <div class="sheet__acts">
        <AurosButton variant="ghost" block @click="showDeleteConfirm = false">取消</AurosButton>
        <AurosButton block @click="doDeleteTx">删除</AurosButton>
      </div>
    </AurosSheet>
  </div>
</template>

<style scoped>
.page {
  min-height: 100svh;
  display: flex;
  flex-direction: column;
}
.body {
  flex: 1;
  padding: 8px 16px calc(env(safe-area-inset-bottom, 0px) + 96px);
}
.sec-gap {
  margin-top: 18px;
}
.nav-icon {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: rgba(237, 255, 254, 0.06);
  color: var(--au-silver);
}

/* ---------- 底部主按钮 ---------- */
.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 12px 16px calc(env(safe-area-inset-bottom, 0px) + 16px);
  background: linear-gradient(180deg, rgba(1, 38, 36, 0) 0%, rgba(1, 29, 28, 0.92) 38%);
}

/* ---------- 弹层内容 ---------- */
.sheet__meta {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin: 0 0 20px;
  font-size: 13px;
  letter-spacing: 0.02em;
  color: var(--au-silver);
}
.sheet__meta i {
  font-style: normal;
  color: rgba(187, 199, 198, 0.4);
}
.sheet__acts {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 22px;
}
.form .sheet__acts {
  margin-top: 24px;
}
.cap {
  margin: 0 0 8px;
  font-size: 10px;
  letter-spacing: 0.16em;
  color: var(--au-silver);
}
.seg {
  display: flex;
  gap: 4px;
  padding: 3px;
  margin-bottom: 18px;
  border-radius: 8px;
  background: rgba(0, 55, 52, 0.55);
}
.seg__item {
  flex: 1;
  height: 34px;
  border: 0;
  border-radius: 6px;
  background: none;
  font-family: inherit;
  font-size: 13px;
  letter-spacing: 0.04em;
  color: rgba(187, 199, 198, 0.75);
  transition:
    background 200ms var(--au-ease),
    color 200ms var(--au-ease);
}
.seg__item.is-active {
  background: rgba(203, 255, 252, 0.14);
  color: var(--au-mist);
}
.fld {
  display: block;
  margin-bottom: 16px;
}
.form {
  color-scheme: dark;
}
.grid2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;
}
.grid2 > * {
  min-width: 0;
}
.err {
  margin: 0 0 4px;
  font-size: 12px;
  line-height: 1.6;
  color: rgba(253, 233, 255, 0.92);
}
.confirm__text {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--au-silver);
}
</style>
