/**
 * API 契约检查：断言后端响应的字段名与类型
 *
 * 为什么需要：单元测试测不出「字段名被序列化错」（如 isDefault → default），
 * SQL 对账也测不出，只有契约层能拦。
 *
 * 用法：
 *   TOKEN=<token> node scripts/api-contract-check.cjs
 *   BASE=http://localhost:8080 TOKEN=$(cat /tmp/token.txt) node scripts/api-contract-check.cjs
 *
 * 退出码：0 全通过；1 有失败
 */
const fs = require('fs')

const BASE = process.env.BASE || 'http://localhost:8080'
const TOKEN =
  process.env.TOKEN ||
  (fs.existsSync('/tmp/token.txt') ? fs.readFileSync('/tmp/token.txt', 'utf8').trim() : '')

/**
 * 契约 = 接口必须返回的字段与类型。
 * 类型写 'string|null' 表示允许 null。forbidden 用于捕获 Jackson 命名坑。
 */
const CONTRACTS = [
  {
    name: '账户列表 GET /api/accounts',
    path: '/api/accounts',
    shape: 'array',
    fields: {
      id: 'string',
      name: 'string',
      purpose: 'string',
      baseCurrency: 'string',
      isDefault: 'boolean',
      sortOrder: 'number',
      totalValueCny: 'number',
      assetCount: 'number',
      assets: 'array',
    },
    forbidden: ['default'],
  },
  {
    name: '手动资产 GET /api/manual-assets',
    path: '/api/manual-assets',
    shape: 'array',
    fields: {
      id: 'string',
      name: 'string',
      type: 'string',
      amount: 'number',
      accountId: 'string|null',
      assetCode: 'string|null',
      quantity: 'number|null',
      unitPrice: 'number|null',
      priceSource: 'string|null',
      isPrimary: 'boolean',
    },
    forbidden: ['primary'],
  },
  {
    name: '持仓列表 GET /api/holdings',
    path: '/api/holdings',
    shape: 'array',
    fields: {
      id: 'string',
      name: 'string',
      code: 'string',
      type: 'string',
      costAlgorithm: 'string',
      shares: 'number',
      cost: 'number',
      marketValue: 'number',
      netInvestment: 'number',
      assetCategory: 'string|null',
    },
  },
  {
    name: '资产总览 GET /api/asset-overview',
    path: '/api/asset-overview',
    shape: 'object',
    fields: {
      totalValue: 'number',
      cashValue: 'number',
      cryptoValue: 'number',
      usStockValue: 'number',
      goldValue: 'number',
      dividendValue: 'number',
      categories: 'array',
    },
  },
  {
    name: '加密价格 GET /api/crypto/prices',
    path: '/api/crypto/prices',
    shape: 'array',
    fields: {
      symbol: 'string',
      usdtPrice: 'number|null',
      cnyPrice: 'number|null',
      source: 'string|null',
      updatedAt: 'string|null',
    },
  },
  {
    name: '分红事件 GET /api/events',
    path: '/api/events',
    shape: 'array',
    fields: {
      id: 'string',
      holdingId: 'string',
      type: 'string',
      date: 'string',
      amount: 'number',
      status: 'string',
      participated: 'boolean',
    },
  },
]

const results = []
function record(name, ok, detail) {
  results.push({ name, ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ' — ' + detail : ''}`)
}

function typeOk(value, type) {
  if (type.includes('|')) return type.split('|').some((t) => typeOk(value, t.trim()))
  if (value === null || value === undefined) return type === 'null'
  switch (type) {
    case 'string':
      return typeof value === 'string'
    case 'number':
      return typeof value === 'number'
    case 'boolean':
      return typeof value === 'boolean'
    case 'array':
      return Array.isArray(value)
    case 'object':
      return typeof value === 'object' && !Array.isArray(value)
    case 'null':
      return value === null
    default:
      return false
  }
}

function sampleOf(data, shape) {
  if (shape === 'array') return Array.isArray(data) && data.length > 0 ? data[0] : null
  return data
}

async function runContract(c) {
  let res, body
  try {
    res = await fetch(BASE + c.path, {
      headers: TOKEN ? { Authorization: 'Bearer ' + TOKEN } : {},
    })
    body = await res.json()
  } catch (e) {
    record(c.name, false, '请求失败: ' + e.message)
    return
  }

  if (res.status !== 200 || body.code !== 200) {
    record(c.name, false, `http=${res.status} code=${body && body.code}`)
    return
  }

  const data = body.data
  if (c.shape === 'array' && !Array.isArray(data)) {
    record(c.name, false, 'data 不是数组')
    return
  }
  if (c.shape === 'object' && (typeof data !== 'object' || data === null)) {
    record(c.name, false, 'data 不是对象')
    return
  }

  const sample = sampleOf(data, c.shape)
  if (!sample) {
    // 空库时只能校验形状，字段检查跳过
    record(c.name, true, `形状正确（无数据，跳过字段检查）`)
    return
  }

  const problems = []
  for (const [field, type] of Object.entries(c.fields || {})) {
    if (!(field in sample)) {
      problems.push(`缺字段 ${field}`)
    } else if (!typeOk(sample[field], type)) {
      problems.push(`${field} 类型应为 ${type}，实际 ${sample[field] === null ? 'null' : typeof sample[field]}`)
    }
  }
  for (const field of c.forbidden || []) {
    if (field in sample) problems.push(`出现违禁字段 ${field}（Jackson 命名坑）`)
  }

  record(c.name, problems.length === 0, problems.join('；'))
}

async function main() {
  if (!TOKEN) throw new Error('缺少 TOKEN：设置环境变量或写入 /tmp/token.txt')
  console.log(`目标：${BASE}\n`)

  for (const c of CONTRACTS) await runContract(c)

  const failed = results.filter((r) => !r.ok)
  console.log(`\n===== ${results.length - failed.length}/${results.length} 通过 =====`)
  process.exit(failed.length === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error('CONTRACT_CHECK_CRASHED', e)
  process.exit(1)
})
