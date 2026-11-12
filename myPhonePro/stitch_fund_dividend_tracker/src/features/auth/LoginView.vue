<script setup lang="ts">
/** 登录：验证码登录（首次自动注册）/ 密码登录；本地 DEV 额外提供一键登录。 */
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { devQuickLogin, loginByCode, loginByPassword, sendCode } from '@/api/auth'
import { markLoggedIn } from '@/shared/auth'
import { toast } from '@/shared/ui'
import TextField from '@/shared/components/TextField.vue'
import BtnKey from '@/shared/components/BtnKey.vue'

const router = useRouter()
const route = useRoute()

const mode = ref<'code' | 'password'>('code')
const email = ref('')
const code = ref('')
const password = ref('')
const sending = ref(false)
const submitting = ref(false)
const countdown = ref(0)
const error = ref('')
const isDev = import.meta.env.DEV

const redirect = computed(() => {
  const r = route.query.redirect
  return typeof r === 'string' && r.startsWith('/') ? r : '/now'
})

function validateEmail(): boolean {
  if (!/^[\w.-]+@[\w.-]+\.\w{2,}$/.test(email.value.trim())) {
    error.value = '请输入正确的邮箱地址'
    return false
  }
  error.value = ''
  return true
}

async function onSendCode(): Promise<void> {
  if (!validateEmail() || countdown.value > 0) return
  sending.value = true
  error.value = ''
  try {
    await sendCode({ email: email.value.trim(), type: 'login' })
    toast('验证码已发送，请查收邮箱')
    countdown.value = 60
    const timer = window.setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) window.clearInterval(timer)
    }, 1000)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '发送验证码失败'
  } finally {
    sending.value = false
  }
}

async function onSubmit(): Promise<void> {
  if (submitting.value) return
  if (!validateEmail()) return
  submitting.value = true
  error.value = ''
  try {
    const resp =
      mode.value === 'code'
        ? await loginByCode({ email: email.value.trim(), code: code.value.trim() })
        : await loginByPassword({ email: email.value.trim(), password: password.value })
    await markLoggedIn(resp)
    toast('已登录')
    await router.replace(redirect.value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登录失败'
  } finally {
    submitting.value = false
  }
}

/** 本地开发一键登录（后端需 AUTH_QUICK_LOGIN=true） */
async function onQuickLogin(): Promise<void> {
  if (submitting.value) return
  submitting.value = true
  error.value = ''
  try {
    const resp = await devQuickLogin()
    await markLoggedIn(resp)
    toast('已用本地开发账号登录')
    await router.replace(redirect.value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '一键登录不可用'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-inner">
      <div class="brand-block">
        <h1 class="brand">myDca</h1>
        <p class="brand-sub">资产与分红追踪</p>
      </div>

      <form class="form" @submit.prevent="onSubmit">
        <TextField v-model="email" label="邮箱" type="email" inputmode="email" autocomplete="email" placeholder="name@example.com" />

        <template v-if="mode === 'code'">
          <div class="code-field">
            <TextField v-model="code" label="验证码" type="text" inputmode="numeric" autocomplete="one-time-code" placeholder="6 位验证码" :maxlength="8" />
            <button class="send-code" type="button" :disabled="sending || countdown > 0" @click="onSendCode">
              {{ countdown > 0 ? `${countdown}s` : sending ? '发送中' : '发送验证码' }}
            </button>
          </div>
          <p class="hint micro">首次用验证码登录会自动创建账号。收不到邮件时检查垃圾箱，或改用密码登录。</p>
        </template>
        <template v-else>
          <TextField v-model="password" label="密码" type="password" autocomplete="current-password" placeholder="输入密码" />
          <p class="hint micro">忘记密码？用验证码登录后可在设置里重设。</p>
        </template>

        <p v-if="error" class="error" role="alert">{{ error }}</p>

        <BtnKey variant="primary" block type="submit" :disabled="submitting">
          {{ submitting ? '登录中…' : '进入' }}
        </BtnKey>
      </form>

      <div class="login-links">
        <button class="link" type="button" @click="mode = mode === 'code' ? 'password' : 'code'">
          {{ mode === 'code' ? '切换密码登录' : '切换验证码登录' }}
        </button>
        <span class="micro">不接验证码时，向管理员确认账号状态</span>
      </div>

      <div v-if="isDev" class="dev-block">
        <button class="link" type="button" :disabled="submitting" @click="onQuickLogin">开发环境一键登录</button>
        <p class="micro">仅本地生效：需要后端 AUTH_QUICK_LOGIN=true</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: calc(var(--safe-top) + var(--space-10)) var(--gutter) calc(var(--safe-bottom) + var(--space-10));
  background: var(--canvas);
}
.login-inner {
  width: 100%;
  max-width: 360px;
}
.brand-block {
  margin-bottom: var(--space-8);
}
.brand {
  font-size: 30px;
  font-weight: 600;
  letter-spacing: -0.02em;
}
.brand-sub {
  margin-top: var(--space-2);
  font-size: var(--text-label);
  color: var(--ink-2);
}
.code-field {
  display: flex;
  align-items: flex-end;
  gap: var(--space-3);
}
.code-field :deep(.field) {
  flex: 1;
  margin-bottom: var(--space-4);
}
.send-code {
  flex: none;
  min-height: 52px;
  padding: 0 var(--space-3);
  margin-bottom: var(--space-4);
  border-radius: var(--radius-key);
  border: 1px solid var(--edge);
  background: var(--canvas);
  box-shadow: var(--el-key);
  color: var(--accent);
  font-size: var(--text-label);
  font-weight: 600;
}
.send-code:disabled {
  color: var(--ink-3);
  box-shadow: none;
  background: var(--surface-inset);
}
.hint {
  margin-top: calc(var(--space-2) * -1);
  margin-bottom: var(--space-4);
  line-height: 1.6;
}
.error {
  margin-bottom: var(--space-3);
  font-size: var(--text-label);
  color: var(--danger);
  line-height: 1.5;
}
.login-links {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-top: var(--space-5);
  align-items: flex-start;
}
.link {
  font-size: var(--text-label);
  color: var(--accent);
  min-height: 44px;
  padding: 0 2px;
}
.dev-block {
  margin-top: var(--space-8);
  padding-top: var(--space-4);
  border-top: 1px solid var(--hairline);
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-1);
}
.dev-block .micro {
  color: var(--ink-3);
}
</style>
