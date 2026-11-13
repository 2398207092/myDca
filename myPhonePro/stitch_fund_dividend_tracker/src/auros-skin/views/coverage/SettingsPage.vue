<script setup lang="ts">
/**
 * 生活支出设置（卡片化 · 2026-11-11）
 * 卡 01 每月固定支出：AurosStat（formatMoney）+ 均摊读数行
 * 卡 02 支出项：一组行（右侧删除）+ 折叠 + 添加一项
 * 业务逻辑与原页面逐行一致；新增仍走 AddExpenseModal；金额统一走 utils/format。
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listExpenses, deleteExpense } from '@/api/expense'
import type { LiveExpenseItem } from '@/api/expense'
import AurosNavBar from '@/auros-skin/components/auros/AurosNavBar.vue'
import AurosState from '@/auros-skin/components/auros/AurosState.vue'
import AurosChapter from '@/auros-skin/components/auros/AurosChapter.vue'
import AurosStat from '@/auros-skin/components/auros/AurosStat.vue'
import AurosGroup from '@/auros-skin/components/auros/AurosGroup.vue'
import AurosRow from '@/auros-skin/components/auros/AurosRow.vue'
import AurosDisclosure from '@/auros-skin/components/auros/AurosDisclosure.vue'
import AurosButton from '@/auros-skin/components/auros/AurosButton.vue'
import AddExpenseModal from './AddExpenseModal.vue'
import { formatMoney } from '@/auros-skin/utils/format'

const router = useRouter()
const pageState = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const expenses = ref<LiveExpenseItem[]>([])
const showAddModal = ref(false)

/* 展示层派生：合计与均摊（不改业务口径） */
const totalMonthly = computed(() => expenses.value.reduce((s, e) => s + e.monthlyAmount, 0))
const dailyAvg = computed(() => Math.round((totalMonthly.value * 12) / 365))
const shownExpenses = computed(() => expenses.value.slice(0, 5))
const restExpenses = computed(() => expenses.value.slice(5))

async function loadData() {
  pageState.value = 'loading'
  try {
    const data = await listExpenses()
    expenses.value = data
    pageState.value = data.length === 0 ? 'empty' : 'ready'
  } catch (e) {
    pageState.value = 'error'
  }
}

async function handleDelete(id: string, name: string) {
  if (!confirm(`确定删除"${name}"吗？`)) return
  try {
    await deleteExpense(id)
    await loadData()
  } catch (e) {
    // ignore
  }
}

function handleAdded() {
  showAddModal.value = false
  loadData()
}

function goBack() {
  router.push('/coverage')
}

function goToCoverage() {
  router.push('/coverage')
}

onMounted(loadData)
</script>

