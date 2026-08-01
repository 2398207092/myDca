<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  sendCode as apiSendCode,
  loginByCode,
  loginByPassword,
  registerByPassword,
  confirmRegisterByPassword,
} from '@/api/auth'

const router = useRouter()

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
  <div class="min-h-screen bg-page-bg flex flex-col">
    <!-- Header -->
    <header class="pt-14 pb-2 px-gutter max-w-[400px] mx-auto w-full">
      <div class="flex items-center gap-2.5 mb-1">
        <span class="material-symbols-outlined text-brand text-2xl">park</span>
        <h1 class="font-display text-2xl text-brand font-semibold">种树</h1>
      </div>
      <p class="font-body text-sm text-text-tertiary ml-[2px]">基金分红追踪器</p>
    </header>

    <!-- Card -->
    <div class="flex-1 max-w-[400px] mx-auto w-full px-gutter pb-10">
      <div class="bg-card-bg rounded-xl card-shadow border border-border-light/40 overflow-hidden">
        <!-- Main Tabs -->
        <div class="flex border-b border-border-light/40">
          <button
            v-for="tab in (['login', 'register'] as const)"
            :key="tab"
            @click="switchMainTab(tab)"
            class="flex-1 py-3.5 text-center font-body text-sm font-medium transition-colors relative"
            :class="mainTab === tab ? 'text-brand' : 'text-text-tertiary hover:text-text-secondary'"
          >
            {{ tab === 'login' ? '登录' : '注册' }}
            <span v-if="mainTab === tab"
                  class="absolute bottom-0 left-1/2 -translate-x-1/2 w-10 h-[2.5px] bg-brand rounded-full" />
          </button>
        </div>

        <!-- Content -->
        <div class="p-5 space-y-4">
          <!-- Sub tabs (login) -->
          <div v-if="mainTab === 'login'" class="flex gap-1 bg-page-bg rounded-lg p-1">
            <button
              v-for="sub in (['code', 'password'] as const)"
              :key="sub"
              @click="switchLoginSubTab(sub)"
              class="flex-1 py-1.5 text-center font-body text-xs rounded-md transition-all"
              :class="loginSubTab === sub
                ? 'bg-card-bg text-text-primary shadow-sm font-medium'
                : 'text-text-tertiary hover:text-text-secondary'"
            >
              {{ sub === 'code' ? '验证码登录' : '密码登录' }}
            </button>
          </div>

          <!-- Sub tabs (register) -->
          <div v-if="mainTab === 'register'" class="flex gap-1 bg-page-bg rounded-lg p-1">
            <button
              v-for="sub in (['code', 'password'] as const)"
              :key="sub"
              @click="switchRegisterSubTab(sub)"
              class="flex-1 py-1.5 text-center font-body text-xs rounded-md transition-all"
              :class="registerSubTab === sub
                ? 'bg-card-bg text-text-primary shadow-sm font-medium'
                : 'text-text-tertiary hover:text-text-secondary'"
            >
              {{ sub === 'code' ? '验证码注册' : '密码注册' }}
            </button>
          </div>

          <!-- Email input -->
          <div>
            <label class="font-body text-xs text-text-secondary mb-1.5 block">邮箱</label>
            <div class="flex gap-0">
              <input
                v-model="emailPrefix"
                type="text"
                placeholder="用户名"
                class="flex-1 px-3 py-2.5 bg-page-bg rounded-l-lg border border-border-light/60 text-sm
                       font-body text-text-primary placeholder:text-text-tertiary/50
                       focus:outline-none focus:border-brand/40 focus:ring-1 focus:ring-brand/20 transition-all"
              />
              <div class="relative">
                <select
                  v-if="!useCustomDomain"
                  v-model="emailDomain"
                  class="h-full px-2 py-2.5 bg-page-bg border border-l-0 border-border-light/60 rounded-r-lg
                         text-sm font-body text-text-primary appearance-none cursor-pointer
                         focus:outline-none focus:border-brand/40 focus:ring-1 focus:ring-brand/20
                         min-w-[100px] transition-all"
                >
                  <option
                    v-for="suffix in emailSuffixes"
                    :key="suffix"
                    :value="suffix"
                  >{{ suffix }}</option>
                  <option value="__custom__">其他...</option>
                </select>
                <input
                  v-else
                  v-model="customDomain"
                  type="text"
                  placeholder="@domain.com"
                  class="h-full px-2 py-2.5 bg-page-bg border border-l-0 border-border-light/60 rounded-r-lg
                         text-sm font-body text-text-primary placeholder:text-text-tertiary/50
                         focus:outline-none focus:border-brand/40 focus:ring-1 focus:ring-brand/20
                         min-w-[120px] transition-all"
                />
                <span v-if="!useCustomDomain"
                      class="absolute right-2 top-1/2 -translate-y-1/2 text-text-tertiary text-[10px] pointer-events-none material-symbols-outlined">
                  arrow_drop_down
                </span>
              </div>
            </div>
          </div>

          <!-- Code input (shown for code login, code register, and password register step 2) -->
          <div v-if="
            (mainTab === 'login' && loginSubTab === 'code') ||
            (mainTab === 'register' && registerSubTab === 'code') ||
            (mainTab === 'register' && registerSubTab === 'password' && pwdRegisterStep === 'verify')">
            <label class="font-body text-xs text-text-secondary mb-1.5 block">验证码</label>
            <div class="flex gap-2">
              <input
                v-model="code"
                type="text"
                maxlength="6"
                placeholder="6 位验证码"
                class="flex-1 px-3 py-2.5 bg-page-bg rounded-lg border border-border-light/60 text-sm
                       font-body text-text-primary placeholder:text-text-tertiary/50 tracking-[6px] text-center
                       focus:outline-none focus:border-brand/40 focus:ring-1 focus:ring-brand/20 transition-all"
              />
              <button
                @click="handleSendCode"
                :disabled="!canSendCode"
                class="px-3.5 py-2.5 rounded-lg font-body text-xs font-medium whitespace-nowrap transition-all"
                :class="canSendCode
                  ? 'bg-brand text-white active:scale-[0.98]'
                  : 'bg-page-bg text-text-tertiary cursor-not-allowed'"
              >
                <span v-if="sendingCode">发送中...</span>
                <span v-else-if="countdown > 0">{{ countdown }}s</span>
                <span v-else>发送验证码</span>
              </button>
            </div>
          </div>

          <!-- Password input (shown for password login, password register step 1) -->
          <div v-if="
            (mainTab === 'login' && loginSubTab === 'password') ||
            (mainTab === 'register' && registerSubTab === 'password' && pwdRegisterStep === 'form')">
            <label class="font-body text-xs text-text-secondary mb-1.5 block">密码</label>
            <input
              v-model="password"
              type="password"
              placeholder="6-20 位字母+数字组合"
              class="w-full px-3 py-2.5 bg-page-bg rounded-lg border border-border-light/60 text-sm
                     font-body text-text-primary placeholder:text-text-tertiary/50
                     focus:outline-none focus:border-brand/40 focus:ring-1 focus:ring-brand/20 transition-all"
            />
          </div>

          <!-- Confirm password (password register step 1) -->
          <div v-if="mainTab === 'register' && registerSubTab === 'password' && pwdRegisterStep === 'form'">
            <label class="font-body text-xs text-text-secondary mb-1.5 block">确认密码</label>
            <input
              v-model="confirmPassword"
              type="password"
              placeholder="再次输入密码"
              class="w-full px-3 py-2.5 bg-page-bg rounded-lg border border-border-light/60 text-sm
                     font-body text-text-primary placeholder:text-text-tertiary/50
                     focus:outline-none focus:border-brand/40 focus:ring-1 focus:ring-brand/20 transition-all"
            />
          </div>

          <!-- Step hint (password register) -->
          <div v-if="mainTab === 'register' && registerSubTab === 'password' && pwdRegisterStep === 'verify'"
               class="text-center font-body text-xs text-text-tertiary">
            验证码已发送至 <span class="text-text-primary font-medium">{{ fullEmail }}</span>
          </div>

          <!-- Error message -->
          <div v-if="errorMsg"
               class="py-2 px-3 bg-error/5 rounded-lg border border-error/10">
            <p class="font-body text-xs text-error">{{ errorMsg }}</p>
          </div>

          <!-- Submit button -->
          <button
            @click="handleSubmit"
            :disabled="!canLogin"
            class="w-full py-3 rounded-lg font-body text-sm font-medium transition-all"
            :class="canLogin
              ? 'bg-brand text-white active:scale-[0.98] shadow-sm'
              : 'bg-page-bg text-text-tertiary cursor-not-allowed'"
          >
            {{ buttonText }}
          </button>

          <!-- Bottom hint -->
          <p class="text-center font-body text-[11px] text-text-tertiary">
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
        </div>
      </div>
    </div>
  </div>
</template>
