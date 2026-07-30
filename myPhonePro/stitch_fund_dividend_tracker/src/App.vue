<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import BottomNav from '@/components/shared/BottomNav.vue'

const route = useRoute()

const showBottomNav = computed(() => {
  return route.meta.level === 1
})

// 登录页隐藏导航
const showAppNav = computed(() => {
  return String(route.name) !== 'login'
})

// 二级详情页排除 KeepAlive 缓存，使用组件名（PascalCase）而非路由名
const excludePages = ['HoldingDetailPage', 'DividendHistoryPage', 'TransactionListPage']
</script>

<template>
  <div class="min-h-screen bg-page-bg">
    <template v-if="showAppNav">
      <router-view v-slot="{ Component }">
        <KeepAlive :exclude="excludePages">
          <component :is="Component" />
        </KeepAlive>
      </router-view>
      <BottomNav v-if="showBottomNav" />
    </template>
    <template v-else>
      <router-view />
    </template>
  </div>
</template>
