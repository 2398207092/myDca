<script setup lang="ts">
/**
 * 应用外壳（2026-11-08 全站 Auros 化）
 * 全站深海底色 + 统一的顶栏/底栏；页面自己负责内容与滚动。
 */
import { computed, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import AurosTabBar from '@/auros-skin/components/auros/AurosTabBar.vue'

const route = useRoute()

/** 一级页面显示底部导航；登录页不显示 */
const showTab = computed(() => route.meta.level === 1 && String(route.name) !== 'login')

// 把深海底色写到 html/body，避免下拉回弹与首帧闪白
watchEffect(() => {
  if (typeof document === 'undefined') return
  document.documentElement.classList.add('auros-route')
})

// 二级详情页排除 KeepAlive 缓存，使用组件名（PascalCase）而非路由名
const excludePages = ['HoldingDetailPage', 'DividendHistoryPage', 'TransactionListPage']
</script>

<template>
  <div class="auros app-shell">
    <router-view v-slot="{ Component }">
      <KeepAlive :exclude="excludePages">
        <component :is="Component" />
      </KeepAlive>
    </router-view>
    <AurosTabBar v-if="showTab" />
  </div>
</template>

<style scoped>
.app-shell {
  position: relative;
  min-height: 100svh;
  background: var(--au-abyss);
  isolation: isolate;
}
/* 全站共用一层深海氛围：右上青光 + 纵向深水色阶 */
.app-shell::before {
  content: '';
  position: fixed;
  inset: 0;
  z-index: -1;
  background:
    radial-gradient(120% 40% at 78% 0%, rgba(0, 130, 124, 0.26) 0%, rgba(1, 38, 36, 0) 58%),
    linear-gradient(180deg, var(--au-abyss) 0%, var(--au-abyss) 64%, var(--au-deep) 100%);
}
</style>
