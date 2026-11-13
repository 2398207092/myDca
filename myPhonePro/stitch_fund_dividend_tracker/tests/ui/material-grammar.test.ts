/**
 * 深度语法契约（台阶/接缝/凸起语义）
 * ------------------------------------------------------------
 * 2026-11-13 补充：把高度轴拆成正负两向——
 *   正向（+1/+2）只属于交互；负向（-1）只属于结构（台阶）；0 是阅读平面。
 * 任何后续改动如果让展示容器使用凸起阴影、或让台阶出现圆角/边距，
 * 都会被这里挡住。
 */
import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const ROOT = process.cwd()
function read(rel: string): string {
  return readFileSync(join(ROOT, rel), 'utf-8')
}

describe('深度 token', () => {
  const tokens = read('src/next/styles/tokens.css')

  it('定义台阶底色、台阶内阴影、接缝的暗/亮两笔', () => {
    expect(tokens).toMatch(/--surface-bay:/)
    expect(tokens).toMatch(/--terrace-shadow:/)
    expect(tokens).toMatch(/--seam-dark:/)
    expect(tokens).toMatch(/--seam-light:/)
  })

  it('凸起阴影仍然是正向交互材质', () => {
    expect(tokens).toMatch(/--el-key:/)
    expect(tokens).toMatch(/--press:/)
  })
})

describe('台阶（结构，不可点）', () => {
  const terrace = read('src/next/shared/components/TerraceSection.vue')

  it('使用负向台阶阴影，不用凸起阴影', () => {
    expect(terrace).toMatch(/var\(--terrace-shadow\)/)
    expect(terrace).not.toMatch(/var\(--el-key\)/)
  })

  it('满宽横带：左右吃掉 gutter、无圆角（避免退化成卡片）', () => {
    expect(terrace).toMatch(/calc\(var\(--gutter\)\s*\*\s*-1\)/)
    expect(terrace).toMatch(/border-radius:\s*0/)
  })

  it('可点击线索不应出现在台阶容器里（模板没有 chevron 字形 / 按压态）', () => {
    expect(terrace).not.toMatch(/›/)
    expect(terrace).not.toMatch(/:active/)
  })
})

describe('接缝（结构记号）', () => {
  const seam = read('src/next/shared/components/SeamDivider.vue')

  it('2px 双向刻线：上沿暗、下沿亮', () => {
    expect(seam).toMatch(/linear-gradient/)
    expect(seam).toMatch(/--seam-dark/)
    expect(seam).toMatch(/--seam-light/)
  })
})

describe('阅读层保持平面（只有可点态才允许凸起）', () => {
  for (const file of ['Readout', 'TrendLine', 'CompositionBar']) {
    it(`${file} 不使用凸起阴影`, () => {
      expect(read(`src/next/shared/components/${file}.vue`)).not.toMatch(/var\(--el-key\)/)
    })
  }
})

describe('台阶内的可导航行：抬回一级 + 按压回弹 + 强调色 chevron', () => {
  it('DataRow 只在 .row.row--tap 上使用凸起/按压', () => {
    const src = read('src/next/shared/components/DataRow.vue')
    const index = src.indexOf('.row.row--tap')
    expect(index).toBeGreaterThan(-1)
    const tapBlock = src.slice(index)
    expect(tapBlock).toMatch(/var\(--el-key\)/)
    expect(tapBlock).toMatch(/var\(--press\)/)
    expect(tapBlock).toMatch(/color:\s*var\(--accent\)/)
  })

  it('EventRow 只在 .event.event--tap 上使用凸起/按压', () => {
    const src = read('src/next/shared/components/EventRow.vue')
    const index = src.indexOf('.event.event--tap')
    expect(index).toBeGreaterThan(-1)
    const tapBlock = src.slice(index)
    expect(tapBlock).toMatch(/var\(--el-key\)/)
    expect(tapBlock).toMatch(/var\(--press\)/)
    expect(tapBlock).toMatch(/color:\s*var\(--accent\)/)
  })
})

describe('账户搭建入口（新建持仓 / 账户 / 现金）', () => {
  it('操作面板包含三个入口', () => {
    const actions = read('src/next/features/sheets/ActionSheet.vue')
    expect(actions).toMatch(/新建持仓/)
    expect(actions).toMatch(/新建账户/)
    expect(actions).toMatch(/加一笔现金/)
  })

  it('SheetHost 注册三个新 Sheet', () => {
    const host = read('src/next/features/sheets/SheetHost.vue')
    expect(host).toMatch(/HoldingCreateSheet/)
    expect(host).toMatch(/AccountCreateSheet/)
    expect(host).toMatch(/CashCreateSheet/)
  })
})

describe('交互层保持凸起 + 按压回弹', () => {
  const btn = read('src/next/shared/components/BtnKey.vue')
  it('BtnKey 使用 --el-key 且按下变 --press', () => {
    expect(btn).toMatch(/var\(--el-key\)/)
    expect(btn).toMatch(/var\(--press\)/)
  })
})

describe('三面已接入台阶语法', () => {
  for (const file of [
    'src/next/features/now/NowView.vue',
    'src/next/features/past/PastView.vue',
    'src/next/features/future/FutureView.vue',
  ]) {
    it(`${file} 使用 TerraceSection`, () => {
      expect(read(file)).toMatch(/TerraceSection/)
    })
  }

  it('现在面不再保留平铺的「年度概览」区块', () => {
    expect(read('src/next/features/now/NowView.vue')).not.toMatch(/年度概览/)
  })

  it('未来面承接「今年已收 / 持仓数量」的对账分组', () => {
    const future = read('src/next/features/future/FutureView.vue')
    expect(future).toMatch(/已发生/)
    expect(future).toMatch(/今年已收分红/)
    expect(future).toMatch(/持仓数量/)
  })
})
