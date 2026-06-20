import { get, setToken } from './request'

export interface TokenResp {
  token: string
}

export async function fetchToken(): Promise<TokenResp> {
  const data = await get<TokenResp>('/auth/token')
  setToken(data.token)
  return data
}
