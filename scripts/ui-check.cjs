/**
 * 手机端 UI 回归检查（Playwright + 系统 Chrome）
 *
 * 用法：
 *   NODE_PATH=<npx playwright 路径> TOKEN=<token> node scripts/ui-check.cjs
 *
 * 环境变量：
 *   TOKEN   必填，登录 token（也可放 /tmp/token.txt）
 *   BASE    默认 http://localhost:5173
 *   OUT     截图目录，默认 .workbuddy/tmp/ui-check
 *
 * 退出码：0 = 全部通过；1 = 有失败项
 *
 * 设计原则：能断言的都断言，截图只用于人工复核“看起来对不对”。
 */
const fs = require('fs')
const path = require('path')

/** 自动定位 playwright：本地 node_modules → npx 缓存，避免依赖外部 NODE_PATH */
function loadPlaywright() {
  try {
    return require('playwright')
  } catch (_) {
    /* 继续找 */
  }
  const bases = [
    path.join(process.env.LOCALAPPDATA || '', 'npm-cache', '_npx'),
    path.join(process.env.HOME || process.env.USERPROFILE || '', 'AppData', 'Local', 'npm-cache', '_npx'),
  ]
  for (const base of bases) {
    if (!base || !fs.existsSync(base)) continue
    for (const dir of fs.readdirSync(base)) {
      const p = path.join(base, dir, 'node_modules', 'playwright')
      if (fs.existsSync(p)) return require(p)
    }
  }
  throw new Error('未找到 playwright：请先执行 `npx playwright --version`，或设置 NODE_PATH 指向其 node_modules')
}

const { chromium } = loadPlaywright()

const BASE = process.env.BASE || 'http://localhost:5173'
const OUT = process.env.OUT || path.join(process.cwd(), '.workbuddy/tmp/ui-check')
const TOKEN = process.env.TOKEN || (fs.existsSync('/tmp/token.txt') ? fs.readFileSync('/tmp/token.txt', 'utf8').trim() : '')

fs.mkdirSync(OUT, { recursive: true })

const results = []
function check(name, ok, detail) {
  results.push({ name, ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ' — ' + detail : ''}`)
}

async function shot(page, name, fullPage = false) {
  const p = path.join(OUT, name + '.png')
  await page.screenshot({ path: p, fullPage })
  return p
}

async function main() {
  if (!TOKEN) throw new Error('缺少 TOKEN：请设置环境变量或写入 /tmp/token.txt')

  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  const ctx = await browser.newContext({
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 2,
    isMobile: true,
    hasTouch: true,
    userAgent:
      'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1',
  })
  const page = await ctx.newPage()

  const consoleErrors = []
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()) })
  page.on('pageerror', (e) => consoleErrors.push(String(e)))

  await page.addInitScript((t) => localStorage.setItem('fund_tracker_token', t), TOKEN)

  // ---------- 1. 发现页：账户区 ----------
  await page.goto(BASE + '/#/discover', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)

  const section = page.getByText('账户与现金').first()
  const hasSection = (await section.count()) > 0
  check('发现页存在「账户与现金」区块', hasSection)
  if (!hasSection) {
    await shot(page, '01-discover-fail')
    return finish(browser, consoleErrors)
  }

  await section.scrollIntoViewIfNeeded()
  await page.waitForTimeout(500)
  await shot(page, '01-accounts-section')

  // 1.1 默认标签唯一
  const defaultTags = await page.getByText('默认', { exact: true }).count()
  check('「默认」标签有且仅有 1 个', defaultTags === 1, `实际 ${defaultTags}`)

  // 账户区容器：从「账户与现金」往上找最近的一张卡片，后续断言都限定在这个范围内
  const accountBlock = page
    .getByText('账户与现金')
    .first()
    .locator('xpath=ancestor::div[contains(@class,"bg-card-bg")][1]')
  const blockText = (await accountBlock.count()) > 0 ? await accountBlock.innerText() : ''

  // 1.2 金额格式统一：账户区内不出现「万」，且所有金额是两位小数
  const wanInBlock = blockText.match(/¥[\d.]+万/g) || []
  check('账户区不混用「万」单位', wanInBlock.length === 0, wanInBlock.join(','))

  const moneyInBlock = blockText.match(/¥[\d,]+(\.\d+)?/g) || []
  const badMoney = moneyInBlock.filter((t) => !/^¥[\d,]+\.\d{2}$/.test(t))
  check('账户区金额均为两位小数', moneyInBlock.length > 0 && badMoney.length === 0, `异常：${badMoney.join(',')}`)

  // 1.3 无重复标签（如「现金 现金」）
  const dupPairs = await page.evaluate(() => {
    const out = []
    document.querySelectorAll('div').forEach((row) => {
      const spans = Array.from(row.querySelectorAll(':scope > div > span'))
      if (spans.length < 2) return
      const texts = spans.map((s) => s.textContent.trim()).filter(Boolean)
      if (texts.length === 2 && texts[0] === texts[1]) out.push(texts.join(' '))
    })
    return out
  })
  check('资产行无重复文字', dupPairs.length === 0, dupPairs.join(' | '))

  // 1.4 滚到底部，最后一项不被底部导航遮挡
  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight))
  await page.waitForTimeout(500)
  await shot(page, '02-bottom')

  const overlap = await page.evaluate(() => {
    const nav = document.querySelector('nav') || document.querySelector('[class*="bottom"]')
    if (!nav) return null
    const navTop = nav.getBoundingClientRect().top
    const cards = Array.from(document.querySelectorAll('main > div, main > section'))
    const last = cards[cards.length - 1]
    if (!last) return null
    const r = last.getBoundingClientRect()
    return { navTop, lastBottom: r.bottom, overlap: r.bottom - navTop }
  })
  check(
    '页面底部内容不被底部导航遮挡',
    overlap == null || overlap.overlap <= 0,
    overlap ? `最后元素底部超出导航 ${Math.round(overlap.overlap)}px` : '未找到导航元素'
  )

  // ---------- 2. 账户管理页 ----------
  await page.getByText('管理账户').first().click()
  await page.waitForTimeout(1200)
  check('可进入账户管理页', page.url().includes('/accounts'))
  await shot(page, '03-accounts-page', true)

  // 2.1 默认账户不显示「设为默认」按钮
  const setDefaultButtons = await page.getByText('设为默认', { exact: true }).count()
  const accountCards = await page.locator('main > div > div').count()
  check('默认账户不显示「设为默认」', setDefaultButtons < accountCards, `按钮 ${setDefaultButtons} / 卡片约 ${accountCards}`)

  // 2.2 新增弹窗可打开且保存按钮可见
  await page.locator('header button').last().click()
  await page.waitForTimeout(600)
  const saveVisible = await page.getByText('保存', { exact: true }).isVisible().catch(() => false)
  check('新增账户弹窗可打开且保存按钮可见', saveVisible)
  await shot(page, '04-create-sheet')
  await page.keyboard.press('Escape').catch(() => {})

  return finish(browser, consoleErrors)
}

async function finish(browser, consoleErrors) {
  check('控制台无报错', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '))
  await browser.close()

  const failed = results.filter((r) => !r.ok)
  const report = { time: new Date().toISOString(), total: results.length, failed: failed.length, results }
  fs.writeFileSync(path.join(OUT, 'report.json'), JSON.stringify(report, null, 2))

  console.log(`\n===== ${results.length - failed.length}/${results.length} 通过，截图在 ${OUT} =====`)
  process.exit(failed.length === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error('UI_CHECK_CRASHED', e)
  process.exit(1)
})
