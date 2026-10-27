<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import {
  getAccounts, createAccount, updateAccount, deleteAccount,
  type AccountItem,
} from '@/api/account'
import PageStateComp from '@/components/shared/PageState.vue'

const router = useRouter()

const pageState = ref<'loading' | 'ready' | 'error'>('loading')
const accounts = ref<AccountItem[]>([])

const purposeOptions = [
  { value: 'daily', label: '日常', icon: 'payments', hint: '日常开销' },
  { value: 'invest', label: '理财', icon: 'savings', hint: '基金买卖' },
  { value: 'exchange', label: '交易所', icon: 'currency_bitcoin', hint: '加密资产' },
  { value: 'other', label: '其他', icon: 'account_balance_wallet', hint: '' },
]
function purposeLabel(p: string): string {
  return purposeOptions.find(o => o.value === p)?.label || '其他'
}
function purposeIcon(p: string): string {
  return purposeOptions.find(o => o.value === p)?.icon || 'account_balance_wallet'
}

function formatMoney(v: number | null | undefined): string {
  if (v == null) return '¥0'
  return '¥' + v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 资产类型标签：与名称重复时不显示 */
function assetLabel(a: { assetCode?: string; name: string }): string {
  const code = a.assetCode || ''
  return code && code !== a.name ? code : ''
}

/** 有资产的账户排前面，其次按金额降序 */
const sortedAccounts = computed(() => [...accounts.value].sort((a, b) => {
  if ((a.assetCount > 0) !== (b.assetCount > 0)) return b.assetCount > 0 ? 1 : -1
  return b.totalValueCny - a.totalValueCny
}))

async function load() {
  pageState.value = 'loading'
  try {
    accounts.value = await getAccounts()
    pageState.value = 'ready'
  } catch (e) {
    console.error('加载账户失败:', e)
    pageState.value = 'error'
  }
}

onMounted(load)
onActivated(load)

// ---- 新增 / 编辑 ----
const showSheet = ref(false)
const editingId = ref<string | null>(null)
const form = ref({ name: '', purpose: 'other', baseCurrency: 'CNY', isDefault: false })
const saving = ref(false)
const formError = ref('')

function openCreate() {
  editingId.value = null
  form.value = {
    name: '',
    purpose: 'other',
    baseCurrency: 'CNY',
    isDefault: accounts.value.length === 0,
  }
  formError.value = ''
  showSheet.value = true
}

function openEdit(a: AccountItem) {
  editingId.value = a.id
  form.value = {
    name: a.name,
    purpose: a.purpose,
    baseCurrency: a.baseCurrency,
    isDefault: a.isDefault,
  }
  formError.value = ''
  showSheet.value = true
}

async function save() {
  const name = form.value.name.trim()
  if (!name) {
    formError.value = '请填写账户名称'
    return
  }
  saving.value = true
  formError.value = ''
  try {
    if (editingId.value) {
      await updateAccount(editingId.value, {
        name,
        purpose: form.value.purpose,
        baseCurrency: form.value.baseCurrency,
        isDefault: form.value.isDefault,
      })
    } else {
      const created = await createAccount({
        name,
        purpose: form.value.purpose,
        baseCurrency: form.value.baseCurrency,
      })
      if (form.value.isDefault) {
        await updateAccount(created.id, { isDefault: true })
      }
    }
    showSheet.value = false
    await load()
  } catch (e: any) {
    formError.value = e.message || '保存失败'
  } finally {
    saving.value = false
  }
}

// ---- 设为默认 ----
async function setDefault(a: AccountItem) {
  if (a.isDefault) return
  try {
    await updateAccount(a.id, { isDefault: true })
    await load()
  } catch (e) {
    console.error('设置默认账户失败:', e)
  }
}

// ---- 删除 ----
const deleting = ref<AccountItem | null>(null)
const deleteError = ref('')
const deletingBusy = ref(false)

function confirmDelete(a: AccountItem) {
  deleting.value = a
  deleteError.value = ''
}

async function doDelete() {
  if (!deleting.value) return
  deletingBusy.value = true
  try {
    await deleteAccount(deleting.value.id)
    deleting.value = null
    await load()
  } catch (e: any) {
    deleteError.value = e.message || '删除失败'
  } finally {
    deletingBusy.value = false
  }
}

function goBack() { router.back() }
</script>

<template>
  <div class="min-h-screen bg-page-bg">
    <header class="flex items-center justify-between px-gutter h-14 sticky top-0 z-50 bg-card-bg border-b border-border-light/40">
      <button class="w-10 h-10 flex items-center justify-center -ml-2 active:opacity-80" @click="goBack">
        <span class="material-symbols-outlined text-text-secondary">arrow_back</span>
      </button>
      <h1 class="font-body text-md font-medium text-text-primary">账户管理</h1>
      <button class="w-10 h-10 flex items-center justify-center active:opacity-80" @click="openCreate">
        <span class="material-symbols-outlined text-text-secondary">add</span>
      </button>
    </header>

    <main class="px-gutter pb-32 pt-md">
      <p class="font-body text-xs text-text-tertiary mb-3">
        账户是资产的容器。现金、USDT、BTC 都可以放进同一个账户，汇总时统一折算成人民币。
      </p>

      <PageStateComp v-if="pageState !== 'ready'" :state="pageState" @retry="load" />

      <div v-else class="space-y-sm">
        <div v-for="a in sortedAccounts" :key="a.id"
             class="bg-card-bg rounded-lg p-lg card-shadow border border-border-light/40">
          <div class="flex items-start justify-between gap-3">
            <div class="flex items-center gap-2 min-w-0">
              <span class="material-symbols-outlined text-[20px] text-text-tertiary shrink-0">{{ purposeIcon(a.purpose) }}</span>
              <div class="min-w-0">
                <div class="flex items-center gap-1.5">
                  <h3 class="font-body text-sm font-medium text-text-primary truncate">{{ a.name }}</h3>
                  <span v-if="a.isDefault"
                        class="font-body text-[9px] text-brand bg-brand-light px-1.5 py-0.5 rounded shrink-0">默认</span>
                </div>
                <p class="font-body text-[11px] text-text-tertiary mt-0.5">
                  {{ purposeLabel(a.purpose) }} · 本位币 {{ a.baseCurrency }} · {{ a.assetCount }} 项资产
                </p>
              </div>
            </div>
            <div class="text-right shrink-0">
              <p class="font-body text-sm font-medium text-text-primary">{{ formatMoney(a.totalValueCny) }}</p>
              <p class="font-body text-[10px] text-text-tertiary mt-0.5">折合人民币</p>
            </div>
          </div>

          <div v-if="a.assets.length > 0" class="mt-md pt-md border-t border-border-light/40 space-y-1">
            <div v-for="asset in a.assets" :key="asset.id" class="flex items-center justify-between py-1">
              <div class="flex items-center gap-2 min-w-0">
                <span class="w-1.5 h-1.5 rounded-full shrink-0"
                      :class="asset.type === 'cash' ? 'bg-pos' : 'bg-gold'"></span>
                <span class="font-body text-xs text-text-secondary truncate">{{ asset.name }}</span>
                <span v-if="assetLabel(asset)" class="font-body text-[10px] text-text-tertiary/60 shrink-0">
                  {{ assetLabel(asset) }}
                </span>
              </div>
              <span class="font-body text-xs text-text-tertiary shrink-0">{{ formatMoney(asset.amount) }}</span>
            </div>
          </div>

          <div v-else class="mt-md pt-md border-t border-border-light/40">
            <p class="font-body text-[11px] text-text-tertiary/60">暂无资产</p>
          </div>

          <div class="mt-md pt-md border-t border-border-light/40 flex items-center justify-end gap-2">
            <button v-if="!a.isDefault"
                    class="px-3 py-1.5 rounded-lg bg-card-alt font-body text-[11px] text-text-secondary active:scale-[0.98] transition-transform"
                    @click="setDefault(a)">设为默认</button>
            <button class="px-3 py-1.5 rounded-lg bg-card-alt font-body text-[11px] text-text-secondary active:scale-[0.98] transition-transform"
                    @click="openEdit(a)">编辑</button>
            <button class="px-3 py-1.5 rounded-lg bg-card-alt font-body text-[11px] text-error active:scale-[0.98] transition-transform"
                    @click="confirmDelete(a)">删除</button>
          </div>
        </div>

        <button class="w-full py-3 rounded-lg border border-dashed border-border-light font-body text-xs text-text-tertiary flex items-center justify-center gap-1 active:opacity-70"
                @click="openCreate">
          <span class="material-symbols-outlined text-[16px]">add</span>
          新增账户
        </button>
      </div>
    </main>

    <!-- 新增 / 编辑弹窗 -->
    <div v-if="showSheet" class="fixed inset-0 z-[60] flex items-end justify-center">
      <div class="absolute inset-0 bg-black/40" @click="showSheet = false"></div>
      <div class="relative w-full max-w-[480px] bg-card-bg rounded-t-2xl px-gutter pt-lg pb-8">
        <div class="flex items-center justify-between mb-lg">
          <h2 class="font-body text-md font-medium text-text-primary">{{ editingId ? '编辑账户' : '新增账户' }}</h2>
          <button class="w-8 h-8 flex items-center justify-center" @click="showSheet = false">
            <span class="material-symbols-outlined text-text-tertiary">close</span>
          </button>
        </div>

        <label class="block font-body text-xs text-text-secondary mb-1">账户名称</label>
        <input v-model="form.name"
               placeholder="如：日常 / 理财 / 欧易"
               class="w-full px-3 py-2.5 rounded-lg bg-card-alt border border-border-light/40 font-body text-sm text-text-primary outline-none focus:border-brand" />

        <label class="block font-body text-xs text-text-secondary mt-md mb-1">用途</label>
        <div class="grid grid-cols-2 gap-2">
          <button v-for="p in purposeOptions" :key="p.value"
                  class="flex items-center gap-2 px-3 py-2.5 rounded-lg border font-body text-xs transition-colors"
                  :class="form.purpose === p.value ? 'border-brand bg-brand-light/40 text-brand' : 'border-border-light/40 bg-card-alt text-text-secondary'"
                  @click="form.purpose = p.value">
            <span class="material-symbols-outlined text-[16px]">{{ p.icon }}</span>
            {{ p.label }}
          </button>
        </div>

        <label class="block font-body text-xs text-text-secondary mt-md mb-1">本位币</label>
        <div class="grid grid-cols-2 gap-2">
          <button class="px-3 py-2.5 rounded-lg border font-body text-xs transition-colors"
                  :class="form.baseCurrency === 'CNY' ? 'border-brand bg-brand-light/40 text-brand' : 'border-border-light/40 bg-card-alt text-text-secondary'"
                  @click="form.baseCurrency = 'CNY'">CNY 人民币</button>
          <button class="px-3 py-2.5 rounded-lg border font-body text-xs transition-colors"
                  :class="form.baseCurrency === 'USDT' ? 'border-brand bg-brand-light/40 text-brand' : 'border-border-light/40 bg-card-alt text-text-secondary'"
                  @click="form.baseCurrency = 'USDT'">USDT</button>
        </div>

        <div v-if="editingId" class="flex items-center justify-between mt-md py-2">
          <span class="font-body text-xs text-text-secondary">设为默认账户</span>
          <button class="w-11 h-6 rounded-full transition-colors relative"
                  :class="form.isDefault ? 'bg-brand' : 'bg-card-alt'"
                  @click="form.isDefault = !form.isDefault">
            <span class="absolute top-0.5 w-5 h-5 rounded-full bg-white transition-all"
                  :class="form.isDefault ? 'left-[22px]' : 'left-0.5'"></span>
          </button>
        </div>

        <p v-if="formError" class="font-body text-xs text-error mt-md">{{ formError }}</p>

        <button class="w-full mt-lg py-3 rounded-lg bg-brand text-white font-body text-sm font-medium active:scale-[0.99] transition-transform disabled:opacity-50"
                :disabled="saving"
                @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </div>
    </div>

    <!-- 删除确认 -->
    <div v-if="deleting" class="fixed inset-0 z-[70] flex items-center justify-center px-gutter">
      <div class="absolute inset-0 bg-black/40" @click="deleting = null"></div>
      <div class="relative w-full max-w-[360px] bg-card-bg rounded-xl p-lg">
        <h3 class="font-body text-sm font-medium text-text-primary">删除账户「{{ deleting.name }}」？</h3>
        <p class="font-body text-xs text-text-tertiary mt-2">
          账户下还有 {{ deleting.assetCount }} 项资产，需先移走才能删除。
        </p>
        <p v-if="deleteError" class="font-body text-xs text-error mt-2">{{ deleteError }}</p>
        <div class="flex gap-2 mt-lg">
          <button class="flex-1 py-2.5 rounded-lg bg-card-alt font-body text-xs text-text-secondary"
                  @click="deleting = null">取消</button>
          <button class="flex-1 py-2.5 rounded-lg bg-error text-white font-body text-xs font-medium disabled:opacity-50"
                  :disabled="deletingBusy"
                  @click="doDelete">{{ deletingBusy ? '删除中…' : '确认删除' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>
