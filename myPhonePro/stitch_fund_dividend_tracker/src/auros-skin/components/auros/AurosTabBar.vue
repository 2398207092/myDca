<script setup lang="ts">
/** 全站深色底栏。细线几何图标，当前项下方一枚点亮的点。 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const items = [
  { key: 'home', label: '首页', path: '/', icon: 'home' },
  { key: 'calendar', label: '日历', path: '/calendar', icon: 'calendar' },
  { key: 'discover', label: '发现', path: '/discover', icon: 'compass' },
  { key: 'profile', label: '我的', path: '/profile', icon: 'person' },
] as const

const active = computed(() => String(route.name || 'home'))

function go(path: string) {
  if (route.path !== path) router.push(path)
}
</script>

<template>
  <nav class="au-tabbar" aria-label="主导航">
    <button
      v-for="it in items"
      :key="it.key"
      type="button"
      class="au-tabbar__item"
      :class="{ 'is-active': active === it.key }"
      @click="go(it.path)"
    >
      <svg viewBox="0 0 20 20" width="20" height="20" fill="none" aria-hidden="true">
        <g v-if="it.icon === 'home'" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round">
          <path d="M3.2 9.4 10 3.4l6.8 6" />
          <path d="M5.2 8.6v7.9h9.6V8.6" />
        </g>
        <g v-else-if="it.icon === 'calendar'" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round">
          <rect x="3.2" y="4.6" width="13.6" height="12" rx="1.4" />
          <path d="M3.2 8.2h13.6M6.9 3.2v2.6M13.1 3.2v2.6" stroke-linecap="round" />
        </g>
        <g v-else-if="it.icon === 'compass'" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round">
          <circle cx="10" cy="10" r="6.9" />
          <path d="m7.4 12.6 1.5-3.7 3.7-1.5-1.5 3.7z" />
        </g>
        <g v-else stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="10" cy="7.1" r="2.9" />
          <path d="M4.4 16.6c.5-2.9 2.8-4.5 5.6-4.5s5.1 1.6 5.6 4.5" />
        </g>
      </svg>
      <span class="au-tabbar__label">{{ it.label }}</span>
      <span class="au-tabbar__dot" aria-hidden="true"></span>
    </button>
  </nav>
</template>

<style scoped>
.au-tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 40;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  padding-bottom: env(safe-area-inset-bottom, 0px);
  background: rgba(1, 29, 28, 0.74);
  border-top: 1px solid var(--au-hair);
  backdrop-filter: blur(18px) saturate(1.2);
  -webkit-backdrop-filter: blur(18px) saturate(1.2);
}
.au-tabbar__item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 58px;
  padding: 0;
  border: 0;
  background: none;
  color: rgba(187, 199, 198, 0.52);
  transition: color 240ms var(--au-ease);
}
.au-tabbar__item.is-active {
  color: var(--au-mist);
}
.au-tabbar__label {
  font-size: 10px;
  line-height: 1;
  letter-spacing: 0.16em;
  text-indent: 0.16em;
}
.au-tabbar__dot {
  position: absolute;
  bottom: 7px;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: transparent;
  transition: background 240ms var(--au-ease), box-shadow 240ms var(--au-ease);
}
.au-tabbar__item.is-active .au-tabbar__dot {
  background: var(--au-lit);
  box-shadow: 0 0 8px rgba(203, 255, 252, 0.8);
}
</style>
