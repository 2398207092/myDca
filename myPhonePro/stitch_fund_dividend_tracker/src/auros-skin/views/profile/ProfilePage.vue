<script setup lang="ts">
/**
 * 我的 · 四层信息骨架版（2026-11-08 第三轮）
 * 章 01 ACCOUNT 账户：身份块（本章唯一形状）+ 一组行
 * 章 02 RATES 汇率：一组行（牌价）+ 更新时间与刷新
 * 章 03 TOOLS 数据工具：一组行（5 个入口）
 * 章 04 SETTINGS 设置与条款：一组行（退出用 muted + 粉字）
 * 密度预算：一页 ≤4 章、一章 ≤3 组、一组 ≤5 行；行一律 AurosRow。
 * 业务逻辑（接口 / 校验 / 状态 / 跳转）与原页面逐行一致，只重排信息层级。
 */
import { ref, computed, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import type { PageState } from '@/types'
import { getProfile, getSettings, updateProfile, type UserProfile } from '@/api/user'
import { listExchangeRates, refreshExchangeRates } from '@/api/exchangeRate'
import { getToken, clearToken } from '@/api/request'
import { getUserInfo, setPassword as apiSetPassword, logout as apiLogout } from '@/api/auth'
import type { UserSettings } from '@/api/user'
import type { ExchangeRateItem } from '@/api/exchangeRate'
import type { UserInfoResp } from '@/types/api'
import { getAuditDates, getAuditContent } from '@/api/audit'
import type { AuditContent } from '@/api/audit'
import { getMonitorDates, getMonitorContent } from '@/api/monitor'
import type { MonitorContent } from '@/api/monitor'
import ToastNotification from '@/auros-skin/components/shared/ToastNotification.vue'
import AurosState from '@/auros-skin/components/auros/AurosState.vue'
import AurosChapter from '@/auros-skin/components/auros/AurosChapter.vue'
import AurosGroup from '@/auros-skin/components/auros/AurosGroup.vue'
import AurosRow from '@/auros-skin/components/auros/AurosRow.vue'
import AurosSheet from '@/auros-skin/components/auros/AurosSheet.vue'
import { UI_SKIN_PACKS, getUiSkin, switchUiSkin } from '@/skins/packs'
import AurosField from '@/auros-skin/components/auros/AurosField.vue'
import AurosButton from '@/auros-skin/components/auros/AurosButton.vue'
import { formatPrice } from '@/auros-skin/utils/format'

const router = useRouter()
const pageState = ref<PageState>('loading')
/** 当前皮肤：由 localStorage 决定（与 skin-boot.js 同一份偏好） */
const currentSkin = getUiSkin()
const currentSkinName = computed(() => UI_SKIN_PACKS.find((p) => p.id === currentSkin)?.name ?? '原版')

// ── User profile ──
const profile = ref<UserProfile | null>(null)
const settings = ref<UserSettings | null>(null)
const exchangeRates = ref<ExchangeRateItem[]>([])
const isRefreshing = ref(false)

// ── Auth state ──
const userInfo = ref<UserInfoResp | null>(null)

// ── Toast ──
const toastMsg = ref<string | null>(null)
const toastType = ref<'success' | 'error' | 'info'>('success')

function showToast(msg: string, type: 'success' | 'error' | 'info' = 'success') {
  toastMsg.value = msg
  toastType.value = type
}

// ── 派生（仅呈现用） ──
const isPro = computed(() => profile.value?.membership === 'pro')
const hasPassword = computed(() => userInfo.value?.hasPassword === true)
/** 组头数量：与组内实际行数一致，避免组头数字与行数对不上 */
const accountRowCount = computed(() => (isPro.value && profile.value?.membershipExpiry ? 3 : 2))
const settingsRowCount = computed(() => (hasPassword.value ? 4 : 5))

// ── Profile Edit Modal ──
const showProfileModal = ref(false)
const editName = ref('')
const editAvatar = ref('')
const savingProfile = ref(false)

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

// ── Monitor Logs (每日监控日志) ──
const showMonitorDatePicker = ref(false)
const monitorDates = ref<string[]>([])
const loadingMonitorDates = ref(false)
const showMonitorContent = ref(false)
const monitorContent = ref<MonitorContent | null>(null)

async function openMonitorDatePicker() {
  showMonitorDatePicker.value = true
  if (monitorDates.value.length === 0) {
    loadingMonitorDates.value = true
    try {
      monitorDates.value = await getMonitorDates()
    } catch (e) {
      console.error('获取监控日志日期列表失败:', e)
    } finally {
      loadingMonitorDates.value = false
    }
  }
}

async function selectMonitorDate(date: string) {
  showMonitorDatePicker.value = false
  try {
    monitorContent.value = await getMonitorContent(date)
    showMonitorContent.value = true
  } catch (e) {
    console.error('获取监控日志内容失败:', e)
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
    showToast('数据库备份成功，已下载到本地', 'success')
  } catch (e: any) {
    showToast('备份失败: ' + (e.message || '未知错误'), 'error')
  } finally {
    isBackingUp.value = false
  }
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
  <div class="auros page">
    <ToastNotification :message="toastMsg" :type="toastType" @close="toastMsg = null" />

    <!-- 状态行 -->
    <header class="topbar">
      <span class="brand">种树</span>
      <span class="topbar__tag">MYDCA</span>
    </header>

    <main class="body">
      <!-- 加载 / 空 / 错 -->
      <AurosState
        v-if="pageState !== 'ready'"
        :state="pageState === 'error' ? 'error' : pageState === 'empty' ? 'empty' : 'loading'"
        :rows="5"
        text="账户信息没有取到"
      />

      <template v-else>
        <!-- ==================== 章 01 · 账户 ==================== -->
        <AurosChapter label="ACCOUNT" title="账户" :gap="false">
          <!-- 本章唯一形状：身份块（大字位头像 + 姓名 + 邮箱，不用图片） -->
          <button type="button" class="ident" @click="openProfileModal">
            <span class="ident__ava">{{ profile?.name?.slice(0, 1) || '种' }}</span>
            <span class="ident__main">
              <span class="ident__name">{{ profile?.name }}</span>
              <span v-if="userInfo?.email" class="ident__mail">{{ userInfo.email }}</span>
            </span>
            <svg class="chev" viewBox="0 0 12 12" width="10" height="10" fill="none" aria-hidden="true">
              <path d="M4.6 2.8 7.8 6l-3.2 3.2" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>

          <AurosGroup title="身份信息" :count="`${accountRowCount} 项`">
            <AurosRow label="会员状态">
              <template #default>
                <span class="st">
                  <i class="st__dot" :class="{ 'is-lit': isPro }" aria-hidden="true"></i>
                  {{ isPro ? 'Pro 会员' : '免费用户' }}
                </span>
              </template>
            </AurosRow>
            <AurosRow
              v-if="isPro && profile?.membershipExpiry"
              label="会员到期"
              :value="profile.membershipExpiry.replace(/-/g, '·')"
            />
            <AurosRow label="基准货币">
              <template #default><span class="st">人民币 (CNY)</span></template>
            </AurosRow>
          </AurosGroup>
        </AurosChapter>

        <!-- ==================== 章 02 · 汇率 ==================== -->
        <AurosChapter label="RATES" title="汇率">
          <AurosGroup title="牌价">
            <AurosRow
              v-for="rate in exchangeRates"
              :key="rate.pair"
              :label="rate.label"
              :value="formatPrice(rate.rate)"
            />
            <AurosRow v-if="!exchangeRates.length" label="汇率数据" tone="muted">
              <template #default><span class="st st--muted">暂无</span></template>
            </AurosRow>
            <AurosRow label="更新时间" :sub="exchangeRates[0]?.updatedAt || '—'">
              <template #default>
                <button type="button" class="act" @click="handleRefresh">
                  <svg
                    class="act__ico"
                    :class="{ 'is-spin': isRefreshing }"
                    viewBox="0 0 16 16"
                    width="12"
                    height="12"
                    fill="none"
                    aria-hidden="true"
                  >
                    <path d="M13.1 8a5.1 5.1 0 1 1-1.5-3.6" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" />
                    <path d="M13.2 2.6v3h-3" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                  立即刷新
                </button>
              </template>
            </AurosRow>
          </AurosGroup>
        </AurosChapter>

        <!-- ==================== 章 03 · 数据工具 ==================== -->
        <AurosChapter label="TOOLS" title="数据工具">
          <AurosGroup title="数据与运维" count="5 项">
            <AurosRow
              label="定时任务"
              sub="查看定时计划，手动立即执行"
              clickable
              chevron
              @click="router.push('/scheduler')"
            />
            <AurosRow
              label="数据审计报告"
              sub="查看每日自动对账结果"
              clickable
              chevron
              @click="openDatePicker"
            />
            <AurosRow
              label="每日监控日志"
              sub="查看定时任务执行与系统运行日志"
              clickable
              chevron
              @click="openMonitorDatePicker"
            />
            <AurosRow
              label="数据口径说明"
              sub="市值 / 成本 / 股息率 / 预测分红的计算口径"
              clickable
              chevron
              @click="showDataInfoModal = true"
            />
            <AurosRow
              :label="isBackingUp ? '正在备份...' : '备份数据库'"
              sub="导出数据库压缩包到本地"
              clickable
              :chevron="!isBackingUp"
              @click="handleDbBackup"
            >
              <template #default>
                <svg
                  v-if="isBackingUp"
                  class="act__ico is-spin"
                  viewBox="0 0 16 16"
                  width="12"
                  height="12"
                  fill="none"
                  aria-hidden="true"
                >
                  <path d="M13.1 8a5.1 5.1 0 1 1-1.5-3.6" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" />
                  <path d="M13.2 2.6v3h-3" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </template>
            </AurosRow>
          </AurosGroup>
        </AurosChapter>

        <!-- ==================== 章 04 · 设置与条款 ==================== -->
        <AurosChapter label="SETTINGS" title="设置与条款">
          <AurosGroup title="账户与条款" :count="`${settingsRowCount} 项`">
            <AurosRow
              v-if="userInfo && !userInfo.hasPassword"
              label="设置密码"
              sub="尚未设置密码，点击设置"
              clickable
              chevron
              @click="showSetPwdModal = true"
            />
            <AurosRow label="免责声明" tone="muted">
              <template #default><span class="st st--muted">即将上线</span></template>
            </AurosRow>
            <AurosRow label="用户协议" tone="muted">
              <template #default><span class="st st--muted">即将上线</span></template>
            </AurosRow>
            <AurosRow label="隐私政策" tone="muted">
              <template #default><span class="st st--muted">即将上线</span></template>
            </AurosRow>
            <AurosRow
              class="row-exit"
              label="退出登录"
              tone="muted"
              clickable
              chevron
              @click="showLogoutConfirm = true"
            />
          </AurosGroup>
        </AurosChapter>

        <!-- 界面皮肤：整套前端换肤（原版 / Auros / 三面版），切换会重新载入当前页 -->
        <AurosChapter label="SKIN" title="界面皮肤" :meta="`当前 · ${currentSkinName}`">
          <AurosRow
            v-for="pack in UI_SKIN_PACKS"
            :key="pack.id"
            :label="pack.name"
            :sub="pack.desc"
            :clickable="pack.id !== currentSkin"
            :chevron="pack.id !== currentSkin"
            :mark="pack.id === currentSkin ? 'lit' : 'none'"
            :value="pack.id === currentSkin ? '使用中' : ''"
            @click="pack.id !== currentSkin && switchUiSkin(pack.id)"
          />
        </AurosChapter>

        <!-- 章外页脚 -->
        <footer class="foot">
          <span>种树 v2.4.0</span>
          <span class="foot__sep" aria-hidden="true">·</span>
          <a
            class="foot__link"
            href="https://github.com/2398207092/myDca#readme"
            target="_blank"
            rel="noopener noreferrer"
          >联系我们</a>
        </footer>
      </template>
    </main>

    <!-- ==================== 个人信息 ==================== -->
    <AurosSheet v-model="showProfileModal" title="个人信息">
      <div class="sh">
        <div class="ident ident--sheet">
          <span class="ident__ava">{{ (editName || profile?.name || '种').slice(0, 1) }}</span>
          <span class="ident__main">
            <span class="ident__name">{{ profile?.name }}</span>
            <span v-if="userInfo?.email" class="ident__mail">{{ userInfo.email }}</span>
          </span>
        </div>

        <AurosField v-model="editName" label="昵称" placeholder="输入昵称" />

        <AurosRow
          label="会员"
          :value="isPro ? `Pro 会员 · ${profile?.membershipExpiry} 到期` : '免费用户'"
        />

        <div class="acts">
          <AurosButton variant="ghost" block @click="showProfileModal = false">取消</AurosButton>
          <AurosButton
            variant="primary"
            block
            :disabled="savingProfile || !editName.trim()"
            @click="handleSaveProfile"
          >{{ savingProfile ? '保存中...' : '保存' }}</AurosButton>
        </div>
      </div>
    </AurosSheet>

    <!-- ==================== 设置密码 ==================== -->
    <AurosSheet v-model="showSetPwdModal" title="设置密码">
      <div class="sh">
        <p v-if="userInfo?.email" class="sh__meta">账号：{{ userInfo.email }}</p>

        <AurosField
          v-model="newPassword"
          label="新密码"
          type="password"
          placeholder="6-20 位字母+数字组合"
        />
        <AurosField
          v-model="confirmNewPassword"
          label="确认密码"
          type="password"
          placeholder="再次输入密码"
        />

        <p v-if="pwdError" class="err">{{ pwdError }}</p>

        <AurosButton
          variant="primary"
          block
          :disabled="settingPassword || !newPassword || newPassword !== confirmNewPassword"
          @click="handleSetPassword"
        >{{ settingPassword ? '保存中...' : '保存' }}</AurosButton>
      </div>
    </AurosSheet>

    <!-- ==================== 退出确认 ==================== -->
    <AurosSheet v-model="showLogoutConfirm" title="确认退出？">
      <div class="sh">
        <p class="sh__meta">退出后需要重新登录才能使用</p>
        <div class="acts">
          <AurosButton variant="ghost" block @click="showLogoutConfirm = false">取消</AurosButton>
          <AurosButton variant="ghost" block class="btn-quit" @click="handleLogout">退出</AurosButton>
        </div>
      </div>
    </AurosSheet>

    <!-- ==================== 数据口径说明 ==================== -->
    <AurosSheet v-model="showDataInfoModal" title="数据口径说明">
      <div class="sh legend">
        <p><em>市值</em> 基于最新基金净值 × 持有份额计算，每交易日更新。</p>
        <p><em>成本</em> 为所有买入交易的总投入金额（含手续费）。</p>
        <p><em>股息率</em> = 近一年分红总额 / 最新市值 × 100%。</p>
        <p><em>预测分红</em> 基于历史分红记录和当前持有份额估算，实际以基金公告为准。</p>
        <p><em>汇率数据</em> 由东方财富提供，仅供参考。</p>
      </div>
    </AurosSheet>

    <!-- ==================== 审计：日期选择 ==================== -->
    <AurosSheet v-model="showDatePicker" title="选择日期">
      <div class="sh">
        <AurosState v-if="loadingDates" state="loading" :rows="4" />
        <template v-else-if="availableDates.length === 0">
          <AurosState state="empty" text="暂无审计日志" />
          <p class="sh__meta">审计任务每天凌晨 3:00 自动运行</p>
        </template>
        <template v-else>
          <AurosRow
            v-for="date in availableDates"
            :key="date"
            :label="date"
            clickable
            chevron
            @click="selectDate(date)"
          />
        </template>
      </div>
    </AurosSheet>

    <!-- ==================== 审计：对账报告 ==================== -->
    <AurosSheet v-model="showAuditContent" title="数据对账报告">
      <div v-if="auditContent" class="sh">
        <p class="sh__date au-num">{{ auditContent.date }}</p>

        <div
          class="sum"
          :class="
            auditContent.errorCount > 0
              ? 'is-bad'
              : auditContent.warningCount > 0
                ? 'is-warn'
                : 'is-ok'
          "
        >
          <i
            class="dot"
            :class="
              auditContent.errorCount > 0
                ? 'dot--pink'
                : auditContent.warningCount > 0
                  ? 'dot--dim'
                  : 'dot--lit'
            "
            aria-hidden="true"
          ></i>
          <span>{{ auditContent.summary }}</span>
        </div>

        <ul v-if="auditContent.entries.length > 0" class="entries">
          <li
            v-for="(entry, i) in auditContent.entries"
            :key="i"
            class="entry"
          >
            <i
              class="dot"
              :class="
                entry.level === 'error'
                  ? 'dot--pink'
                  : entry.level === 'warning'
                    ? 'dot--dim'
                    : 'dot--lit'
              "
              aria-hidden="true"
            ></i>
            <p
              class="entry__msg"
              :class="
                entry.level === 'error'
                  ? 'lv--bad'
                  : entry.level === 'warning'
                    ? 'lv--warn'
                    : 'lv--info'
              "
            >{{ entry.message }}</p>
          </li>
        </ul>

        <div class="sheet-foot">
          <p>
            共检测
            <span v-if="auditContent.errorCount > 0" class="is-bad">{{ auditContent.errorCount }} 个错误</span>
            <span v-if="auditContent.errorCount > 0 && auditContent.warningCount > 0">，</span>
            <span v-if="auditContent.warningCount > 0" class="is-warn">{{ auditContent.warningCount }} 个警告</span>
            <span v-if="auditContent.errorCount === 0 && auditContent.warningCount === 0" class="is-ok">全部正常</span>
          </p>
        </div>
      </div>
    </AurosSheet>

    <!-- ==================== 监控：日期选择 ==================== -->
    <AurosSheet v-model="showMonitorDatePicker" title="选择日期">
      <div class="sh">
        <AurosState v-if="loadingMonitorDates" state="loading" :rows="4" />
        <template v-else-if="monitorDates.length === 0">
          <AurosState state="empty" text="暂无监控日志" />
          <p class="sh__meta">定时任务执行后会自动记录</p>
        </template>
        <template v-else>
          <AurosRow
            v-for="date in monitorDates"
            :key="date"
            :label="date"
            clickable
            chevron
            @click="selectMonitorDate(date)"
          />
        </template>
      </div>
    </AurosSheet>

    <!-- ==================== 监控：每日日志 ==================== -->
    <AurosSheet v-model="showMonitorContent" title="每日监控日志">
      <div v-if="monitorContent" class="sh">
        <p class="sh__date au-num">{{ monitorContent.date }}</p>

        <div class="sum" :class="monitorContent.failCount > 0 ? 'is-bad' : 'is-ok'">
          <i class="dot" :class="monitorContent.failCount > 0 ? 'dot--pink' : 'dot--lit'" aria-hidden="true"></i>
          <span>{{ monitorContent.summary }}</span>
        </div>

        <ul v-if="monitorContent.entries.length > 0" class="entries">
          <li
            v-for="(entry, i) in monitorContent.entries"
            :key="i"
            class="entry entry--log"
          >
            <i class="dot" :class="entry.success ? 'dot--lit' : 'dot--pink'" aria-hidden="true"></i>
            <div class="entry__body">
              <div class="entry__top">
                <p class="entry__name" :class="entry.success ? '' : 'is-bad'">{{ entry.taskName }}</p>
                <span class="entry__dur au-num">{{ (entry.durationMs / 1000).toFixed(1) }}s</span>
              </div>
              <p class="entry__msg" :class="entry.success ? '' : 'is-bad'">{{ entry.detail }}</p>
            </div>
          </li>
        </ul>

        <div class="sheet-foot">
          <p>
            共 <span class="is-strong">{{ monitorContent.totalCount }}</span> 条记录
            <span v-if="monitorContent.failCount > 0" class="is-bad"> · {{ monitorContent.failCount }} 条异常</span>
          </p>
        </div>
      </div>
    </AurosSheet>
  </div>
</template>

<style scoped>
/* ============================================================
 * 我的 · 卡片化（一章一张 au-card）
 * 字号只取 10/12/13/16/20；字重 400/500；圆角 6/16/50%
 * 阴影只允许"光"（0 0）；卡内分隔用 1px 发丝线；行一律 AurosRow
 * ============================================================ */
.page {
  position: relative;
  min-height: 100svh;
  display: flex;
  flex-direction: column;
}
.body {
  flex: 1;
  padding: 8px 16px calc(env(safe-area-inset-bottom, 0px) + 62px);
}

/* ---------- 状态行 ---------- */
.topbar {
  flex: none;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: calc(env(safe-area-inset-top, 0px) + 36px);
  padding: 0 16px 6px;
  box-sizing: border-box;
}
.brand {
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 0.12em;
  color: var(--au-white);
}
.topbar__tag {
  font-size: 10px;
  letter-spacing: 0.16em;
  color: rgba(187, 199, 198, 0.62);
}

/* ---------- 章 01 的形状：身份块（大字位头像，不用图片） ---------- */
.ident {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 2px 0;
  border: 0;
  background: none;
  font-family: inherit;
  text-align: left;
}
.ident--sheet {
  padding: 0 0 4px;
}
.ident__ava {
  position: relative;
  flex: none;
  width: 64px;
  height: 64px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  background: linear-gradient(160deg, rgba(0, 67, 63, 0.9) 0%, rgba(1, 29, 28, 0.96) 100%);
  box-shadow: 0 0 26px rgba(203, 255, 252, 0.08);
  color: var(--au-mist);
  font-size: 26px;
  font-weight: 500;
  line-height: 1;
  letter-spacing: 0;
}
.ident__ava::before {
  content: '';
  position: absolute;
  left: 14px;
  right: 14px;
  top: 0;
  height: 1px;
  background: rgba(237, 255, 254, 0.16);
}
.ident__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.ident__name {
  font-size: 20px;
  font-weight: 500;
  letter-spacing: -0.01em;
  color: var(--au-white);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ident__mail {
  font-size: 12px;
  letter-spacing: 0.02em;
  color: rgba(187, 199, 198, 0.72);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.chev {
  flex: none;
  color: rgba(187, 199, 198, 0.5);
}
/* 形状与第一组之间的呼吸（卡内） */
.ident + :deep(.group) {
  margin-top: 18px;
}

/* ---------- 行右侧状态字 ---------- */
.st {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  letter-spacing: 0.01em;
  color: var(--au-mist);
  white-space: nowrap;
}
.st__dot {
  flex: none;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(237, 255, 254, 0.2);
}
.st__dot.is-lit {
  background: var(--au-lit);
  box-shadow: 0 0 8px rgba(203, 255, 252, 0.8);
}
.st--muted {
  font-size: 10px;
  letter-spacing: 0.06em;
  color: rgba(187, 199, 198, 0.45);
}

/* ---------- 行内动作 ---------- */
.act {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 0;
  border: 0;
  background: none;
  font-family: inherit;
  font-size: 12px;
  letter-spacing: 0.02em;
  color: var(--au-mist);
}
.act:active {
  opacity: 0.72;
}
.act__ico {
  color: var(--au-silver);
}
.is-spin {
  animation: au-spin 900ms linear infinite;
}

/* 退出：危险/终态操作，用 silver + 正常字重；粉色只留给统计强调 */
.row-exit :deep(.row__label) {
  color: rgba(187, 199, 198, 0.72);
  font-weight: 400;
}

/* ---------- 章外页脚 ---------- */
.foot {
  margin-top: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 10px;
  letter-spacing: 0.08em;
  color: rgba(187, 199, 198, 0.38);
}
.foot__sep {
  color: rgba(187, 199, 198, 0.24);
}
.foot__link {
  color: rgba(187, 199, 198, 0.5);
  text-decoration: none;
}
.foot__link:active {
  opacity: 0.7;
}

/* ---------- 弹层内容 ---------- */
.sh {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-bottom: 4px;
}
.sh__meta {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  letter-spacing: 0.02em;
  color: rgba(187, 199, 198, 0.72);
}
.sh__date {
  margin: 0;
  font-size: 20px;
  font-weight: 500;
  letter-spacing: -0.01em;
  color: var(--au-white);
}
.err {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.02em;
  color: rgba(253, 233, 255, 0.92);
}
.acts {
  display: flex;
  gap: 10px;
  margin-top: 4px;
}
.acts > * {
  flex: 1;
  min-width: 0;
}
.acts .btn-quit {
  color: rgba(187, 199, 198, 0.72);
}

/* 口径说明 */
.legend {
  gap: 12px;
}
.legend p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--au-silver);
}
.legend em {
  font-style: normal;
  font-weight: 500;
  color: var(--au-mist);
}

/* 报告 / 日志 */
.sum {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 0;
  border-bottom: 1px solid rgba(237, 255, 254, 0.08);
  font-size: 13px;
  line-height: 1.6;
  letter-spacing: 0.01em;
}
.sum .dot {
  margin-top: 7px;
}
.is-ok {
  color: var(--au-lit);
}
.is-warn {
  color: var(--au-mist);
}
.is-bad {
  color: rgba(253, 233, 255, 0.92);
}
.is-strong {
  font-weight: 500;
  color: var(--au-mist);
}
.dot {
  flex: none;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(237, 255, 254, 0.2);
}
.dot--lit {
  background: var(--au-lit);
  box-shadow: 0 0 8px rgba(203, 255, 252, 0.8);
}
.dot--pink {
  background: var(--au-pink);
  box-shadow: 0 0 8px rgba(253, 233, 255, 0.7);
}
.dot--dim {
  background: rgba(237, 255, 254, 0.45);
}
.entries {
  list-style: none;
  margin: 0;
  padding: 0;
}
.entry {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 11px 0;
  border-bottom: 1px solid rgba(237, 255, 254, 0.08);
}
.entry .dot {
  margin-top: 7px;
}
.entry__msg {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-size: 12px;
  line-height: 1.65;
  letter-spacing: 0.01em;
  color: var(--au-silver);
  word-break: break-word;
}
.entry__msg.lv--bad {
  color: rgba(253, 233, 255, 0.92);
}
.entry__msg.lv--warn {
  color: var(--au-mist);
}
.entry__msg.lv--info {
  color: var(--au-silver);
}
.entry__msg.is-bad {
  color: rgba(253, 233, 255, 0.92);
}
.entry--log {
  gap: 10px;
}
.entry__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.entry__top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.entry__name {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.02em;
  color: var(--au-mist);
}
.entry__name.is-bad {
  color: rgba(253, 233, 255, 0.92);
}
.entry__dur {
  flex: none;
  font-size: 10px;
  letter-spacing: 0.04em;
  color: rgba(187, 199, 198, 0.55);
}
.sheet-foot {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid rgba(237, 255, 254, 0.08);
}
.sheet-foot p {
  margin: 0;
  font-size: 10px;
  line-height: 1.7;
  letter-spacing: 0.04em;
  color: rgba(187, 199, 198, 0.72);
}

/* ---------- 动效（只动 transform/opacity） ---------- */
@keyframes au-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .is-spin {
    animation: none;
  }
}

/* 窄屏（320）：只收字号与留白，不动结构 */
@media (max-width: 340px) {
  .ident__ava {
    width: 56px;
    height: 56px;
  }
  .ident__name {
    font-size: 16px;
  }
  .foot {
    margin-top: 26px;
  }
}
</style>
