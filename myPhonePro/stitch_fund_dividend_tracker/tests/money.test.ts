import { describe, expect, it } from 'vitest'
import {
  deltaArrow,
  deltaTone,
  fmtDelta,
  fmtHoldingQuantity,
  fmtMoney,
  fmtNav,
  fmtPercent,
  fmtShares,
  fmtSignedMoney,
  toNumber,
} from '@/next/shared/money'
import { currentMonth, dateGroupLabel, formatMonthDay, monthRange, shiftMonth, weekdayZH } from '@/next/shared/date'
import { cashSummary, groupByDate, unifyEvents } from '@/next/shared/stream'
import type { DividendEventItem } from '@/api/event'
import type { HoldingItem } from '@/api/holding'
import type { TransactionItem } from '@/api/transaction'

describe('money', () => {
  it('金额：千分位 + 两位小数 + 负号在币种前', () => {
    expect(fmtMoney(0)).toBe('¥0.00')
    expect(fmtMoney(1234.5)).toBe('¥1,234.50')
    expect(fmtMoney(-1234.5)).toBe('-¥1,234.50')
    expect(fmtMoney('12.3')).toBe('¥12.30')
    expect(fmtMoney(1_234_567.891)).toBe('¥1,234,567.89')
    expect(fmtMoney(null)).toBe('—')
    expect(fmtMoney(undefined)).toBe('—')
  })

  it('带符号金额', () => {
    expect(fmtSignedMoney(0)).toBe('¥0.00')
    expect(fmtSignedMoney(1240.35)).toBe('+¥1,240.35')
    expect(fmtSignedMoney(-320)).toBe('-¥320.00')
  })

  it('百分比与涨跌文案', () => {
    expect(fmtPercent(3.674)).toBe('3.67%')
    expect(fmtPercent(0.9736, 2, true)).toBe('+0.97%')
    expect(fmtPercent(-0.4, 2, true)).toBe('-0.40%')
    expect(fmtDelta(1240.35, 0.9736)).toBe('▲ +¥1,240.35（+0.97%）')
    expect(fmtDelta(-320, -0.41)).toBe('▼ -¥320.00（-0.41%）')
  })

  it('份额/净值位数', () => {
    expect(fmtShares(10000)).toBe('10,000.00 份')
    expect(fmtNav(1.242)).toBe('1.2420')
    expect(fmtNav(12.3)).toBe('12.3000')
  })

  it('持仓数量按类型格式化：加密币 8 位 + 枚，基金 2 位 + 份', () => {
    expect(fmtHoldingQuantity(0.01139738, 'crypto')).toBe('0.01139738 枚')
    expect(fmtHoldingQuantity(0.00008631, 'crypto')).toBe('0.00008631 枚')
    expect(fmtHoldingQuantity(10000, 'fund')).toBe('10,000.00 份')
    expect(fmtHoldingQuantity(1068.2558, 'ETF')).toBe('1,068.26 份')
    expect(fmtHoldingQuantity(null, 'crypto')).toBe('—')
  })

  it('涨跌方向', () => {
    expect(deltaTone(0.01)).toBe('up')
    expect(deltaTone(-0.01)).toBe('down')
    expect(deltaTone(0)).toBe('flat')
    expect(deltaArrow(0.01)).toBe('▲')
    expect(deltaArrow(-0.01)).toBe('▼')
    expect(deltaArrow(0)).toBe('')
    expect(toNumber('abc')).toBe(0)
  })
})

describe('date', () => {
  it('月份区间与翻月跨年', () => {
    expect(monthRange('2026-02')).toEqual({ from: '2026-02-01', to: '2026-02-28' })
    expect(monthRange('2028-02')).toEqual({ from: '2028-02-01', to: '2028-02-29' })
    expect(shiftMonth('2026-01', -1)).toBe('2025-12')
    expect(shiftMonth('2026-12', 1)).toBe('2027-01')
  })

  it('日期文案', () => {
    expect(formatMonthDay('2026-08-14')).toBe('08-14')
    expect(weekdayZH('2026-11-13')).toBe('周五')
    expect(dateGroupLabel('2026-11-13', new Date(2026, 10, 13, 9, 0))).toBe('11-13 今天 · 周五')
    expect(dateGroupLabel('2026-08-14', new Date(2026, 10, 13, 9, 0))).toBe('08-14 周五')
    expect(currentMonth(new Date(2026, 7, 1))).toBe('2026-08')
  })
})

const holding = (id: string, name: string): HoldingItem =>
  ({
    id,
    name,
    code: '000000',
    type: 'fund',
    costAlgorithm: 'diluted',
    shares: 100,
    costPerShare: 1,
    cost: 100,
    marketValue: 120,
    latestPrice: 1.2,
    priceDate: '2026-11-12',
    predictedDividend: 0,
    dividendRate: 0,
    priceDividendRate: 0,
    totalDividendReceived: 0,
    netInvestment: 100,
    dividendRecoveryRate: 0,
    estimatedRecoveryYears: 0,
    reinvestRecoveryYears: 0,
    color: '#000000',
  }) as HoldingItem

const tx = (patch: Partial<TransactionItem>): TransactionItem => ({
  id: 'tx1',
  holdingId: 'h1',
  type: 'buy',
  date: '2026-11-13',
  quantity: 100,
  price: 1,
  fee: 0,
  total: 100,
  ...patch,
})

