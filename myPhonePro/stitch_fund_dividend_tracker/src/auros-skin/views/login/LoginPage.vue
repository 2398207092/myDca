<script setup lang="ts">
/**
 * 登录 / 注册（Auros 全屏表单页 · 2026-11-11 卡片化）
 * 结构：品牌卡 + 表单卡（卡 = 一个信息单位）；卡内沿用 AurosField / AurosButton。
 * 业务逻辑与原页面逐行一致，只换外壳与呈现。
 */
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  sendCode as apiSendCode,
  loginByCode,
  loginByPassword,
  registerByPassword,
  confirmRegisterByPassword,
  devQuickLogin,
} from '@/api/auth'
import AurosCard from '@/auros-skin/components/auros/AurosCard.vue'
import AurosField from '@/auros-skin/components/auros/AurosField.vue'
import AurosButton from '@/auros-skin/components/auros/AurosButton.vue'

const router = useRouter()

/** 本地开发环境才显示「一键登录」，生产域名不出现 */
const isLocalEnv = ['localhost', '127.0.0.1'].includes(location.hostname)
const quickLoading = ref(false)
async function handleQuickLogin() {
  quickLoading.value = true
  errorMsg.value = ''
  try {
    await devQuickLogin()
    router.push('/')
  } catch (e: any) {
    errorMsg.value = e.message || '一键登录失败'
  } finally {
    quickLoading.value = false
  }
}

// ── Tabs ──
const mainTab = ref<'login' | 'register'>('login')
const loginSubTab = ref<'code' | 'password'>('code')
const registerSubTab = ref<'code' | 'password'>('code')

// ── Form ──
const emailPrefix = ref('')
const emailDomain = ref('@qq.com')
const customDomain = ref('')
const useCustomDomain = ref(false)
const code = ref('')
const password = ref('')
const confirmPassword = ref('')

// ── State ──
const sendingCode = ref(false)
const codeSent = ref(false)
const countdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null
const loading = ref(false)
const errorMsg = ref('')

// ── Password register step ──
const pwdRegisterStep = ref<'form' | 'verify'>('form')

const emailSuffixes = [
  '@qq.com', '@163.com', '@outlook.com', '@gmail.com',
  '@126.com', '@sina.com', '@foxmail.com', '@yeah.net',
]

const emailSuffixList = computed(() => {
  if (useCustomDomain.value) return [...emailSuffixes, '其他']
  return emailSuffixes
})

const fullEmail = computed(() => {
  if (useCustomDomain.value) return emailPrefix.value + customDomain.value
  return emailPrefix.value + emailDomain.value
})

const isValidEmail = computed(() => {
  return /^[\w.-]+@[\w.-]+\.\w{2,}$/.test(fullEmail.value)
})

const canSendCode = computed(() => {
  return isValidEmail.value && !sendingCode.value && countdown.value === 0
})

const canLogin = computed(() => {
  if (mainTab.value === 'login' && loginSubTab.value === 'code') {
    return isValidEmail.value && code.value.length === 6 && !loading.value
  }
  if (mainTab.value === 'login' && loginSubTab.value === 'password') {
    return isValidEmail.value && password.value.length >= 6 && !loading.value
  }
  if (mainTab.value === 'register' && registerSubTab.value === 'code') {
    return isValidEmail.value && code.value.length === 6 && !loading.value
  }
  if (mainTab.value === 'register' && registerSubTab.value === 'password') {
    if (pwdRegisterStep.value === 'form') {
      return isValidEmail.value && password.value.length >= 6 && password.value === confirmPassword.value && !loading.value
    }
    return code.value.length === 6 && !loading.value
  }
  return false
})

const buttonText = computed(() => {
  if (loading.value) return '处理中...'
  if (mainTab.value === 'login') return '登录'
  if (mainTab.value === 'register') return '注册'
  return '确定'
})

// ── Email domain select ──
function selectDomain(suffix: string) {
  if (suffix === '其他') {
    useCustomDomain.value = true
    customDomain.value = ''
    emailDomain.value = ''
    return
  }
  useCustomDomain.value = false
  emailDomain.value = suffix
}

