/**
 * UI 皮肤包注册表（2026-11-12）
 *
 * 「皮肤」= 一整套前端界面（不是配色）：
 *   classic → /index.html   原版界面（浅色卡片列表）
 *   auros   → /auros.html   Auros 卡片版（深海底色 + 卡片式信息架构）
 *
 * 两套前端共用同一个后端、同一份接口与数据；切换时整页跳转到对应入口
 * （hash 路由路径保持一致，所以切换后仍停在同一个页面）。
 * 选择记在 localStorage，两个入口的 public/skin-boot.js 在首帧前读它并纠正入口。
 */

export type UiSkinId = 'classic' | 'auros'

export const UI_SKIN_STORAGE_KEY = 'mydca.ui_skin'

export interface UiSkinPack {
  id: UiSkinId
  /** 显示名 */
  name: string
  /** 一句话描述 */
  desc: string
  /** 入口 HTML */
  entry: string
}

export const UI_SKIN_PACKS: UiSkinPack[] = [
  { id: 'classic', name: '原版', desc: '暖白底 · 卡片列表', entry: '/index.html' },
  { id: 'auros', name: 'Auros 卡片版', desc: '深海暗色 · 卡片式信息架构', entry: '/auros.html' },
]

export function getUiSkin(): UiSkinId {
  if (typeof window === 'undefined') return 'classic'
  try {
    return window.localStorage.getItem(UI_SKIN_STORAGE_KEY) === 'auros' ? 'auros' : 'classic'
  } catch {
    return 'classic'
  }
}

/** 切到另一套皮肤：写入偏好 + 整页跳到对应入口（保留当前路径 hash） */
export function switchUiSkin(id: UiSkinId): void {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(UI_SKIN_STORAGE_KEY, id)
  } catch {
    /* 隐私模式忽略：本次跳转仍生效 */
  }
  const pack = UI_SKIN_PACKS.find((p) => p.id === id) ?? UI_SKIN_PACKS[0]
  window.location.href = pack.entry + window.location.hash
}
