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
    // Pilot Infrastructure Decision (docs/PIOS_PILOT_INFRASTRUCTURE_DECISION.md):
    // route the five pilot backend services through this single preview
    // origin so one Cloudflare Tunnel can reach all of them. Paths are
    // forwarded unchanged (no rewrite) -- the backends already serve these
    // exact paths. `network-management`'s own `/v1/connections` (port 8085)
    // is intentionally not routed here; it is excluded from the pilot flow
    // (ADR-037), so there is no collision with passenger-experience's
    // `/v1/connections` below.
    proxy: {
      '/v1/drivers': 'http://localhost:8081',
      '/v1/connections': 'http://localhost:8082',
      '/v1/orders': 'http://localhost:8083',
      '/v1/proposals': 'http://localhost:8084',
      '/v1/assignments': 'http://localhost:8084',
      '/v1/identities': 'http://localhost:8086',
    },
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
