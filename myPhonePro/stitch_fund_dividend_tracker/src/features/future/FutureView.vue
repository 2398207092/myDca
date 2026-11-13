<script setup lang="ts">
/**
 * 未来面：回答「接下来会怎样」。
 * 深度语法：预测分红是海平面上的唯一主读数；覆盖 / 定投（含本月预算）/ 预测与对账各一个台阶。
 * 口径：预测与已收分标签；覆盖 = 预测年分红 ÷ 年支出；现金一律墨色 + 符号。
 */
import { computed, ref } from 'vue'
import Readout from '@/shared/components/Readout.vue'
import SeamDivider from '@/shared/components/SeamDivider.vue'
import TerraceSection from '@/shared/components/TerraceSection.vue'
import DataRow from '@/shared/components/DataRow.vue'
import EmptyState from '@/shared/components/EmptyState.vue'
import ErrorState from '@/shared/components/ErrorState.vue'
import SkeletonRows from '@/shared/components/SkeletonRows.vue'
import DcaPlanRow from './components/DcaPlanRow.vue'
import { useFutureData } from './useFutureData'
import { updateDcaPlan, type DcaPlanVO } from '@/api/dca'
import { DCA_FREQUENCY_LABEL, labelOf } from '@/shared/dict'
import { fmtMoney, fmtPercent } from '@/shared/money'
import { openSheet, refreshData, toast } from '@/shared/ui'
import { errorText } from '@/shared/useAsync'

const {
  data,
  loading,
  error,
  load,
  predictedAnnual,
  monthlyPredicted,
  overallDividendRate,
  totalReceived,
  holdingCount,
  totalAnnualExpense,
  coveredExpenseCount,
  totalExpenseCount,
  coveragePercent,
  coverageGap,
  plans,
  accountNameOf,
  budget,
  budgetTotal,
  budgetPlans,
  budgetTradingDays,
  monthLabelText,
} = useFutureData()

/** 覆盖比例取整给进度条无障碍读数用 */
const coverageNow = computed(() => Math.round(coveragePercent.value))

/** 正在切换启停的计划 id：同一计划在接口回来前忽略再次切换 */
const togglingId = ref<string | null>(null)

async function togglePlan(plan: DcaPlanVO): Promise<void> {
  if (togglingId.value === plan.id) return
  const next = plan.status === 'active' ? 'paused' : 'active'
  togglingId.value = plan.id
  try {
    await updateDcaPlan(plan.id, { status: next })
    toast(next === 'active' ? '定投计划已恢复' : '定投计划已暂停')
    refreshData()
  } catch (e) {
    toast(errorText(e), 'error')
  } finally {
    togglingId.value = null
  }
}

function openPlan(plan?: DcaPlanVO): void {
  openSheet('dca', plan ? { plan } : {})
}

function openExpenseSheet(): void {
  openSheet('expense')
}
</script>

