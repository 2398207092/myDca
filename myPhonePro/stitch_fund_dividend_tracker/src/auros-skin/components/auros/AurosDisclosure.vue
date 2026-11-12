<script setup lang="ts">
/**
 * AurosDisclosure —— "还有 N 条"的折叠（密度预算的出口）
 * 默认收起；展开后渲染插槽内容。样式与 AurosRow 同一句话，不引入新形状。
 */
import { ref } from 'vue'

const props = withDefaults(
  defineProps<{
    count: number | string
    label?: string
    modelValue?: boolean
  }>(),
  { label: '展开', modelValue: false },
)
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()
const open = ref(props.modelValue)

function toggle() {
  open.value = !open.value
  emit('update:modelValue', open.value)
}
</script>

<template>
  <div class="disc">
    <button type="button" class="disc__btn" :aria-expanded="open" @click="toggle">
      <span class="disc__text">还有 {{ count }} 条</span>
      <span class="disc__action">{{ open ? '收起' : label }}</span>
      <svg class="disc__chev" :class="{ 'is-open': open }" viewBox="0 0 12 12" width="10" height="10" fill="none">
        <path d="M2.6 4.6 6 8l3.4-3.4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </button>
    <div v-if="open" class="disc__body"><slot /></div>
  </div>
</template>

<style scoped>
.disc__btn {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding: 6px 0;
  border: 0;
  background: none;
  font-family: inherit;
  text-align: left;
}
.disc__text {
  font-size: 13px;
  color: rgba(187, 199, 198, 0.8);
}
.disc__action {
  margin-left: auto;
  font-size: 10px;
  letter-spacing: 0.08em;
  color: var(--au-silver);
}
.disc__chev {
  flex: none;
  color: rgba(187, 199, 198, 0.6);
  transition: transform 240ms var(--au-ease);
}
.disc__chev.is-open {
  transform: rotate(180deg);
}
.disc__body {
  animation: disc-in 260ms var(--au-ease) both;
}
@keyframes disc-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
