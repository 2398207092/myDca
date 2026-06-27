<script setup lang="ts">
import { ref, computed, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listTransactions, updateTransaction, deleteTransaction, type TransactionItem, type UpdateTransactionReq } from '@/api/transaction'

const route = useRoute()
const router = useRouter()
const holdingId = computed(() => route.params.id as string || route.query.holdingId as string)

const loading = ref(true)
const transactions = ref<TransactionItem[]>([])

onActivated(async () => {
  loading.value = true
  try {
    transactions.value = await listTransactions(holdingId.value)
  } catch (e) {
    console.error('加载交易明细失败', e)
  } finally {
    loading.value = false
  }
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
    transactions.value = await listTransactions(holdingId.value)
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
    transactions.value = await listTransactions(holdingId.value)
  } catch (e: any) {
    console.error('删除失败:', e)
    showDeleteConfirm.value = false
  }
}

function formatAmount(n: number, withSign = false): string {
  const s = n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  return withSign ? `¥ ${s}` : s
}

function formatQuantity(n: number): string {
  if (n === 0) return '—'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 })
}

function txLabel(type: string): { text: string; color: string } {
  switch (type) {
    case 'buy': return { text: '买入', color: 'text-brand' }
    case 'sell': return { text: '卖出', color: 'text-error' }
    case 'dividend': return { text: '分红', color: 'text-brand' }
    default: return { text: type, color: 'text-text-primary' }
  }
}

function goBack() {
  router.back()
}

function goHome() {
  router.push({ name: 'home' })
}

function goAddTrade() {
  router.push({ name: 'trade-add', query: { holdingId: holdingId.value } })
}
</script>

