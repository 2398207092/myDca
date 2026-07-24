import type { ApiResponse } from '@/types/api'

const TOKEN_KEY = 'fund_tracker_token'
const BASE_URL = '/api'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/** 检查当前 Token 是否有效（已登录状态），无需跳转登录页 */
export async function initAuth(): Promise<boolean> {
  const token = getToken()
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
    return json.code === 200
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
