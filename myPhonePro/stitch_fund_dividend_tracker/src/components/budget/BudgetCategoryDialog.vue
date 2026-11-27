<script setup lang="ts">
/**
 * 预算类型 新建/编辑 弹窗。
 * 空态不预置默认分类，全靠用户自己加。
 */
import { ref, watch } from 'vue'
import type { BudgetCategoryItem } from '@/api/budget'

const open = defineModel<boolean>('open', { default: false })

const props = defineProps<{
  category?: BudgetCategoryItem | null
  /** 只允许在指定年度内新增（编辑时禁用） */
  year: number
  saving?: boolean
}>()

const emit = defineEmits<{
  (e: 'submit', payload: { name: string; amount: number }): void
  (e: 'remove'): void
}>()

const name = ref('')
const amountText = ref('')
const error = ref('')

watch(open, (v) => {
  if (!v) return
  name.value = props.category?.name ?? ''
  amountText.value = props.category ? String(props.category.amount) : ''
  error.value = ''
})

function submit() {
  const trimmed = name.value.trim()
  if (!trimmed) {
    error.value = '请填写预算类型名称'
    return
  }
  if (trimmed.length > 30) {
    error.value = '名称最多 30 个字'
    return
  }
  const amount = Number(amountText.value)
  if (!Number.isFinite(amount) || amount <= 0) {
    error.value = '请填写大于 0 的年度额度'
    return
  }
  if (Math.round(amount * 100) !== amount * 100) {
    error.value = '金额最多两位小数'
    return
  }
  error.value = ''
  emit('submit', { name: trimmed, amount })
}

function remove() {
  if (!props.category) return
  if (!window.confirm(`确定删除「${props.category.name}」这个预算类型？`)) return
  emit('remove')
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
          <h3 class="font-body text-sm font-medium text-text-primary">
            {{ category ? '编辑预算类型' : '添加预算类型' }}
          </h3>
          <button class="w-7 h-7 flex items-center justify-center rounded-full hover:bg-card-alt" @click="open = false">
            <span class="material-symbols-outlined text-lg text-text-secondary">close</span>
          </button>
        </div>

        <div class="flex-1 overflow-y-auto px-lg pb-lg space-y-md">
          <div>
            <label class="font-body text-xs text-text-tertiary">类型名称</label>
            <input
              v-model="name"
              type="text"
              maxlength="30"
              placeholder="如：餐饮、日用、出行"
              class="mt-1 w-full px-3 py-2.5 rounded-lg bg-card-alt font-body text-sm text-text-primary placeholder:text-text-tertiary/50 outline-none focus:ring-2 focus:ring-brand/30"
            />
          </div>
          <div>
            <label class="font-body text-xs text-text-tertiary">{{ year }} 年度额度（元）</label>
            <input
              v-model="amountText"
              type="number"
              inputmode="decimal"
              min="0"
              step="0.01"
              placeholder="0.00"
              class="mt-1 w-full px-3 py-2.5 rounded-lg bg-card-alt font-body text-sm text-text-primary tabular-nums placeholder:text-text-tertiary/50 outline-none focus:ring-2 focus:ring-brand/30"
            />
          </div>
          <p v-if="error" class="font-body text-xs text-neg">{{ error }}</p>

          <div class="flex items-center gap-2 pt-1">
            <button
              v-if="category"
              class="px-3 py-2 rounded-lg font-body text-xs text-neg hover:bg-card-alt transition-colors"
              :disabled="saving"
              @click="remove"
            >删除</button>
            <div class="flex-1"></div>
            <button
              class="px-lg py-2 bg-brand text-white rounded-lg font-body text-xs font-medium active:scale-[0.98] transition-transform disabled:opacity-50"
              :disabled="saving"
              @click="submit"
            >{{ saving ? '保存中…' : '保存' }}</button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
