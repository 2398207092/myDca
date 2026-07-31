<script setup lang="ts">
import { ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import type { PageState } from '@/types'
import { getProfile, getSettings, updateProfile, type UserProfile } from '@/api/user'
import { listExchangeRates, refreshExchangeRates } from '@/api/exchangeRate'
import { getToken, clearToken } from '@/api/request'
import { getUserInfo, setPassword as apiSetPassword, logout as apiLogout } from '@/api/auth'
import type { UserSettings } from '@/api/user'
import type { ExchangeRateItem } from '@/api/exchangeRate'
import type { UserInfoResp } from '@/types/api'
import PageStateView from '@/components/shared/PageState.vue'
import { getAuditDates, getAuditContent } from '@/api/audit'
import type { AuditContent } from '@/api/audit'
import ToastNotification from '@/components/shared/ToastNotification.vue'
import AppHeader from '@/components/shared/AppHeader.vue'

const router = useRouter()
const pageState = ref<PageState>('loading')

// ── User profile ──
const profile = ref<UserProfile | null>(null)
const settings = ref<UserSettings | null>(null)
const exchangeRates = ref<ExchangeRateItem[]>([])
const isRefreshing = ref(false)
const avatarError = ref(false)

// ── Auth state ──
const userInfo = ref<UserInfoResp | null>(null)

// ── Toast ──
const toastMsg = ref<string | null>(null)
const toastType = ref<'success' | 'error' | 'info'>('success')

function showToast(msg: string, type: 'success' | 'error' | 'info' = 'success') {
  toastMsg.value = msg
  toastType.value = type
}

// ── Profile Edit Modal ──
const showProfileModal = ref(false)
const editName = ref('')
const editAvatar = ref('')
const savingProfile = ref(false)

const AVATAR_OPTIONS = [
  { seed: 'fund-tracker', bg: 'ff7a45' },
  { seed: 'happy-panda', bg: '36cfc9' },
  { seed: 'sunny-bear', bg: 'ffc53d' },
  { seed: 'lucky-fox', bg: 'b37feb' },
  { seed: 'cute-cat', bg: 'ff85c0' },
  { seed: 'wise-owl', bg: '5cdbd3' },
]

function avatarUrl(seed: string, bg: string) {
  return `https://api.dicebear.com/7.x/thumbs/svg?seed=${seed}&backgroundColor=${bg}`
}

function openProfileModal() {
  if (!profile.value) return
  editName.value = profile.value.name
  editAvatar.value = profile.value.avatar
  showProfileModal.value = true
}

async function handleSaveProfile() {
  if (!editName.value.trim()) {
    showToast('名字不能为空', 'error')
    return
  }
  savingProfile.value = true
  try {
    const updated = await updateProfile({
      name: editName.value.trim(),
      avatar: editAvatar.value,
    })
    profile.value = updated
    showProfileModal.value = false
    showToast('个人信息已更新', 'success')
  } catch (e: any) {
    showToast(e.message || '保存失败', 'error')
  } finally {
    savingProfile.value = false
  }
}

// ── Set password ──
const showSetPwdModal = ref(false)
const newPassword = ref('')
const confirmNewPassword = ref('')
const settingPassword = ref(false)
const pwdError = ref('')

async function handleSetPassword() {
  if (newPassword.value.length < 6 || newPassword.value.length > 20) {
    pwdError.value = '密码长度需为 6-20 位'
    return
  }
  if (newPassword.value !== confirmNewPassword.value) {
    pwdError.value = '两次密码输入不一致'
    return
  }
  settingPassword.value = true
  pwdError.value = ''
  try {
    await apiSetPassword({ email: userInfo.value!.email, password: newPassword.value })
    userInfo.value!.hasPassword = true
    showSetPwdModal.value = false
    newPassword.value = ''
    confirmNewPassword.value = ''
    showToast('密码设置成功', 'success')
  } catch (e: any) {
    pwdError.value = e.message || '设置密码失败'
  } finally {
    settingPassword.value = false
  }
}

// ── Logout ──
const showLogoutConfirm = ref(false)

async function handleLogout() {
  try {
    await apiLogout()
  } catch { /* ignore */ }
  clearToken()
  router.push({ name: 'login' })
}

// ── Exchange rates ──
async function handleRefresh() {
  isRefreshing.value = true
  try {
    const resp = await refreshExchangeRates()
    exchangeRates.value = resp.rates
  } catch (e) {
    console.error('刷新汇率失败:', e)
  } finally {
    setTimeout(() => { isRefreshing.value = false }, 1000)
  }
}

// ── Modals ──
const showDataInfoModal = ref(false)
const isBackingUp = ref(false)

// ── Audit (merged from Toolbox) ──
const showDatePicker = ref(false)
const availableDates = ref<string[]>([])
const loadingDates = ref(false)
const showAuditContent = ref(false)
const auditContent = ref<AuditContent | null>(null)

async function openDatePicker() {
  showDatePicker.value = true
  if (availableDates.value.length === 0) {
    loadingDates.value = true
    try {
      availableDates.value = await getAuditDates()
    } catch (e) {
      console.error('获取审计日期列表失败:', e)
    } finally {
      loadingDates.value = false
    }
  }
}

async function selectDate(date: string) {
  showDatePicker.value = false
  try {
    auditContent.value = await getAuditContent(date)
    showAuditContent.value = true
  } catch (e) {
    console.error('获取审计内容失败:', e)
  }
}

// ── Db backup ──
async function handleDbBackup() {
  isBackingUp.value = true
  try {
    const token = getToken()
    const res = await fetch('/api/admin/db/backup', {
      headers: token ? { 'Authorization': `Bearer ${token}` } : {},
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || `请求失败: ${res.status}`)
    }
    const disposition = res.headers.get('Content-Disposition') || ''
    const match = disposition.match(/filename="?([^";\n]+)"?/)
    const filename = match ? match[1] : `fund_tracker_${new Date().toISOString().slice(0, 10)}.sql.gz`
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    showToast('✅ 数据库备份成功，已下载到本地', 'success')
  } catch (e: any) {
    showToast('❌ 备份失败: ' + (e.message || '未知错误'), 'error')
  } finally {
    isBackingUp.value = false
  }
}

