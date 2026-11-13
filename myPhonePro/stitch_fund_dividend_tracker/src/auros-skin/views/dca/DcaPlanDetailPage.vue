<script setup lang="ts">
/**
 * 定投详情 · 四层信息骨架 v3（2026-11-08）
 * 章 01 PLAN 计划：AurosStat（每期金额）+ 一组计划参数
 * 章 02 POSITION 累计：一组累计读数
 * 章 03 EXECUTIONS 执行记录：组头 全部 · N 期，默认 5 行 + AurosDisclosure 折叠其余
 * 章 04 ACTIONS 操作：纯行（执行 / 暂停·恢复 / 终止 / 编辑 / 删除）
 * 业务逻辑、金额口径、接口与上一版逐行一致，只重排信息结构。
 */
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getDcaPlan, updateDcaPlan, deleteDcaPlan, executeDcaPlan, type DcaPlanVO } from '@/api/dca'
import { listExchangeRates } from '@/api/exchangeRate'
import { listTransactions, type TransactionItem } from '@/api/transaction'
import { formatMoney, formatQty, formatPrice } from '@/auros-skin/utils/format'
import ToastNotification from '@/auros-skin/components/shared/ToastNotification.vue'
import AurosNavBar from '@/auros-skin/components/auros/AurosNavBar.vue'
import AurosState from '@/auros-skin/components/auros/AurosState.vue'
import AurosChapter from '@/auros-skin/components/auros/AurosChapter.vue'
import AurosStat from '@/auros-skin/components/auros/AurosStat.vue'
import AurosGroup from '@/auros-skin/components/auros/AurosGroup.vue'
import AurosRow from '@/auros-skin/components/auros/AurosRow.vue'
import AurosDisclosure from '@/auros-skin/components/auros/AurosDisclosure.vue'
import AurosSheet from '@/auros-skin/components/auros/AurosSheet.vue'
import AurosButton from '@/auros-skin/components/auros/AurosButton.vue'
import DcaExecuteSheet from '@/auros-skin/components/dca/DcaExecuteSheet.vue'
import DcaPlanSheet from '@/auros-skin/components/dca/DcaPlanSheet.vue'

const route = useRoute()
const router = useRouter()

// Toast 通知（写操作失败必须可见，禁止静默失败）
const toastMsg = ref<string | null>(null)
const toastType = ref<'success' | 'error' | 'info'>('error')
function showToast(msg: string, type: 'success' | 'error' | 'info' = 'error') {
  toastMsg.value = msg
  toastType.value = type
}

const pageState = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const plan = ref<DcaPlanVO | null>(null)

// 加密币定投：金额口径是 USDT，展示层换算人民币
const isCrypto = computed(() => plan.value?.tradingMarket === 'crypto')
const usdCny = ref<number | null>(null)
onMounted(async () => {
  try {
    const rates = await listExchangeRates()
    usdCny.value = rates.find((r) => r.pair === 'USD/CNY')?.rate ?? null
  } catch {
    usdCny.value = null
  }
})
const dcaTransactions = ref<TransactionItem[]>([])

// Sheet state
const showExecuteSheet = ref(false)
const showEditSheet = ref(false)
const showDeleteConfirm = ref(false)
const updating = ref(false)

const planId = computed(() => route.params.id as string)

async function loadData() {
  pageState.value = 'loading'
  try {
    const p = await getDcaPlan(planId.value)
    plan.value = p

    // Fetch transactions for this holding and filter by source === 'dca'
    const allTxs = await listTransactions(p.holdingId)
    // Filter by dca plan
    dcaTransactions.value = allTxs.filter(tx => tx.source === 'dca' && tx.dcaPlanId === p.id)
  } catch (e) {
    console.error('加载定投计划详情失败:', e)
    pageState.value = 'error'
    return
  }
  pageState.value = 'ready'
}

