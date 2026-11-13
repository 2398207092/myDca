/**
 * 全局 UI 状态：底部 Sheet 栈（当前只开一个）、全局刷新信号、Toast。
 * 不引状态库：三个 ref 足够覆盖这个规模。
 */
import { reactive } from 'vue'

export type SheetName =
  | 'action'
  | 'transaction'
  | 'dividend'
  | 'snapshot'
  | 'dca'
  | 'expense'
  | 'holding'
  | 'settings'
  | 'composition'
  | 'event'
  | 'holding-create'
  | 'account-create'
  | 'cash-create'

export interface SheetState {
  name: SheetName
  props: Record<string, unknown>
}

export interface ToastItem {
  id: number
  message: string
  kind: 'ok' | 'error'
}

export const ui = reactive({
  sheet: null as SheetState | null,
  /** 数据主动刷新信号：动作成功后 +1，各面/组件 watch 它重拉 */
  refreshTick: 0,
  toasts: [] as ToastItem[],
})

let toastSeq = 0

export function openSheet(name: SheetName, props: Record<string, unknown> = {}): void {
  ui.sheet = { name, props }
}

export function closeSheet(): void {
  ui.sheet = null
}

export function refreshData(): void {
  ui.refreshTick += 1
}

export function toast(message: string, kind: 'ok' | 'error' = 'ok'): void {
  const id = ++toastSeq
  ui.toasts.push({ id, message, kind })
  window.setTimeout(() => {
    const idx = ui.toasts.findIndex((t) => t.id === id)
    if (idx >= 0) ui.toasts.splice(idx, 1)
  }, kind === 'error' ? 4200 : 2600)
}

export function dismissToast(id: number): void {
  const idx = ui.toasts.findIndex((t) => t.id === id)
  if (idx >= 0) ui.toasts.splice(idx, 1)
}
