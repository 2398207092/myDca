<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageStateComp from '@/components/shared/PageState.vue'
import ToastNotification from '@/components/shared/ToastNotification.vue'
import BudgetCategoryDialog from '@/components/budget/BudgetCategoryDialog.vue'
import BudgetRecordDialog from '@/components/budget/BudgetRecordDialog.vue'
import {
  getBudgetOverview,
  listBudgetRecords,
  createBudgetCategory,
  updateBudgetCategory,
  deleteBudgetCategory,
  createBudgetRecord,
  deleteBudgetRecord,
  type BudgetCategoryItem,
  type BudgetOverview,
  type BudgetRecordItem,
} from '@/api/budget'
import { formatYuan } from '@/utils/format'

const router = useRouter()
const pageState = ref<'loading' | 'ready' | 'error'>('loading')
const year = ref(new Date().getFullYear())
const overview = ref<BudgetOverview | null>(null)
const records = ref<BudgetRecordItem[]>([])

const toastMsg = ref<string | null>(null)
const toastType = ref<'success' | 'error' | 'info'>('error')
function showToast(msg: string, type: 'success' | 'error' | 'info' = 'error') {
  toastMsg.value = msg
  toastType.value = type
}
function toastError(e: unknown, fallback: string) {
  showToast(e instanceof Error && e.message ? e.message : fallback, 'error')
}

const categories = computed<BudgetCategoryItem[]>(() => overview.value?.categories ?? [])
const hasCategories = computed(() => categories.value.length > 0)

// ==================== 金额 ====================
const money = formatYuan
function percent(value: number | undefined | null): number {
  const n = Number(value ?? 0)
  if (!Number.isFinite(n)) return 0
  return Math.min(100, Math.max(0, n))
}
function isOverspent(): boolean {
  return Number(overview.value?.remaining ?? 0) < 0
}

// ==================== 加载 ====================
async function load() {
  pageState.value = 'loading'
  try {
    const [o, r] = await Promise.all([getBudgetOverview(year.value), listBudgetRecords(year.value)])
    overview.value = o
    records.value = r
    pageState.value = 'ready'
  } catch (e) {
    pageState.value = 'error'
    toastError(e, '加载预算失败')
  }
}

async function silentReload() {
  try {
    const [o, r] = await Promise.all([getBudgetOverview(year.value), listBudgetRecords(year.value)])
    overview.value = o
    records.value = r
  } catch (e) {
    toastError(e, '刷新预算失败')
  }
}

onMounted(load)

function switchYear(delta: number) {
  year.value += delta
  load()
}

// ==================== 预算类型 ====================
const showCategoryDialog = ref(false)
const editingCategory = ref<BudgetCategoryItem | null>(null)
const savingCategory = ref(false)

function openCreateCategory() {
  editingCategory.value = null
  showCategoryDialog.value = true
}

function openEditCategory(cat: BudgetCategoryItem) {
  editingCategory.value = cat
  showCategoryDialog.value = true
}

async function submitCategory(payload: { name: string; amount: number }) {
  savingCategory.value = true
  try {
    if (editingCategory.value) {
      await updateBudgetCategory(editingCategory.value.id, payload)
      showToast('已更新预算类型', 'success')
    } else {
      await createBudgetCategory({ year: year.value, name: payload.name, amount: payload.amount })
      showToast('已添加预算类型', 'success')
    }
    showCategoryDialog.value = false
    await silentReload()
  } catch (e) {
    toastError(e, '保存预算类型失败')
  } finally {
    savingCategory.value = false
  }
}

async function removeCategory() {
  const cat = editingCategory.value
  if (!cat) return
  savingCategory.value = true
  try {
    await deleteBudgetCategory(cat.id)
    showToast('已删除预算类型', 'success')
    showCategoryDialog.value = false
    await silentReload()
  } catch (e) {
    toastError(e, '删除失败')
  } finally {
    savingCategory.value = false
  }
}

// ==================== 记账 ====================
const showRecordDialog = ref(false)
const savingRecord = ref(false)

function openRecordDialog() {
  if (!hasCategories.value) {
    showToast('先添加一个预算类型，才能记账', 'info')
    openCreateCategory()
    return
  }
  showRecordDialog.value = true
}

async function submitRecord(payload: { categoryId: string; occurDate: string; amount: number; note: string }) {
  savingRecord.value = true
  try {
    await createBudgetRecord(payload)
    showToast('已记一笔', 'success')
    showRecordDialog.value = false
    await silentReload()
  } catch (e) {
    toastError(e, '记账失败')
  } finally {
    savingRecord.value = false
  }
}

