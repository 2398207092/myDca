const pw = require('C:/Users/23982/AppData/Local/npm-cache/_npx/e41f203b7505f1fb/node_modules/playwright')
const path = require('path')
const { pathToFileURL } = require('url')
const DIR = path.join(process.cwd(), 'design', '概念', '帧图')

const pages = [['帧A-首屏.html', '帧A-首屏.png'], ['帧B-持仓抽屉.html', '帧B-持仓抽屉.png']]

;(async () => {
  const browser = await pw.chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' })
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 })
  const page = await ctx.newPage()
  for (const [html, png] of pages) {
    await page.goto(pathToFileURL(path.join(DIR, html)).href, { waitUntil: 'networkidle' })
    await page.waitForTimeout(400)
    await page.screenshot({ path: path.join(DIR, png) })
    console.log('OK', png)
  }
  await browser.close()
})()
