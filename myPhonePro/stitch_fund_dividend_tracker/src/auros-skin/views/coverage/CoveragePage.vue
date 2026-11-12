<script setup lang="ts">
/**
 * 分红覆盖（四层骨架构样板 · 2026-11-08）
 * 章 01 概览：主角数 + 占比条 + 一组读数
 * 章 02 里程碑：章形状 = 五段导轨
 * 章 03 下一个目标：主角数 + 进度条
 * 章 04 支出：一组行 + 折叠
 */
import { ref, onActivated, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getCoverageData } from '@/api/expense'
import type { CoverageData } from '@/api/expense'
import AurosNavBar from '@/auros-skin/components/auros/AurosNavBar.vue'
import AurosState from '@/auros-skin/components/auros/AurosState.vue'
import AurosChapter from '@/auros-skin/components/auros/AurosChapter.vue'
import AurosStat from '@/auros-skin/components/auros/AurosStat.vue'
import AurosRail from '@/auros-skin/components/auros/AurosRail.vue'
import AurosGroup from '@/auros-skin/components/auros/AurosGroup.vue'
import AurosRow from '@/auros-skin/components/auros/AurosRow.vue'
import AurosDisclosure from '@/auros-skin/components/auros/AurosDisclosure.vue'

const router = useRouter()
const pageState = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const coverage = ref<CoverageData | null>(null)

const milestones = [
  { name: '初出茅庐', requiredExpenses: 1 },
  { name: '小有所成', requiredExpenses: 2 },
  { name: '渐入佳境', requiredExpenses: 3 },
  { name: '收益达人', requiredExpenses: 4 },
  { name: '财务自由', requiredExpenses: 5 },
]

const progressPercent = computed(() => {
  if (!coverage.value || coverage.value.totalExpenses === 0) return 0
  return Math.round((coverage.value.coveredExpenses / coverage.value.totalExpenses) * 100)
})

const nextTargetExpense = computed(() => {
  if (!coverage.value) return null
  return coverage.value.expenses.find((e) => e.inProgress) ?? null
})

const nextMilestone = computed(() => {
  if (!coverage.value) return null
  const idx = coverage.value.currentMilestoneIndex
  return idx < milestones.length - 1 ? { ...milestones[idx + 1] } : null
})

/** 支出：前 5 行 + 其余折叠（密度预算） */
const shownExpenses = computed(() => coverage.value?.expenses.slice(0, 5) ?? [])
const restExpenses = computed(() => coverage.value?.expenses.slice(5) ?? [])

