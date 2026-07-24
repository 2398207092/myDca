import type { ApiResponse } from '@/types/api'

const TOKEN_KEY = 'fund_tracker_token'
const BASE_URL = '/api'

// ── 本地开发自动登录（仅 localhost 生效，部署后自动禁用） ──
// 在项目根目录 .env 中配置 VITE_DEV_EMAIL / VITE_DEV_PASSWORD
const DEV_EMAIL = import.meta.env.VITE_DEV_EMAIL as string | undefined
const DEV_PASSWORD = import.meta.env.VITE_DEV_PASSWORD as string | undefined

/** 判断是否本地开发环境 */
const isLocalDev = (): boolean => {
  const host = window.location.hostname
  return host === 'localhost' || host === '127.0.0.1'
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/** 本地开发自动登录 */
async function autoLogin(): Promise<boolean> {
  if (!DEV_EMAIL || !DEV_PASSWORD) return false
  try {
    const res = await fetch(BASE_URL + '/auth/login-pwd', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: DEV_EMAIL, password: DEV_PASSWORD }),
    })
    if (!res.ok) return false
    const json = await res.json()
    if (json.code === 200 && json.data?.token) {
      setToken(json.data.token)
      return true
    }
    return false
  } catch {
    return false
  }
}

/** 检查当前 Token 是否有效（已登录状态），无需跳转登录页 */
export async function initAuth(): Promise<boolean> {
  let token = getToken()

  // 本地开发：无 Token 时尝试自动登录
  if (!token && isLocalDev()) {
    const ok = await autoLogin()
    if (ok) {
      token = getToken()
    }
  }

  if (!token) return false

  try {
    const res = await fetch(BASE_URL + '/auth/user-info', {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
    })
    if (!res.ok) {
      clearToken()
      return false
    }
    const json = await res.json()
    // code === 200 且 email 不是 "unknown" 才算有效登录
    // （旧版 Token 无 userId，后端会返回 email=unknown）
    const valid = json.code === 200 && json.data?.email && json.data.email !== 'unknown'
    if (!valid && isLocalDev()) {
      // 本地开发：Token 无效时再试一次自动登录
      clearToken()
      const ok = await autoLogin()
      if (ok) return true
    }
    return valid
  } catch {
    clearToken()
    return false
  }
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    if (res.status === 401) {
      clearToken()
      window.location.hash = '#/login'
      throw new Error('未认证')
    }
    const err = await res.json().catch(() => ({}))
    throw new Error(err.message || `请求失败: ${res.status}`)
  }
  const json: ApiResponse<T> = await res.json()
  if (json.code !== 200) {
    throw new Error(json.message || '接口异常')
  }
  return json.data
}

function getHeaders(): Record<string, string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }
  const token = getToken()
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  return headers
}

export async function get<T>(url: string, params?: Record<string, string | number | undefined>): Promise<T> {
  const query: string[] = []
  if (params) {
    for (const [key, val] of Object.entries(params)) {
      if (val !== undefined && val !== null && val !== '') {
        query.push(`${encodeURIComponent(key)}=${encodeURIComponent(val)}`)
      }
    }
  }
  const fullUrl = BASE_URL + url + (query.length ? '?' + query.join('&') : '')
  const res = await fetch(fullUrl, { headers: getHeaders() })
  return handleResponse<T>(res)
}

export async function post<T>(url: string, body?: unknown): Promise<T> {
  const res = await fetch(BASE_URL + url, {
    method: 'POST',
    headers: getHeaders(),
    body: body ? JSON.stringify(body) : undefined,
  })
  return handleResponse<T>(res)
}

export async function put<T>(url: string, body?: unknown): Promise<T> {
  const res = await fetch(BASE_URL + url, {
    method: 'PUT',
    headers: getHeaders(),
    body: body ? JSON.stringify(body) : undefined,
  })
  return handleResponse<T>(res)
}

export async function del<T>(url: string): Promise<T> {
  const res = await fetch(BASE_URL + url, {
    method: 'DELETE',
    headers: getHeaders(),
  })
  return handleResponse<T>(res)
}
