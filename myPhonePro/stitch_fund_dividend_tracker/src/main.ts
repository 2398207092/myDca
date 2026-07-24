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
