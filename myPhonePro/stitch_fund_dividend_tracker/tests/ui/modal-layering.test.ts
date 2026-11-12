/**
 * 浮层契约回归测试（新前端版）
 * ------------------------------------------------------------
 * 旧前端用 Tailwind 语义类 z-header/z-modal，源码扫描目标是 src/views|components。
 * 破坏性重构后这些目录已删除，改为「CSS 变量 + SheetFrame」契约：
 *   1. src/styles/tokens.css 的层级必须严格递增，sheet 高于 dock、toast 高于 sheet
 *   2. 全局唯一浮层骨架 SheetFrame 必须 Teleport 到 body、role=dialog、aria-modal、
 *      支持 Escape 关闭、锁定 body 滚动
 *   3. 所有 Sheet 必须使用 SheetFrame，且不得在组件里写死 z-index
 *   4. 触摸目标契约：BtnKey/IconKey 最小 44；底部坞动作键 56
 */
import { describe, it, expect } from 'vitest'
import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

const ROOT = process.cwd()
const SRC = join(ROOT, 'src')

function read(rel: string): string {
  return readFileSync(join(ROOT, rel), 'utf-8')
}

function walkVueFiles(dir: string): string[] {
  const out: string[] = []
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const p = join(dir, entry.name)
    if (entry.isDirectory()) out.push(...walkVueFiles(p))
    else if (entry.name.endsWith('.vue')) out.push(p)
  }
  return out
}

function rel(p: string): string {
  return p.replace(/\\/g, '/').replace(ROOT.replace(/\\/g, '/') + '/', '')
}

const ALL_VUE = [...walkVueFiles(join(SRC, 'shared')), ...walkVueFiles(join(SRC, 'features'))].map(rel)
const SHEET_VUE = [...walkVueFiles(join(SRC, 'features/sheets')), ...walkVueFiles(join(SRC, 'features/now'))].map(rel)

function zValue(source: string, name: string): number {
  const m = source.match(new RegExp(`--${name}:\\s*(\\d+)`))
  expect(m, `tokens.css 缺少 --${name}`).not.toBeNull()
  return parseInt(m![1], 10)
}

const tokens = read('src/styles/tokens.css')

describe('层级契约（CSS 变量）', () => {
  it('topbar < dock < sheet < toast 严格递增', () => {
    const topbar = zValue(tokens, 'z-topbar')
    const dock = zValue(tokens, 'z-dock')
    const sheet = zValue(tokens, 'z-sheet')
    const toast = zValue(tokens, 'z-toast')
    expect(dock).toBeGreaterThan(topbar)
    expect(sheet).toBeGreaterThan(dock)
    expect(toast).toBeGreaterThan(sheet)
  })

  it('Sheet 必须高于底坞（底坞不能盖住 Sheet）', () => {
    expect(zValue(tokens, 'z-sheet')).toBeGreaterThan(zValue(tokens, 'z-dock'))
  })
})

describe('SheetFrame 浮层骨架契约', () => {
  const sf = read('src/shared/components/SheetFrame.vue')

  it('Teleport 到 body（不受页面 transform/层级影响）', () => {
    expect(sf).toMatch(/<Teleport\s+to="body"/)
  })

  it('对话框语义：role=dialog + aria-modal + aria-label', () => {
    expect(sf).toMatch(/role="dialog"/)
    expect(sf).toMatch(/aria-modal="true"/)
    expect(sf).toMatch(/:aria-label="props\.title"/)
  })

  it('Escape 关闭 + 遮罩可点关闭', () => {
    expect(sf).toMatch(/Escape/)
    expect(sf).toMatch(/class="scrim"/)
  })

  it('打开时锁定 body 滚动，关闭时恢复', () => {
    expect(sf).toMatch(/document\.body\.style\.overflow\s*=\s*'hidden'/)
    expect(sf).toMatch(/document\.body\.style\.overflow\s*=\s*''/)
  })

  it('关闭后把焦点还给触发元素', () => {
    expect(sf).toMatch(/lastActive/)
  })
})

describe('Sheet 组件不得写死 z-index', () => {
  for (const file of SHEET_VUE) {
    it(`${file} 不含硬编码 z-index`, () => {
      const source = read(file)
      expect(source).not.toMatch(/z-index\s*:\s*\d{2,}/)
    })
  }
})

describe('所有 Sheet 使用统一骨架', () => {
  const sheets = [
    'src/features/sheets/ActionSheet.vue',
    'src/features/sheets/TransactionSheet.vue',
    'src/features/sheets/DividendSheet.vue',
    'src/features/sheets/SnapshotSheet.vue',
    'src/features/sheets/SettingsSheet.vue',
    'src/features/sheets/DcaSheet.vue',
    'src/features/sheets/ExpenseSheet.vue',
    'src/features/sheets/HoldingSheet.vue',
    'src/features/now/CompositionSheet.vue',
    'src/features/past/EventSheet.vue',
  ]
  for (const file of sheets) {
    it(`${file} 使用 SheetFrame`, () => {
      expect(read(file)).toMatch(/SheetFrame/)
    })
  }
})

describe('触摸目标契约', () => {
  it('BtnKey 最小高度 44', () => {
    expect(read('src/shared/components/BtnKey.vue')).toMatch(/min-height:\s*44px/)
  })

  it('IconKey 默认 44×44', () => {
    const src = read('src/shared/components/IconKey.vue')
    expect(src).toMatch(/size\?\s*:\s*number/)
    expect(src).toMatch(/:\s*44\b/)
  })

  it('底部坞动作键 56×56', () => {
    const src = read('src/shared/components/FaceDock.vue')
    expect(src).toMatch(/width:\s*56px/)
    expect(src).toMatch(/height:\s*56px/)
  })
})

describe('没有残留旧设计类名', () => {
  it('新前端源码不出现 z-modal / z-header / font-body-md 等旧 Tailwind 契约', () => {
    for (const file of ALL_VUE) {
      const source = read(file)
      expect(source, `${file} 残留旧类名`).not.toMatch(/\bz-modal\b|\bz-header\b|\bfont-body-md\b/)
    }
  })
})
