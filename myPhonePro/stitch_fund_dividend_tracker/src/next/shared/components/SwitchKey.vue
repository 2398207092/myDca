<script setup lang="ts">
/** 物理拨杆：轨道凹陷，滑块凸起；状态同时有文字（aria + slot）承载，不只靠位置/颜色。 */
const props = defineProps<{
  modelValue: boolean
  label: string
}>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

function toggle(): void {
  emit('update:modelValue', !props.modelValue)
}
</script>

<template>
  <button
    class="switch"
    :class="{ 'switch--on': modelValue }"
    type="button"
    role="switch"
    :aria-checked="modelValue"
    :aria-label="label"
    @click="toggle"
  >
    <span class="knob" />
  </button>
</template>

<style scoped>
.switch {
  width: 48px;
  height: 28px;
  flex: none;
  border-radius: var(--radius-pill);
  background: var(--canvas);
  box-shadow: var(--in-slot);
  position: relative;
  padding: 0;
  border: 1px solid transparent;
}
.knob {
  position: absolute;
  left: 3px;
  top: 3px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--canvas);
  box-shadow: var(--el-key);
  transition: transform 160ms ease, background 160ms ease;
}
.switch--on .knob {
  transform: translateX(20px);
  background: var(--accent);
  box-shadow: -1px -1px 3px rgba(255, 255, 255, 0.55), 2px 2px 5px rgba(31, 35, 41, 0.28);
}
.switch:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
</style>