watch(() => route.params.id, (newId) => {
  if (newId && route.name === 'dca-plan-detail') loadData()
})

onMounted(loadData)

function goHome() {
  router.push({ name: 'home' })
}

// === Status helpers ===
const statusInfo = computed(() => {
  if (!plan.value) return { label: '', icon: '' }
  switch (plan.value.status) {
    case 'active':
      return { label: '进行中', icon: 'play_arrow' }
    case 'paused':
      return { label: '已暂停', icon: 'pause' }
    case 'ended':
      return { label: '已终止', icon: 'stop' }
    default:
      return { label: plan.value.status, icon: 'circle' }
  }
})

const frequencyLabel = computed(() => {
  if (!plan.value) return ''
  switch (plan.value.frequency) {
    case 'daily': return '每日'
    case 'weekly': return '每周'
    case 'biweekly': return '双周'
    case 'monthly': return '每月'
    default: return plan.value.frequency
  }
})

const dayLabel = computed(() => {
  if (!plan.value || plan.value.day == null) return '--'
  if (plan.value.frequency === 'weekly' || plan.value.frequency === 'biweekly') {
    const labels = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']
    return labels[plan.value.day] || `周${plan.value.day}`
  }
  return `每月${plan.value.day}日`
})

/** 读数头「频率」：把频率与扣款日合成一个读数（每日没有扣款日） */
const scheduleText = computed(() => {
  const p = plan.value
  if (!p) return ''
  if (p.frequency === 'daily') return '每日'
  if (p.frequency === 'weekly' || p.frequency === 'biweekly') return `${frequencyLabel.value} · ${dayLabel.value}`
  return `${frequencyLabel.value} · ${p.day != null ? `${p.day}日` : '--'}`
})

// === Actions ===
// 加密币平均成本需按汇率折算成人民币；汇率缺失时返回 null（显示 --），不得用未折算 USDT 值冒充人民币
const avgCost = computed<number | null>(() => {
  if (!plan.value || plan.value.totalShares === 0) return 0
  if (isCrypto.value && !usdCny.value) return null
  const invested = isCrypto.value ? plan.value.totalInvested * usdCny.value! : plan.value.totalInvested
  return invested / plan.value.totalShares
})

/** 每期金额的副行：加密币补一行人民币估算 */
const amountCaption = computed(() => {
  if (!isCrypto.value || !usdCny.value || !plan.value) return '每期金额'
  return `≈ ${formatMoney(plan.value.amount * usdCny.value)} / 期`
})

/** 累计投入读数（加密币附人民币估算副行） */
const investedText = computed(() => {
  if (!plan.value) return ''
  return isCrypto.value
    ? `${formatMoney(plan.value.totalInvested, { symbol: false })} USDT`
    : formatMoney(plan.value.totalInvested)
})
const investedSub = computed(() => {
  if (!isCrypto.value || !usdCny.value || !plan.value) return ''
  return `≈ ${formatMoney(plan.value.totalInvested * usdCny.value)}`
})

/* ---------------- 章 03：执行记录（全部，默认 5 行 + 折叠） ---------------- */
/** 执行记录：全部（dcaTransactions 按日期倒序，最近在前），不按年份窗口裁剪 */
const executions = computed(() => dcaTransactions.value)
const execRowsShown = computed(() => executions.value.slice(0, 5))
const execRowsRest = computed(() => executions.value.slice(5))

function priceText(n: number): string {
  return `${formatPrice(n)}${isCrypto.value ? ' USDT' : ' 元'}`
}

/** 操作类行：更新中不响应点击（等价旧按钮 disabled） */
function requestPauseResume() {
  if (!updating.value) handlePauseResume()
}
function requestEnd() {
  if (!updating.value) handleEnd()
}
function requestDelete() {
  if (!updating.value) showDeleteConfirm.value = true
}

function handleExecute() {
  showExecuteSheet.value = true
}

function handleExecuted() {
  showExecuteSheet.value = false
  loadData()
}

