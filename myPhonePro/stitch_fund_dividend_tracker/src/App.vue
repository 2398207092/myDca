<script setup lang="ts">
/**
 * myDca 新前端根组件。
 * 只有「现在 / 过去 / 未来」三面带壳（顶栏 + 底坞）；登录页不戴壳。
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '@/shared/components/AppShell.vue'
import SheetHost from '@/features/sheets/SheetHost.vue'
import ToastHost from '@/shared/components/ToastHost.vue'

const route = useRoute()
const withChrome = computed(() => route.meta.chrome === true)
</script>

<template>
  <AppShell v-if="withChrome">
    <router-view />
  </AppShell>
  <router-view v-else />
  <SheetHost />
  <ToastHost />
</template>
