import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './styles/tokens.css'
import './styles/base.css'

/**
 * 旧版 PWA / 双皮肤体系的收尾清理：
 * 新前端是唯一入口，不再区分皮肤，也不再使用缓存优先的 Service Worker。
 * 这里顺手注销旧 SW、清掉旧的 shell 缓存，避免浏览器继续展示旧入口。
 */
if ('serviceWorker' in navigator) {
  navigator.serviceWorker
    .getRegistrations()
    .then((registrations) => registrations.forEach((registration) => void registration.unregister()))
    .catch(() => {})
}
if ('caches' in window) {
  caches
    .keys()
    .then((keys) =>
      keys
        .filter((key) => key.startsWith('fund-tracker-shell-'))
        .forEach((key) => void caches.delete(key)),
    )
    .catch(() => {})
}

const app = createApp(App)
app.use(router)
app.mount('#app')
