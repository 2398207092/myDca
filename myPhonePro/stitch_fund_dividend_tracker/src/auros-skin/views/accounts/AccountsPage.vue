<script setup lang="ts">
/**
 * 账户管理 · 卡片版（2026-11-11）
 * 卡 01 ACCOUNTS 账户总览：AurosStat（折合总资产）+ 1 行账户构成
 * 卡 02 ACCOUNTS LIST 账户明细：每个账户一个 AurosGroup，组内资产行 ≤5 行
 * 卡 03 操作（无标题块）：AurosCard 次级面（soft），收纳管理 / 添加入口
 * 业务逻辑与接口不变；数字统一走 utils/format（¥ 千分位两位小数）。
 */
import { ref, computed, onMounted, onActivated } from 'vue'
import {
  getAccounts, createAccount, updateAccount, deleteAccount,
  type AccountItem, type AccountAsset,
} from '@/api/account'
import AurosNavBar from '@/auros-skin/components/auros/AurosNavBar.vue'
import AurosCard from '@/auros-skin/components/auros/AurosCard.vue'
import AurosChapter from '@/auros-skin/components/auros/AurosChapter.vue'
import AurosStat from '@/auros-skin/components/auros/AurosStat.vue'
import AurosGroup from '@/auros-skin/components/auros/AurosGroup.vue'
import AurosRow from '@/auros-skin/components/auros/AurosRow.vue'
import AurosDisclosure from '@/auros-skin/components/auros/AurosDisclosure.vue'
import AurosSheet from '@/auros-skin/components/auros/AurosSheet.vue'
import AurosField from '@/auros-skin/components/auros/AurosField.vue'
import AurosButton from '@/auros-skin/components/auros/AurosButton.vue'
import AurosState from '@/auros-skin/components/auros/AurosState.vue'
import ToastNotification from '@/auros-skin/components/shared/ToastNotification.vue'
import { formatMoney, formatQty } from '@/auros-skin/utils/format'

// Toast 通知（写操作失败必须可见，禁止静默失败）
const toastMsg = ref<string | null>(null)
const toastType = ref<'success' | 'error' | 'info'>('error')
function showToast(msg: string, type: 'success' | 'error' | 'info' = 'error') {
  toastMsg.value = msg
  toastType.value = type
}

const pageState = ref<'loading' | 'ready' | 'error'>('loading')
const accounts = ref<AccountItem[]>([])

const purposeOptions = [
  { value: 'daily', label: '日常' },
  { value: 'invest', label: '理财' },
  { value: 'exchange', label: '交易所' },
  { value: 'other', label: '其他' },
]
/** 用途联动本位币：交易所账户必须 USDT 本位币（与后端校验同一条规则） */
function setPurpose(p: string) {
  form.value.purpose = p
  if (p === 'exchange') form.value.baseCurrency = 'USDT'
}

function purposeLabel(p: string): string {
  return purposeOptions.find(o => o.value === p)?.label || '其他'
}

/** 资产类型标签：与名称重复时不显示 */
function assetLabel(a: { assetCode?: string; name: string }): string {
  const code = a.assetCode || ''
  return code && code !== a.name ? code : ''
}

/** 资产行副行：加密资产给数量（formatQty 两位小数）；其余优先资产代码，否则类型名 */
function assetSub(asset: AccountAsset): string {
  if (asset.type === 'crypto' && asset.quantity != null) {
    const qty = formatQty(asset.quantity, 2)
    const unit = asset.assetCode || asset.currency || ''
    return unit ? `${qty} ${unit}` : qty
  }
  const code = assetLabel(asset)
  if (code) return code
  if (asset.type === 'cash') return asset.name === '现金' ? '' : '现金'
  if (asset.type === 'crypto') return asset.name === '加密' ? '' : '加密'
  return asset.type === asset.name ? '' : asset.type
}

/** 资产行状态点：现金=lit，其余=dim */
function assetMark(asset: AccountAsset): 'lit' | 'dim' {
  return asset.type === 'cash' ? 'lit' : 'dim'
}

