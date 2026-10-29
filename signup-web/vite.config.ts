import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // 开发期把 /api 代理到后端，浏览器看来是同源请求，省掉 CORS 的麻烦。
    // 生产环境由 Nginx 做同样的转发，前端代码里不需要出现后端域名。
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
