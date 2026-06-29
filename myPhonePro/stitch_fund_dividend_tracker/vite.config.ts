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
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
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
