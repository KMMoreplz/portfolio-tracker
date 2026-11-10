import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Локальная разработка: /api уходит на portfolio-service, /market — на market-data-service.
// В Docker этим занимается nginx (см. nginx.conf.template), прокси прозрачны для кода.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/market': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        // /market/quotes/SBER → /api/quotes/SBER: у market-data все маршруты под /api
        rewrite: (path) => path.replace(/^\/market/, '/api'),
      },
    },
  },
})
