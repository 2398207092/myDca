<script setup lang="ts">
/**
 * AurosNavBar —— 全站顶栏（2026-11-08 全站重做）
 * 44px，左对齐标题，返回键为 9px 细箭头；底边一条发丝线，不描边、不投影。
 */
import { useRouter } from 'vue-router'

const props = withDefaults(
  defineProps<{
    title?: string
    sub?: string
    back?: boolean
    border?: boolean
    /** 指定返回目标（默认 router.back()） */
    backTo?: string
  }>(),
  {
    title: '',
    sub: '',
    back: true,
    border: true,
    backTo: '',
  },
)

const router = useRouter()

function goBack() {
  if (props.backTo) {
    router.push(props.backTo)
    return
  }
  if (window.history.length > 1) router.back()
  else router.push('/')
}
</script>

<template>
  <header class="nav" :class="{ 'nav--border': border }">
    <button v-if="back" type="button" class="nav__back" aria-label="返回" @click="goBack">
      <svg viewBox="0 0 12 12" width="12" height="12" fill="none">
        <path d="M7.4 2.6 4 6l3.4 3.4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </button>
    <div class="nav__title">
      <span class="nav__t">{{ title }}</span>
      <span v-if="sub" class="nav__s">{{ sub }}</span>
    </div>
    <div class="nav__right"><slot name="right" /></div>
  </header>
</template>

<style scoped>
.nav {
  position: sticky;
  top: 0;
  z-index: 20;
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 44px;
  padding: 0 16px;
  background: rgba(1, 38, 36, 0.78);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
}
.nav--border::after {
  content: '';
  position: absolute;
  left: 16px;
  right: 16px;
  bottom: 0;
  height: 1px;
  background: var(--au-hair);
}
.nav__back {
  flex: none;
  width: 28px;
  height: 28px;
  margin-left: -6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: none;
  color: var(--au-silver);
  transition: color 160ms var(--au-ease);
}
.nav__back:active {
  color: var(--au-mist);
}
.nav__title {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 8px;
  overflow: hidden;
}
.nav__t {
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 0.02em;
  color: var(--au-white);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.nav__s {
  font-size: 10px;
  letter-spacing: 0.08em;
  color: var(--au-silver);
  white-space: nowrap;
}
.nav__right {
  flex: none;
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
