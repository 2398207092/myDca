import { get, put } from './request'

export interface UserProfile {
  id: string
  name: string
  avatar: string
  membership: string
  membershipExpiry: string | null
  phone: string
  version: string
}

export interface UserSettings {
  currency: string
  currencyLabel: string
  forecastHorizon: string
  customForecastValue: number | null
  notificationsEnabled: boolean
}

export interface UpdateProfileReq {
  name?: string
  avatar?: string
  phone?: string
}

export interface UpdateSettingsReq {
  currency?: string
  currencyLabel?: string
  forecastHorizon?: string
  customForecastValue?: number | null
  notificationsEnabled?: boolean
}

export async function getProfile(): Promise<UserProfile> {
  return get<UserProfile>('/user/profile')
}

export async function updateProfile(req: UpdateProfileReq): Promise<UserProfile> {
  return put<UserProfile>('/user/profile', req)
}

export async function getSettings(): Promise<UserSettings> {
  return get<UserSettings>('/user/settings')
}

export async function updateSettings(req: UpdateSettingsReq): Promise<UserSettings> {
  return put<UserSettings>('/user/settings', req)
}
