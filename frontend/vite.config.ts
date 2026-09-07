import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'
import { BACKEND_ROUTES } from './server/backendRoutes.mjs'

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
    // Growth Loops TZ v1, Phase 1: this map now lives in
    // `server/backendRoutes.mjs`, shared with `server/serve.mjs` (the
    // production replacement for `vite preview` that also needs to
    // reverse-proxy these exact same paths) — imported here rather than
    // inlined so the two can never silently drift apart.
    proxy: BACKEND_ROUTES,
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
        // Matches tokens.css's own --pios-color-accent / --pios-color-background
        // for the dark "Премиум графит" palette (2026-09-07) -- the splash
        // screen a PWA install shows before the app itself has painted.
        theme_color: '#d97a2f',
        background_color: '#1c1712',
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