<template>
  <div class="future">
    <template v-if="loading && !data">
      <SkeletonRows :rows="5" />
    </template>
    <ErrorState v-else-if="error && !data" :message="error" @retry="load()" />
    <template v-else-if="data">
      <!-- 海平面：唯一主读数 + 口径说明 -->
      <Readout label="预测分红 · 未来 12 个月" :amount="fmtMoney(predictedAnnual)" size="lg">
        <p class="readout-note">基于当前持仓与历史分红节奏估算 · 预测，不是已到账</p>
      </Readout>

      <SeamDivider />

      <!-- 台阶 1：分红覆盖生活支出 -->
      <TerraceSection label="分红覆盖生活支出">
        <template #action>
          <button class="link-key" type="button" @click="openExpenseSheet">管理 ›</button>
        </template>
        <template v-if="totalExpenseCount > 0">
          <div class="coverage-bar-row">
            <div
              class="coverage-track"
              role="progressbar"
              aria-label="预测分红覆盖年支出的比例"
              aria-valuemin="0"
              aria-valuemax="100"
              :aria-valuenow="coverageNow"
            >
              <div class="coverage-fill" :style="{ width: `${coveragePercent}%` }" />
            </div>
            <b class="coverage-pct num">{{ fmtPercent(coveragePercent, 1) }}</b>
          </div>
          <div class="coverage-sides">
            <span class="side">
              <span class="micro">年支出</span>
              <b class="num side-value">{{ fmtMoney(totalAnnualExpense) }}</b>
            </span>
            <span v-if="coverageGap > 0" class="side">
              <span class="micro">还差</span>
              <b class="num side-value">{{ fmtMoney(coverageGap) }}</b>
              <span class="micro">/ 年</span>
            </span>
            <span v-else class="side">
              <b class="micro">预测分红已覆盖全部年支出</b>
            </span>
          </div>
          <p class="note micro">
            已覆盖 {{ coveredExpenseCount }} / {{ totalExpenseCount }} 项 · 覆盖 = 预测年分红 ÷ 年支出；
            预测会随持仓与分红节奏变化，不是已到账。
          </p>
        </template>
        <EmptyState
          v-else
          compact
          title="还没有生活支出项"
          description="添加房租、餐饮等月度支出，用预测分红衡量「覆盖生活支出」的进度。"
        >
          <button class="link-key" type="button" @click="openExpenseSheet">添加支出项 ›</button>
        </EmptyState>
      </TerraceSection>

      <!-- 台阶 2：定投计划 + 本月预算（同一件事：钱按计划往外走） -->
      <TerraceSection label="定投计划">
        <template #action>
          <button class="link-key" type="button" @click="openPlan()">新建 ›</button>
        </template>
        <template v-if="plans.length">
          <DcaPlanRow
            v-for="plan in plans"
            :key="plan.id"
            :plan="plan"
            :account-name="accountNameOf(plan.accountId)"
            @edit="openPlan(plan)"
            @toggle="togglePlan(plan)"
          />
        </template>
        <EmptyState
          v-else
          compact
          title="还没有定投计划"
          description="设定固定日期与金额，到期后执行一期定投并自动记账。"
        >
          <button class="link-key" type="button" @click="openPlan()">新建定投计划 ›</button>
        </EmptyState>

        <SeamDivider tight />

        <div class="sub-head">
          <span class="micro">
            本月定投预算 · {{ monthLabelText }}
            <template v-if="budget"> · {{ budgetTradingDays }} 个交易日</template>
          </span>
          <b v-if="budget" class="budget-total num">{{ fmtMoney(budgetTotal) }}</b>
        </div>
        <template v-if="budget">
          <DataRow
            v-for="(item, index) in budgetPlans"
            :key="`${item.holdingName}-${index}`"
            :label="item.holdingName"
            :meta="`${labelOf(DCA_FREQUENCY_LABEL, item.frequency)} · ${item.executions} 次`"
            :value="fmtMoney(item.budgetAmount)"
          />
          <EmptyState
            v-if="!budgetPlans.length"
            compact
            title="本月没有进行中的定投计划"
            description="暂停的计划不计入预算；新建或恢复计划后，这里会显示本月预计扣款总额。"
          />
          <p v-else class="note micro">预算 = 各计划每期金额 × 本月预计执行次数；含加密计划时已按汇率折算人民币。</p>
        </template>
        <p v-else class="note micro">本月预算暂时无法计算，请稍后刷新重试。</p>
      </TerraceSection>

      <!-- 台阶 3：预测与对账（预测 / 已发生分组，标签分开，绝不混排） -->
      <TerraceSection label="预测与对账">
        <p class="sub-head micro">预测</p>
        <DataRow label="本月预计分红" meta="预测年分红 ÷ 12" :value="fmtMoney(monthlyPredicted)" tall />
        <DataRow label="成本息率" meta="后端口径" :value="fmtPercent(overallDividendRate)" tall />
        <SeamDivider tight />
        <p class="sub-head micro">已发生 / 现状</p>
        <DataRow label="今年已收分红" meta="已到账，非预测" :value="fmtMoney(totalReceived)" tall />
        <DataRow label="持仓数量" meta="当前持仓" :value="`${holdingCount} 个`" tall />
        <!-- 未展示 dashboard.tenYearExpectedReturn：后端实现是「成本息率 × 10」的比率，不是金额 -->
      </TerraceSection>
    </template>
  </div>
</template>

<style scoped>
.future {
  padding-bottom: var(--space-8);
}
.readout-note {
  margin-top: var(--space-2);
  font-size: var(--text-micro);
  color: var(--ink-3);
  line-height: 1.5;
  max-width: 300px;
}
.coverage-bar-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-2);
}
.coverage-pct {
  flex: none;
  font-size: 20px;
  line-height: 1.2;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--ink);
}
.coverage-track {
  flex: 1;
  min-width: 0;
  height: 8px;
  border-radius: var(--radius-pill);
  background: var(--canvas);
  box-shadow: var(--in-well);
  overflow: hidden;
}
.coverage-fill {
  height: 100%;
  border-radius: var(--radius-pill);
  background: var(--accent);
  transition: width 200ms ease;
}
.coverage-sides {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
  margin-top: var(--space-2);
  color: var(--ink-2);
}
.side {
  display: inline-flex;
  align-items: baseline;
  gap: var(--space-1);
  min-width: 0;
}
.side-value {
  font-size: var(--text-num-sm);
  font-weight: 500;
  color: var(--ink);
  white-space: nowrap;
}
.note {
  color: var(--ink-3);
  line-height: 1.5;
  margin-top: var(--space-2);
}
.sub-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
  color: var(--ink-3);
  margin-bottom: var(--space-1);
}
.budget-total {
  font-size: var(--text-body);
  font-weight: 600;
  color: var(--ink);
  white-space: nowrap;
}
.link-key {
  min-height: 44px;
  padding: 0 4px;
  font-size: var(--text-label);
  font-weight: 500;
  color: var(--accent);
  display: inline-flex;
  align-items: center;
}
.link-key:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
  border-radius: 6px;
}
</style>
