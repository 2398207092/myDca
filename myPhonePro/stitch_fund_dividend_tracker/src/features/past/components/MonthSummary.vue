<script setup lang="ts">
/**
 * 月汇总：本月买入 / 本月卖出 / 本月分红到账。
 * 现金流入/流出只用墨色 + 符号 + 文字，不用红绿（红绿只属于盈亏）。
 */
import { computed } from 'vue'
import { fmtMoney, fmtSignedMoney } from '@/shared/money'
import type { MonthStats } from '../usePastData'

const props = defineProps<{
  stats: MonthStats
  monthText: string
  isCurrent: boolean
}>()

const headText = computed(() => (props.isCurrent ? '本月汇总' : `${props.monthText} 汇总`))

function signedOrZero(value: number, direction: 'in' | 'out'): string {
  if (!(value > 0)) return fmtMoney(0)
  return direction === 'out' ? fmtSignedMoney(-value) : fmtSignedMoney(value)
}

const buyText = computed(() => signedOrZero(props.stats.buy, 'out'))
const sellText = computed(() => signedOrZero(props.stats.sell, 'in'))
const dividendText = computed(() => signedOrZero(props.stats.dividend, 'in'))

/** 口径提示：待确认 / 复投的金额绝不混进现金合计 */
const notes = computed<string[]>(() => {
  const list: string[] = ['现金口径：不含待确认与复投']
  if (props.stats.pendingDividend > 0) {
    list.push(`待确认分红 ${fmtMoney(props.stats.pendingDividend)} 未计入`)
  }
  if (props.stats.reinvestDividend > 0) {
    list.push(`已复投 ${fmtMoney(props.stats.reinvestDividend)} 未计现金`)
  }
  return list
})
</script>

<template>
  <section class="summary" aria-label="月度现金汇总">
    <p class="micro head">{{ headText }}</p>
    <div class="stats">
      <div class="stat">
        <span class="micro">买入 · 扣款</span>
        <b class="stat-value num">{{ buyText }}</b>
      </div>
      <div class="stat">
        <span class="micro">卖出 · 到账</span>
        <b class="stat-value num">{{ sellText }}</b>
      </div>
      <div class="stat">
        <span class="micro">分红 · 到账</span>
        <b class="stat-value num">{{ dividendText }}</b>
      </div>
    </div>
    <p class="notes micro">{{ notes.join('；') }}</p>
  </section>
</template>

<style scoped>
.summary {
  padding-top: var(--space-1);
}
.head {
  margin-bottom: var(--space-2);
}
.stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-2);
}
.stat {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 0;
}
.stat-value {
  font-size: var(--text-num-sm);
  font-weight: 600;
  color: var(--ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.notes {
  margin-top: var(--space-2);
  line-height: 1.55;
}
</style>
