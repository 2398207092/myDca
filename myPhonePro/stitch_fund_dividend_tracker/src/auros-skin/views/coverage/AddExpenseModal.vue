<script setup lang="ts">
/**
 * 添加自定义支出（Auros 表单弹层 · 2026-11-08）
 * 只换外壳：校验、createExpense 调用、成功/失败状态与原来逐行一致。
 * 对外契约不变：无 props；emits 仍为 close / added（SettingsPage 用 v-if 挂载本组件）。
 */
import { ref } from 'vue'
import { createExpense } from '@/api/expense'
import AurosSheet from '@/auros-skin/components/auros/AurosSheet.vue'
import AurosField from '@/auros-skin/components/auros/AurosField.vue'
import AurosButton from '@/auros-skin/components/auros/AurosButton.vue'

const emit = defineEmits<{
  close: []
  added: []
}>()

const name = ref('')
const icon = ref('📌')
const amount = ref<number | null>(null)
const loading = ref(false)
const error = ref('')


/** 抽屉开合：关闭时通知父页卸载本组件（父页仍是 v-if 挂载） */
const open = ref(true)

function onSheetUpdate(v: boolean) {
  if (!v) {
    open.value = false
    emit('close')
  }
}

/** AurosField 输出字符串；金额保持「空 = null」语义，与原来 v-model.number 一致 */
function onAmount(v: string) {
  amount.value = v === '' ? null : Number(v)
}

async function handleSubmit() {
  if (!name.value.trim()) {
    error.value = '请输入支出名称'
    return
  }
  if (!amount.value || amount.value <= 0) {
    error.value = '请输入有效的金额'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await createExpense({
      name: name.value.trim(),
      icon: icon.value,
      monthlyAmount: amount.value,
    })
    emit('added')
  } catch (e) {
    error.value = '创建失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AurosSheet :model-value="open" title="添加自定义支出" @update:model-value="onSheetUpdate">
    <div class="form">
      <AurosField v-model="name" label="名称" placeholder="如：健身房" />

      <AurosField
        :model-value="amount ?? ''"
        label="月度金额"
        suffix="CNY"
        type="number"
        inputmode="decimal"
        placeholder="如：200"
        mono
        @update:model-value="onAmount"
      />

      <p v-if="error" class="error">{{ error }}</p>
    </div>

    <div class="actions">
      <AurosButton variant="ghost" block @click="emit('close')">取消</AurosButton>
      <AurosButton block :disabled="loading" @click="handleSubmit">{{ loading ? '添加中…' : '添加' }}</AurosButton>
    </div>
  </AurosSheet>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px 0 2px;
}

.error {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.02em;
  color: rgba(253, 233, 255, 0.88);
}

.actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--au-hair);
}
</style>
