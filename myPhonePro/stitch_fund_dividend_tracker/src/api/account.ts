import { get, post, put, del } from './request'

export interface AccountAsset {
  id: string
  name: string
  type: string
  amount: number
  accountId?: string
  assetCode?: string
  quantity?: number
  unitPrice?: number
  priceSource?: string
  priceUpdatedAt?: string
  currency?: string
  note?: string
  isPrimary?: boolean
}

export interface AccountItem {
  id: string
  name: string
  purpose: string
  baseCurrency: string
  isDefault: boolean
  sortOrder: number
  totalValueCny: number
  assetCount: number
  assets: AccountAsset[]
}

export interface CreateAccountReq {
  name: string
  purpose?: string
  baseCurrency?: string
  sortOrder?: number
}

export interface UpdateAccountReq {
  name?: string
  purpose?: string
  baseCurrency?: string
  isDefault?: boolean
  sortOrder?: number
}

export function getAccounts(): Promise<AccountItem[]> {
  return get<AccountItem[]>('/accounts')
}

export function createAccount(req: CreateAccountReq): Promise<AccountItem> {
  return post<AccountItem>('/accounts', req)
}

export function updateAccount(id: string, req: UpdateAccountReq): Promise<AccountItem> {
  return put<AccountItem>(`/accounts/${id}`, req)
}

export function deleteAccount(id: string): Promise<void> {
  return del<void>(`/accounts/${id}`)
}
