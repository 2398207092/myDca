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

const iconCategories = [
  {
    label: '生活',
    icons: ['📌', '🏡', '🛏️', '🧹', '👕', '👶', '🐱', '🐶'],
  },
  {
    label: '出行',
    icons: ['🚗', '🚌', '🚇', '✈️', '⛽', '🅿️'],
  },
  {
    label: '饮食',
    icons: ['☕', '🍜', '🍱', '🥤', '🍺', '🛒'],
  },
  {
    label: '娱乐学习',
    icons: ['🎮', '🎬', '🎵', '📚', '🏋️', '⚽'],
  },
  {
    label: '医疗保险',
    icons: ['💊', '🏥', '🛡️', '🦷'],
  },
]

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
    <div class="bg-white w-full max-w-md rounded-3xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
      <!-- Header -->
      <header class="pt-8 pb-4 text-center">
        <h1 class="text-xl font-bold text-gray-800">添加自定义支出</h1>
      </header>

      <!-- Scrollable Form -->
      <div class="px-6 pb-6 overflow-y-auto flex-1">
        <!-- Name -->
        <section class="mb-6">
          <label class="block text-gray-400 text-sm font-medium mb-2">名称</label>
          <input
            v-model="name"
            class="w-full bg-gray-50 border-none rounded-xl py-4 px-4 text-gray-700 focus:ring-2 focus:ring-[#FF7A45] focus:bg-white transition-all"
            placeholder="如：健身房"
            type="text"
          />
        </section>

        <!-- Icon Picker -->
        <section class="mb-6">
          <label class="block text-gray-400 text-sm font-medium mb-3">图标</label>
          <div v-for="cat in iconCategories" :key="cat.label" class="mb-4">
            <p class="text-gray-300 text-xs mb-2">{{ cat.label }}</p>
            <div class="grid grid-cols-7 gap-2">
              <button
                v-for="ic in cat.icons"
                :key="ic"
                @click="icon = ic"
                :class="[
                  'aspect-square flex items-center justify-center rounded-xl text-xl transition-all',
                  icon === ic
                    ? 'bg-[#FF7A45]/10 border-2 border-[#FF7A45]'
                    : 'bg-gray-50 hover:bg-gray-100'
                ]"
              >
                <span>{{ ic }}</span>
              </button>
            </div>
          </div>
        </section>

        <!-- Amount -->
        <section class="mb-2">
          <label class="block text-gray-400 text-sm font-medium mb-2">月度金额（元）</label>
          <input
            v-model.number="amount"
            class="w-full bg-gray-50 border-none rounded-xl py-4 px-4 text-gray-700 focus:ring-2 focus:ring-[#FF7A45] focus:bg-white transition-all"
            placeholder="如：200"
            type="number"
          />
        </section>

        <p v-if="error" class="text-red-500 text-sm mt-2">{{ error }}</p>
      </div>

      <!-- Footer -->
      <footer class="p-6 pt-2 flex gap-4 bg-white">
        <button
          @click="$emit('close')"
          class="flex-1 py-4 bg-[#F4F3F3] text-gray-700 font-semibold rounded-2xl hover:bg-gray-200 transition-colors"
        >
          取消
        </button>
        <button
          @click="handleSubmit"
          :disabled="loading"
          class="flex-1 py-4 bg-[#333333] text-white font-semibold rounded-2xl hover:bg-black transition-colors disabled:opacity-50"
        >
          {{ loading ? '添加中...' : '添加' }}
        </button>
      </footer>
    </div>
  </div>
</template>
