<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { NavTab } from '@/types'

const route = useRoute()
const router = useRouter()

const tabs: { key: NavTab; label: string; icon: string; route: string }[] = [
  { key: 'holdings', label: '持仓', icon: 'bar_chart', route: '/' },
  { key: 'calendar', label: '分红日历', icon: 'calendar_month', route: '/calendar' },
  { key: 'discover', label: '发现', icon: 'rocket_launch', route: '/discover' },
  { key: 'profile', label: '我的', icon: 'person', route: '/profile' },
]

const activeTab = computed(() => {
  const name = route.name
  if (name === 'home') return 'holdings'
  if (name === 'calendar') return 'calendar'
  if (name === 'discover') return 'discover'
  if (name === 'profile') return 'profile'
  return 'holdings'
})

function navigate(tab: NavTab, path: string) {
  if (tab === activeTab.value) return
  router.push(path)
}
</script>

<template>
  <nav
    class="fixed bottom-0 w-full z-50 rounded-t-xl bg-surface shadow-[0_-4px_12px_0_rgba(0,0,0,0.05)] border-t border-outline-variant"
  >
    <div class="flex justify-around items-center h-16 w-full px-sm safe-bottom">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="flex flex-col items-center justify-center transition-all duration-150 active:scale-95"
        :class="
          activeTab === tab.key
            ? 'bg-primary-container/20 text-on-primary-container rounded-full px-4 py-1'
            : 'text-on-secondary-container hover:text-primary'
        "
        @click="navigate(tab.key, tab.route)"
      >
        <span
          class="material-symbols-outlined"
          :class="{ fill: activeTab === tab.key }"
        >{{ tab.icon }}</span>
        <span class="font-label-bold text-[10px]">{{ tab.label }}</span>
      </button>
    </div>
  </nav>
</template>
