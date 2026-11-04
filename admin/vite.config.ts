import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 管理端（ADR 0048）：打包后放进 web 镜像的 /admin/，所以 base 是 /admin/。
// 营养字段表直接引用 App 那一份（@shared → frontend/src/utils），不另抄一份。
export default defineConfig({
  base: '/admin/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@shared': fileURLToPath(new URL('../frontend/src/utils', import.meta.url))
    }
  },
  server: {
    port: 5176,
    strictPort: true,
    // 允许读到 ../frontend/src/utils（字段表）
    fs: { allow: ['..'] },
    proxy: {
      // 与 App 的 dev 代理同一个后端端口（docs/operations/configuration.md）
      // 去掉 Origin 的原因同 frontend/vite.config.ts：后端配了 CORS 白名单时，带 dev 源的写请求会被判跨域 403
      '/api': {
        target: 'http://127.0.0.1:8083',
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'))
        }
      }
    }
  },
  build: {
    chunkSizeWarningLimit: 1500
  }
})
