import { get, post, setToken } from './request'
import type { LoginResp, UserInfoResp } from '@/types/api'

export interface TokenResp {
  token: string
}

export interface SendCodeReq {
  email: string
  type: 'login' | 'register' | 'set_password'
}

export interface LoginReq {
  email: string
  code?: string
  password?: string
}

export interface SetPasswordReq {
  email: string
  password: string
  code?: string
}

/** 获取应用级 Token（兼容旧版） */
export async function fetchToken(): Promise<TokenResp> {
  const data = await get<TokenResp>('/auth/token')
  setToken(data.token)
  return data
}

/** 发送验证码 */
export async function sendCode(req: SendCodeReq): Promise<void> {
  await post<void>('/auth/send-code', req)
}

/** 验证码登录（首次登录自动注册） */
export async function loginByCode(req: LoginReq): Promise<LoginResp> {
  const data = await post<LoginResp>('/auth/login', req)
  if (data.token) {
    setToken(data.token)
  }
  return data
}

/** 密码登录 */
export async function loginByPassword(req: LoginReq): Promise<LoginResp> {
  const data = await post<LoginResp>('/auth/login-pwd', req)
  if (data.token) {
    setToken(data.token)
  }
  return data
}

/** 密码注册（第一步：发送验证码到邮箱） */
export async function registerByPassword(req: SendCodeReq): Promise<void> {
  await post<void>('/auth/register-pwd', req)
}

/** 密码注册（第二步：验证码确认） */
export async function confirmRegisterByPassword(req: SetPasswordReq): Promise<LoginResp> {
  const data = await post<LoginResp>('/auth/register-pwd-confirm', req)
  if (data.token) {
    setToken(data.token)
  }
  return data
}

/** 设置密码 */
export async function setPassword(req: SetPasswordReq): Promise<void> {
  await post<void>('/auth/set-password', req)
}

/** 获取当前用户信息 */
export async function getUserInfo(): Promise<UserInfoResp> {
  return await get<UserInfoResp>('/auth/user-info')
}

/** 退出登录 */
export async function logout(): Promise<void> {
  await post<void>('/auth/logout')
}