/** 有资产的账户排前面，其次按金额降序 */
const sortedAccounts = computed(() => [...accounts.value].sort((a, b) => {
  if ((a.assetCount > 0) !== (b.assetCount > 0)) return b.assetCount > 0 ? 1 : -1
  return b.totalValueCny - a.totalValueCny
}))

// === 章 01 总览读数 ===
const accountsTotal = computed(() => sortedAccounts.value.reduce((sum, a) => sum + a.totalValueCny, 0))
const assetTypeCount = computed(() =>
  new Set(accounts.value.flatMap(a => a.assets.map(x => x.type))).size
)

// === 章 02 分组（一章 ≤3 组，超出的账户折叠） ===
const visibleAccounts = computed(() => sortedAccounts.value.slice(0, 3))
const restAccounts = computed(() => sortedAccounts.value.slice(3))
const restRowCount = computed(() => restAccounts.value.reduce((n, a) => n + Math.max(1, a.assets.length), 0))

function groupMeta(a: AccountItem): string {
  return [a.isDefault ? '默认' : '', purposeLabel(a.purpose), a.baseCurrency].filter(Boolean).join(' · ')
}

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

const editingAccount = computed(() =>
  editingId.value ? accounts.value.find(a => a.id === editingId.value) ?? null : null
)

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
  } catch (e: any) {
    console.error('设置默认账户失败:', e)
    showToast(e?.message || '设置默认账户失败，请重试', 'error')
  }
}

/** 编辑弹层内的「设为默认」：走与原来列表按钮同一条逻辑 */
function handleSetDefault() {
  const a = editingAccount.value
  if (!a) return
  showSheet.value = false
  setDefault(a)
}

// ---- 管理账户（章末入口） ----
const showManage = ref(false)

/** 从管理弹层进入编辑：先收管理层，再开编辑层（AurosSheet 顺序保证 body 锁不丢） */
function manageEdit(a: AccountItem) {
  showManage.value = false
  openEdit(a)
}

// ---- 删除 ----
const deleting = ref<AccountItem | null>(null)
const deleteError = ref('')
const deletingBusy = ref(false)

const deleteSheet = computed<boolean>({
  get: () => deleting.value !== null,
  set: (v) => {
    if (!v) deleting.value = null
  },
})

function confirmDelete(a: AccountItem) {
  deleting.value = a
  deleteError.value = ''
}

