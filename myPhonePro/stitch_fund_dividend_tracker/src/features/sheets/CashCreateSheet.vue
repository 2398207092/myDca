<script setup lang="ts">
/** 加一笔现金：manual asset（type=cash），进入账户可用余额，可用于买入 / 定投扣款。 */
import { computed, onMounted, reactive, ref } from 'vue'
import { createManualAsset } from '@/api/manualAsset'
import { getAccounts, type AccountItem } from '@/api/account'
import SheetFrame from '@/shared/components/SheetFrame.vue'
import TextField from '@/shared/components/TextField.vue'
import SelectField from '@/shared/components/SelectField.vue'
import BtnKey from '@/shared/components/BtnKey.vue'
import { closeSheet, refreshData, toast } from '@/shared/ui'
import { errorText } from '@/shared/useAsync'
import { fmtMoney, toNumber } from '@/shared/money'

const accounts = ref<AccountItem[]>([])
const accountsError = ref('')
const submitting = ref(false)
const submitError = ref('')

const form = reactive({ name: '现金', amount: '', accountId: '', note: '' })
const accountOptions = computed(() =>
  accounts.value.map((a) => ({ value: a.id, label: `${a.name}（${a.baseCurrency}）` })),
)

onMounted(async () => {
  try {
    accounts.value = await getAccounts()
    const def = accounts.value.find((a) => a.isDefault) ?? accounts.value[0]
    if (def) form.accountId = def.id
  } catch (e) {
    accountsError.value = errorText(e)
  }
})

async function submit(): Promise<void> {
  if (submitting.value) return
  if (!(toNumber(form.amount) > 0)) {
    submitError.value = '金额必须大于 0'
    return
  }
  if (!form.accountId) {
    submitError.value = '请选择归属账户'
    return
  }
  submitting.value = true
  submitError.value = ''
  try {
    await createManualAsset({
      name: form.name.trim() || '现金',
      type: 'cash',
      amount: toNumber(form.amount),
      currency: 'CNY',
      accountId: form.accountId,
      note: form.note.trim() || undefined,
    })
    toast(`已加入现金 ${fmtMoney(toNumber(form.amount))}`)
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
  <SheetFrame title="加一笔现金" subtitle="现金进入账户余额，之后买入 / 定投会从对应账户扣款" @close="closeSheet">
    <TextField
      v-model="form.amount"
      label="金额"
      inputmode="decimal"
      placeholder="例如 5000"
      suffix="元"
    />
    <TextField v-model="form.name" label="名称" placeholder="现金" :maxlength="20" />
    <SelectField
      v-model="form.accountId"
      label="归属账户"
      :options="accountOptions"
      placeholder="选择账户"
    />
    <p v-if="accountsError" class="hint micro">账户加载失败：{{ accountsError }}</p>
    <TextField v-model="form.note" label="备注（可选）" placeholder="例如 银行卡转入" :maxlength="40" />
    <p class="note micro">现金是手动余额，不会自动同步银行流水；买入/定投扣款会实时联动这里的余额。</p>
    <p v-if="submitError" class="submit-error" role="alert">{{ submitError }}</p>

    <template #footer>
      <BtnKey variant="secondary" block :disabled="submitting" @click="closeSheet">取消</BtnKey>
      <BtnKey variant="primary" block :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : '加入现金' }}
      </BtnKey>
    </template>
  </SheetFrame>
</template>

<style scoped>
.hint,
.note {
  margin-top: var(--space-2);
  line-height: 1.6;
  color: var(--warn);
}
.note {
  color: var(--ink-3);
}
.submit-error {
  margin-top: var(--space-2);
  font-size: var(--text-label);
  color: var(--danger);
  line-height: 1.5;
}
</style>
