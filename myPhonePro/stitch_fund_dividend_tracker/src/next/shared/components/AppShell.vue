<script setup lang="ts">
/** 应用壳：顶栏 + 内容 + 底坞。只在三面路由下启用（登录页不戴壳）。 */
import TopBar from './TopBar.vue'
import FaceDock from './FaceDock.vue'
</script>

<template>
  <div class="shell">
    <a class="skip-link" href="#main-content">跳到内容</a>
    <TopBar />
    <main id="main-content" class="shell-main">
      <slot />
    </main>
    <FaceDock />
  </div>
</template>

<style scoped>
.shell {
  min-height: 100dvh;
  max-width: 520px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  background: var(--canvas);
}
.shell-main {
  flex: 1;
  padding: 0 var(--gutter) calc(var(--dock-h) + var(--safe-bottom) + var(--space-6));
}
.skip-link {
  position: absolute;
  left: -9999px;
  top: 0;
  z-index: 500;
  background: var(--accent);
  color: var(--on-accent);
  padding: var(--space-2) var(--space-4);
  border-radius: 0 0 var(--radius-key) 0;
}
.skip-link:focus {
  left: 0;
}
</style>
