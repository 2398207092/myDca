// 种树 PWA Service Worker
// 策略:应用 shell 缓存优先,API 请求走网络,离线回退到首页

const CACHE_VERSION = 'v1-20260730'
const APP_SHELL_CACHE = `fund-tracker-shell-${CACHE_VERSION}`
const OFFLINE_URL = '/'

// 应用 shell 核心资源(相对路径,构建后会被 Vite 加 hash)
const APP_SHELL_URLS = [
  '/',
  '/index.html',
  '/manifest.json',
  '/favicon.svg',
  '/icon-512.jpg',
]

// 安装:预缓存应用 shell
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(APP_SHELL_CACHE)
      .then((cache) => cache.addAll(APP_SHELL_URLS))
      .then(() => self.skipWaiting())
      .catch((err) => console.warn('[SW] 预缓存失败:', err))
  )
})

// 激活:清理旧缓存
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(
        keys
          .filter((key) => key !== APP_SHELL_CACHE)
          .map((key) => caches.delete(key))
      ))
      .then(() => self.clients.claim())
  )
})

// 请求拦截
self.addEventListener('fetch', (event) => {
  const { request } = event
  const url = new URL(request.url)

  // 1. API 请求:只走网络,不缓存(实时金融数据)
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(fetch(request))
    return
  }

  // 2. 导航请求(HTML 页面):缓存优先,网络回退,离线兜底
  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .then((response) => {
          const copy = response.clone()
          caches.open(APP_SHELL_CACHE).then((cache) => cache.put(request, copy))
          return response
        })
        .catch(() => caches.match(request).then((cached) => cached || caches.match(OFFLINE_URL)))
    )
    return
  }

  // 3. 静态资源(JS/CSS/图片/字体):缓存优先,网络回退
  event.respondWith(
    caches.match(request).then((cached) => {
      if (cached) return cached
      return fetch(request).then((response) => {
        // 只缓存成功的同源响应
        if (response.ok && url.origin === self.location.origin) {
          const copy = response.clone()
          caches.open(APP_SHELL_CACHE).then((cache) => cache.put(request, copy))
        }
        return response
      })
    })
  )
})