/** 编辑保存成功 → 关闭弹层并刷新（下次执行日可能已重算） */
function handleEdited() {
  showEditSheet.value = false
  loadData()
}

async function handlePauseResume() {
  if (!plan.value) return
  updating.value = true
  try {
    const newStatus = plan.value.status === 'active' ? 'paused' : 'active'
    await updateDcaPlan(plan.value.id, { status: newStatus })
    await loadData()
  } catch (e: any) {
    console.error('操作失败:', e)
    showToast(e?.message || '暂停/恢复操作失败，请重试', 'error')
  } finally {
    updating.value = false
  }
}

async function handleEnd() {
  if (!plan.value) return
  updating.value = true
  try {
    await updateDcaPlan(plan.value.id, { status: 'ended' })
    await loadData()
  } catch (e: any) {
    console.error('终止失败:', e)
    // 终止失败必须让用户知道定投仍在运行（否则会继续真金扣款）
    showToast(e?.message || '终止定投失败，定投仍在运行，请重试', 'error')
  } finally {
    updating.value = false
  }
}

async function handleDelete() {
  if (!plan.value) return
  updating.value = true
  try {
    await deleteDcaPlan(plan.value.id)
    router.back()
  } catch (e: any) {
    console.error('删除失败:', e)
    showToast(e?.message || '删除计划失败，请重试', 'error')
  } finally {
    updating.value = false
    showDeleteConfirm.value = false
  }
}
</script>

