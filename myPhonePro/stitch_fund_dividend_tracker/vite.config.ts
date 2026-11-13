import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: ['padlock-hypnoses-disburse.ngrok-free.dev'],
    // Windows 下编辑器/AI 的原子写会留下 *~RF*.TMP / .xxx.tmpdir 临时目录，
    // 原生文件监听（chokidar）一旦 watch 到这类被锁文件就直接 EBUSY 崩掉 dev server。
    // 处理：忽略临时文件 + 改用轮询（不创建原生 watcher，从根上避开锁）。
    watch: {
      ignored: (p: string) =>
        p.includes('node_modules') ||
        p.includes('.git') ||
        p.includes('dist') ||
        /(^|[\\/])\.?[^\\/]*(\.tmpdir|~RF[^\\/]*\.TMP|\.tmp$|~$)/.test(p),
      usePolling: true,
      interval: 400,
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 开发走 dev server 即同源，移除浏览器 Origin 头避免后端 CORS 校验
        // （否则手机通过局域网 IP 访问时，Origin 不在后端允许列表导致 403）
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.removeHeader('origin')
          })
        },
      },
    },
  },
  test: {
    // Vitest 配置：jsdom 模拟浏览器环境
    environment: 'jsdom',
    globals: true,
    include: ['tests/**/*.test.ts'],
    // 模拟 localStorage 等浏览器 API
    setupFiles: ['./tests/setup.ts'],
    // 使用 Vite 的路径别名
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
  },
})