<template>
  <div class="min-h-screen bg-page-bg flex flex-col">
    <!-- Header — 统一 -->
    <header class="flex items-center justify-between px-gutter h-14 sticky top-0 z-50 bg-card-bg border-b border-border-light/40">
      <button @click="goBack" class="w-10 h-10 flex items-center justify-center -ml-2 active:opacity-80">
        <span class="material-symbols-outlined text-text-secondary">arrow_back</span>
      </button>
      <div class="flex-1 text-center">
        <h1 class="font-body text-md font-medium text-text-primary">交易明细</h1>
      </div>
      <button @click="goHome" class="w-10 h-10 flex items-center justify-center active:opacity-80 transition-opacity">
        <span class="material-symbols-outlined text-text-secondary">home</span>
      </button>
    </header>

    <!-- Content -->
    <main class="flex-1 px-gutter pb-24 space-y-md">
      <!-- Loading -->
      <div v-if="loading" class="flex justify-center items-center py-32">
        <span class="material-symbols-outlined animate-spin text-text-tertiary text-3xl">progress_activity</span>
      </div>

      <!-- Empty -->
      <div v-else-if="transactions.length === 0" class="flex flex-col items-center justify-center py-32 gap-md">
        <span class="text-5xl block text-text-tertiary/40">📋</span>
        <p class="font-body text-sm text-text-secondary">暂无交易记录</p>
        <button class="mt-sm px-lg py-sm rounded-lg bg-brand-light text-brand font-body text-sm font-medium transition-colors active:scale-95" @click="goAddTrade">
          添加第一笔交易
        </button>
      </div>

      <!-- Transaction List -->
      <div v-else class="space-y-xs">
        <div v-for="tx in transactions" :key="tx.id"
             class="flex items-center justify-between bg-card-bg rounded-xl p-lg card-shadow border border-border-light/40 transition-colors cursor-pointer active:scale-[0.98]"
             @click="showTxAction(tx)">
          <!-- Left: type + date -->
          <div class="flex items-center gap-md">
            <div class="w-10 h-10 rounded-full flex items-center justify-center"
                 :class="tx.type === 'buy' ? 'bg-brand-light' : tx.type === 'sell' ? 'bg-error/10' : 'bg-brand-light'">
              <span class="material-symbols-outlined text-xl"
                    :class="tx.type === 'buy' ? 'text-brand' : tx.type === 'sell' ? 'text-error' : 'text-brand'">
                {{ tx.type === 'buy' ? 'trending_up' : tx.type === 'sell' ? 'trending_down' : 'payments' }}
              </span>
            </div>
            <div>
              <span class="font-body text-sm font-medium text-text-primary" :class="txLabel(tx.type).color">
                {{ txLabel(tx.type).text }}
              </span>
              <p class="font-body text-xs text-text-tertiary">{{ tx.date }}</p>
            </div>
          </div>

          <!-- Right: detail -->
          <div class="text-right">
            <p class="font-body text-sm font-medium text-text-primary">
              <template v-if="tx.type === 'dividend'">
                {{ formatAmount(tx.total, true) }}
              </template>
              <template v-else>
                {{ formatQuantity(tx.quantity) }} 份
              </template>
            </p>
            <p v-if="tx.type !== 'dividend'" class="font-body text-xs text-text-tertiary">
              @ {{ formatAmount(tx.price, true) }}
              <template v-if="tx.fee"> / 手续费 {{ formatAmount(tx.fee, true) }}</template>
            </p>
          </div>
        </div>
      </div>
    </main>

    <!-- Bottom add button -->
    <div class="fixed bottom-0 left-0 right-0 z-40 bg-gradient-to-t from-page-bg via-page-bg/95 to-transparent pt-8 pb-gutter px-gutter">
      <button class="w-full h-[52px] rounded-xl bg-brand text-white font-body font-medium text-md transition-colors active:scale-[0.98] flex items-center justify-center gap-sm shadow-card"
              @click="goAddTrade">
        <span class="material-symbols-outlined">add</span>
        <span>添加交易</span>
      </button>
    </div>

    <!-- === Action Sheet (edit / delete) === -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showActionSheet" class="fixed inset-0 z-[100] bg-black/40" @click="closeActionSheet"></div>
      </Transition>
      <Transition name="slide-up">
        <div v-if="showActionSheet" class="fixed bottom-0 left-0 right-0 z-[110] bg-card-bg rounded-t-2xl px-gutter py-lg shadow-overlay">
          <div class="w-10 h-1 bg-border-light rounded-full mx-auto mb-lg"></div>
          <button class="w-full flex items-center gap-md px-md py-lg rounded-xl hover:bg-card-alt transition-colors"
                  @click="openEdit">
            <span class="material-symbols-outlined text-brand">edit</span>
            <span class="font-body text-sm font-medium text-text-primary">编辑</span>
          </button>
          <button class="w-full flex items-center gap-md px-md py-lg rounded-xl hover:bg-card-alt transition-colors"
                  @click="openDelete">
            <span class="material-symbols-outlined text-error">delete</span>
            <span class="font-body text-sm font-medium text-text-primary">删除</span>
          </button>
          <button class="w-full mt-md h-12 rounded-xl bg-card-alt text-text-secondary font-body font-medium text-md transition-colors active:scale-[0.98]"
                  @click="closeActionSheet">
            取消
          </button>
        </div>
      </Transition>
    </Teleport>

    <!-- === Edit Bottom Sheet === -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showEditSheet" class="fixed inset-0 z-[100] bg-black/40" @click="showEditSheet = false"></div>
      </Transition>
      <Transition name="slide-up">
        <div v-if="showEditSheet"
             class="fixed bottom-0 left-0 right-0 z-[110] bg-card-bg rounded-t-2xl px-gutter py-lg shadow-overlay"
             style="max-height: 85vh; overflow-y: auto;">
          <div class="w-10 h-1 bg-border-light rounded-full mx-auto mb-lg"></div>
          <h3 class="font-body text-md font-medium text-text-primary mb-md">编辑交易</h3>

          <!-- Type selector -->
          <label class="font-body text-xs text-text-tertiary mb-sm block">交易类型</label>
          <div class="flex gap-sm mb-lg">
            <button v-for="t in (['buy', 'sell', 'bonus_share', 'reinvest'] as const)" :key="t"
                    type="button"
                    class="flex-1 h-10 rounded-lg font-body font-medium text-sm transition-all"
                    :class="editType === t ? 'bg-brand-light text-brand shadow-sm' : 'bg-card-alt text-text-secondary'"
                    @click="editType = t">
              {{ { buy: '买入', sell: '卖出', bonus_share: '送股', reinvest: '复投' }[t] }}
            </button>
          </div>

          <!-- Date -->
          <label class="font-body text-xs text-text-tertiary mb-sm block">交易日期</label>
          <input v-model="editDate" type="date"
                 class="w-full h-11 rounded-xl bg-card-alt px-md text-text-primary font-body text-sm outline-none mb-lg transition-colors focus:ring-2 focus:ring-brand" />

          <!-- Quantity + Price -->
          <div class="grid grid-cols-2 gap-md">
            <div>
              <label class="font-body text-xs text-text-tertiary mb-sm block">数量</label>
              <input v-model.number="editQuantity" type="number" step="any" min="0"
                     class="w-full h-11 rounded-xl bg-card-alt px-md text-text-primary font-body text-sm outline-none mb-lg transition-colors focus:ring-2 focus:ring-brand" />
            </div>
            <div>
              <label class="font-body text-xs text-text-tertiary mb-sm block">单价</label>
              <input v-model.number="editPrice" type="number" step="any" min="0"
                     class="w-full h-11 rounded-xl bg-card-alt px-md text-text-primary font-body text-sm outline-none mb-lg transition-colors focus:ring-2 focus:ring-brand" />
            </div>
          </div>

          <!-- Fee -->
          <label class="font-body text-xs text-text-tertiary mb-sm block">手续费</label>
          <input v-model.number="editFee" type="number" step="any" min="0"
                 class="w-full h-11 rounded-xl bg-card-alt px-md text-text-primary font-body text-sm outline-none mb-lg transition-colors focus:ring-2 focus:ring-brand" />

          <p v-if="error" class="text-error font-body text-xs mb-sm">{{ error }}</p>

          <!-- Actions -->
          <div class="flex gap-md mt-md">
            <button class="flex-1 h-12 rounded-xl bg-card-alt text-text-secondary font-body font-medium text-md transition-colors active:scale-[0.98]"
                    @click="showEditSheet = false">
              取消
            </button>
            <button class="flex-1 h-12 rounded-xl bg-brand text-white font-body font-medium text-md transition-colors active:scale-[0.98] flex items-center justify-center gap-sm disabled:opacity-50"
                    :disabled="editSaving"
                    @click="doEdit">
              <span v-if="editSaving" class="material-symbols-outlined animate-spin text-lg">progress_activity</span>
              <span>{{ editSaving ? '保存中...' : '保存' }}</span>
            </button>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- === Delete Confirm Dialog === -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showDeleteConfirm" class="fixed inset-0 z-[100] bg-black/40" @click="showDeleteConfirm = false"></div>
      </Transition>
      <Transition name="scale-up">
        <div v-if="showDeleteConfirm"
             class="fixed inset-0 z-[110] flex items-center justify-center"
             @click.self="showDeleteConfirm = false">
          <div class="bg-card-bg rounded-2xl px-xl py-lg mx-gutter max-w-sm w-full shadow-overlay">
            <div class="flex flex-col items-center text-center">
              <div class="w-12 h-12 rounded-full bg-error/10 flex items-center justify-center mb-md">
                <span class="material-symbols-outlined text-2xl text-error">delete_forever</span>
              </div>
              <h3 class="font-body text-md font-medium text-text-primary mb-sm">确认删除</h3>
              <p class="font-body text-sm text-text-secondary mb-xl">删除后数据将不可恢复，确定要继续吗？</p>
              <div class="flex gap-md w-full">
                <button class="flex-1 h-12 rounded-xl bg-card-alt text-text-secondary font-body font-medium text-md transition-colors active:scale-[0.98]"
                        @click="showDeleteConfirm = false">
                  取消
                </button>
                <button class="flex-1 h-12 rounded-xl bg-error text-white font-body font-medium text-md transition-colors active:scale-[0.98] flex items-center justify-center gap-sm"
                        @click="doDeleteTx">
                  <span class="material-symbols-outlined">delete</span>
                  <span>删除</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.fade-enter-active { transition: opacity 0.2s ease; }
.fade-leave-active { transition: opacity 0.15s ease; }
.fade-enter-from,
.fade-leave-to { opacity: 0; }

.slide-up-enter-active { transition: transform 0.3s cubic-bezier(0.32, 0.72, 0, 1); }
.slide-up-leave-active { transition: transform 0.2s ease; }
.slide-up-enter-from { transform: translateY(100%); }
.slide-up-leave-to { transform: translateY(100%); }

.scale-up-enter-active { transition: transform 0.2s ease, opacity 0.2s ease; }
.scale-up-leave-active { transition: transform 0.15s ease, opacity 0.15s ease; }
.scale-up-enter-from { transform: scale(0.9); opacity: 0; }
.scale-up-leave-to { transform: scale(0.9); opacity: 0; }
</style>
