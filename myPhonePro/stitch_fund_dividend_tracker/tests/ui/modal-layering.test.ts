/**
 * ============================================================
 * 弹窗层级（z-index）回归测试
 * ============================================================
 *
 * 背景：曾出现「弹窗被 AppHeader/BottomNav（z-header=250）遮挡」的层级问题。
 * 修复将弹窗浮层统一为语义化 z 类（z-modal-backdrop=300 / z-modal=400），
 * 并禁止再使用低于导航层级的硬编码值（z-[100]/z-[110]/z-[60] 等）。
 *
 * jsdom 不计算真实层叠上下文，无法断言「弹窗是否盖住导航栏」，
 * 因此本测试采用「源码契约扫描」：直接断言 SFC 源码与 tailwind 配置中的
 * 层级契约，任何后续改动破坏该契约（如把弹窗降回 z-50、重排层级）即失败。
 *
 * 覆盖范围：
 *   1. tailwind.config.js 的 zIndex 语义化分层必须严格递增
 *   2. 全项目全屏浮层（fixed inset-0）必须使用 z-modal / z-modal-backdrop
 *   3. 全项目禁止残留硬编码弹窗 z 值（z-[100]/z-[110]/z-[60]/z-[200]）
 *   4. 本次 7 个修复页面的回归锚点
 *   5. 共享组件（AppHeader/BottomNav/Toast）层级契约
 */

import { describe, it, expect } from 'vitest'
import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

// vitest 运行时 cwd 即前端项目根目录（vite.config.ts 所在目录）
const ROOT = process.cwd()
const SRC = join(ROOT, 'src')

/** 本次修复涉及的 7 个页面（含弹窗） */
const FIXED_PAGES = [
  'src/views/dca/DcaPlanDetailPage.vue',
  'src/views/discover/DiscoverPage.vue',
  'src/views/dividends/DividendHistoryPage.vue',
  'src/views/holding-detail/HoldingDetailPage.vue',
  'src/views/home/HomePage.vue',
  'src/views/profile/ProfilePage.vue',
  'src/views/transactions/TransactionListPage.vue',
]

/** 语义化层级顺序（必须严格递增，禁止重排） */
const Z_ORDER = [
  'base',
  'dropdown',
  'sticky',
  'header',
  'modal-backdrop',
  'modal',
  'toast',
  'tooltip',
]

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

/** 项目内全部 .vue 源码（views + components），相对 ROOT 的路径 */
const ALL_VUE = [
  ...walkVueFiles(join(SRC, 'views')),
  ...walkVueFiles(join(SRC, 'components')),
].map((p) => p.replace(/\\/g, '/').replace(ROOT.replace(/\\/g, '/'), ''))

/** 提取 SFC 中静态 class="..." 字符串 */
function extractStaticClasses(source: string): string[] {
  const out: string[] = []
  const re = /class="([^"]*)"/g
  let m: RegExpExecArray | null
  while ((m = re.exec(source)) !== null) out.push(m[1])
  return out
}

/** 解析 tailwind.config.js 的 zIndex 对象为 { key: number } */
function parseZIndex(): Record<string, number> {
  const config = read('tailwind.config.js')
  const block = config.match(/zIndex\s*:\s*\{([\s\S]*?)\}/)
  expect(block, 'tailwind.config.js 必须包含 zIndex 语义化配置').not.toBeNull()
  const map: Record<string, number> = {}
  const re = /'?([a-z-]+)'?\s*:\s*'?(\d+)'?/g
  let m: RegExpExecArray | null
  while ((m = re.exec(block![1])) !== null) {
    map[m[1]] = parseInt(m[2], 10)
  }
  return map
}

// ============================================================
// 1. tailwind zIndex 语义化分层契约
// ============================================================

describe('z-index 语义化层级契约', () => {
  it('分层必须严格递增：base < dropdown < sticky < header < modal-backdrop < modal < toast < tooltip', () => {
    const map = parseZIndex()
    for (const key of Z_ORDER) {
      expect(map[key], `zIndex 缺少语义层级 "${key}"`).toBeDefined()
    }
    for (let i = 1; i < Z_ORDER.length; i++) {
      expect(
        map[Z_ORDER[i]],
        `${Z_ORDER[i]}(${map[Z_ORDER[i]]}) 必须大于 ${Z_ORDER[i - 1]}(${map[Z_ORDER[i - 1]]})`,
      ).toBeGreaterThan(map[Z_ORDER[i - 1]])
    }
  })

  it('弹窗层级必须高于导航/头部层级（本次回归的核心）', () => {
    const map = parseZIndex()
    expect(map['modal']).toBeGreaterThan(map['header'])
    expect(map['modal-backdrop']).toBeGreaterThan(map['header'])
  })
})

// ============================================================
// 2. 全屏浮层必须使用语义化 z-modal / z-modal-backdrop
// ============================================================

describe('全屏浮层层级', () => {
  for (const rel of ALL_VUE) {
    it(`${rel} 的全屏浮层（fixed inset-0）使用 z-modal / z-modal-backdrop`, () => {
      const source = read(rel)
      const overlays = extractStaticClasses(source).filter((c) => c.includes('fixed inset-0'))
      for (const cls of overlays) {
        expect(
          cls,
          `${rel} 全屏浮层缺少语义化层级（z-modal / z-modal-backdrop）: "${cls}"`,
        ).toMatch(/\bz-modal-backdrop\b|\bz-modal\b/)
      }
    })
  }
})

// ============================================================
// 3. 禁止残留硬编码弹窗 z 值
// ============================================================

describe('禁止硬编码弹窗 z 值', () => {
  for (const rel of ALL_VUE) {
    it(`${rel} 不含 z-[100]/z-[110]/z-[60]/z-[200] 硬编码值`, () => {
      const source = read(rel)
      expect(source).not.toMatch(/z-\[(?:100|110|60|200)\]/)
    })
  }
})

// ============================================================
// 4. 本次 7 个修复页面的回归锚点
// ============================================================

describe('7 个修复页面回归锚点', () => {
  for (const rel of FIXED_PAGES) {
    it(`${rel} 使用语义化弹窗层级（z-modal / z-modal-backdrop）`, () => {
      const source = read(rel)
      expect(source).toMatch(/z-modal-backdrop|z-modal/)
    })
  }
})

// ============================================================
// 5. 共享组件层级契约
// ============================================================

describe('共享组件层级契约', () => {
  it('AppHeader 使用 z-header（250，低于弹窗 400），不得使用 z-modal', () => {
    const src = read('src/components/shared/AppHeader.vue')
    expect(src).toMatch(/z-header/)
    expect(src).not.toMatch(/z-modal/)
  })

  it('BottomNav 使用 z-header（250，低于弹窗 400），不得使用 z-modal', () => {
    const src = read('src/components/shared/BottomNav.vue')
    expect(src).toMatch(/z-header/)
    expect(src).not.toMatch(/z-modal/)
  })

  it('ToastNotification 使用 z-toast（500，高于弹窗 400）', () => {
    const src = read('src/components/shared/ToastNotification.vue')
    expect(src).toMatch(/z-toast/)
    expect(src).not.toMatch(/z-\[200\]/)
  })

  it('日历页「月度分红明细」弹窗使用 z-modal', () => {
    const src = read('src/views/calendar/CalendarPage.vue')
    expect(src).toMatch(/z-modal/)
  })

  it('添加支出弹窗（AddExpenseModal）使用 z-modal', () => {
    const src = read('src/views/coverage/AddExpenseModal.vue')
    expect(src).toMatch(/z-modal/)
  })
})
