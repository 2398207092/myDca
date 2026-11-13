<script setup lang="ts">
/** 文本/数字输入：凹槽（inset），标签显式，错误靠近字段。 */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: string
    label: string
    placeholder?: string
    type?: 'text' | 'number' | 'email' | 'password'
    inputmode?: 'text' | 'decimal' | 'numeric' | 'email'
    autocomplete?: string
    error?: string
    hint?: string
    maxlength?: number
    suffix?: string
  }>(),
  { placeholder: '', type: 'text', inputmode: 'text', autocomplete: 'off', error: '', hint: '', maxlength: 64, suffix: '' },
)

const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()

const inputId = computed(() => `f-${Math.random().toString(36).slice(2, 8)}`)

function onInput(e: Event): void {
  emit('update:modelValue', (e.target as HTMLInputElement).value)
}
</script>

<template>
  <div class="field">
    <label class="field-label" :for="inputId">{{ label }}</label>
    <div class="field-well" :class="{ 'field-well--error': error }">
      <input
        :id="inputId"
        class="field-input"
        :value="modelValue"
        :type="type"
        :inputmode="inputmode"
        :autocomplete="autocomplete"
        :placeholder="placeholder"
        :maxlength="maxlength"
        :aria-invalid="Boolean(error)"
        @input="onInput"
      />
      <span v-if="suffix" class="field-suffix">{{ suffix }}</span>
    </div>
    <p v-if="error" class="field-error" role="alert">{{ error }}</p>
    <p v-else-if="hint" class="field-hint">{{ hint }}</p>
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
  gap: var(--space-2);
  min-height: 52px;
  padding: 0 var(--space-4);
  border-radius: var(--radius-key);
  background: var(--canvas);
  box-shadow: var(--in-slot);
  border: 1px solid transparent;
}
.field-well--error {
  border-color: rgba(166, 27, 27, 0.5);
}
.field-input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: transparent;
  font-size: var(--text-body);
  color: var(--ink);
  padding: 0;
}
.field-input::placeholder {
  color: var(--ink-3);
}
.field-suffix {
  font-size: var(--text-label);
  color: var(--ink-3);
}
.field-error {
  font-size: var(--text-micro);
  color: var(--danger);
  line-height: 1.45;
}
.field-hint {
  font-size: var(--text-micro);
  color: var(--ink-3);
  line-height: 1.45;
}
</style>