function switchToCustomDomain() {
  useCustomDomain.value = true
  customDomain.value = '@'
}

// ── Send code ──
async function handleSendCode() {
  if (!canSendCode.value) return
  const type = mainTab.value === 'login' ? 'login' as const
    : mainTab.value === 'register' ? 'register' as const
    : 'login' as const

  sendingCode.value = true
  errorMsg.value = ''
  try {
    await apiSendCode({ email: fullEmail.value, type })
    codeSent.value = true
    errorMsg.value = ''
    startCountdown()
  } catch (e: any) {
    errorMsg.value = e.message || '发送失败'
  } finally {
    sendingCode.value = false
  }
}

function startCountdown() {
  countdown.value = 60
  countdownTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      if (countdownTimer) clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

// ── Submit ──
async function handleSubmit() {
  if (!canLogin.value) return
  loading.value = true
  errorMsg.value = ''

  try {
    if (mainTab.value === 'login') {
      if (loginSubTab.value === 'code') {
        await loginByCode({ email: fullEmail.value, code: code.value })
      } else {
        await loginByPassword({ email: fullEmail.value, password: password.value })
      }
      router.push({ name: 'home' })
    } else {
      if (registerSubTab.value === 'code') {
        await loginByCode({ email: fullEmail.value, code: code.value })
        router.push({ name: 'home' })
      } else {
        if (pwdRegisterStep.value === 'form') {
          await registerByPassword({ email: fullEmail.value, type: 'register' })
          pwdRegisterStep.value = 'verify'
          startCountdown()
          errorMsg.value = ''
        } else {
          await confirmRegisterByPassword({ email: fullEmail.value, password: password.value, code: code.value })
          router.push({ name: 'home' })
        }
      }
    }
  } catch (e: any) {
    errorMsg.value = e.message || '操作失败'
  } finally {
    loading.value = false
  }
}

// ── Switch main tab ──
function switchMainTab(tab: 'login' | 'register') {
  mainTab.value = tab
  errorMsg.value = ''
  code.value = ''
  password.value = ''
  confirmPassword.value = ''
  pwdRegisterStep.value = 'form'
}

function switchLoginSubTab(tab: 'code' | 'password') {
  loginSubTab.value = tab
  errorMsg.value = ''
  code.value = ''
  password.value = ''
}

function switchRegisterSubTab(tab: 'code' | 'password') {
  registerSubTab.value = tab
  errorMsg.value = ''
  code.value = ''
  password.value = ''
  confirmPassword.value = ''
  pwdRegisterStep.value = 'form'
}
</script>

<template>
  <div class="auros page">
    <main class="body">
      <!-- 卡 01：品牌 -->
      <AurosCard class="intro-card">
        <div class="intro__ring" aria-hidden="true">
          <svg viewBox="0 0 160 160" width="160" height="160" fill="none">
            <circle cx="80" cy="80" r="74" stroke="rgba(203, 255, 252, 0.07)" stroke-width="1" stroke-dasharray="2 6" />
            <circle cx="80" cy="80" r="62" stroke="rgba(237, 255, 254, 0.1)" stroke-width="1" />
            <circle cx="80" cy="80" r="44" stroke="rgba(237, 255, 254, 0.06)" stroke-width="1" />
            <circle cx="80" cy="18" r="2.4" fill="var(--au-pink)" />
            <circle cx="138" cy="108" r="1.8" fill="var(--au-lit)" />
            <circle cx="22" cy="92" r="1.4" fill="rgba(237, 255, 254, 0.5)" />
          </svg>
        </div>
        <p class="intro__kicker">MYDCA · SIGN IN</p>
        <p class="intro__brand">种树</p>
        <p class="intro__sub">基金分红追踪器</p>
      </AurosCard>

      <!-- 卡 02：表单 -->
      <AurosCard class="form-card" tone="soft">
        <!-- 主分段：登录 / 注册 -->
        <div class="seg" role="tablist" aria-label="登录或注册">
          <button
            v-for="tab in (['login', 'register'] as const)"
            :key="tab"
            type="button"
            role="tab"
            :aria-selected="mainTab === tab"
            class="seg__item"
            :class="{ 'is-active': mainTab === tab }"
            @click="switchMainTab(tab)"
          >{{ tab === 'login' ? '登录' : '注册' }}</button>
        </div>

        <!-- 子分段：验证码 / 密码 -->
        <div class="seg seg--sub" role="tablist" aria-label="登录方式">
          <template v-if="mainTab === 'login'">
            <button
              v-for="sub in (['code', 'password'] as const)"
              :key="sub"
              type="button"
              role="tab"
              :aria-selected="loginSubTab === sub"
              class="seg__item"
              :class="{ 'is-active': loginSubTab === sub }"
              @click="switchLoginSubTab(sub)"
            >{{ sub === 'code' ? '验证码登录' : '密码登录' }}</button>
          </template>
          <template v-else>
            <button
              v-for="sub in (['code', 'password'] as const)"
              :key="sub"
              type="button"
              role="tab"
              :aria-selected="registerSubTab === sub"
              class="seg__item"
              :class="{ 'is-active': registerSubTab === sub }"
              @click="switchRegisterSubTab(sub)"
            >{{ sub === 'code' ? '验证码注册' : '密码注册' }}</button>
          </template>
        </div>

        <!-- 表单字段 -->
        <div class="fields">
          <!-- 邮箱 -->
          <AurosField v-model="emailPrefix" label="邮箱" placeholder="用户名" />

          <div class="domains">
            <button
              v-for="suffix in emailSuffixes"
              :key="suffix"
              type="button"
              class="chip"
              :class="{ 'is-on': !useCustomDomain && emailDomain === suffix }"
              @click="selectDomain(suffix)"
            >{{ suffix }}</button>
            <button
              type="button"
              class="chip"
              :class="{ 'is-on': useCustomDomain }"
              @click="selectDomain('其他')"
            >其他…</button>
          </div>

          <AurosField
            v-if="useCustomDomain"
            v-model="customDomain"
            label="自定义域名"
            placeholder="@domain.com"
          />

          <p class="preview">
            <i class="dot" :class="{ 'dot--lit': isValidEmail }" aria-hidden="true"></i>
            <span>{{ fullEmail || '用户名@域名' }}</span>
          </p>

          <!-- 验证码 -->
          <div
            v-if="
              (mainTab === 'login' && loginSubTab === 'code') ||
              (mainTab === 'register' && registerSubTab === 'code') ||
              (mainTab === 'register' && registerSubTab === 'password' && pwdRegisterStep === 'verify')"
            class="field"
          >
            <span class="field__label">验证码</span>
            <span class="field__box">
              <input
                v-model="code"
                class="field__input is-code"
                type="text"
                inputmode="numeric"
                maxlength="6"
                placeholder="6 位验证码"
              />
              <button
                type="button"
                class="send"
                :disabled="!canSendCode"
                @click="handleSendCode"
              >
                <span v-if="sendingCode">发送中...</span>
                <span v-else-if="countdown > 0">{{ countdown }}s</span>
                <span v-else>发送验证码</span>
              </button>
            </span>
          </div>

          <!-- 密码 -->
          <AurosField
            v-if="
              (mainTab === 'login' && loginSubTab === 'password') ||
              (mainTab === 'register' && registerSubTab === 'password' && pwdRegisterStep === 'form')"
            v-model="password"
            label="密码"
            type="password"
            placeholder="6-20 位字母+数字组合"
          />

          <!-- 确认密码 -->
          <AurosField
            v-if="mainTab === 'register' && registerSubTab === 'password' && pwdRegisterStep === 'form'"
            v-model="confirmPassword"
            label="确认密码"
            type="password"
            placeholder="再次输入密码"
          />

          <!-- 密码注册第二步提示 -->
          <p
            v-if="mainTab === 'register' && registerSubTab === 'password' && pwdRegisterStep === 'verify'"
            class="step-hint"
          >
            验证码已发送至 <span class="step-hint__mail">{{ fullEmail }}</span>
          </p>
        </div>

        <!-- 错误 -->
        <p v-if="errorMsg" class="err" role="alert">{{ errorMsg }}</p>

        <!-- 主按钮（全页唯一极光渐变） -->
        <AurosButton variant="primary" block :disabled="!canLogin" @click="handleSubmit">
          {{ buttonText }}
        </AurosButton>

        <!-- 本地开发一键登录 -->
        <AurosButton
          v-if="isLocalEnv && mainTab === 'login'"
          variant="ghost"
          block
          :disabled="quickLoading"
          @click="handleQuickLogin"
        >{{ quickLoading ? '登录中...' : '一键登录（本地开发）' }}</AurosButton>

        <!-- 底部提示 -->
        <p class="hint">
          <template v-if="mainTab === 'login' && loginSubTab === 'code'">
            首次登录将自动注册账号
          </template>
          <template v-else-if="mainTab === 'register' && registerSubTab === 'code'">
            注册成功将自动登录
          </template>
          <template v-else-if="mainTab === 'register' && registerSubTab === 'password' && pwdRegisterStep === 'form'">
            注册后需验证邮箱，请确保邮箱可用
          </template>
          <template v-else-if="mainTab === 'register' && registerSubTab === 'password' && pwdRegisterStep === 'verify'">
            验证通过后自动登录
          </template>
          <template v-else>&nbsp;</template>
        </p>
      </AurosCard>
    </main>
  </div>
</template>

<style scoped>
/* ============================================================
 * 登录 / 注册 · Auros 卡片化（两张卡：品牌 + 表单）
 * 字号只取 10/12/13/16/20/26/40；字重 400/500；圆角 6/16/50%
 * 阴影只允许"光"（0 0）；极光渐变全页只给主按钮
 * ============================================================ */
.page {
  min-height: 100svh;
  display: flex;
  flex-direction: column;
  padding-top: env(safe-area-inset-top, 0px);
  box-sizing: border-box;
}
.body {
  flex: 1;
  width: 100%;
  max-width: 400px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  padding: 12px 16px calc(env(safe-area-inset-bottom, 0px) + 20px);
  box-sizing: border-box;
}

/* ---------- 卡 01：品牌 ---------- */
.intro-card {
  position: relative;
  overflow: hidden;
  margin-top: auto;
}
.intro__ring {
  position: absolute;
  right: -18px;
  top: -34px;
  width: 160px;
  height: 160px;
  z-index: 0;
  pointer-events: none;
  -webkit-mask-image: linear-gradient(90deg, transparent 0%, rgba(0, 0, 0, 0.5) 30%, var(--au-mask) 62%);
  mask-image: linear-gradient(90deg, transparent 0%, rgba(0, 0, 0, 0.5) 30%, var(--au-mask) 62%);
  animation: au-breathe 9s ease-in-out infinite;
}
.intro__ring::before {
  content: '';
  position: absolute;
  inset: -24px 0 -24px -24px;
  background: radial-gradient(circle at 52% 46%, rgba(0, 130, 124, 0.24) 0%, rgba(0, 38, 36, 0) 64%);
}
.intro-card > p {
  position: relative;
  z-index: 1;
}
.intro__kicker {
  margin: 0 0 10px;
  font-size: 10px;
  letter-spacing: 0.16em;
  color: rgba(187, 199, 198, 0.62);
}
.intro__brand {
  margin: 0;
  font-size: 40px;
  font-weight: 500;
  line-height: 1;
  letter-spacing: -0.02em;
  color: var(--au-white);
}
.intro__sub {
  margin: 10px 0 0;
  font-size: 13px;
  letter-spacing: 0.06em;
  color: var(--au-silver);
}

/* ---------- 卡 02：表单 ---------- */
.form-card {
  margin-bottom: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* 分段控制 */
.seg {
  display: flex;
  gap: 4px;
  padding: 3px;
  border-radius: 6px;
  background: rgba(0, 55, 52, 0.55);
}
.seg__item {
  flex: 1;
  height: 34px;
  border: 0;
  border-radius: 6px;
  background: none;
  font-family: inherit;
  font-size: 13px;
  letter-spacing: 0.04em;
  color: rgba(187, 199, 198, 0.75);
  transition: background 200ms var(--au-ease), color 200ms var(--au-ease);
}
.seg__item.is-active {
  background: rgba(203, 255, 252, 0.14);
  color: var(--au-mist);
}
.seg__item:active {
  transform: scale(0.99);
}
.seg--sub .seg__item {
  height: 30px;
  font-size: 12px;
  color: rgba(187, 199, 198, 0.6);
}

/* 表单字段 */
.fields {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.domains {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: -6px;
}
.chip {
  height: 26px;
  padding: 0 9px;
  border: 0;
  border-radius: 6px;
  background: rgba(0, 55, 52, 0.55);
  font-family: inherit;
  font-size: 10px;
  letter-spacing: 0.03em;
  color: rgba(187, 199, 198, 0.72);
  transition: background 200ms var(--au-ease), color 200ms var(--au-ease);
}
.chip.is-on {
  background: rgba(203, 255, 252, 0.14);
  color: var(--au-mist);
}
.chip:active {
  transform: scale(0.98);
}

.preview {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: -2px 0 0;
  font-size: 10px;
  letter-spacing: 0.04em;
  color: rgba(187, 199, 198, 0.66);
}
.preview span {
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.dot {
  flex: none;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: rgba(237, 255, 254, 0.2);
}
.dot.dot--lit {
  background: var(--au-lit);
  box-shadow: 0 0 8px rgba(203, 255, 252, 0.8);
}

/* 验证码行（自带「发送验证码」动作，故单独成块） */
.field {
  display: block;
}
.field__label {
  display: block;
  margin-bottom: 7px;
  font-size: 10px;
  letter-spacing: 0.16em;
  color: var(--au-silver);
}
.field__box {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 46px;
  padding: 0 12px;
  border-radius: 6px;
  background: rgba(0, 55, 52, 0.55);
  transition: background 200ms var(--au-ease);
}
.field__box::before {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 1px;
  background: rgba(237, 255, 254, 0.12);
  transition: background 200ms var(--au-ease);
}
.field__box:focus-within {
  background: rgba(0, 67, 63, 0.75);
}
.field__box:focus-within::before {
  background: rgba(203, 255, 252, 0.75);
}
.field__input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: none;
  outline: none;
  color: var(--au-mist);
  font-family: inherit;
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 0.01em;
}
.field__input.is-code {
  letter-spacing: 0.18em;
}
.field__input::placeholder {
  color: rgba(187, 199, 198, 0.45);
  font-weight: 400;
  letter-spacing: 0.01em;
}
.send {
  flex: none;
  height: 30px;
  padding: 0 10px;
  border: 0;
  border-radius: 6px;
  background: rgba(0, 67, 63, 0.85);
  font-family: inherit;
  font-size: 12px;
  letter-spacing: 0.02em;
  color: var(--au-mist);
  transition: background 200ms var(--au-ease), color 200ms var(--au-ease);
}
.send:disabled {
  background: rgba(0, 55, 52, 0.4);
  color: rgba(187, 199, 198, 0.4);
}
.send:active:not(:disabled) {
  transform: scale(0.97);
}

/* 提示 */
.step-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  letter-spacing: 0.02em;
  color: var(--au-silver);
}
.step-hint__mail {
  font-weight: 500;
  color: var(--au-mist);
}
.err {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  letter-spacing: 0.02em;
  color: rgba(253, 233, 255, 0.92);
}
.hint {
  margin: 0;
  text-align: center;
  font-size: 10px;
  line-height: 1.7;
  letter-spacing: 0.06em;
  color: rgba(187, 199, 198, 0.5);
}

@media (prefers-reduced-motion: reduce) {
  .intro__ring {
    animation: none;
  }
}

/* 短屏：只压缩品牌字号与留白，不动结构 */
@media (max-height: 720px) {
  .intro__brand {
    font-size: 26px;
  }
  .body {
    padding-top: 6px;
    padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 12px);
  }
  .form-card {
    gap: 12px;
  }
  .fields {
    gap: 12px;
  }
}
</style>
