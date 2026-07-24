import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      // Sprint 0: Frontend Foundation — an installable manifest only.
      // No offline caching strategy is customized beyond the plugin's own
      // default precache of the built app shell; a future task revisits
      // this once a real feature exists to cache meaningfully.
      manifest: {
        name: 'PIOS',
        short_name: 'PIOS',
        description: 'PIOS — Next Generation Taxi Platform',
        theme_color: '#111827',
        background_color: '#ffffff',
        display: 'standalone',
        start_url: '/',
        icons: [
          {
            src: 'pios-icon.svg',
            sizes: 'any',
            type: 'image/svg+xml',
            purpose: 'any',
          },
          {
            src: 'pios-icon.svg',
            sizes: 'any',
            type: 'image/svg+xml',
            purpose: 'maskable',
          },
        ],
      },
    }),
  ],
})
