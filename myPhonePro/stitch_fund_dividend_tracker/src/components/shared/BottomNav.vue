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
    class="fixed bottom-0 w-full z-50 rounded-t-xl bg-card-bg shadow-elevated border-t border-border-light"
  >
    <div class="flex justify-around items-center h-16 w-full px-sm safe-bottom">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="flex flex-col items-center justify-center transition-all duration-200 active:scale-95 min-w-[64px] py-1 rounded-xl"
        :class="
          activeTab === tab.key
            ? 'bg-brand-light'
            : 'hover:bg-card-alt'
        "
        @click="navigate(tab.key, tab.route)"
      >
        <span
          class="material-symbols-outlined text-[24px] transition-all duration-200"
          :class="activeTab === tab.key ? 'text-brand' : 'text-text-secondary'"
          :style="activeTab === tab.key
            ? { fontVariationSettings: '&quot;FILL&quot; 1, &quot;wght&quot; 700' }
            : { fontVariationSettings: '&quot;FILL&quot; 0, &quot;wght&quot; 400' }"
        >{{ tab.icon }}</span>
        <span
          class="text-xs transition-all duration-200 mt-0.5"
          :class="activeTab === tab.key ? 'text-brand font-medium' : 'text-text-secondary'"
        >{{ tab.label }}</span>
      </button>
    </div>
  </nav>
</template>
