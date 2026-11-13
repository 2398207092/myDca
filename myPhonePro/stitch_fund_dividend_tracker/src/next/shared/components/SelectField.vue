<script setup lang="ts">
/** 下拉选择：凹槽 + 原生 select（移动端原生选择器最稳，且键盘可操作）。 */
import { computed } from 'vue'

export interface SelectOption {
  value: string
  label: string
  disabled?: boolean
}

const props = withDefaults(
  defineProps<{
    modelValue: string
    label: string
    options: SelectOption[]
    placeholder?: string
    error?: string
    disabled?: boolean
  }>(),
  { placeholder: '请选择', error: '', disabled: false },
)
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()

const selectId = computed(() => `s-${Math.random().toString(36).slice(2, 8)}`)

function onChange(e: Event): void {
  emit('update:modelValue', (e.target as HTMLSelectElement).value)
}
</script>

<template>
  <div class="field">
    <label class="field-label" :for="selectId">{{ label }}</label>
    <div class="field-well" :class="{ 'field-well--error': error }">
      <select
        :id="selectId"
        class="field-select"
        :value="modelValue"
        :disabled="disabled"
        :aria-invalid="Boolean(error)"
        @change="onChange"
      >
        <option value="" disabled>{{ placeholder }}</option>
        <option v-for="opt in options" :key="opt.value" :value="opt.value" :disabled="opt.disabled">
          {{ opt.label }}
        </option>
      </select>
      <span class="chevron" aria-hidden="true">▾</span>
    </div>
    <p v-if="error" class="field-error" role="alert">{{ error }}</p>
  </div>
</template>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}
.field-label {
  font-size: var(--text-label);
  font-weight: 500;
  color: var(--ink-2);
}
.field-well {
  display: flex;
  align-items: center;
  min-height: 52px;
  padding: 0 var(--space-3) 0 var(--space-4);
  border-radius: var(--radius-key);
  background: var(--canvas);
  box-shadow: var(--in-slot);
  border: 1px solid transparent;
}
.field-well--error {
  border-color: rgba(166, 27, 27, 0.5);
}
.field-select {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: transparent;
  appearance: none;
  font-size: var(--text-body);
  color: var(--ink);
  padding: 0;
}
.field-select:invalid {
  color: var(--ink-3);
}
.chevron {
  color: var(--ink-3);
  font-size: 12px;
}
.field-error {
  font-size: var(--text-micro);
  color: var(--danger);
  line-height: 1.45;
}
</style>