<template>
  <div class="page">
    <AurosNavBar title="生活支出设置" sub="EXPENSES">
      <template #right>
        <button type="button" class="nav-text" @click="goToCoverage">覆盖</button>
      </template>
    </AurosNavBar>

    <main class="body">
      <AurosState
        v-if="pageState !== 'ready'"
        :state="pageState === 'loading' ? 'loading' : pageState === 'empty' ? 'empty' : 'error'"
        :rows="4"
        :text="
          pageState === 'empty'
            ? '还没有生活支出。先登记一项固定开销，分红覆盖才有分母。'
            : '生活支出数据没有取到'
        "
        :action-label="pageState === 'empty' ? '添加支出' : pageState === 'error' ? '重试' : ''"
        @action="pageState === 'empty' ? (showAddModal = true) : loadData()"
      />

      <template v-else>
        <!-- 卡 01 每月固定支出 -->
        <AurosChapter label="FIXED COST" title="每月固定支出" :gap="false">
          <AurosStat
            :value="formatMoney(totalMonthly)"
            pink
            size="lg"
            caption="分红覆盖以年度支出为分母"
          />
          <div class="group-wrap">
            <AurosGroup title="账本概览">
              <AurosRow
                label="均摊每日"
                :value="formatMoney(dailyAvg)"
                :sub="`${expenses.length} 项固定支出 · 按全年 365 天折算`"
              />
            </AurosGroup>
          </div>
        </AurosChapter>

        <!-- 卡 02 支出项 -->
        <AurosChapter label="LIST" title="支出项" meta="点 × 删除">
          <AurosGroup title="支出明细">
            <AurosRow v-for="exp in shownExpenses" :key="exp.id" :label="exp.name">
              <template #default>
                <span class="row-val au-num">{{ formatMoney(exp.monthlyAmount) }}</span>
                <button
                  type="button"
                  class="row-del"
                  :aria-label="`删除 ${exp.name}`"
                  @click="handleDelete(exp.id, exp.name)"
                >
                  <svg viewBox="0 0 16 16" width="12" height="12" fill="none" aria-hidden="true">
                    <path d="m4 4 8 8M12 4l-8 8" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" />
                  </svg>
                </button>
              </template>
            </AurosRow>
          </AurosGroup>

          <AurosDisclosure v-if="restExpenses.length" :count="restExpenses.length">
            <AurosGroup title="其余支出" :count="`${restExpenses.length} 项`">
              <AurosRow v-for="exp in restExpenses" :key="exp.id" :label="exp.name">
                <template #default>
                  <span class="row-val au-num">{{ formatMoney(exp.monthlyAmount) }}</span>
                  <button
                    type="button"
                    class="row-del"
                    :aria-label="`删除 ${exp.name}`"
                    @click="handleDelete(exp.id, exp.name)"
                  >
                    <svg viewBox="0 0 16 16" width="12" height="12" fill="none" aria-hidden="true">
                      <path d="m4 4 8 8M12 4l-8 8" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" />
                    </svg>
                  </button>
                </template>
              </AurosRow>
            </AurosGroup>
          </AurosDisclosure>

          <div class="add-wrap">
            <AurosButton variant="ghost" block @click="showAddModal = true">
              <svg viewBox="0 0 16 16" width="12" height="12" fill="none" aria-hidden="true">
                <path d="M8 3.2v9.6M3.2 8h9.6" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" />
              </svg>
              添加一项
            </AurosButton>
          </div>
        </AurosChapter>
      </template>
    </main>

    <footer class="bar">
      <AurosButton variant="primary" block @click="goToCoverage">
        查看分红覆盖
        <svg viewBox="0 0 12 12" width="10" height="10" fill="none" aria-hidden="true">
          <path d="M3 9 9 3M4.4 3H9v4.6" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </AurosButton>
    </footer>

    <!-- Add Expense Modal -->
    <AddExpenseModal v-if="showAddModal" @close="showAddModal = false" @added="handleAdded" />
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
  padding: 8px 16px calc(env(safe-area-inset-bottom, 0px) + 112px);
}
.nav-text {
  border: 0;
  background: none;
  padding: 0;
  font-family: inherit;
  font-size: 13px;
  color: var(--au-silver);
}

/* 形状与组之间的呼吸（章内） */
.group-wrap {
  margin-top: 18px;
}

/* 行右侧：金额 + 删除 */
.row-val {
  font-size: 13px;
  font-weight: 500;
  color: var(--au-mist);
  white-space: nowrap;
}
.row-del {
  flex: none;
  width: 28px;
  height: 28px;
  margin-right: -6px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: none;
  color: rgba(187, 199, 198, 0.55);
  transition: color 160ms var(--au-ease), background 160ms var(--au-ease);
}
.row-del:active {
  color: var(--au-pink);
  background: rgba(253, 233, 255, 0.06);
}

.add-wrap {
  margin-top: 16px;
}

/* ---------- 底部动作（全页唯一渐变） ---------- */
.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 12px 16px calc(env(safe-area-inset-bottom, 0px) + 16px);
  background: linear-gradient(180deg, rgba(1, 38, 36, 0) 0%, rgba(1, 29, 28, 0.92) 38%);
}

@media (prefers-reduced-motion: reduce) {
  .row-del {
    transition: none;
  }
}
</style>
