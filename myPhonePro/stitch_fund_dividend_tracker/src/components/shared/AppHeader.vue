<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'

interface Props {
  title: string
  showBack?: boolean
  showLogo?: boolean
  rightIcon?: string
  rightAction?: () => void
}

const props = withDefaults(defineProps<Props>(), {
  showBack: false,
  showLogo: false,
})

const router = useRouter()
const scrolled = ref(false)

function handleScroll() {
  scrolled.value = window.scrollY > 10
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})

function goBack() {
  router.back()
}
</script>

<template>
  <header
    class="fixed top-0 w-full z-50 bg-surface transition-shadow duration-200"
    :class="scrolled ? 'shadow-md' : 'shadow-sm'"
  >
    <div class="flex items-center justify-between px-gutter h-14 w-full max-w-[600px] mx-auto">
      <div class="flex items-center gap-2">
        <button
          v-if="showBack"
          class="w-10 h-10 flex items-center justify-center -ml-2 active:opacity-80 transition-opacity"
          @click="goBack"
        >
          <span class="material-symbols-outlined text-on-surface-variant">arrow_back</span>
        </button>
        <span
          v-if="showLogo"
          class="material-symbols-outlined text-primary"
          style="font-size: 28px;"
        >park</span>
        <h1
          class="font-headline-lg text-headline-lg font-bold"
          :class="showLogo ? 'text-primary' : 'text-on-surface'"
        >{{ title }}</h1>
      </div>
      <div class="flex items-center gap-2">
        <button
          v-if="rightIcon"
          class="w-10 h-10 flex items-center justify-center rounded-full hover:bg-surface-container transition-colors"
          @click="rightAction"
        >
          <span class="material-symbols-outlined text-on-surface-variant">{{ rightIcon }}</span>
        </button>
      </div>
    </div>
  </header>
</template>