function group(n: number): string {
  return Math.round(n)
    .toString()
    .replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

async function loadData() {
  pageState.value = 'loading'
  try {
    const data = await getCoverageData()
    coverage.value = data
    pageState.value = data.totalExpenses === 0 ? 'empty' : 'ready'
  } catch (e) {
    pageState.value = 'error'
  }
}

const goToSettings = () => router.push('/coverage/settings')

onActivated(loadData)
</script>

<template>
  <div class="page">
    <AurosNavBar title="分红覆盖">
      <template #right>
        <button type="button" class="icon-btn" aria-label="设置" @click="goToSettings">
          <svg viewBox="0 0 16 16" width="14" height="14" fill="none">
            <circle cx="8" cy="8" r="2.2" stroke="currentColor" stroke-width="1.2" />
            <path
              d="M8 1.6v1.6M8 12.8v1.6M1.6 8h1.6M12.8 8h1.6M3.5 3.5l1.1 1.1M11.4 11.4l1.1 1.1M12.5 3.5l-1.1 1.1M4.6 11.4l-1.1 1.1"
              stroke="currentColor"
              stroke-width="1.2"
              stroke-linecap="round"
            />
          </svg>
        </button>
      </template>
    </AurosNavBar>

    <main class="body">
      <AurosState
        v-if="pageState !== 'ready'"
        :state="pageState === 'loading' ? 'loading' : pageState === 'empty' ? 'empty' : 'error'"
        :rows="4"
        :text="pageState === 'empty' ? '还没有生活支出账本。先设一张账单，才看得见分红能覆盖多少。' : '分红覆盖数据没有取到'"
        :action-label="pageState === 'empty' ? '去设置' : pageState === 'error' ? '重试' : ''"
        @action="pageState === 'empty' ? goToSettings : loadData()"
      />

      <template v-else-if="coverage">
        <!-- 01 概览：主角数 + 形状 + 一组读数 -->
        <AurosChapter
          label="COVERAGE"
          title="分红已覆盖"
          :meta="`${coverage.coveredExpenses} / ${coverage.totalExpenses} 项`"
          :gap="false"
        >
          <AurosStat
            :value="String(progressPercent)"
            unit="%"
            pink
            caption="整体覆盖进度"
          />
          <div class="rail-wrap">
            <AurosRail
              :percent="progressPercent"
              :label-left="`已覆盖 ${coverage.coveredExpenses} 项`"
              :label-right="`共 ${coverage.totalExpenses} 项`"
            />
          </div>
          <AurosGroup title="年度金额" count="元">
            <AurosRow label="固定支出总额" :value="`¥${group(coverage.totalAnnualExpense)}`" />
            <AurosRow label="预计年度分红" :value="`¥${group(coverage.predictedAnnualDividend)}`" mark="lit" />
            <AurosRow label="今年已实收" :value="`¥${group(coverage.totalDividendReceived)}`" />
          </AurosGroup>
        </AurosChapter>

        <!-- 02 里程碑：章形状 = 五段导轨 -->
        <AurosChapter label="MILESTONES" title="成长之路" :meta="nextMilestone ? `下一段 · ${nextMilestone.name}` : '已点亮全部'">
          <div class="mile">
            <i class="mile__line"></i>
            <i
              class="mile__line mile__line--on"
              :style="{ width: `${(coverage.currentMilestoneIndex / (milestones.length - 1)) * 100}%` }"
            ></i>
            <div v-for="(m, i) in milestones" :key="m.name" class="mile__node">
              <i
                class="mile__dot"
                :class="{ 'is-on': i <= coverage.currentMilestoneIndex, 'is-now': i === coverage.currentMilestoneIndex }"
              ></i>
              <span class="mile__label" :class="{ 'is-on': i <= coverage.currentMilestoneIndex }">{{ m.name }}</span>
            </div>
          </div>
          <AurosGroup title="当前段位" :count="`${coverage.currentMilestoneIndex + 1} / ${milestones.length}`">
            <AurosRow
              :label="milestones[coverage.currentMilestoneIndex]?.name ?? '—'"
              :sub="nextMilestone ? `再覆盖 ${Math.max(0, nextMilestone.requiredExpenses - coverage.coveredExpenses)} 项进入下一段` : '已到终点'"
              :value="`${coverage.coveredExpenses} 项`"
              mark="lit"
            />
          </AurosGroup>
        </AurosChapter>

        <!-- 03 下一个目标：主角数 + 进度条 -->
        <AurosChapter
          v-if="nextTargetExpense"
          label="NEXT"
          title="下一个目标"
          :meta="`还差 ¥${group(Math.max(0, nextTargetExpense.annualAmount - coverage.predictedAnnualDividend))}`"
        >
          <div class="next">
            <div class="next__main">
              <span class="next__name">{{ nextTargetExpense.name }}</span>
              <span class="next__meta">¥{{ group(nextTargetExpense.annualAmount) }} / 年</span>
            </div>
            <AurosRail
              :percent="progressPercent"
              tone="pink"
              :label-left="`覆盖 ${progressPercent}%`"
              :label-right="`缺口 ¥${group(Math.max(0, nextTargetExpense.annualAmount - coverage.predictedAnnualDividend))}`"
            />
          </div>
        </AurosChapter>

        <!-- 04 支出：一组行 + 折叠 -->
        <AurosChapter label="EXPENSES" title="生活支出" :meta="`${coverage.expenses.length} 项`">
          <AurosGroup title="固定开销" :count="`${coverage.totalExpenses} 项`">
            <AurosRow
              v-for="exp in shownExpenses"
              :key="exp.id"
              :label="exp.name"
              :value="`¥${group(exp.annualAmount)}`"
              :mark="exp.covered ? 'lit' : exp.inProgress ? 'pink' : 'dim'"
              :sub="exp.covered ? '已覆盖' : exp.inProgress ? '进行中' : '未覆盖'"
              :tone="exp.covered ? 'default' : 'muted'"
            />
          </AurosGroup>
          <AurosDisclosure v-if="restExpenses.length" :count="restExpenses.length">
            <AurosGroup title="其余" :count="`${restExpenses.length} 项`">
              <AurosRow
                v-for="exp in restExpenses"
                :key="exp.id"
                :label="exp.name"
                :value="`¥${group(exp.annualAmount)}`"
                mark="dim"
                sub="未覆盖"
                tone="muted"
              />
            </AurosGroup>
          </AurosDisclosure>
        </AurosChapter>
      </template>
    </main>
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
  padding: 8px 16px calc(env(safe-area-inset-bottom, 0px) + 40px);
}
.icon-btn {
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
.rail-wrap {
  margin-top: 16px;
}
.rail-wrap + :deep(.group) {
  margin-top: 18px;
}

/* 里程碑：本章允许的唯一形状 */
.mile {
  position: relative;
  display: flex;
  justify-content: space-between;
  padding: 6px 2px 0;
}
.mile__line {
  position: absolute;
  left: 6px;
  right: 6px;
  top: 11px;
  height: 1px;
  background: rgba(237, 255, 254, 0.12);
}
.mile__line--on {
  right: auto;
  background: linear-gradient(90deg, rgba(203, 255, 252, 0.2), var(--au-lit));
  box-shadow: 0 0 6px rgba(139, 245, 232, 0.5);
}
.mile__node {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  width: 20%;
}
.mile__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(237, 255, 254, 0.14);
}
.mile__dot.is-on {
  background: var(--au-lit);
  box-shadow: 0 0 8px rgba(203, 255, 252, 0.7);
}
.mile__dot.is-now {
  width: 10px;
  height: 10px;
  margin-top: -1px;
  animation: au-breathe 2.6s ease-in-out infinite;
}
.mile__label {
  font-size: 10px;
  letter-spacing: 0.02em;
  color: rgba(187, 199, 198, 0.55);
  white-space: nowrap;
}
.mile__label.is-on {
  color: var(--au-mist);
}
.mile + :deep(.group) {
  margin-top: 18px;
}

/* 下一个目标 */
.next {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.next__main {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.next__name {
  font-size: 16px;
  font-weight: 500;
  color: var(--au-mist);
}
.next__meta {
  font-size: 10px;
  letter-spacing: 0.06em;
  color: var(--au-silver);
}
</style>
