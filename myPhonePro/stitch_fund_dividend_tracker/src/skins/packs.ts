/**
 * UI 皮肤包注册表（2026-11-13 三套皮肤）
 *
 * 「皮肤」= 一整套前端界面（不是配色）：
 *   classic → /index.html   原版界面（浅色卡片列表）
 *   auros   → /auros.html   Auros 卡片版（深海底色 + 卡片式信息架构）
 *   next    → /next.html    三面版（现在 / 过去 / 未来，分段与谷底）
 *
 * 三套前端共用同一个后端、同一份接口与数据；切换时整页跳到对应入口。
 * 原版 ⇄ Auros 的路由路径一致，切换时保留当前 hash；三面版路由完全不同
 * （#/now #/past #/future），切进/切出时回它自己的首页，避免落在不存在的路由上。
 * 选择记在 localStorage，三个入口的 public/skin-boot.js 在首帧前读它并纠正入口。
 */

export type UiSkinId = 'classic' | 'auros' | 'next'

export const UI_SKIN_STORAGE_KEY = 'mydca.ui_skin'

export interface UiSkinPack {
  id: UiSkinId
  /** 显示名 */
  name: string
  /** 一句话描述 */
  desc: string
  /** 入口 HTML */
  entry: string
  /** 该皮肤的首页 hash（跨皮肤切换时跳这里） */
  homeHash: string
}

export const UI_SKIN_PACKS: UiSkinPack[] = [
  { id: 'classic', name: '原版', desc: '暖白底 · 卡片列表', entry: '/index.html', homeHash: '#/' },
  { id: 'auros', name: 'Auros 卡片版', desc: '深海暗色 · 卡片式信息架构', entry: '/auros.html', homeHash: '#/' },
  {
    id: 'next',
    name: '三面版',
    desc: '现在 / 过去 / 未来 · 分段与谷底',
    entry: '/next.html',
    homeHash: '#/now',
  },
]

/** 路由路径互通的两套皮肤：切换时保留当前 hash */
const HASH_COMPATIBLE: UiSkinId[] = ['classic', 'auros']

export function getUiSkin(): UiSkinId {
  if (typeof window === 'undefined') return 'classic'
  try {
    const raw = window.localStorage.getItem(UI_SKIN_STORAGE_KEY)
    return UI_SKIN_PACKS.some((p) => p.id === raw) ? (raw as UiSkinId) : 'classic'
  } catch {
    return 'classic'
  }
}

/** 切到另一套皮肤：写入偏好 + 整页跳到对应入口 */
export function switchUiSkin(id: UiSkinId): void {
  if (typeof window === 'undefined') return
  const from = getUiSkin()
  try {
    window.localStorage.setItem(UI_SKIN_STORAGE_KEY, id)
  } catch {
    /* 隐私模式忽略：本次跳转仍生效 */
  }
  const pack = UI_SKIN_PACKS.find((p) => p.id === id) ?? UI_SKIN_PACKS[0]
  const keepHash =
    from !== id && HASH_COMPATIBLE.includes(from) && HASH_COMPATIBLE.includes(id) && Boolean(window.location.hash)
  window.location.href = pack.entry + (keepHash ? window.location.hash : pack.homeHash)
}