function onAvatarError() {
  avatarError.value = true
}


function goBack() {
  router.back()
}

async function loadData() {
  try {
    const [p, s, r, u] = await Promise.all([
      getProfile(),
      getSettings(),
      listExchangeRates(),
      getUserInfo().catch(() => null),
    ])
    profile.value = p
    settings.value = s
    exchangeRates.value = r
    userInfo.value = u
    pageState.value = 'ready'
  } catch (e) {
    console.error('加载个人中心数据失败:', e)
  }
}

onActivated(() => {
  loadData()
})
</script>

<template>
  <div class="min-h-screen bg-page-bg">
    <!-- Toast -->
    <ToastNotification :message="toastMsg" :type="toastType" @close="toastMsg = null" />

    <AppHeader title="我的" :show-logo="true" />

    <PageStateView v-if="pageState !== 'ready'" :state="pageState" />

    <main v-if="pageState === 'ready'" class="pt-16 pb-24 px-gutter max-w-[600px] mx-auto space-y-section">
      <!-- ==================== User Info Card ==================== -->
      <section>
        <div
          class="bg-card-bg rounded-xl p-lg card-shadow border border-border-light/40 flex items-center gap-md cursor-pointer active:scale-[0.98] transition-transform"
          @click="openProfileModal"
        >
          <div class="relative shrink-0">
            <img
              v-if="profile?.avatar && !avatarError"
              :src="profile?.avatar"
              alt="User Avatar"
              class="w-20 h-20 rounded-full border-2 border-brand-light object-cover"
              @error="onAvatarError"
            />
            <div
              v-else
              class="w-20 h-20 rounded-full border-2 border-brand-light bg-card-alt flex items-center justify-center"
            >
              <span class="material-symbols-outlined text-[36px] text-text-secondary">person</span>
            </div>
            <div
              v-if="profile?.membership === 'pro'"
              class="absolute -bottom-1 -right-1 bg-brand text-white p-1 rounded-full border-2 border-card-bg flex items-center justify-center"
            >
              <span
                class="material-symbols-outlined text-[14px]"
                style="font-variation-settings: 'FILL' 1;"
              >verified</span>
            </div>
          </div>
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-xs flex-wrap mb-xs">
              <h2 class="font-display text-lg text-text-primary truncate">
                {{ profile?.name }}
              </h2>
              <span
                v-if="profile?.membership === 'pro'"
                class="bg-brand text-white px-1 py-0.5 rounded font-body text-[10px] font-medium leading-none flex items-center gap-[2px]"
                style="position: relative; top: 1px"
              >
                <span class="material-symbols-outlined text-[11px]" style="font-variation-settings:'FILL' 1">workspace_premium</span>
                Pro
              </span>
              <span v-if="profile?.membership === 'pro' && profile?.membershipExpiry" class="text-text-tertiary font-body text-[10px]">
                {{ profile.membershipExpiry.replace(/-/g, '·') }}
              </span>
              <span v-if="profile?.membership !== 'pro'" class="text-text-tertiary font-body text-xs">
                免费用户
              </span>
            </div>
            <p v-if="userInfo?.email" class="font-body text-xs text-text-tertiary mt-1">
              {{ userInfo.email }}
            </p>
          </div>
          <span class="material-symbols-outlined text-text-tertiary shrink-0">
            chevron_right
          </span>
        </div>
      </section>

      <!-- ==================== Exchange Rates ==================== -->
      <section>
        <div class="bg-card-bg rounded-xl overflow-hidden card-shadow border border-border-light/40">
          <div class="p-lg bg-card-alt flex justify-between items-center">
            <div class="flex items-center gap-sm">
              <span class="material-symbols-outlined text-brand">currency_exchange</span>
              <span class="font-body text-sm font-medium text-text-primary">货币与汇率</span>
            </div>
            <button
              class="flex items-center gap-xs bg-card-bg px-3 py-1 rounded-full border border-border-light hover:bg-card-alt transition-colors"
            >
              <span class="font-body text-xs text-text-secondary">人民币 (CNY)</span>
              <span class="material-symbols-outlined text-[16px] text-text-tertiary">expand_more</span>
            </button>
          </div>
          <div class="p-lg grid grid-cols-2 gap-0 divide-x divide-border-light">
            <div class="pl-sm" v-for="rate in exchangeRates" :key="rate.pair">
              <p class="font-body text-xs text-text-tertiary mb-1">{{ rate.label }}</p>
              <p class="font-display text-lg font-semibold text-brand">{{ rate.rate.toFixed(4) }}</p>
            </div>
          </div>
          <div class="px-lg pb-lg flex justify-between items-center border-t border-border-light/30 pt-sm">
            <span class="font-body text-xs text-text-tertiary flex items-center gap-1">
              <span class="material-symbols-outlined text-[14px]">schedule</span>
              更新于: {{ exchangeRates[0]?.updatedAt }}
            </span>
            <button
              class="text-brand font-body text-xs font-medium flex items-center gap-xs active:scale-95 transition-transform"
              @click="handleRefresh"
            >
              立即刷新
              <span
                class="material-symbols-outlined text-[14px]"
                :class="{ 'animate-spin': isRefreshing }"
              >refresh</span>
            </button>
          </div>
        </div>
      </section>

      <!-- ==================== Function List ==================== -->
      <section class="bg-card-bg rounded-xl overflow-hidden card-shadow border border-border-light/40">
        <div class="divide-y divide-border-light">
          <!-- Data Legend -->
          <div
            class="flex items-center justify-between p-lg hover:bg-card-alt transition-colors cursor-pointer group"
            @click="showDataInfoModal = true"
          >
            <div class="flex items-center gap-md">
              <span class="material-symbols-outlined text-text-secondary">info</span>
              <span class="font-body text-sm font-medium text-text-primary">数据口径说明</span>
            </div>
            <span class="material-symbols-outlined text-text-tertiary group-hover:translate-x-1 transition-transform">chevron_right</span>
          </div>

          <!-- Backup Database -->
          <div
            class="flex items-center justify-between p-lg hover:bg-card-alt transition-colors cursor-pointer group"
            @click="handleDbBackup"
          >
            <div class="flex items-center gap-md">
              <span class="material-symbols-outlined text-text-secondary">database</span>
              <span class="font-body text-sm font-medium text-text-primary">
                {{ isBackingUp ? '正在备份...' : '备份数据库' }}
              </span>
            </div>
            <span v-if="!isBackingUp" class="material-symbols-outlined text-text-tertiary group-hover:translate-x-1 transition-transform">chevron_right</span>
            <span v-else class="material-symbols-outlined text-brand animate-spin">refresh</span>
          </div>

          <!-- Contact → GitHub -->
          <a
            href="https://github.com/2398207092/myDca#readme"
            target="_blank"
            rel="noopener noreferrer"
            class="flex items-center justify-between p-lg hover:bg-card-alt transition-colors cursor-pointer group no-underline"
          >
            <div class="flex items-center gap-md">
              <span class="material-symbols-outlined text-text-secondary">headset_mic</span>
              <span class="font-body text-sm font-medium text-text-primary">联系我们</span>
            </div>
            <span class="material-symbols-outlined text-text-tertiary group-hover:translate-x-1 transition-transform">chevron_right</span>
          </a>

          <!-- Set Password Hint -->
          <div v-if="userInfo && !userInfo.hasPassword"
            class="flex items-center justify-between p-lg hover:bg-card-alt transition-colors cursor-pointer group"
            @click="showSetPwdModal = true"
          >
            <div class="flex items-center gap-md">
              <span class="material-symbols-outlined text-warning">lock_open</span>
              <div>
                <span class="font-body text-sm font-medium text-text-primary">设置密码</span>
                <p class="font-body text-[11px] text-warning mt-[1px]">⚠ 尚未设置密码，点击设置</p>
              </div>
            </div>
            <span class="material-symbols-outlined text-text-tertiary group-hover:translate-x-1 transition-transform">chevron_right</span>
          </div>
        </div>
      </section>

      <!-- ==================== Data Tools (merged from Toolbox) ==================== -->
      <section class="bg-card-bg rounded-xl overflow-hidden card-shadow border border-border-light/40">
        <div class="px-lg pt-md pb-sm bg-card-alt">
          <div class="flex items-center gap-sm">
            <span class="material-symbols-outlined text-brand text-lg">construction</span>
            <span class="font-body text-sm font-medium text-text-primary">数据工具</span>
          </div>
        </div>
        <div class="divide-y divide-border-light">
          <div
            class="flex items-center justify-between p-lg hover:bg-card-alt transition-colors cursor-pointer group active:scale-[0.99] active:transition-transform"
            @click="openDatePicker"
          >
            <div class="flex items-center gap-md">
              <span class="material-symbols-outlined text-brand">verified</span>
              <div>
                <p class="font-body text-sm font-medium text-text-primary">数据审计报告</p>
                <p class="font-body text-xs text-text-tertiary mt-0.5">查看每日自动对账结果</p>
              </div>
            </div>
            <span class="material-symbols-outlined text-text-tertiary group-hover:translate-x-1 transition-transform">chevron_right</span>
          </div>
        </div>
      </section>

      <!-- ==================== Legal Links ==================== -->
      <section class="bg-card-bg rounded-xl overflow-hidden card-shadow border border-border-light/40">
        <div class="divide-y divide-border-light">
          <div class="flex items-center justify-between p-lg opacity-40 cursor-not-allowed">
            <span class="font-body text-sm text-text-tertiary">免责声明</span>
            <span class="font-body text-[11px] text-text-tertiary/50">即将上线</span>
          </div>
          <div class="flex items-center justify-between p-lg opacity-40 cursor-not-allowed">
            <span class="font-body text-sm text-text-tertiary">用户协议</span>
            <span class="font-body text-[11px] text-text-tertiary/50">即将上线</span>
          </div>
          <div class="flex items-center justify-between p-lg opacity-40 cursor-not-allowed">
            <span class="font-body text-sm text-text-tertiary">隐私政策</span>
            <span class="font-body text-[11px] text-text-tertiary/50">即将上线</span>
          </div>
        </div>
      </section>

      <!-- ==================== Logout ==================== -->
      <section>
        <button
          class="w-full bg-card-bg rounded-xl p-lg card-shadow border border-border-light/40 flex items-center justify-center gap-md hover:bg-card-alt transition-colors active:scale-[0.98]"
          @click="showLogoutConfirm = true"
        >
          <span class="material-symbols-outlined text-alert">logout</span>
          <span class="font-body text-sm font-medium text-alert">退出登录</span>
        </button>
      </section>

      <!-- Version -->
      <div class="text-center pb-8">
        <p class="font-body text-xs text-text-tertiary opacity-50">种树 v2.4.0</p>
      </div>
    </main>

    <!-- ==================== Profile Edit Modal ==================== -->
    <Teleport to="body">
      <div
        v-if="showProfileModal"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 px-gutter"
        @click.self="showProfileModal = false"
      >
        <div class="bg-card-bg rounded-xl p-lg w-full max-w-sm">
          <div class="flex items-center justify-between mb-md">
            <h3 class="font-body text-sm font-medium text-text-primary">个人信息</h3>
            <button
              class="w-8 h-8 flex items-center justify-center text-text-tertiary hover:bg-card-alt rounded-lg transition-colors"
              @click="showProfileModal = false"
            >
              <span class="material-symbols-outlined">close</span>
            </button>
          </div>

          <!-- Avatar Selection -->
          <div class="text-center mb-md">
            <p class="font-body text-xs text-text-tertiary mb-sm">选择头像</p>
            <div class="flex flex-wrap justify-center gap-sm">
              <div
                v-for="opt in AVATAR_OPTIONS"
                :key="opt.seed"
                class="w-14 h-14 rounded-full cursor-pointer transition-all overflow-hidden"
                :class="editAvatar === avatarUrl(opt.seed, opt.bg)
                  ? 'ring-2 ring-brand ring-offset-2 ring-offset-card-bg scale-110'
                  : 'hover:scale-105 opacity-70 hover:opacity-100'"
                @click="editAvatar = avatarUrl(opt.seed, opt.bg)"
              >
                <img :src="avatarUrl(opt.seed, opt.bg)" class="w-full h-full object-cover" />
              </div>
            </div>
          </div>

          <!-- Name Edit -->
          <div class="mb-md">
            <label class="font-body text-xs text-text-tertiary mb-1 block">昵称</label>
            <input
              v-model="editName"
              type="text"
              maxlength="20"
              placeholder="输入昵称"
              class="w-full px-3 py-2.5 bg-page-bg rounded-lg border border-border-light/60 text-sm
                     font-body text-text-primary placeholder:text-text-tertiary/50
                     focus:outline-none focus:border-brand/40 focus:ring-1 focus:ring-brand/20 transition-all"
            />
          </div>

          <!-- Membership Info -->
          <div class="mb-md p-md bg-card-alt rounded-xl">
            <div class="flex items-center gap-sm mb-1">
              <span class="material-symbols-outlined text-[18px] text-brand" style="font-variation-settings: 'FILL' 1;">workspace_premium</span>
              <span class="font-body text-xs font-medium text-text-primary">会员</span>
            </div>
            <p v-if="profile?.membership === 'pro'" class="font-body text-sm text-text-secondary">
              Pro 会员 · {{ profile?.membershipExpiry }} 到期
            </p>
            <p v-else class="font-body text-sm text-text-secondary">免费用户</p>
          </div>

          <!-- Actions -->
          <div class="flex gap-2">
            <button
              class="flex-1 py-2.5 rounded-lg bg-page-bg font-body text-sm font-medium text-text-secondary active:scale-[0.98] transition-transform"
              @click="showProfileModal = false"
            >取消</button>
            <button
              class="flex-1 py-2.5 rounded-lg bg-brand text-white font-body text-sm font-medium active:scale-[0.98] transition-transform"
              :disabled="savingProfile || !editName.trim()"
              :class="savingProfile || !editName.trim() ? 'opacity-50 cursor-not-allowed' : ''"
              @click="handleSaveProfile"
            >{{ savingProfile ? '保存中...' : '保存' }}</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ==================== Set Password Modal ==================== -->
    <Teleport to="body">
      <div
        v-if="showSetPwdModal"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 px-gutter"
        @click.self="showSetPwdModal = false"
      >
        <div class="bg-card-bg rounded-xl p-lg w-full max-w-sm">
          <div class="flex items-center justify-between mb-md">
            <h3 class="font-body text-sm font-medium text-text-primary">设置密码</h3>
            <button
              class="w-8 h-8 flex items-center justify-center text-text-tertiary hover:bg-card-alt rounded-lg transition-colors"
              @click="showSetPwdModal = false"
            >
              <span class="material-symbols-outlined">close</span>
            </button>
          </div>
          <div class="space-y-3">
            <p v-if="userInfo?.email" class="font-body text-xs text-text-tertiary">
              账号：{{ userInfo.email }}
            </p>
            <input
              v-model="newPassword"
              type="password"
              placeholder="6-20 位字母+数字组合"
              class="w-full px-3 py-2.5 bg-page-bg rounded-lg border border-border-light/60 text-sm font-body text-text-primary placeholder:text-text-tertiary/50 focus:outline-none focus:border-brand/40 focus:ring-1 focus:ring-brand/20 transition-all"
            />
            <input
              v-model="confirmNewPassword"
              type="password"
              placeholder="再次输入密码"
              class="w-full px-3 py-2.5 bg-page-bg rounded-lg border border-border-light/60 text-sm font-body text-text-primary placeholder:text-text-tertiary/50 focus:outline-none focus:border-brand/40 focus:ring-1 focus:ring-brand/20 transition-all"
            />
            <div v-if="pwdError" class="py-1.5 px-2.5 bg-error/5 rounded-lg border border-error/10">
              <p class="font-body text-xs text-error">{{ pwdError }}</p>
            </div>
            <button
              @click="handleSetPassword"
              :disabled="settingPassword || !newPassword || newPassword !== confirmNewPassword"
              class="w-full py-2.5 rounded-lg font-body text-sm font-medium transition-all"
              :class="settingPassword || !newPassword || newPassword !== confirmNewPassword
                ? 'bg-page-bg text-text-tertiary cursor-not-allowed'
                : 'bg-brand text-white active:scale-[0.98]'"
            >
              {{ settingPassword ? '保存中...' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ==================== Logout Confirm Modal ==================== -->
    <Teleport to="body">
      <div
        v-if="showLogoutConfirm"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 px-gutter"
        @click.self="showLogoutConfirm = false"
      >
        <div class="bg-card-bg rounded-xl p-lg w-full max-w-sm">
          <h3 class="font-body text-sm font-medium text-text-primary mb-2">确认退出？</h3>
          <p class="font-body text-xs text-text-tertiary mb-md">退出后需要重新登录才能使用</p>
          <div class="flex gap-2">
            <button
              class="flex-1 py-2.5 rounded-lg bg-page-bg font-body text-sm font-medium text-text-secondary active:scale-[0.98] transition-transform"
              @click="showLogoutConfirm = false"
            >取消</button>
            <button
              class="flex-1 py-2.5 rounded-lg bg-alert text-white font-body text-sm font-medium active:scale-[0.98] transition-transform"
              @click="handleLogout"
            >退出</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ==================== Data Info Modal ==================== -->
    <Teleport to="body">
      <div
        v-if="showDataInfoModal"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 px-gutter"
        @click.self="showDataInfoModal = false"
      >
        <div class="bg-card-bg rounded-xl p-lg w-full max-w-sm">
          <div class="flex items-center justify-between mb-md">
            <h3 class="font-body text-sm font-medium text-text-primary">数据口径说明</h3>
            <button
              class="w-8 h-8 flex items-center justify-center text-text-tertiary hover:bg-card-alt rounded-lg transition-colors"
              @click="showDataInfoModal = false"
            >
              <span class="material-symbols-outlined">close</span>
            </button>
          </div>
          <div class="space-y-md font-body text-sm text-text-secondary leading-relaxed">
            <p><span class="font-medium text-text-primary">市值</span> 基于最新基金净值 × 持有份额计算，每交易日更新。</p>
            <p><span class="font-medium text-text-primary">成本</span> 为所有买入交易的总投入金额（含手续费）。</p>
            <p><span class="font-medium text-text-primary">股息率</span> = 近一年分红总额 / 最新市值 × 100%。</p>
            <p><span class="font-medium text-text-primary">预测分红</span> 基于历史分红记录和当前持有份额估算，实际以基金公告为准。</p>
            <p><span class="font-medium text-text-primary">汇率数据</span> 由东方财富提供，仅供参考。</p>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ==================== Audit: Date Picker Modal ==================== -->
    <Teleport to="body">
      <div
        v-if="showDatePicker"
        class="fixed inset-0 z-[100] flex items-end justify-center bg-black/40"
        @click.self="showDatePicker = false"
      >
        <div class="bg-card-bg rounded-t-2xl w-full max-w-lg px-gutter pt-lg pb-8 animate-slide-up max-h-[70vh] flex flex-col">
          <div class="flex items-center justify-between mb-md shrink-0">
            <h3 class="font-body text-base font-medium text-text-primary">选择日期</h3>
            <button
              class="w-8 h-8 flex items-center justify-center text-text-tertiary hover:bg-card-alt rounded-lg transition-colors"
              @click="showDatePicker = false"
            >
              <span class="material-symbols-outlined">close</span>
            </button>
          </div>
          <div v-if="loadingDates" class="flex items-center justify-center py-12">
            <span class="material-symbols-outlined text-brand animate-spin text-3xl">refresh</span>
          </div>
          <div v-else-if="availableDates.length === 0" class="text-center py-12">
            <span class="material-symbols-outlined text-text-tertiary text-4xl mb-3">calendar_month</span>
            <p class="font-body text-sm text-text-tertiary">暂无审计日志</p>
            <p class="font-body text-xs text-text-tertiary mt-1 opacity-60">审计任务每天凌晨 3:00 自动运行</p>
          </div>
          <div v-else class="overflow-y-auto flex-1 -mx-gutter px-gutter space-y-1">
            <div
              v-for="date in availableDates"
              :key="date"
              class="flex items-center justify-between p-md rounded-xl hover:bg-card-alt cursor-pointer transition-colors active:scale-[0.98] active:transition-transform"
              @click="selectDate(date)"
            >
              <div class="flex items-center gap-md">
                <span class="material-symbols-outlined text-brand text-xl">calendar_today</span>
                <span class="font-body text-sm text-text-primary">{{ date }}</span>
              </div>
              <span class="material-symbols-outlined text-text-tertiary text-xl">chevron_right</span>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ==================== Audit: Content Modal ==================== -->
    <Teleport to="body">
      <div
        v-if="showAuditContent && auditContent"
        class="fixed inset-0 z-[110] flex items-end justify-center bg-black/40"
        @click.self="showAuditContent = false"
      >
        <div class="bg-card-bg rounded-t-2xl w-full max-w-lg px-gutter pt-lg pb-8 animate-slide-up max-h-[80vh] flex flex-col">
          <div class="flex items-center justify-between mb-md shrink-0">
            <div class="flex items-center gap-sm">
              <span class="material-symbols-outlined text-brand">verified</span>
              <h3 class="font-body text-base font-medium text-text-primary">数据对账报告</h3>
            </div>
            <button
              class="w-8 h-8 flex items-center justify-center text-text-tertiary hover:bg-card-alt rounded-lg transition-colors"
              @click="showAuditContent = false"
            >
              <span class="material-symbols-outlined">close</span>
            </button>
          </div>
          <div class="text-center mb-md shrink-0">
            <p class="font-display text-lg font-semibold text-text-primary">{{ auditContent.date }}</p>
          </div>
          <div
            class="rounded-xl p-md mb-md shrink-0"
            :class="auditContent.errorCount > 0
              ? 'bg-red-50 border border-red-200'
              : auditContent.warningCount > 0
                ? 'bg-amber-50 border border-amber-200'
                : 'bg-green-50 border border-green-200'"
          >
            <p
              class="font-body text-sm font-medium text-center"
              :class="auditContent.errorCount > 0
                ? 'text-red-600'
                : auditContent.warningCount > 0
                  ? 'text-amber-600'
                  : 'text-green-600'"
            >{{ auditContent.summary }}</p>
          </div>
          <div v-if="auditContent.entries.length > 0" class="overflow-y-auto flex-1 -mx-gutter px-gutter space-y-2">
            <div
              v-for="(entry, i) in auditContent.entries"
              :key="i"
              class="flex items-start gap-sm p-md rounded-xl"
              :class="entry.level === 'error'
                ? 'bg-red-50/50'
                : entry.level === 'warning'
                  ? 'bg-amber-50/50'
                  : 'bg-card-alt'"
            >
              <span v-if="entry.level === 'error'" class="material-symbols-outlined text-red-500 text-lg shrink-0 mt-0.5">error</span>
              <span v-else-if="entry.level === 'warning'" class="material-symbols-outlined text-amber-500 text-lg shrink-0 mt-0.5">warning</span>
              <span v-else class="material-symbols-outlined text-green-500 text-lg shrink-0 mt-0.5">check_circle</span>
              <p class="font-body text-sm leading-relaxed" :class="{
                'text-red-700': entry.level === 'error',
                'text-amber-700': entry.level === 'warning',
                'text-text-secondary': entry.level === 'info',
              }">{{ entry.message }}</p>
            </div>
          </div>
          <div class="mt-md pt-md border-t border-border-light shrink-0">
            <p class="font-body text-xs text-text-tertiary text-center">
              共检测 <span v-if="auditContent.errorCount > 0" class="text-red-500 font-medium">{{ auditContent.errorCount }} 个错误</span>
              <span v-if="auditContent.errorCount > 0 && auditContent.warningCount > 0">，</span>
              <span v-if="auditContent.warningCount > 0" class="text-amber-500 font-medium">{{ auditContent.warningCount }} 个警告</span>
              <span v-if="auditContent.errorCount === 0 && auditContent.warningCount === 0" class="text-green-500 font-medium">全部正常</span>
            </p>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.animate-slide-up {
  animation: slideUp 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes slideUp {
  from {
    transform: translateY(100%);
    opacity: 0.5;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}
</style>
