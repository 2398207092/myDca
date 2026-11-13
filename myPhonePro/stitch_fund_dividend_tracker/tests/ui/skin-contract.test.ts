import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const ROOT = process.cwd()
const read = (rel: string) => readFileSync(join(ROOT, rel), 'utf-8')

describe('三面版皮肤契约', () => {
  it('涨跌色与 classic 的 pos/neg 一致（绿涨红跌），避免切皮肤语义反转', () => {
    const tokens = read('src/next/styles/tokens.css')
    const tw = read('tailwind.config.js')
    const up = tokens.match(/--up:\s*(#[0-9a-fA-F]{6})/)?.[1].toLowerCase()
    const down = tokens.match(/--down:\s*(#[0-9a-fA-F]{6})/)?.[1].toLowerCase()
    const pos = tw.match(/pos:\s*\{[^}]*DEFAULT:\s*'(#[0-9a-fA-F]{6})'/)?.[1].toLowerCase()
    const neg = tw.match(/neg:\s*\{[^}]*DEFAULT:\s*'(#[0-9a-fA-F]{6})'/)?.[1].toLowerCase()
    expect(up).toBe(pos)
    expect(down).toBe(neg)
  })

  it('持仓详情优先展示年化收益（annualizedReturn），另列持有期 IRR', () => {
    const src = read('src/next/features/sheets/HoldingSheet.vue')
    expect(src).toMatch(/a\.annualizedReturn/)
    expect(src).toMatch(/持有期 IRR/)
    expect(src).not.toMatch(/年化收益（IRR）/)
  })

  it('构成子行可打开单支持仓详情（HoldingSheet 不能是死代码）', () => {
    const src = read('src/next/features/now/CompositionSheet.vue')
    expect(src).toMatch(/openSheet\('holding'/)
    expect(src).toMatch(/:tap="Boolean\(item\.holdingId\)"/)
  })

  it('加密币数量按 8 位小数 + 枚展示，不再走 fmtShares', () => {
    const money = read('src/next/shared/money.ts')
    expect(money).toMatch(/fmtHoldingQuantity/)
    expect(money).toMatch(/holdingType === 'crypto'/)
    for (const file of [
      'src/next/features/sheets/HoldingSheet.vue',
      'src/next/features/sheets/TransactionSheet.vue',
      'src/next/features/past/EventSheet.vue',
      'src/next/shared/stream.ts',
    ]) {
      const src = read(file)
      expect(src).not.toMatch(/fmtShares\(/)
    }
  })

  it('新建加密持仓必须标 costEstimated，且按 USDT 成本折算', () => {
    const src = read('src/next/features/sheets/HoldingCreateSheet.vue')
    expect(src).toMatch(/costEstimated:\s*isCrypto\.value\s*\?\s*true/)
    expect(src).toMatch(/quoteCurrency:\s*isCrypto\.value\s*\?\s*'USDT'/)
    expect(src).toMatch(/listExchangeRates/)
  })

  it('大读数按整串字符降档（含 ¥/千分位），防止 320 极端值溢出', () => {
    const src = read('src/next/shared/components/Readout.vue')
    expect(src).toMatch(/props\.amount\.length/)
    expect(src).not.toMatch(/replace\(\/\[\^\\d\]\/g/)
  })

  it('部署校验包含第三入口 next.html', () => {
    const deploy = readFileSync(join(ROOT, '..', '..', '.github', 'workflows', 'deploy.yml'), 'utf-8')
    expect(deploy).toMatch(/next\.html/)
    expect(deploy).toMatch(/data-entry="next"/)
    expect(deploy).toMatch(/\/assets\/next-/)
  })
})
