import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './styles/tokens.css'
import './styles/base.css'

const app = createApp(App)
app.use(router)
app.mount('#app')

// 生产环境注册 Service Worker（开发时不注册，避免缓存干扰 HMR）
// 三套皮肤共用同一个 /sw.js 与同一份缓存策略。
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker
      .register('/sw.js')
      .then(() => console.log('[PWA] Service Worker 已注册'))
      .catch((err) => console.warn('[PWA] Service Worker 注册失败:', err))
  })
}
