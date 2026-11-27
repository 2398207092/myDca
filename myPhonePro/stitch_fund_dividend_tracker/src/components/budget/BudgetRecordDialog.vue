<script setup lang="ts">
/**
 * 记一笔弹窗：金额 + 预算类型 + 日期 + 备注。
 * 只写预算账本，不动「账户与现金」。
 */
import { computed, ref, watch } from 'vue'
import type { BudgetCategoryItem } from '@/api/budget'

const open = defineModel<boolean>('open', { default: false })

const props = defineProps<{
  categories: BudgetCategoryItem[]
  /** 默认选中的预算类型 */
  defaultCategoryId?: string
  /** 预算年度（校验日期不跨年） */
  year: number
  saving?: boolean
}>()

const emit = defineEmits<{
  (e: 'submit', payload: { categoryId: string; occurDate: string; amount: number; note: string }): void
}>()

function localDateStr(d = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

const amountText = ref('')
const categoryId = ref('')
const occurDate = ref(localDateStr())
const note = ref('')
const error = ref('')

const canSubmit = computed(() => props.categories.length > 0 && !props.saving)

watch(open, (v) => {
  if (!v) return
  amountText.value = ''
  categoryId.value = props.defaultCategoryId || props.categories[0]?.id || ''
  occurDate.value = localDateStr()
  note.value = ''
  error.value = ''
})

function submit() {
  if (!categoryId.value) {
    error.value = '请先选择预算类型'
    return
  }
  const amount = Number(amountText.value)
  if (!Number.isFinite(amount) || amount <= 0) {
    error.value = '请填写大于 0 的金额'
    return
  }
  if (Math.round(amount * 100) !== amount * 100) {
    error.value = '金额最多两位小数'
    return
  }
  if (!occurDate.value) {
    error.value = '请选择日期'
    return
  }
  if (Number(occurDate.value.slice(0, 4)) !== props.year) {
    error.value = `日期必须在 ${props.year} 年内`
    return
  }
  error.value = ''
  emit('submit', {
    categoryId: categoryId.value,
    occurDate: occurDate.value,
    amount,
    note: note.value.trim(),
  })
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-modal flex items-center justify-center bg-black/40 px-gutter"
      @click.self="open = false"
    >
      <div class="bg-card-bg rounded-2xl w-full max-w-[420px] max-h-[80vh] flex flex-col shadow-overlay">
        <div class="flex items-center justify-between px-lg pt-lg pb-sm">
          <h3 class="font-body text-sm font-medium text-text-primary">记一笔</h3>
          <button class="w-7 h-7 flex items-center justify-center rounded-full hover:bg-card-alt" @click="open = false">
            <span class="material-symbols-outlined text-lg text-text-secondary">close</span>
          </button>
        </div>

        <div class="flex-1 overflow-y-auto px-lg pb-lg space-y-md">
          <!-- 金额 -->
          <div class="bg-card-alt/60 rounded-xl px-md py-3">
            <label class="font-body text-xs text-text-tertiary">金额</label>
            <div class="flex items-baseline gap-1 mt-0.5">
              <span class="font-display text-lg text-text-tertiary">¥</span>
              <input
                v-model="amountText"
                type="number"
                inputmode="decimal"
                min="0"
                step="0.01"
                placeholder="0.00"
                class="flex-1 min-w-0 bg-transparent font-display text-2xl font-semibold text-text-primary tabular-nums outline-none placeholder:text-text-tertiary/40"
              />
            </div>
          </div>

          <!-- 预算类型 -->
          <div>
            <label class="font-body text-xs text-text-tertiary">预算类型</label>
            <div v-if="categories.length" class="mt-1.5 flex flex-wrap gap-2">
              <button
                v-for="c in categories"
                :key="c.id"
                class="px-3 py-1.5 rounded-full font-body text-xs border transition-colors"
                :class="categoryId === c.id
                  ? 'bg-brand text-white border-brand'
                  : 'bg-card-bg text-text-secondary border-border-light/60'"
                @click="categoryId = c.id"
              >{{ c.name }}</button>
            </div>
            <p v-else class="mt-1.5 font-body text-xs text-text-tertiary">还没有预算类型，先添加一个</p>
          </div>

          <!-- 日期 + 备注 -->
          <div class="grid grid-cols-2 gap-sm">
            <div>
              <label class="font-body text-xs text-text-tertiary">日期</label>
              <input
                v-model="occurDate"
                type="date"
                class="mt-1 w-full px-3 py-2 rounded-lg bg-card-alt font-body text-xs text-text-primary tabular-nums outline-none focus:ring-2 focus:ring-brand/30"
              />
            </div>
            <div>
              <label class="font-body text-xs text-text-tertiary">备注（可选）</label>
              <input
                v-model="note"
                type="text"
                maxlength="200"
                placeholder="如：午饭"
                class="mt-1 w-full px-3 py-2 rounded-lg bg-card-alt font-body text-xs text-text-primary placeholder:text-text-tertiary/50 outline-none focus:ring-2 focus:ring-brand/30"
              />
            </div>
          </div>

          <p v-if="error" class="font-body text-xs text-neg">{{ error }}</p>

          <button
            class="w-full py-3 bg-brand text-white rounded-lg font-body text-sm font-medium active:scale-[0.99] transition-transform disabled:opacity-50"
            :disabled="!canSubmit"
            @click="submit"
          >{{ saving ? '保存中…' : '保存' }}</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
