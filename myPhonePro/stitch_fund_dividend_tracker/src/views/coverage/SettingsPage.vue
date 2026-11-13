<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listExpenses, deleteExpense } from '@/api/expense'
import type { LiveExpenseItem } from '@/api/expense'
import AppHeader from '@/components/shared/AppHeader.vue'
import PageStateComp from '@/components/shared/PageState.vue'
import AddExpenseModal from './AddExpenseModal.vue'

const router = useRouter()
const pageState = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const expenses = ref<LiveExpenseItem[]>([])
const showAddModal = ref(false)

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
  <div class="min-h-screen bg-page-bg flex flex-col">
    <!-- TopAppBar -->
    <header class="flex items-center justify-between px-gutter h-14 bg-card-bg border-b border-border-light/40 sticky top-0 z-50">
      <button @click="goBack" class="w-10 h-10 flex items-center justify-center -ml-2 active:opacity-80">
        <span class="material-symbols-outlined text-text-secondary">arrow_back</span>
      </button>
      <h1 class="font-body text-md font-medium text-text-primary">生活支出设置</h1>
      <button @click="router.push('/')" class="w-10 h-10 flex items-center justify-center active:opacity-80 transition-opacity">
        <span class="material-symbols-outlined text-text-secondary">home</span>
      </button>
    </header>

    <PageStateComp :state="pageState" @retry="loadData" />

    <!-- Content -->
    <main v-if="pageState === 'ready'" class="flex-1 px-gutter pb-32 overflow-y-auto">
      <!-- Banner -->
      <div class="mt-md p-lg bg-brand-light/60 rounded-xl relative overflow-hidden">
        <div class="absolute -right-6 -top-6 w-24 h-24 rounded-full bg-brand-light/40 pointer-events-none"></div>
        <p class="font-body text-sm text-text-secondary relative z-10">
          📋 告诉我你的生活花销，我来算算你的股息能替你付哪些账单
        </p>
      </div>

      <!-- Section Title -->
      <div class="flex items-center justify-between mt-xl mb-md">
        <h2 class="font-body text-md font-medium text-text-primary">我的支出</h2>
      </div>

      <!-- Expense List -->
      <div class="bg-card-bg rounded-xl card-shadow border border-border-light/40 overflow-hidden">
        <div
          v-for="exp in expenses"
          :key="exp.id"
          class="flex items-center px-md py-3 border-b border-border-light/40 gap-md last:border-b-0"
        >
          <div class="w-9 h-9 flex items-center justify-center bg-brand-light rounded-full text-base flex-shrink-0">
            <span>{{ exp.icon }}</span>
          </div>
          <div class="flex-1 min-w-0">
            <p class="font-body text-sm font-medium text-text-primary truncate">{{ exp.name }}</p>
          </div>
          <div class="flex items-center gap-1 flex-shrink-0">
            <span class="font-display text-md font-semibold text-text-primary tabular-nums">{{ exp.monthlyAmount }}</span>
            <span class="font-body text-xs text-text-tertiary">元/月</span>
          </div>
          <button @click="handleDelete(exp.id, exp.name)" class="text-text-tertiary hover:text-error p-1 flex-shrink-0 -mr-1">
            <span class="material-symbols-outlined text-[18px]">close</span>
          </button>
        </div>
      </div>

      <!-- Add Button -->
      <button
        @click="showAddModal = true"
        class="w-full mt-md py-3 bg-card-bg rounded-xl flex items-center justify-center gap-2 border-2 border-dashed border-border-light hover:border-brand/50 hover:text-brand transition-colors active:scale-[0.98]"
      >
        <span class="material-symbols-outlined text-brand text-lg">add_circle</span>
        <span class="font-body text-sm text-text-tertiary">添加自定义支出</span>
      </button>
    </main>

    <!-- Empty state -->
    <main v-if="pageState === 'empty'" class="flex-1 flex flex-col items-center justify-center px-gutter gap-md text-center">
      <span class="text-6xl">💰</span>
      <h2 class="font-body text-md font-medium text-text-primary">还没有生活支出</h2>
      <p class="font-body text-sm text-text-tertiary">添加你的第一项支出吧</p>
      <button @click="showAddModal = true" class="px-6 py-3 bg-brand text-white rounded-xl font-body text-sm font-medium active:scale-[0.97] transition-all">
        添加支出
      </button>
    </main>

    <!-- Fixed Bottom Button -->
    <div class="fixed bottom-0 left-0 right-0 max-w-[600px] mx-auto p-md bg-card-bg shadow-elevated">
      <button
        @click="goToCoverage"
        class="w-full h-12 bg-brand text-white rounded-xl font-body text-sm font-medium flex items-center justify-center gap-2 active:scale-[0.97] transition-all"
      >
        <span>查看分红覆盖</span>
        <span class="material-symbols-outlined text-lg">arrow_forward</span>
      </button>
    </div>

    <!-- Add Expense Modal -->
    <AddExpenseModal v-if="showAddModal" @close="showAddModal = false" @added="handleAdded" />
  </div>
</template>
