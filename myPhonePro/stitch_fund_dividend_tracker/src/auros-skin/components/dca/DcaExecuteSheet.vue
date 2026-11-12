<script setup lang="ts">
/**
 * 执行定投（Auros 弹层 · 2026-11-08）
 * 外壳：AurosSheet；确认 / 执行中 / 成功 / 失败四态与原逻辑、文案一致。
 * 对外 props/emits 与旧版完全一致（父级仍用 v-if 控制挂载）。
 */
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { executeDcaPlan, type DcaExecutionResultVO } from '@/api/dca'
import { formatMoney, formatQty, formatPrice } from '@/auros-skin/utils/format'
import AurosSheet from '@/auros-skin/components/auros/AurosSheet.vue'
import AurosRow from '@/auros-skin/components/auros/AurosRow.vue'
import AurosButton from '@/auros-skin/components/auros/AurosButton.vue'

interface Props {
  planId: string
  holdingName: string
  amount: number
  /** 交易市场：crypto 计划的金额单位是 USDT，不是人民币 */
  tradingMarket?: string
}

const props = defineProps<Props>()

/** 加密币定投：金额/单价单位是 USDT，份额单位是枚 */
const isCrypto = (props.tradingMarket ?? 'fund') === 'crypto'

const emit = defineEmits<{
  close: []
  executed: []
}>()

const state = ref<'confirm' | 'loading' | 'success' | 'error'>('confirm')
const result = ref<DcaExecutionResultVO | null>(null)
const errorMsg = ref('')

/* ---------------- 抽屉开合（仅 UI 态） ---------------- */
const open = ref(false)
let closeTimer: ReturnType<typeof setTimeout> | null = null
onMounted(() => {
  open.value = true
})
onBeforeUnmount(() => {
  if (closeTimer) clearTimeout(closeTimer)
})

/** 等抽屉退出动效（约 320ms）后再通知父级卸载，避免弹层瞬间消失 */
function requestClose() {
  open.value = false
  if (closeTimer) clearTimeout(closeTimer)
  closeTimer = setTimeout(() => emit('close'), 320)
}

const sheetTitle = computed(() => {
  if (state.value === 'success') return '执行成功'
  if (state.value === 'error') return '执行失败'
  return '执行定投'
})

async function handleExecute() {
  // 防重复提交：执行中忽略后续触发（后端另有当日防重兜底）
  if (state.value === 'loading') return
  state.value = 'loading'
  errorMsg.value = ''
  try {
    const res = await executeDcaPlan(props.planId)
    result.value = res
    state.value = 'success'
    emit('executed')
  } catch (e: any) {
    errorMsg.value = e.message || '执行失败，请重试'
    state.value = 'error'
  }
}

function handleRetry() {
  state.value = 'confirm'
  handleExecute()
}

/** 金额读数：加密币走 USDT（不带 ¥），其余走人民币 */
function moneyText(n: number): string {
  return isCrypto ? `${formatMoney(n, { symbol: false })} USDT` : formatMoney(n)
}

/** 净值 / 单价：四位小数（单位在行尾说明） */
function priceText(n: number): string {
  return `${formatPrice(n)}${isCrypto ? ' USDT' : ' 元'}`
}

/** 份额 / 数量：基金两位、加密币四位（与持仓页口径一致） */
function qtyText(n: number): string {
  return `${formatQty(n, isCrypto ? 4 : 2)} ${isCrypto ? '枚' : '份'}`
}
</script>

<template>
  <AurosSheet v-model="open" :title="sheetTitle" @close="requestClose">
    <p class="sub">{{ holdingName }}</p>

    <!-- 确认 -->
    <template v-if="state === 'confirm'">
      <div class="readout">
        <AurosRow label="金额" :value="moneyText(amount)" />
      </div>
      <div class="acts">
        <AurosButton variant="ghost" block @click="requestClose">取消</AurosButton>
        <AurosButton block @click="handleExecute">确认执行</AurosButton>
      </div>
    </template>

    <!-- 执行中 -->
    <template v-else-if="state === 'loading'">
      <div class="busy">
        <i class="busy__ring" aria-hidden="true"></i>
        <p class="busy__text">正在执行定投...</p>
      </div>
    </template>

    <!-- 成功 -->
    <template v-else-if="state === 'success' && result">
      <p class="hero au-num">{{ moneyText(result.amount) }}</p>
      <div class="readout">
        <AurosRow label="成交份额" :value="qtyText(result.quantity)" />
        <AurosRow :label="result.navDate ? '成交净值' : '成交单价'" :value="priceText(result.navPrice)" />
        <AurosRow label="净值日期" :value="result.navDate || '按执行时 USDT 价格'" />
      </div>
      <!-- 扣款说明：让用户明确知道扣没扣、为什么没扣 -->
      <p v-if="result.deductionInfo" class="hint">{{ result.deductionInfo }}</p>
      <AurosButton class="submit" block @click="requestClose">完成</AurosButton>
    </template>

    <!-- 失败 -->
    <template v-else-if="state === 'error'">
      <p class="err">{{ errorMsg }}</p>
      <div class="acts">
        <AurosButton variant="ghost" block @click="requestClose">关闭</AurosButton>
        <AurosButton block @click="handleRetry">重试</AurosButton>
      </div>
    </template>
  </AurosSheet>
</template>

<style scoped>
.sub {
  margin: 0 0 18px;
  font-size: 13px;
  letter-spacing: 0.02em;
  color: var(--au-silver);
}

/* 金额读数 */
.hero {
  margin: 0 0 18px;
  font-size: 40px;
  font-weight: 500;
  line-height: 1;
  letter-spacing: -0.03em;
  color: var(--au-mist);
}

.readout {
  margin-top: 2px;
}

.hint {
  margin: 14px 0 0;
  font-size: 10px;
  line-height: 1.6;
  letter-spacing: 0.03em;
  color: rgba(187, 199, 198, 0.64);
}

.err {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: rgba(253, 233, 255, 0.92);
}

.acts {
  display: flex;
  gap: 10px;
  margin-top: 22px;
}
.submit {
  margin-top: 22px;
}

/* 执行中：一圈呼吸的磷光环 */
.busy {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18px;
  padding: 26px 0 20px;
}
.busy__ring {
  width: 26px;
  height: 26px;
  border: 1.2px solid rgba(237, 255, 254, 0.16);
  border-top-color: var(--au-lit);
  border-radius: 50%;
  box-shadow: 0 0 14px rgba(203, 255, 252, 0.32);
  animation: au-spin 900ms linear infinite;
}
.busy__text {
  margin: 0;
  font-size: 13px;
  letter-spacing: 0.04em;
  color: var(--au-silver);
}
@keyframes au-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .busy__ring {
    animation: none;
  }
}
</style>