<template>
  <div class="page">
    <ToastNotification :message="toastMsg" :type="toastType" @close="toastMsg = null" />

    <AurosNavBar title="定投计划">
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
      <AurosState
        v-if="pageState !== 'ready'"
        :state="pageState === 'loading' ? 'loading' : pageState === 'error' ? 'error' : 'empty'"
        :rows="4"
        :text="pageState === 'error' ? '定投计划详情没有取到' : '这条定投计划不存在'"
        :action-label="pageState === 'error' ? '重试' : ''"
        @action="loadData"
      />

      <template v-else-if="plan">
        <!-- 章 01 PLAN 计划：主角数 + 一组计划参数 -->
        <AurosChapter
          label="PLAN"
          title="计划"
          :meta="statusInfo.label"
          :gap="false"
        >
          <AurosStat
            :value="isCrypto ? formatMoney(plan.amount, { symbol: false }) : formatMoney(plan.amount)"
            :unit="isCrypto ? 'USDT' : ''"
            :caption="amountCaption"
            pink
          />
          <div class="sec-gap">
            <AurosGroup title="计划参数" count="4 项">
              <AurosRow label="频率" :value="scheduleText" />
              <AurosRow label="下次执行" :value="plan.nextExecutionDate || '--'" />
              <AurosRow label="标的" :sub="plan.holdingName" :value="plan.holdingCode" />
              <AurosRow label="开始日期" :value="plan.startedAt" />
            </AurosGroup>
          </div>
        </AurosChapter>

        <!-- 章 02 POSITION 累计：一组累计读数 -->
        <AurosChapter label="POSITION" title="累计">
          <AurosGroup title="累计读数" count="4 项">
            <AurosRow label="累计投入" :value="investedText" :sub="investedSub" />
            <AurosRow label="累计期数" :value="`${plan.totalExecutions} 期`" />
            <AurosRow
              label="平均成本"
              :value="avgCost === null ? '--' : `${formatPrice(avgCost)} 元/${isCrypto ? '枚' : '份'}`"
              :sub="avgCost === null ? '汇率获取失败' : ''"
              :tone="avgCost === null ? 'muted' : 'default'"
            />
            <AurosRow label="累计份额" :value="`${formatQty(plan.totalShares, isCrypto ? 4 : 2)} ${isCrypto ? '枚' : '份'}`" />
          </AurosGroup>
        </AurosChapter>

        <!-- 章 03 EXECUTIONS 执行记录：组头 全部 · N 期，默认 5 行 + 折叠 -->
        <AurosChapter label="EXECUTIONS" title="执行记录">
          <AurosState v-if="!executions.length" state="empty" text="暂无执行记录" />
          <AurosGroup v-else title="全部" :count="`${executions.length} 期`">
            <AurosRow
              v-for="tx in execRowsShown"
              :key="tx.id"
              :label="tx.date"
              :sub="`@ ${priceText(tx.price)}`"
              :value="`${formatQty(tx.quantity, isCrypto ? 4 : 2)} ${isCrypto ? '枚' : '份'}`"
            />
            <AurosDisclosure v-if="execRowsRest.length" :count="execRowsRest.length">
              <AurosRow
                v-for="tx in execRowsRest"
                :key="tx.id"
                :label="tx.date"
                :sub="`@ ${priceText(tx.price)}`"
                :value="`${formatQty(tx.quantity, isCrypto ? 4 : 2)} ${isCrypto ? '枚' : '份'}`"
              />
            </AurosDisclosure>
          </AurosGroup>
        </AurosChapter>

        <!-- 章 04 ACTIONS 操作：纯行 -->
        <AurosChapter label="ACTIONS" title="操作">
          <AurosGroup title="计划管理">
            <AurosRow
              v-if="plan.status === 'active'"
              label="执行一期"
              clickable
              chevron
              @click="handleExecute"
            />
            <AurosRow
              :label="plan.status === 'active' ? '暂停' : '恢复'"
              :clickable="!updating"
              :tone="updating ? 'muted' : 'default'"
              chevron
              @click="requestPauseResume"
            />
            <AurosRow
              v-if="plan.status !== 'ended'"
              label="终止"
              :clickable="!updating"
              :tone="updating ? 'muted' : 'default'"
              chevron
              @click="requestEnd"
            />
            <AurosRow
              v-if="plan.status !== 'ended'"
              label="编辑计划"
              clickable
              chevron
              @click="showEditSheet = true"
            />
            <AurosRow
              label="删除计划"
              :clickable="!updating"
              :tone="updating ? 'muted' : 'default'"
              chevron
              @click="requestDelete"
            />
          </AurosGroup>
        </AurosChapter>
      </template>
    </main>

    <!-- 编辑计划 / 执行一期：两个弹层自渲染 AurosSheet，这里只负责挂载 -->
    <DcaPlanSheet
      v-if="showEditSheet && plan"
      :holding-id="plan.holdingId"
      :holding-name="plan.holdingName"
      :holding-code="plan.holdingCode"
      :trading-market="plan.tradingMarket"
      :plan="plan"
      @close="showEditSheet = false"
      @saved="handleEdited"
    />
    <DcaExecuteSheet
      v-if="showExecuteSheet"
      :plan-id="planId"
      :holding-name="plan?.holdingName || ''"
      :amount="plan?.amount || 0"
      :trading-market="plan?.tradingMarket || 'fund'"
      @close="showExecuteSheet = false"
      @executed="handleExecuted"
    />

    <!-- 删除确认 -->
    <AurosSheet v-model="showDeleteConfirm" title="删除计划">
      <p class="confirm__text">删除后定投计划数据将不可恢复，确定要继续吗？</p>
      <div class="confirm__acts">
        <AurosButton variant="ghost" block :disabled="updating" @click="showDeleteConfirm = false">取消</AurosButton>
        <AurosButton block :disabled="updating" @click="handleDelete">
          {{ updating ? '删除中...' : '删除' }}
        </AurosButton>
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
  padding: 8px 16px calc(env(safe-area-inset-bottom, 0px) + 44px);
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

/* ---------- 删除确认 ---------- */
.confirm__text {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--au-silver);
}
.confirm__acts {
  display: flex;
  gap: 10px;
  margin-top: 22px;
}
</style>
