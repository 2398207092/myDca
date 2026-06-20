import type { ApiResponse } from '@/types/api'

const TOKEN_KEY = 'fund_tracker_token'
const BASE_URL = '/api'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

/** Initialize auth token from backend on app startup */
export async function initAuth(): Promise<void> {
  if (getToken()) return // already have a token
  try {
    const res = await fetch(BASE_URL + '/auth/token', {
      headers: { 'Content-Type': 'application/json' },
    })
    const json = await res.json()
    if (json.code === 200 && json.data?.token) {
      setToken(json.data.token)
    }
  } catch (e) {
    console.warn('[Auth] 获取 Token 失败，请求可能返回 401:', e)
  }
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    if (res.status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      // Auto re-auth: try to get token and reload
      await initAuth()
      window.location.reload()
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
