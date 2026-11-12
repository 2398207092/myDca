<script setup lang="ts">
/**
 * AurosField —— 全站输入
 * 下沉井色块 + 6px 圆角 + 顶缘一道发丝亮线；聚焦时亮线变磷光，不画四边框。
 * 井色取 --au-deep（比抬升卡更暗），保证卡片里也能看见输入区。
 */
const props = withDefaults(
  defineProps<{
    modelValue: string | number | null | undefined
    label?: string
    placeholder?: string
    type?: string
    inputmode?: 'text' | 'decimal' | 'numeric' | 'tel' | 'search'
    suffix?: string
    disabled?: boolean
    mono?: boolean
  }>(),
  { label: '', placeholder: '', type: 'text', inputmode: 'text', suffix: '', disabled: false, mono: false },
)
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void; (e: 'enter'): void }>()

function onInput(e: Event) {
  emit('update:modelValue', (e.target as HTMLInputElement).value)
}
</script>

<template>
  <label class="field" :class="{ 'is-disabled': disabled }">
    <span v-if="label" class="field__label">{{ label }}</span>
    <span class="field__box">
      <input
        class="field__input"
        :class="{ 'is-mono': mono }"
        :type="type"
        :inputmode="inputmode"
        :value="props.modelValue ?? ''"
        :placeholder="placeholder"
        :disabled="disabled"
        @input="onInput"
        @keyup.enter="emit('enter')"
      />
      <span v-if="suffix" class="field__suffix">{{ suffix }}</span>
    </span>
  </label>
</template>

<style scoped>
.field {
  display: block;
}
.field__label {
  display: block;
  margin-bottom: 7px;
  font-size: 10px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--au-silver);
}
.field__box {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 46px;
  padding: 0 12px;
  border-radius: 6px;
  background: rgba(1, 29, 28, 0.55);
  transition: background 200ms var(--au-ease);
}
.field__box::before {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 1px;
  background: rgba(237, 255, 254, 0.12);
  transition: background 200ms var(--au-ease);
}
.field:focus-within .field__box {
  background: rgba(1, 29, 28, 0.82);
}
.field:focus-within .field__box::before {
  background: rgba(203, 255, 252, 0.75);
}
.field__input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: none;
  outline: none;
  color: var(--au-mist);
  font-family: inherit;
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 0.01em;
}
.field__input.is-mono {
  font-variant-numeric: tabular-nums;
}
.field__input::placeholder {
  color: rgba(187, 199, 198, 0.45);
  font-weight: 400;
}
.field__suffix {
  flex: none;
  font-size: 12px;
  color: var(--au-silver);
}
.field.is-disabled {
  opacity: 0.5;
}
</style>