async function removeRecord(record: BudgetRecordItem) {
  if (!window.confirm(`删除这笔 ${money(record.amount)} 的记录？`)) return
  try {
    await deleteBudgetRecord(record.id)
    await silentReload()
  } catch (e) {
    toastError(e, '删除记录失败')
  }
}

// ==================== 流水按月分组 ====================
const groupedRecords = computed(() => {
  const groups = new Map<string, BudgetRecordItem[]>()
  for (const r of records.value) {
    const key = (r.occurDate || '').slice(0, 7)
    const list = groups.get(key) ?? []
    list.push(r)
    groups.set(key, list)
  }
  return [...groups.entries()].map(([month, items]) => ({
    month,
    label: `${Number(month.slice(5, 7))}月`,
    items,
    total: items.reduce((sum, r) => sum + Number(r.amount || 0), 0),
  }))
})
</script>

<template>
  <div class="min-h-screen bg-page-bg">
    <ToastNotification :message="toastMsg" :type="toastType" @close="toastMsg = null" />

    <header class="flex items-center justify-between px-gutter h-14 sticky top-0 z-50 bg-card-bg border-b border-border-light/40">
      <button class="w-10 h-10 flex items-center justify-center -ml-2 active:opacity-80" @click="router.back()">
        <span class="material-symbols-outlined text-text-secondary">arrow_back</span>
      </button>
      <h1 class="font-body text-md font-medium text-text-primary">年度预算</h1>
      <button class="w-10 h-10 flex items-center justify-center active:opacity-80" @click="openCreateCategory">
        <span class="material-symbols-outlined text-text-secondary">add</span>
      </button>
    </header>

    <main class="px-gutter pb-32 pt-md max-w-[600px] mx-auto space-y-md">
      <!-- 年份切换 -->
      <div class="flex items-center justify-center gap-4">
        <button class="w-8 h-8 flex items-center justify-center rounded-full hover:bg-card-alt" @click="switchYear(-1)">
          <span class="material-symbols-outlined text-xl text-text-secondary">chevron_left</span>
        </button>
        <span class="font-display text-lg font-semibold text-text-primary tabular-nums">{{ year }} 年</span>
        <button class="w-8 h-8 flex items-center justify-center rounded-full hover:bg-card-alt" @click="switchYear(1)">
          <span class="material-symbols-outlined text-xl text-text-secondary">chevron_right</span>
        </button>
      </div>

      <PageStateComp v-if="pageState !== 'ready'" :state="pageState" @retry="load" />

      <template v-else>
        <!-- 总览 -->
        <section class="bg-card-bg rounded-lg p-lg card-shadow border border-border-light/40">
          <div class="flex items-start justify-between">
            <div>
              <p class="font-body text-xs text-text-tertiary">{{ year }} 年预算</p>
              <p class="font-display text-2xl font-semibold text-text-primary tabular-nums mt-1">
                {{ money(overview?.totalAmount) }}
              </p>
            </div>
            <div class="text-right">
              <p class="font-body text-xs text-text-tertiary">{{ isOverspent() ? '已超支' : '剩余' }}</p>
              <p class="font-display text-lg font-semibold tabular-nums mt-1"
                 :class="isOverspent() ? 'text-neg' : 'text-brand'">
                {{ money(Math.abs(Number(overview?.remaining ?? 0))) }}
              </p>
            </div>
          </div>

          <div class="mt-md h-1.5 rounded-full bg-card-alt overflow-hidden">
            <div
              class="h-full rounded-full transition-all duration-300"
              :class="isOverspent() ? 'bg-neg' : 'bg-brand'"
              :style="{ width: percent(overview?.usagePercent) + '%' }"
            ></div>
          </div>
          <div class="flex items-center justify-between mt-1.5">
            <span class="font-body text-xs text-text-tertiary">已用 {{ money(overview?.totalSpent) }}</span>
            <span class="font-body text-xs text-text-tertiary tabular-nums">{{ Number(overview?.usagePercent ?? 0).toFixed(0) }}%</span>
          </div>

          <button
            class="mt-md w-full py-2.5 bg-brand text-white rounded-lg font-body text-sm font-medium flex items-center justify-center gap-1.5 active:scale-[0.99] transition-transform"
            @click="openRecordDialog"
          >
            <span class="material-symbols-outlined text-lg">add</span>
            记一笔
          </button>
        </section>

        <!-- 预算类型 -->
        <section class="bg-card-bg rounded-lg p-lg card-shadow border border-border-light/40">
          <div class="flex items-center justify-between mb-md">
            <h2 class="font-body text-sm font-medium text-text-primary">预算类型</h2>
            <button class="font-body text-xs text-brand flex items-center gap-0.5" @click="openCreateCategory">
              <span class="material-symbols-outlined text-md">add</span>
              添加
            </button>
          </div>

          <!-- 空态：不预置默认分类，提示用户自己加 -->
          <div v-if="!hasCategories" class="flex flex-col items-center gap-2 py-8">
            <span class="material-symbols-outlined text-3xl text-text-tertiary/60">savings</span>
            <p class="font-body text-sm text-text-primary">还没有预算类型</p>
            <p class="font-body text-xs text-text-tertiary text-center leading-relaxed">
              先添加你想控制的开销类型（如餐饮、日用、出行），<br />设一个年度额度，再开始记账。
            </p>
            <button
              class="mt-1 px-lg py-2 bg-brand text-white rounded-lg font-body text-xs font-medium active:scale-[0.98] transition-transform"
              @click="openCreateCategory"
            >添加预算类型</button>
          </div>

          <div v-else class="space-y-sm">
            <button
              v-for="cat in categories"
              :key="cat.id"
              class="w-full text-left rounded-lg px-md py-3 hover:bg-card-alt transition-colors"
              @click="openEditCategory(cat)"
            >
              <div class="flex items-center justify-between gap-2">
                <span class="font-body text-sm text-text-primary truncate">{{ cat.name }}</span>
                <span class="font-body text-xs tabular-nums shrink-0"
                      :class="Number(cat.remaining) < 0 ? 'text-neg' : 'text-text-tertiary'">
                  {{ money(cat.spent) }} / {{ money(cat.amount) }}
                </span>
              </div>
              <div class="mt-1.5 h-1 rounded-full bg-card-alt overflow-hidden">
                <div
                  class="h-full rounded-full"
                  :class="Number(cat.remaining) < 0 ? 'bg-neg' : 'bg-brand'"
                  :style="{ width: percent(Number(cat.amount) > 0 ? (Number(cat.spent) / Number(cat.amount)) * 100 : 0) + '%' }"
                ></div>
              </div>
              <div class="flex items-center justify-between mt-1">
                <span class="font-body text-xs text-text-tertiary">{{ cat.recordCount }} 笔</span>
                <span class="font-body text-xs tabular-nums"
                      :class="Number(cat.remaining) < 0 ? 'text-neg' : 'text-text-tertiary'">
                  {{ Number(cat.remaining) < 0 ? '超 ' : '剩 ' }}{{ money(Math.abs(Number(cat.remaining))) }}
                </span>
              </div>
            </button>
          </div>
        </section>

        <!-- 记账流水 -->
        <section class="bg-card-bg rounded-lg p-lg card-shadow border border-border-light/40">
          <div class="flex items-center justify-between mb-md">
            <h2 class="font-body text-sm font-medium text-text-primary">记账流水</h2>
            <span class="font-body text-xs text-text-tertiary tabular-nums">{{ records.length }} 笔</span>
          </div>

          <div v-if="records.length === 0" class="py-8 text-center">
            <p class="font-body text-xs text-text-tertiary">{{ year }} 年还没有记账</p>
          </div>

          <div v-else class="space-y-md">
            <div v-for="group in groupedRecords" :key="group.month">
              <div class="flex items-center justify-between mb-1">
                <span class="font-body text-xs text-text-tertiary">{{ group.label }}</span>
                <span class="font-body text-xs text-text-tertiary tabular-nums">{{ money(group.total) }}</span>
              </div>
              <div class="space-y-0.5">
                <div
                  v-for="rec in group.items"
                  :key="rec.id"
                  class="flex items-center justify-between gap-2 py-2 px-2 rounded-lg hover:bg-card-alt transition-colors"
                >
                  <div class="flex items-center gap-2 min-w-0">
                    <span class="font-body text-xs text-text-tertiary tabular-nums shrink-0">{{ rec.occurDate.slice(8, 10) }}日</span>
                    <span class="font-body text-xs text-text-primary truncate">{{ rec.categoryName }}</span>
                    <span v-if="rec.note" class="font-body text-xs text-text-tertiary truncate">{{ rec.note }}</span>
                  </div>
                  <div class="flex items-center gap-1 shrink-0">
                    <span class="font-body text-xs text-text-primary tabular-nums">{{ money(rec.amount) }}</span>
                    <button class="w-6 h-6 flex items-center justify-center rounded-full hover:bg-card-bg" @click="removeRecord(rec)">
                      <span class="material-symbols-outlined text-base text-text-tertiary">delete</span>
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>
      </template>
    </main>

    <BudgetCategoryDialog
      v-model:open="showCategoryDialog"
      :category="editingCategory"
      :year="year"
      :saving="savingCategory"
      @submit="submitCategory"
      @remove="removeCategory"
    />

    <BudgetRecordDialog
      v-model:open="showRecordDialog"
      :categories="categories"
      :year="year"
      :saving="savingRecord"
      @submit="submitRecord"
    />
  </div>
</template>
