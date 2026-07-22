<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import BottomNav from '@/components/shared/BottomNav.vue'

const route = useRoute()

const level1Pages = new Set(['home', 'calendar', 'discover', 'profile'])
const showBottomNav = computed(() => {
  return level1Pages.has(String(route.name))
})

// 二级详情页排除 KeepAlive 缓存，使用组件名（PascalCase）而非路由名
const excludePages = ['HoldingDetailPage', 'DividendHistoryPage', 'TransactionListPage']
</script>

<template>
  <div class="min-h-screen bg-page-bg">
    <router-view v-slot="{ Component }">
      <KeepAlive :exclude="excludePages">
        <component :is="Component" />
      </KeepAlive>
    </router-view>
    <BottomNav v-if="showBottomNav" />
  </div>
</template>
