<script setup lang="ts">
/**
 * 快照确认：打开时读 /asset-overview，二次确认后写 /asset-overview/snapshot。
 * 口径：快照记录的是资产分类汇总的当前总资产（含现金 / 黄金 / 加密资产）；
 * 同一天重复生成会先删后建（后端幂等），不会重复累加。
 */
import { computed, onMounted, ref } from 'vue'
import { getAssetOverview, takeSnapshot, type AssetOverview } from '@/api/assetOverview'
import { getAccounts } from '@/api/account'
import SheetFrame from '@/shared/components/SheetFrame.vue'
import Readout from '@/shared/components/Readout.vue'
import DataRow from '@/shared/components/DataRow.vue'
import BtnKey from '@/shared/components/BtnKey.vue'
import SectionLabel from '@/shared/components/SectionLabel.vue'
import SkeletonRows from '@/shared/components/SkeletonRows.vue'
import ErrorState from '@/shared/components/ErrorState.vue'
import EmptyState from '@/shared/components/EmptyState.vue'
import { fmtMoney, fmtPercent, toNumber } from '@/shared/money'
import { categoryColor } from '@/shared/composition'
import { formatShortDate, todayStr } from '@/shared/date'
import { closeSheet, refreshData, toast } from '@/shared/ui'

type Step = 'summary' | 'confirm'

const overview = ref<AssetOverview | null>(null)
const accountsTotal = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)
const step = ref<Step>('summary')
const submitting = ref(false)
const submitError = ref('')

const today = todayStr()
const todayLabel = computed(() => formatShortDate(today))
const totalValue = computed(() => toNumber(overview.value?.totalValue))
const totalText = computed(() => (overview.value ? fmtMoney(totalValue.value) : '—'))
const canSnapshot = computed(() => Boolean(overview.value) && totalValue.value > 0)
/** 账户合计（含未归类持仓）与快照分类口径的差额 */
const gap = computed(() => Math.max(0, accountsTotal.value - totalValue.value))
const hasGap = computed(() => gap.value > 0.01)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const [overviewData, accounts] = await Promise.all([getAssetOverview(), getAccounts()])
    overview.value = overviewData
    accountsTotal.value = accounts.reduce((sum, a) => sum + toNumber(a.totalValueCny), 0)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '读取资产概览失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})

function goConfirm(): void {
  if (!canSnapshot.value) return
  step.value = 'confirm'
}

async function confirmSnapshot(): Promise<void> {
  if (submitting.value || !overview.value) return
  submitting.value = true
  submitError.value = ''
  try {
    await takeSnapshot()
    toast(`已生成今日快照：${totalText.value}`)
    refreshData()
    closeSheet()
  } catch (e) {
    submitError.value = e instanceof Error ? e.message : '生成快照失败'
    toast(submitError.value, 'error')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <SheetFrame
    title="生成资产快照"
    :subtitle="`${todayLabel} · 记录当前总资产（含现金 / 黄金 / 加密资产）`"
    @close="closeSheet"
  >
    <SkeletonRows v-if="loading && !overview" :rows="4" />
    <ErrorState v-else-if="error && !overview" :message="error" @retry="load()" />

    <template v-else-if="overview">
      <!-- 第一步：看当前口径 -->
      <template v-if="step === 'summary'">
        <Readout label="当前总资产" :amount="totalText" size="lg" />
        <p class="readout-meta micro">{{ todayLabel }} · 口径：资产分类汇总（现金 / 黄金 / 加密资产等）</p>

        <div class="divider" />

        <SectionLabel text="构成（分类汇总）" />
        <DataRow
          v-for="category in overview.categories"
          :key="category.type"
          :label="category.name"
          :dot-color="categoryColor(category.type)"
          :meta="fmtPercent(category.percentage, 1)"
          :value="fmtMoney(category.value)"
        />
        <EmptyState
          v-if="!overview.categories.length"
          compact
          title="暂无可记录的资产"
          description="当前总资产为 ¥0.00，先添加持仓或现金再来生成快照。"
        />

        <p class="note micro">
          快照记录的是分类汇总的总资产；同一天重复生成会覆盖当天旧快照，不会重复累加。
        </p>
        <p v-if="hasGap" class="gap-note micro">
          注意：另有未归类持仓 {{ fmtMoney(gap) }} 不在快照分类口径内（账户合计 {{ fmtMoney(accountsTotal) }}）。
        </p>
      </template>

      <!-- 第二步：二次确认 -->
      <template v-else>
        <p class="confirm-lead">
          将记录当前总资产 <b class="num">{{ totalText }}</b>（含现金 / 黄金 / 加密资产）
        </p>
        <p v-if="hasGap" class="gap-note micro">
          未归类持仓 {{ fmtMoney(gap) }} 不在快照口径内；账户合计为 {{ fmtMoney(accountsTotal) }}。
        </p>
        <DataRow label="快照日期" :value="todayLabel" />
        <DataRow label="总资产" :value="totalText" />
        <DataRow
          v-for="category in overview.categories"
          :key="`confirm-${category.type}`"
          :label="category.name"
          :value="fmtMoney(category.value)"
        />
        <p class="warn-line micro">⚠ 同一天重复生成会覆盖当天旧快照（幂等，不会累加）。</p>
        <p v-if="submitError" class="submit-error" role="alert">⚠ {{ submitError }}</p>
        <p class="note micro">确认后写入今天的资产快照；之后在「过去」面可以看到这条快照。</p>
      </template>
    </template>

    <template #footer>
      <template v-if="step === 'summary'">
        <BtnKey variant="secondary" block @click="closeSheet">取消</BtnKey>
        <BtnKey
          variant="primary"
          block
          :disabled="!canSnapshot || loading"
          @click="goConfirm"
        >
          生成快照
        </BtnKey>
      </template>
      <template v-else>
        <BtnKey variant="secondary" block :disabled="submitting" @click="step = 'summary'">返回</BtnKey>
        <BtnKey variant="primary" block :disabled="submitting" @click="confirmSnapshot">
          {{ submitting ? '生成中…' : '确认生成' }}
        </BtnKey>
      </template>
    </template>
  </SheetFrame>
</template>

<style scoped>
.readout-meta {
  margin-top: var(--space-2);
}
.divider {
  height: 1px;
  background: var(--hairline);
  margin: var(--space-5) 0 var(--space-4);
}
.confirm-lead {
  font-size: var(--text-body);
  line-height: 1.6;
  color: var(--ink);
  padding: var(--space-2) 0 var(--space-3);
}
.confirm-lead b {
  font-size: var(--text-readout-md);
  font-weight: 600;
  white-space: nowrap;
}
.warn-line {
  margin-top: var(--space-3);
  color: var(--warn);
  line-height: 1.6;
}
.submit-error {
  margin-top: var(--space-2);
  font-size: var(--text-label);
  color: var(--danger);
  line-height: 1.5;
}
.note {
  margin-top: var(--space-3);
  line-height: 1.6;
}
.gap-note {
  margin-top: var(--space-2);
  color: var(--warn);
  line-height: 1.6;
}
</style>
