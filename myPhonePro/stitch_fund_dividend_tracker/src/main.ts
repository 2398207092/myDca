import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { getToken, setToken } from './api/request'
import { fetchToken } from './api/auth'
import './assets/styles/main.css'

// 启动时检查并初始化 Token
async function initApp() {
  if (!getToken()) {
    try {
      await fetchToken()
      console.log('>>> Token 已初始化')
    } catch (e) {
      console.error('>>> Token 初始化失败:', e)
    }
  }

  const app = createApp(App)
  app.use(router)
  app.mount('#app')
}

initApp()
