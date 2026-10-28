import { get, post, put, del } from './request'

export interface ManualAssetItem {
  id: string
  name: string
  type: 'crypto' | 'cash'
  amount: number
  currency: string
  note?: string
  isPrimary?: boolean
  accountId?: string
  assetCode?: string
  quantity?: number
  unitPrice?: number
  priceSource?: string
  priceUpdatedAt?: string
  createdAt: string
  updatedAt: string
}

export interface CreateManualAssetReq {
  name: string
  type: 'crypto' | 'cash'
  amount: number
  currency?: string
  note?: string
  accountId?: string
  assetCode?: string
  quantity?: number
  unitPrice?: number
  priceSource?: string
}

export interface UpdateManualAssetReq {
  name?: string
  type?: string
  amount?: number
  currency?: string
  note?: string
  accountId?: string
  assetCode?: string
  quantity?: number
  unitPrice?: number
  priceSource?: string
}

export async function listManualAssets(): Promise<ManualAssetItem[]> {
  return get<ManualAssetItem[]>('/manual-assets')
}

export async function createManualAsset(req: CreateManualAssetReq): Promise<ManualAssetItem> {
  return post<ManualAssetItem>('/manual-assets', req)
}

export async function updateManualAsset(id: string, req: UpdateManualAssetReq): Promise<ManualAssetItem> {
  return put<ManualAssetItem>(`/manual-assets/${id}`, req)
}

export async function deleteManualAsset(id: string): Promise<void> {
  return del<void>(`/manual-assets/${id}`)
}
