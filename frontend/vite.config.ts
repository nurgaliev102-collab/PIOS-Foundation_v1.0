import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  // Sprint 8.5 (Pilot Deployment Preparation): `vite preview` rejects
  // requests whose Host header it doesn't recognize (DNS-rebinding
  // protection). A Cloudflare Tunnel puts a `*.trycloudflare.com` host on
  // every request reaching this server from the public internet, so that
  // suffix is allowed here -- this is a deployment-only change, it does
  // not affect `vite dev` or the production build's own behavior.
  preview: {
    allowedHosts: ['.trycloudflare.com'],
  },
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
