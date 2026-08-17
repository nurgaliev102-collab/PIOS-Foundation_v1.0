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
  // `piosapp.ru` added for the named Cloudflare Tunnel
  // (docs/PIOS_NAMED_TUNNEL_DEPLOYMENT.md) -- the quick-tunnel suffix above
  // is left in place rather than removed, since it is still a valid,
  // narrowly-scoped host and removing it is outside this change's own scope.
  // `home-pc.tail385153.ts.net` added for Tailscale Funnel (this machine's
  // own MagicDNS name, the only hostname Funnel's built-in HTTPS cert
  // actually covers -- Funnel cannot serve an arbitrary custom domain).
  preview: {
    allowedHosts: ['.trycloudflare.com', 'piosapp.ru', 'home-pc.tail385153.ts.net'],
    // Pilot Infrastructure Decision (docs/PIOS_PILOT_INFRASTRUCTURE_DECISION.md):
    // route the five pilot backend services through this single preview
    // origin so one Cloudflare Tunnel can reach all of them. Paths are
    // forwarded unchanged (no rewrite) -- the backends already serve these
    // exact paths. `network-management`'s own `/v1/connections` (port 8085)
    // is intentionally not routed here; it is excluded from the pilot flow
    // (ADR-037), so there is no collision with passenger-experience's
    // `/v1/connections` below.
    //
    // The five `/v1/health/<module-name>` rows exist because all five
    // modules otherwise serve an identical `/v1/health` -- one path could
    // not be routed to five different ports through this single origin.
    // PILOT_INFRASTRUCTURE_ROUTING_DECISION.md Variant A (Product Owner
    // approved 2026-08-03): each module names itself in its own health
    // path, no rewrite, same property as every other row here.
    //
    // `/v1/advisor` (ADR-056: AI Advisor for Owner Control Center, Accepted
    // 2026-08-17, Open Question 2's own resolution): `ai-advisor` is not
    // asked to restart anything, including this very preview process, so
    // ADR-048 Decision 1's argument against routing *control* through
    // `vite preview` does not apply here -- unlike `platform-ops`, this is
    // a plain, unprivileged proxy row, forwarded unchanged like every row
    // above it.
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
