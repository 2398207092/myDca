<script setup lang="ts">
/**
 * 设置：登录邮箱、账户列表（含持仓的 CNY 合计）、刷新、同步分红事件、退出登录。
 * 不做皮肤切换（皮肤是另一件事，不在本 Sheet 范围）。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getAccounts, type AccountItem } from '@/api/account'
import { syncAllEvents } from '@/api/event'
import { getUserInfo } from '@/api/auth'
import { authState, signOut } from '@/shared/auth'
import SheetFrame from '@/shared/components/SheetFrame.vue'
import DataRow from '@/shared/components/DataRow.vue'
import SectionLabel from '@/shared/components/SectionLabel.vue'
import SeamDivider from '@/shared/components/SeamDivider.vue'
import BtnKey from '@/shared/components/BtnKey.vue'
import EmptyState from '@/shared/components/EmptyState.vue'
import ErrorState from '@/shared/components/ErrorState.vue'
import SkeletonRows from '@/shared/components/SkeletonRows.vue'
import { useAsyncData } from '@/shared/useAsync'
import { closeSheet, refreshData, toast } from '@/shared/ui'
import { fmtMoney } from '@/shared/money'
import { ACCOUNT_PURPOSE_LABEL, labelOf } from '@/shared/dict'

type Step = 'main' | 'logout'

const router = useRouter()

const asyncData = useAsyncData<AccountItem[]>(() => getAccounts())
const accounts = computed(() => asyncData.data.value ?? [])
const email = computed(() => authState.user?.email ?? '—')

const step = ref<Step>('main')
const syncing = ref(false)
const syncError = ref('')
const loggingOut = ref(false)

onMounted(async () => {
  // 进设置时兜底拉一次用户信息（authState 之外可留空容错）
  if (!authState.user) {
    try {
      authState.user = await getUserInfo()
    } catch {
      /* 留空容错：邮箱显示 — */
    }
  }
})

function accountMeta(account: AccountItem): string {
  const purpose = labelOf(ACCOUNT_PURPOSE_LABEL, account.purpose) || '账户'
  const parts = [account.baseCurrency, purpose, `${account.assetCount} 项资产`]
  if (account.isDefault) parts.push('默认账户')
  return parts.join(' · ')
}

function refreshAll(): void {
  refreshData()
  toast('已刷新数据')
}

async function syncEvents(): Promise<void> {
  if (syncing.value) return
  syncing.value = true
  syncError.value = ''
  try {
    const result = await syncAllEvents()
    const created = Number(result?.totalCreated ?? 0)
    toast(`同步完成，本次新增 ${created} 条分红事件`)
    refreshData()
  } catch (e) {
    syncError.value = e instanceof Error ? e.message : '同步分红事件失败'
    toast(syncError.value, 'error')
  } finally {
    syncing.value = false
  }
}

async function logout(): Promise<void> {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await signOut()
    closeSheet()
    await router.replace({ name: 'login' })
  } catch (e) {
    toast(e instanceof Error ? e.message : '退出登录失败', 'error')
    loggingOut.value = false
  }
}
</script>

<template>
  <SheetFrame title="账户与设置" subtitle="账户、数据同步与退出登录" @close="closeSheet">
    <template v-if="step === 'main'">
      <SectionLabel text="账号" />
      <DataRow label="登录邮箱" :value="email" :meta="authState.user ? '' : '未获取到登录信息'" />

      <SeamDivider />

      <SectionLabel text="账户" />
      <SkeletonRows v-if="asyncData.loading.value && !asyncData.data.value" :rows="2" />
      <ErrorState
        v-else-if="asyncData.error.value && !asyncData.data.value"
        :message="asyncData.error.value"
        @retry="asyncData.load()"
      />
      <template v-else>
        <DataRow
          v-for="account in accounts"
          :key="account.id"
          :label="account.name"
          :meta="accountMeta(account)"
          :value="fmtMoney(account.totalValueCny)"
        />
        <EmptyState
          v-if="!accounts.length"
          compact
          title="还没有账户"
          description="账户合计会显示在这里；账户含持仓市值与现金。"
        />
      </template>
      <p class="note micro">账户合计含持仓市值与现金；USDT 账户按 CNY 折算后计入合计。</p>

      <SeamDivider />

      <SectionLabel text="数据" />
      <div class="actions">
        <BtnKey variant="secondary" block @click="refreshAll">刷新数据</BtnKey>
        <BtnKey variant="secondary" block :disabled="syncing" @click="syncEvents">
          {{ syncing ? '同步中…' : '同步分红事件' }}
        </BtnKey>
      </div>
      <p class="note micro">刷新会重新拉取各面数据；同步会把基金分红记录同步为分红事件，可能新增待确认事件。</p>
      <p v-if="syncError" class="sync-error" role="alert">⚠ {{ syncError }}</p>

      <SeamDivider />

      <SectionLabel text="登录" />
      <div class="actions">
        <BtnKey variant="danger" block @click="step = 'logout'">退出登录</BtnKey>
      </div>
      <p class="note micro">退出后需要重新登录才能查看资产；本机数据不会被删除。</p>
    </template>

    <template v-else>
      <p class="confirm-lead">退出登录？</p>
      <DataRow label="当前账号" :value="email" />
      <p class="warn-line micro">⚠ 退出后需要重新登录才能查看资产；本机缓存与数据不会被删除。</p>
      <div class="actions">
        <BtnKey variant="secondary" block :disabled="loggingOut" @click="step = 'main'">取消</BtnKey>
        <BtnKey variant="danger" block :disabled="loggingOut" @click="logout">
          {{ loggingOut ? '退出中…' : '确认退出登录' }}
        </BtnKey>
      </div>
    </template>
  </SheetFrame>
</template>

<style scoped>
.actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.note {
  margin-top: var(--space-3);
  line-height: 1.6;
}
.sync-error {
  margin-top: var(--space-2);
  font-size: var(--text-label);
  color: var(--danger);
  line-height: 1.5;
}
.confirm-lead {
  font-size: var(--text-heading);
  font-weight: 600;
  padding: var(--space-2) 0 var(--space-3);
}
.warn-line {
  margin-top: var(--space-3);
  color: var(--warn);
  line-height: 1.6;
}
</style>
