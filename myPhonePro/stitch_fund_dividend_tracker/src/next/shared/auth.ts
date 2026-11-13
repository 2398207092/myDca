/**
 * 登录态：token 校验沿用 src/api/request.ts 的初始化逻辑（401 会跳 #/login）。
 * 路由守卫 await bootstrapAuth()，保证进入业务页前 token 已校验。
 */
import { reactive } from 'vue'
import { clearToken, initAuth } from '@/api/request'
import { getUserInfo, logout as apiLogout } from '@/api/auth'
import type { LoginResp, UserInfoResp } from '@/types/api'

export const authState = reactive({
  ready: false,
  authed: false,
  user: null as UserInfoResp | null,
})

let readyPromise: Promise<boolean> | null = null

async function pullUser(): Promise<void> {
  try {
    authState.user = await getUserInfo()
  } catch {
    authState.user = null
  }
}

/** 只执行一次：校验本地 token（含本地开发自动登录），失败则变成未登录 */
export function bootstrapAuth(): Promise<boolean> {
  if (readyPromise) return readyPromise
  readyPromise = (async () => {
    const ok = await initAuth()
    authState.authed = ok
    if (ok) await pullUser()
    authState.ready = true
    return ok
  })()
  return readyPromise
}

/** 登录成功（api/auth 各登录函数已写入 token） */
export async function markLoggedIn(resp: LoginResp): Promise<void> {
  authState.authed = Boolean(resp.token)
  await pullUser()
}

export function resetAuth(): void {
  clearToken()
  authState.authed = false
  authState.user = null
  readyPromise = null
}

export async function signOut(): Promise<void> {
  try {
    await apiLogout()
  } catch {
    /* 后端退出失败也要清本地 token */
  }
  resetAuth()
}
