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
  <div class="min-h-screen bg-surface flex flex-col">
    <!-- TopAppBar -->
    <header class="flex items-center justify-between px-container-padding h-16 bg-surface sticky top-0 z-50">
      <button @click="goBack" class="material-symbols-outlined text-primary">arrow_back</button>
      <h1 class="font-headline-md text-headline-md font-bold text-on-surface">生活支出设置</h1>
      <button @click="router.push('/')" class="w-10 h-10 flex items-center justify-center active:opacity-80 transition-opacity">
        <span class="material-symbols-outlined text-on-surface-variant">home</span>
      </button>
    </header>

    <PageStateComp :state="pageState" @retry="loadData" />

    <!-- Content -->
    <main v-if="pageState === 'ready'" class="flex-1 px-container-padding pb-32 overflow-y-auto">
      <!-- Banner -->
      <div class="mt-md p-lg bg-surface-container-low rounded-xl relative overflow-hidden">
        <p class="text-on-surface-variant font-body-md leading-relaxed">
          告诉我你的生活花销，我来算算你的股息能替你付哪些账单
        </p>
      </div>

      <!-- Section Title -->
      <div class="flex items-center justify-between mt-xl mb-md">
        <h2 class="font-headline-md text-headline-md text-on-surface">我的支出</h2>
      </div>

      <!-- Expense List -->
      <div class="bg-surface-container-lowest rounded-xl overflow-hidden">
        <div
          v-for="exp in expenses"
          :key="exp.id"
          class="flex items-center p-md border-b border-surface-variant/50 gap-md last:border-b-0"
        >
          <div class="w-10 h-10 flex items-center justify-center bg-primary-fixed rounded-full text-xl">
            <span>{{ exp.icon }}</span>
          </div>
          <div class="flex-1">
            <p class="font-label-bold text-label-bold">{{ exp.name }}</p>
          </div>
          <div class="flex items-center gap-xs">
            <div class="bg-surface-container rounded-lg px-3 py-2 min-w-[64px] text-right">
              <span class="font-headline-md text-on-surface">{{ exp.monthlyAmount }}</span>
            </div>
            <span class="text-on-surface-variant text-caption font-caption">元/月</span>
          </div>
          <button @click="handleDelete(exp.id, exp.name)" class="text-error/60 hover:text-error p-1">
            <span class="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>
      </div>

      <!-- Add Button -->
      <button
        @click="showAddModal = true"
        class="w-full mt-md p-md bg-surface-container-lowest rounded-xl flex items-center justify-center gap-2 border border-dashed border-outline-variant hover:bg-surface-container-low active:scale-[0.98] transition-all"
      >
        <span class="material-symbols-outlined text-primary">add_circle</span>
        <span class="text-on-surface-variant font-label-bold text-label-bold">添加自定义支出</span>
      </button>
    </main>

    <!-- Empty state -->
    <main v-if="pageState === 'empty'" class="flex-1 flex flex-col items-center justify-center px-container-padding gap-md text-center">
      <span class="text-6xl">💰</span>
      <h2 class="font-headline-md text-headline-md text-on-surface">还没有生活支出</h2>
      <p class="text-body-md text-on-surface-variant">添加你的第一项支出吧</p>
      <button @click="showAddModal = true" class="px-6 py-3 bg-primary-container text-white rounded-2xl font-semibold">
        添加支出
      </button>
    </main>

    <!-- Fixed Bottom Button -->
    <div class="fixed bottom-0 left-0 right-0 max-w-[600px] mx-auto p-md bg-gradient-to-t from-surface via-surface to-transparent">
      <button
        @click="goToCoverage"
        class="w-full h-14 bg-surface-container-lowest hover:bg-surface-container-low transition-all rounded-2xl flex items-center justify-center gap-2 active:scale-[0.98] border border-dashed border-outline-variant"
      >
        <span class="text-on-surface-variant font-headline-md text-headline-md">查看分红覆盖</span>
        <span class="material-symbols-outlined text-on-surface-variant">arrow_forward</span>
      </button>
    </div>

    <!-- Add Expense Modal -->
    <AddExpenseModal v-if="showAddModal" @close="showAddModal = false" @added="handleAdded" />
  </div>
</template>
