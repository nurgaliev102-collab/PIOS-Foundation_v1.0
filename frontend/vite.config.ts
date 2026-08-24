import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  preview: {
    allowedHosts: ['.trycloudflare.com', 'piosapp.ru', 'home-pc.tail385153.ts.net'],
    proxy: {
      '/v1/drivers': 'http://localhost:8081',
      '/v1/connections': 'http://localhost:8082',
      '/v1/orders': 'http://localhost:8083',
      '/v1/proposals': 'http://localhost:8084',
      '/v1/assignments': 'http://localhost:8084',
      '/v1/identities': 'http://localhost:8086',
      '/v1/advisor': 'http://localhost:8091',
      '/v1/health/driver-management': 'http://localhost:8081',
      '/v1/health/passenger-experience': 'http://localhost:8082',
      '/v1/health/order-management': 'http://localhost:8083',
      '/v1/health/dispatch': 'http://localhost:8084',
      '/v1/health/identity': 'http://localhost:8086',
    },
  },
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.ts',
      manifest: {
        name: 'PIOS',
        short_name: 'PIOS',
        description: 'PIOS — Next Generation Taxi Platform',
        theme_color: '#111827',
        background_color: '#ffffff',
        display: 'standalone',
        start_url: '/',
        icons: [
          { src: 'pios-icon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any' },
          { src: 'pios-icon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'maskable' },
        ],
      },
      injectManifest: {
        cleanupOutdatedCaches: true,
      },
    }),
  ],
})
