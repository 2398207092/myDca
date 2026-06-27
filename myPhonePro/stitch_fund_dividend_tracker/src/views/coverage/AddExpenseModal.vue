<script setup lang="ts">
import { ref } from 'vue'
import { createExpense } from '@/api/expense'

const emit = defineEmits<{
  close: []
  added: []
}>()

const name = ref('')
const icon = ref('📌')
const amount = ref<number | null>(null)
const loading = ref(false)
const error = ref('')

const allIcons = ['📌', '🏡', '🛏️', '🧹', '👕', '👶', '🐱', '🐶', '🚗', '🚌', '🚇', '✈️', '⛽', '🅿️', '☕', '🍜', '🍱', '🥤', '🍺', '🛒', '🎮', '🎬', '🎵', '📚', '🏋️', '⚽', '💊', '🏥', '🛡️', '🦷']

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
  <!-- Overlay -->
  <div
    class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-6"
    @click.self="$emit('close')"
  >
    <!-- Modal -->
    <div class="bg-card-bg w-full max-w-md rounded-2xl shadow-elevated overflow-hidden flex flex-col max-h-[90vh]">
      <!-- Header -->
      <header class="pt-6 pb-4 text-center">
        <h1 class="font-body text-md font-medium text-text-primary">添加自定义支出</h1>
      </header>

      <!-- Scrollable Form -->
      <div class="px-gutter pb-4 overflow-y-auto flex-1">
        <!-- Name + Icon inline -->
        <section class="mb-3">
          <label class="block font-body text-xs text-text-tertiary mb-2">名称</label>
          <div class="flex gap-3">
            <input
              v-model="name"
              class="flex-1 bg-card-alt rounded-xl py-3 px-md text-text-primary font-body text-sm outline-none focus:ring-2 focus:ring-brand transition-all"
              placeholder="如：健身房"
              type="text"
            />
            <div class="relative">
              <div
                class="w-12 h-12 bg-card-alt rounded-xl flex items-center justify-center text-xl border-2 transition-all"
                :class="icon ? 'border-brand bg-brand-light' : 'border-transparent'"
              >
                {{ icon }}
              </div>
            </div>
          </div>
        </section>

        <!-- Icon Picker - compact flat grid -->
        <section class="mb-3">
          <div class="grid grid-cols-8 gap-[6px]">
            <button
              v-for="ic in allIcons"
              :key="ic"
              @click="icon = ic"
              :class="[
                'aspect-square flex items-center justify-center rounded-lg text-base transition-all',
                icon === ic
                  ? 'bg-brand-light border border-brand'
                  : 'bg-card-alt hover:bg-card-alt/80 border border-transparent'
              ]"
            >
              <span>{{ ic }}</span>
            </button>
          </div>
        </section>

        <!-- Amount -->
        <section class="mb-2">
          <label class="block font-body text-xs text-text-tertiary mb-2">月度金额（元）</label>
          <input
            v-model.number="amount"
            class="w-full bg-card-alt rounded-xl py-3 px-md text-text-primary font-body text-sm outline-none focus:ring-2 focus:ring-brand transition-all"
            placeholder="如：200"
            type="number"
          />
        </section>

        <p v-if="error" class="text-error font-body text-xs mt-2">{{ error }}</p>
      </div>

      <!-- Footer -->
      <footer class="p-gutter pt-2 flex gap-md bg-card-bg">
        <button
          @click="$emit('close')"
          class="flex-1 h-12 bg-card-alt text-text-secondary font-body text-sm font-medium rounded-xl active:scale-[0.98] transition-all"
        >
          取消
        </button>
        <button
          @click="handleSubmit"
          :disabled="loading"
          class="flex-1 h-12 bg-brand text-white font-body text-sm font-medium rounded-xl active:scale-[0.98] transition-all disabled:opacity-50"
        >
          {{ loading ? '添加中...' : '添加' }}
        </button>
      </footer>
    </div>
  </div>
</template>
