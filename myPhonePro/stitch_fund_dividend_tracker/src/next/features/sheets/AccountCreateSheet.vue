<script setup lang="ts">
/** 新建账户：名称 / 用途 / 本位币。USDT 账户只能挂加密资产。 */
import { reactive, ref } from 'vue'
import { createAccount } from '@/api/account'
import SheetFrame from '@/next/shared/components/SheetFrame.vue'
import TextField from '@/next/shared/components/TextField.vue'
import SelectField from '@/next/shared/components/SelectField.vue'
import BtnKey from '@/next/shared/components/BtnKey.vue'
import { closeSheet, refreshData, toast } from '@/next/shared/ui'
import { errorText } from '@/next/shared/useAsync'

const PURPOSES = [
  { value: 'daily', label: '日常' },
  { value: 'invest', label: '投资' },
  { value: 'exchange', label: '出入金' },
  { value: 'other', label: '其他' },
]
const CURRENCIES = [
  { value: 'CNY', label: 'CNY 人民币' },
  { value: 'USDT', label: 'USDT 计价' },
]

const form = reactive({ name: '', purpose: 'invest', baseCurrency: 'CNY' })
const submitting = ref(false)
const submitError = ref('')

async function submit(): Promise<void> {
  if (submitting.value) return
  if (!form.name.trim()) {
    submitError.value = '账户名称不能为空'
    return
  }
  submitting.value = true
  submitError.value = ''
  try {
    await createAccount({
      name: form.name.trim(),
      purpose: form.purpose,
      baseCurrency: form.baseCurrency,
    })
    toast('账户已创建')
    refreshData()
    closeSheet()
  } catch (e) {
    submitError.value = errorText(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <SheetFrame title="新建账户" subtitle="账户只是资产的归属；现金与持仓都可以挂到账户下" @close="closeSheet">
    <TextField v-model="form.name" label="账户名称" placeholder="例如 理财账户" :maxlength="20" />
    <SelectField v-model="form.purpose" label="用途" :options="PURPOSES" placeholder="选择用途" />
    <SelectField v-model="form.baseCurrency" label="本位币" :options="CURRENCIES" placeholder="CNY" />
    <p class="note micro">
      提示：USDT 账户只能挂加密资产；CNY 账户挂人民币资产。账户合计按 CNY 折算展示。
    </p>
    <p v-if="submitError" class="submit-error" role="alert">{{ submitError }}</p>

    <template #footer>
      <BtnKey variant="secondary" block :disabled="submitting" @click="closeSheet">取消</BtnKey>
      <BtnKey variant="primary" block :disabled="submitting" @click="submit">
        {{ submitting ? '创建中…' : '创建账户' }}
      </BtnKey>
    </template>
  </SheetFrame>
</template>

<style scoped>
.note {
  margin-top: var(--space-2);
  line-height: 1.6;
}
.submit-error {
  margin-top: var(--space-2);
  font-size: var(--text-label);
  color: var(--danger);
  line-height: 1.5;
}
</style>
