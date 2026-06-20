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
    case 'buy': return { text: '买入', color: 'text-success' }
    case 'sell': return { text: '卖出', color: 'text-error' }
    case 'dividend': return { text: '分红', color: 'text-primary' }
    default: return { text: type, color: 'text-on-surface' }
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
  <div class="page-container flex flex-col min-h-screen bg-surface">
    <!-- Header -->
    <header class="fixed top-0 left-0 right-0 z-40 bg-surface px-gutter">
      <div class="max-w-[600px] mx-auto flex items-center justify-between h-14">
        <button class="flex items-center gap-1 text-on-surface" @click="goBack">
          <span class="material-symbols-outlined text-2xl">arrow_back</span>
          <span class="text-title-medium font-title-medium">交易明细</span>
        </button>
        <button class="w-9 h-9 flex items-center justify-center rounded-full hover:bg-surface-container-high transition-colors" @click="goHome">
          <span class="material-symbols-outlined text-2xl text-on-surface-variant">home</span>
        </button>
      </div>
    </header>

    <!-- Content -->
    <main class="flex-1 pt-14 pb-24 px-gutter">
      <div class="max-w-[600px] mx-auto">
        <!-- Loading -->
        <div v-if="loading" class="flex justify-center items-center py-32">
          <span class="material-symbols-outlined animate-spin text-on-surface-variant text-3xl">progress_activity</span>
        </div>

        <!-- Empty -->
        <div v-else-if="transactions.length === 0" class="flex flex-col items-center justify-center py-32 gap-md">
          <span class="material-symbols-outlined text-5xl text-on-surface-variant/40">receipt_long</span>
          <p class="text-body-medium font-body-medium text-on-surface-variant">暂无交易记录</p>
          <button class="mt-sm px-lg py-sm rounded-lg bg-primary-container text-on-primary-container text-label-large font-label-large transition-colors hover:brightness-95 active:scale-95" @click="goAddTrade">
            添加第一笔交易
          </button>
        </div>

        <!-- Transaction List -->
        <div v-else class="space-y-xs">
          <div v-for="tx in transactions" :key="tx.id"
               class="flex items-center justify-between bg-surface-container-lowest rounded-xl px-lg py-md transition-colors hover:bg-surface-container-high cursor-pointer active:scale-[0.98]"
               @click="showTxAction(tx)">
            <!-- Left: type + date -->
            <div class="flex items-center gap-md">
              <div class="w-10 h-10 rounded-full flex items-center justify-center"
                   :class="{
                     'bg-success/10': tx.type === 'buy',
                     'bg-error/10': tx.type === 'sell',
                     'bg-primary-container/40': tx.type === 'dividend'
                   }">
                <span class="material-symbols-outlined text-xl"
                      :class="{
                        'text-success': tx.type === 'buy',
                        'text-error': tx.type === 'sell',
                        'text-primary': tx.type === 'dividend'
                      }">
                  {{ tx.type === 'buy' ? 'trending_up' : tx.type === 'sell' ? 'trending_down' : 'payments' }}
                </span>
              </div>
              <div>
                <span class="text-label-large font-label-large text-on-surface" :class="txLabel(tx.type).color">
                  {{ txLabel(tx.type).text }}
                </span>
                <p class="text-body-small font-body-small text-on-surface-variant">{{ tx.date }}</p>
              </div>
            </div>

            <!-- Right: detail -->
            <div class="text-right">
              <p class="text-label-large font-label-large text-on-surface">
                <template v-if="tx.type === 'dividend'">
                  {{ formatAmount(tx.total, true) }}
                </template>
                <template v-else>
                  {{ formatQuantity(tx.quantity) }} 份
                </template>
              </p>
              <p v-if="tx.type !== 'dividend'" class="text-body-small font-body-small text-on-surface-variant">
                @ {{ formatAmount(tx.price, true) }}
                <template v-if="tx.fee"> / 手续费 {{ formatAmount(tx.fee, true) }}</template>
              </p>
            </div>
          </div>
        </div>
      </div>
    </main>

    <!-- Bottom add button -->
    <div class="fixed bottom-0 left-0 right-0 z-40 bg-gradient-to-t from-surface via-surface/95 to-transparent pt-8 pb-gutter px-gutter">
      <div class="max-w-[600px] mx-auto">
        <button class="w-full h-12 rounded-xl bg-primary-container text-on-primary-container text-label-large font-label-large transition-colors hover:brightness-95 active:scale-[0.98] flex items-center justify-center gap-sm"
                @click="goAddTrade">
          <span class="material-symbols-outlined">add</span>
          <span>添加交易</span>
        </button>
      </div>
    </div>

    <!-- === Action Sheet (edit / delete) === -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showActionSheet" class="fixed inset-0 z-[100] bg-black/40" @click="closeActionSheet"></div>
      </Transition>
      <Transition name="slide-up">
        <div v-if="showActionSheet" class="fixed bottom-0 left-0 right-0 z-[110] bg-surface rounded-t-2xl px-gutter py-lg shadow-2xl max-w-[600px] mx-auto">
          <div class="w-10 h-1 bg-on-surface-variant/20 rounded-full mx-auto mb-lg"></div>
          <button class="w-full flex items-center gap-md px-md py-lg rounded-xl hover:bg-surface-container-high transition-colors"
                  @click="openEdit">
            <span class="material-symbols-outlined text-primary">edit</span>
            <span class="text-label-large font-label-large text-on-surface">编辑</span>
          </button>
          <button class="w-full flex items-center gap-md px-md py-lg rounded-xl hover:bg-surface-container-high transition-colors"
                  @click="openDelete">
            <span class="material-symbols-outlined text-error">delete</span>
            <span class="text-label-large font-label-large text-on-surface">删除</span>
          </button>
          <button class="w-full mt-md h-12 rounded-xl bg-surface-container-high text-on-surface-variant text-label-large font-label-large transition-colors hover:bg-surface-container-highest active:scale-[0.98]"
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
             class="fixed bottom-0 left-0 right-0 z-[110] bg-surface rounded-t-2xl px-gutter py-lg shadow-2xl max-w-[600px] mx-auto"
             style="max-height: 85vh; overflow-y: auto;">
          <div class="w-10 h-1 bg-on-surface-variant/20 rounded-full mx-auto mb-lg"></div>
          <h3 class="text-title-large font-title-large text-on-surface mb-md">编辑交易</h3>

          <!-- Type selector -->
          <label class="text-label-medium font-label-medium text-on-surface-variant mb-sm block">交易类型</label>
          <div class="flex gap-sm mb-lg">
            <button v-for="t in (['buy', 'sell', 'bonus_share', 'reinvest'] as const)" :key="t"
                    type="button"
                    class="flex-1 h-10 rounded-lg text-label-large font-label-large transition-all"
                    :class="editType === t ? 'bg-primary-container text-on-primary-container shadow-sm' : 'bg-surface-container-high text-on-surface-variant'"
                    @click="editType = t">
              {{ { buy: '买入', sell: '卖出', bonus_share: '送股', reinvest: '复投' }[t] }}
            </button>
          </div>

          <!-- Date -->
          <label class="text-label-medium font-label-medium text-on-surface-variant mb-sm block">交易日期</label>
          <input v-model="editDate" type="date"
                 class="w-full h-11 rounded-xl bg-surface-container-high px-md text-on-surface text-body-large font-body-large outline-none mb-lg transition-colors focus:ring-2 focus:ring-primary" />

          <!-- Quantity + Price -->
          <div class="grid grid-cols-2 gap-md">
            <div>
              <label class="text-label-medium font-label-medium text-on-surface-variant mb-sm block">数量</label>
              <input v-model.number="editQuantity" type="number" step="any" min="0"
                     class="w-full h-11 rounded-xl bg-surface-container-high px-md text-on-surface text-body-large font-body-large outline-none mb-lg transition-colors focus:ring-2 focus:ring-primary" />
            </div>
            <div>
              <label class="text-label-medium font-label-medium text-on-surface-variant mb-sm block">单价</label>
              <input v-model.number="editPrice" type="number" step="any" min="0"
                     class="w-full h-11 rounded-xl bg-surface-container-high px-md text-on-surface text-body-large font-body-large outline-none mb-lg transition-colors focus:ring-2 focus:ring-primary" />
            </div>
          </div>

          <!-- Fee -->
          <label class="text-label-medium font-label-medium text-on-surface-variant mb-sm block">手续费</label>
          <input v-model.number="editFee" type="number" step="any" min="0"
                 class="w-full h-11 rounded-xl bg-surface-container-high px-md text-on-surface text-body-large font-body-large outline-none mb-lg transition-colors focus:ring-2 focus:ring-primary" />

          <p v-if="error" class="text-error text-body-small font-body-small mb-sm">{{ error }}</p>

          <!-- Actions -->
          <div class="flex gap-md mt-md">
            <button class="flex-1 h-12 rounded-xl bg-surface-container-high text-on-surface-variant text-label-large font-label-large transition-colors hover:bg-surface-container-highest active:scale-[0.98]"
                    @click="showEditSheet = false">
              取消
            </button>
            <button class="flex-1 h-12 rounded-xl bg-primary text-on-primary text-label-large font-label-large transition-colors hover:brightness-95 active:scale-[0.98] flex items-center justify-center gap-sm disabled:opacity-50"
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
          <div class="bg-surface rounded-2xl px-xl py-lg mx-gutter max-w-sm w-full shadow-2xl">
            <div class="flex flex-col items-center text-center">
              <div class="w-12 h-12 rounded-full bg-error/10 flex items-center justify-center mb-md">
                <span class="material-symbols-outlined text-2xl text-error">delete_forever</span>
              </div>
              <h3 class="text-title-large font-title-large text-on-surface mb-sm">确认删除</h3>
              <p class="text-body-medium font-body-medium text-on-surface-variant mb-xl">删除后数据将不可恢复，确定要继续吗？</p>
              <div class="flex gap-md w-full">
                <button class="flex-1 h-12 rounded-xl bg-surface-container-high text-on-surface-variant text-label-large font-label-large transition-colors hover:bg-surface-container-highest active:scale-[0.98]"
                        @click="showDeleteConfirm = false">
                  取消
                </button>
                <button class="flex-1 h-12 rounded-xl bg-error text-on-error text-label-large font-label-large transition-colors hover:brightness-95 active:scale-[0.98] flex items-center justify-center gap-sm"
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
