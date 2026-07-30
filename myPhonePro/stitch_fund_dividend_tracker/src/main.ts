import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { initAuth } from './api/request'
import './assets/styles/main.css'

// 启动时检查 Token 有效性
async function initApp() {
  // 先挂载 App，让路由系统就绪
  const app = createApp(App)
  app.use(router)
  app.mount('#app')

  // 再检查 Token，未登录则跳转
  const loggedIn = await initAuth()
  if (!loggedIn) {
    router.push({ name: 'login' })
  }
}

initApp()

// 生产环境注册 Service Worker(开发时不注册,避免缓存干扰 HMR)
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker
      .register('/sw.js')
      .then(() => console.log('[PWA] Service Worker 已注册'))
      .catch((err) => console.warn('[PWA] Service Worker 注册失败:', err))
  })
}