/** 编辑弹层内的「删除」：先收起编辑层，再开删除确认 */
function handleDelete() {
  const a = editingAccount.value
  if (!a) return
  showSheet.value = false
  confirmDelete(a)
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
</script>

<template>
  <div class="page">
    <ToastNotification :message="toastMsg" :type="toastType" @close="toastMsg = null" />

    <AurosNavBar title="账户管理" />

    <main class="body">
      <AurosState v-if="pageState === 'loading'" state="loading" :rows="4" />

      <AurosState
        v-else-if="pageState === 'error'"
        state="error"
        text="账户列表没有取到"
        action-label="重试"
        @action="load"
      />

      <AurosState
        v-else-if="!sortedAccounts.length"
        state="empty"
        text="还没有账户。先建一个，把现金、USDT 和持仓放进去。"
        action-label="新增账户"
        @action="openCreate"
      />

      <template v-else>
        <!-- 卡 01 账户总览：主角数 + 一行构成 -->
        <AurosChapter label="ACCOUNTS" title="账户总览" :gap="false">
          <AurosStat :value="formatMoney(accountsTotal)" size="lg" pink caption="全部账户折合人民币" />
          <AurosRow
            class="overview"
            label="账户与资产"
            :value="`${sortedAccounts.length} 个账户 · ${assetTypeCount} 类资产`"
          />
        </AurosChapter>

        <!-- 卡 02 账户明细：每个账户一组 -->
        <AurosChapter label="ACCOUNTS LIST" title="账户明细" meta="按余额排序">
          <AurosGroup
            v-for="a in visibleAccounts"
            :key="a.id"
            :title="a.name"
            :count="groupMeta(a)"
            :total="formatMoney(a.totalValueCny)"
          >
            <AurosRow
              v-for="asset in a.assets.slice(0, 5)"
              :key="asset.id"
              :label="asset.name"
              :sub="assetSub(asset)"
              :value="formatMoney(asset.amount)"
              :mark="assetMark(asset)"
            />
            <AurosDisclosure v-if="a.assets.length > 5" :count="a.assets.length - 5">
              <AurosRow
                v-for="asset in a.assets.slice(5)"
                :key="asset.id"
                :label="asset.name"
                :sub="assetSub(asset)"
                :value="formatMoney(asset.amount)"
                :mark="assetMark(asset)"
              />
            </AurosDisclosure>
            <AurosRow v-if="!a.assets.length" label="暂无资产" value="—" tone="muted" />
          </AurosGroup>

          <AurosDisclosure v-if="restAccounts.length" :count="restRowCount">
            <AurosGroup
              v-for="a in restAccounts"
              :key="a.id"
              :title="a.name"
              :count="groupMeta(a)"
              :total="formatMoney(a.totalValueCny)"
            >
              <AurosRow
                v-for="asset in a.assets.slice(0, 5)"
                :key="asset.id"
                :label="asset.name"
                :sub="assetSub(asset)"
                :value="formatMoney(asset.amount)"
                :mark="assetMark(asset)"
              />
              <AurosDisclosure v-if="a.assets.length > 5" :count="a.assets.length - 5">
                <AurosRow
                  v-for="asset in a.assets.slice(5)"
                  :key="asset.id"
                  :label="asset.name"
                  :sub="assetSub(asset)"
                  :value="formatMoney(asset.amount)"
                  :mark="assetMark(asset)"
                />
              </AurosDisclosure>
              <AurosRow v-if="!a.assets.length" label="暂无资产" value="—" tone="muted" />
            </AurosGroup>
          </AurosDisclosure>
        </AurosChapter>

        <!-- 卡 03 操作：无标题块，用次级面卡片收纳（卡内不套卡） -->
        <AurosCard tone="soft">
          <AurosRow
            label="管理账户"
            sub="设为默认 / 编辑 / 删除"
            clickable
            chevron
            @click="showManage = true"
          />
          <AurosRow
            label="添加账户"
            sub="新建一个资产容器"
            clickable
            chevron
            @click="openCreate"
          />
        </AurosCard>
      </template>
    </main>

    <!-- 管理账户 -->
    <AurosSheet v-model="showManage" title="管理账户">
      <AurosRow
        v-for="a in sortedAccounts"
        :key="a.id"
        :label="a.name"
        :sub="groupMeta(a)"
        clickable
        chevron
        @click="manageEdit(a)"
      />
    </AurosSheet>

    <!-- 新增 / 编辑账户 -->
    <AurosSheet v-model="showSheet" :title="editingId ? '编辑账户' : '新增账户'" @close="formError = ''">
      <div class="form">
        <AurosField v-model="form.name" label="账户名称" placeholder="如：日常 / 理财 / 欧易" />

        <div>
          <p class="form__k">用途</p>
          <div class="chips">
            <button
              v-for="p in purposeOptions"
              :key="p.value"
              type="button"
              class="chip"
              :class="{ 'is-on': form.purpose === p.value }"
              @click="form.purpose = p.value"
            >
              {{ p.label }}
            </button>
          </div>
        </div>

        <div>
          <p class="form__k">本位币</p>
          <div class="chips">
            <button
              type="button"
              class="chip"
              :class="{ 'is-on': form.baseCurrency === 'CNY' }"
              @click="form.baseCurrency = 'CNY'"
            >
              CNY 人民币
            </button>
            <button
              type="button"
              class="chip"
              :class="{ 'is-on': form.baseCurrency === 'USDT' }"
              @click="form.baseCurrency = 'USDT'"
            >
              USDT
            </button>
          </div>
        </div>

        <p v-if="formError" class="form__err">{{ formError }}</p>

        <div v-if="editingAccount" class="form__subacts">
          <button v-if="!editingAccount.isDefault" type="button" class="act" @click="handleSetDefault">
            设为默认账户
          </button>
          <button type="button" class="act is-danger" @click="handleDelete">删除账户</button>
        </div>

        <AurosButton variant="primary" block :disabled="saving" @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </AurosButton>
      </div>
    </AurosSheet>

    <!-- 删除确认 -->
    <AurosSheet v-model="deleteSheet" title="删除账户">
      <template v-if="deleting">
        <p class="confirm">删除账户「<b>{{ deleting.name }}</b>」？账户下还有 {{ deleting.assetCount }} 项资产，需先移走才能删除。</p>
        <p v-if="deleteError" class="form__err">{{ deleteError }}</p>
        <div class="sheet__acts">
          <button type="button" class="sbtn" @click="deleting = null">取消</button>
          <button type="button" class="sbtn is-danger" :disabled="deletingBusy" @click="doDelete">
            {{ deletingBusy ? '删除中…' : '确认删除' }}
          </button>
        </div>
      </template>
    </AurosSheet>
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

/* 章 01：总览行（卡内间距，卡本身已给 18/16 内边距） */
.overview {
  margin-top: 18px;
}

/* ---------- 弹层表单 ---------- */
.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 2px 0 6px;
}
.form__k {
  margin: 0 0 8px;
  font-size: 10px;
  letter-spacing: 0.14em;
  color: var(--au-silver);
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.chip {
  height: 34px;
  padding: 0 14px;
  border: 0;
  border-radius: 6px;
  background: rgba(0, 55, 52, 0.55);
  color: rgba(187, 199, 198, 0.8);
  font-family: inherit;
  font-size: 13px;
  letter-spacing: 0.04em;
  transition: background 200ms var(--au-ease), color 200ms var(--au-ease);
}
.chip.is-on {
  background: rgba(203, 255, 252, 0.14);
  color: var(--au-mist);
}
.chip:active {
  transform: scale(0.97);
}
.form__err {
  margin: 0;
  font-size: 13px;
  letter-spacing: 0.02em;
  color: rgba(253, 233, 255, 0.9);
}
.form__subacts {
  display: flex;
  gap: 10px;
}
.act {
  flex: 1;
  height: 38px;
  border: 0;
  border-radius: 6px;
  background: rgba(237, 255, 254, 0.05);
  color: var(--au-silver);
  font-family: inherit;
  font-size: 13px;
  letter-spacing: 0.04em;
  transition: transform 160ms var(--au-ease);
}
.act.is-danger {
  color: rgba(253, 233, 255, 0.85);
}
.act:active {
  transform: scale(0.98);
}

/* ---------- 删除确认 ---------- */
.confirm {
  margin: 0 0 6px;
  font-size: 13px;
  line-height: 1.7;
  letter-spacing: 0.02em;
  color: var(--au-silver);
}
.confirm b {
  font-weight: 500;
  color: var(--au-mist);
}
.sheet__acts {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}
.sbtn {
  flex: 1;
  height: 44px;
  border: 0;
  border-radius: 6px;
  background: rgba(0, 55, 52, 0.9);
  color: var(--au-mist);
  font-family: inherit;
  font-size: 13px;
  letter-spacing: 0.08em;
  transition: transform 160ms var(--au-ease);
}
.sbtn.is-danger {
  color: rgba(253, 233, 255, 0.9);
}
.sbtn:disabled {
  opacity: 0.5;
}
.sbtn:active:not(:disabled) {
  transform: scale(0.98);
}

@media (prefers-reduced-motion: reduce) {
  .chip,
  .act,
  .sbtn {
    transition: none;
  }
}
</style>