const ev = (patch: Partial<DividendEventItem>): DividendEventItem => ({
  id: 'ev1',
  holdingId: 'h1',
  holdingName: '红利ETF',
  type: 'payout',
  date: '2026-11-13',
  amount: 126,
  status: 'distributed',
  description: '中期分红',
  participated: true,
  converted: false,
  ...patch,
})

describe('stream（统一事件流）', () => {
  const holdings = [holding('h1', '红利ETF'), holding('h2', '沪深300')]

  it('交易方向与带符号金额', () => {
    const events = unifyEvents(
      [
        tx({ id: 'buy', type: 'buy', quantity: 1000, price: 1, fee: 0, total: 1000 }),
        tx({ id: 'sell', type: 'sell', quantity: 1000, price: 0.5, fee: 0, total: 500 }),
        tx({ id: 'reinvest', type: 'reinvest', total: 0, quantity: 10, price: 1 }),
      ],
      [],
      holdings,
    )
    const buy = events.find((e) => e.id === 'tx:buy')!
    const sell = events.find((e) => e.id === 'tx:sell')!
    const reinvest = events.find((e) => e.id === 'tx:reinvest')!
    expect(buy.direction).toBe('out')
    expect(buy.amountText).toBe('-¥1,000.00')
    expect(sell.direction).toBe('in')
    expect(sell.amountText).toBe('+¥500.00')
    expect(reinvest.direction).toBe('none')
    expect(reinvest.amountText).toBe('')
  })

  it('定投交易使用「定投执行」标题', () => {
    const events = unifyEvents([tx({ id: 'dca', dcaPlanId: 'p1', total: 1000 })], [], holdings)
    expect(events[0].title).toBe('定投执行')
  })

  it('卖出金额按「净值×数量 − 手续费」计入现金流入（后端 total 含手续费）', () => {
    const events = unifyEvents(
      [tx({ id: 'sell', type: 'sell', quantity: 100, price: 1.5, fee: 2, total: 152 })],
      [],
      holdings,
    )
    const sell = events.find((e) => e.id === 'tx:sell')!
    expect(sell.amountText).toBe('+¥148.00')
    expect(sell.direction).toBe('in')
  })

  it('买入金额按「净值×数量 + 手续费」计入现金流出', () => {
    const events = unifyEvents(
      [tx({ id: 'buy2', type: 'buy', quantity: 100, price: 1.5, fee: 2, total: 152 })],
      [],
      holdings,
    )
    expect(events[0].amountText).toBe('-¥152.00')
  })

  it('非 payout 事件（登记/除权/公告）不计金额、不显示状态', () => {
    const events = unifyEvents(
      [],
      [
        ev({ id: 'reg', type: 'registration', status: 'distributed', amount: 126 }),
        ev({ id: 'ex', type: 'ex_dividend', status: 'pending', amount: 126 }),
        ev({ id: 'payout', type: 'payout', status: 'distributed', amount: 126 }),
      ],
      holdings,
    )
    const reg = events.find((e) => e.id === 'ev:reg')!
    const ex = events.find((e) => e.id === 'ev:ex')!
    expect(reg.amountText).toBe('')
    expect(reg.direction).toBe('none')
    expect(reg.statusText).toBe('')
    expect(ex.amountText).toBe('')
    expect(ex.statusText).toBe('')
    expect(cashSummary(events).inflow).toBe(126)
  })

  it('已复投事件不计现金、文案为「已复投」', () => {
    const events = unifyEvents([], [ev({ id: 'conv', status: 'distributed', converted: true, amount: 126 })], holdings)
    const conv = events[0]
    expect(conv.direction).toBe('none')
    expect(conv.amountText).toBe('已复投')
    expect(conv.title).toBe('分红复投')
  })

  it('分红状态：已到账才计入现金流入，待确认/已取消不计', () => {
    const events = unifyEvents(
      [],
      [
        ev({ id: 'ok', status: 'distributed', amount: 126 }),
        ev({ id: 'pending', status: 'pending', amount: 40 }),
        ev({ id: 'cancelled', status: 'cancelled', amount: 9 }),
      ],
      holdings,
    )
    expect(events.find((e) => e.id === 'ev:ok')!.direction).toBe('in')
    expect(events.find((e) => e.id === 'ev:ok')!.title).toBe('分红到账')
    expect(events.find((e) => e.id === 'ev:pending')!.direction).toBe('none')
    expect(events.find((e) => e.id === 'ev:cancelled')!.direction).toBe('none')
    const sum = cashSummary(events)
    expect(sum.inflow).toBe(126)
    expect(sum.dividend).toBe(126)
    expect(sum.outflow).toBe(0)
  })

  it('按日期倒序分组', () => {
    const events = unifyEvents(
      [tx({ id: 'a', date: '2026-11-12', total: 100 }), tx({ id: 'b', date: '2026-11-13', total: 100 })],
      [ev({ id: 'c', date: '2026-11-13' })],
      holdings,
    )
    const groups = groupByDate(events)
    expect(groups.map((g) => g.date)).toEqual(['2026-11-13', '2026-11-12'])
    expect(groups[0].items.length).toBe(2)
  })

  it('现金汇总 = 流入 − 流出（复投/待确认不参与）', () => {
    const events = unifyEvents(
      [tx({ id: 'buy', type: 'buy', total: 1000 }), tx({ id: 'sell', type: 'sell', total: 300 })],
      [ev({ id: 'ok', status: 'distributed', amount: 50 })],
      holdings,
    )
    const sum = cashSummary(events)
    expect(sum.inflow).toBe(350)
    expect(sum.outflow).toBe(1000)
    expect(sum.dividend).toBe(50)
  })
})
