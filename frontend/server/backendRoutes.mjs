/**
 * Single source of truth for "which backend module serves which API path
 * prefix in production" — PIOS_PILOT_INFRASTRUCTURE_DECISION.md's own
 * routing table. Previously this lived only inline inside `vite.config.ts`'s
 * `preview.proxy` (used by `vite preview`); `server/serve.mjs` (the Growth
 * Loops TZ v1, Phase 1 replacement for `vite preview` in production) needs
 * the identical map to reverse-proxy the same paths itself, so it is
 * extracted here and imported by both rather than duplicated — duplicating
 * it would let the two silently drift apart the next time a module's port
 * changes.
 */
export const BACKEND_ROUTES = {
  '/v1/drivers': 'http://localhost:8081',
  '/v1/connections': 'http://localhost:8082',
  '/v1/orders': 'http://localhost:8083',
  '/v1/proposals': 'http://localhost:8084',
  '/v1/assignments': 'http://localhost:8084',
  // ADR-083 (D-10, Driver Web Push): Dispatch's own new
  // /v1/driver-push-subscriptions endpoints, same port as every other
  // Dispatch-owned prefix above.
  '/v1/driver-push-subscriptions': 'http://localhost:8084',
  '/v1/identities': 'http://localhost:8086',
  '/v1/advisor': 'http://localhost:8091',
  '/v1/health/driver-management': 'http://localhost:8081',
  '/v1/health/passenger-experience': 'http://localhost:8082',
  '/v1/health/order-management': 'http://localhost:8083',
  '/v1/health/dispatch': 'http://localhost:8084',
  '/v1/health/identity': 'http://localhost:8086',
}

/**
 * Longest-prefix match against [BACKEND_ROUTES] — required because
 * `/v1/health/driver-management` must win over a hypothetical shorter
 * `/v1/health` entry, and because a path like
 * `/v1/proposals/abc-123/confirm-price` must still match the `/v1/proposals`
 * prefix even though it isn't an exact key.
 */
export function resolveBackendTarget(pathname) {
  let bestMatch = null
  for (const prefix of Object.keys(BACKEND_ROUTES)) {
    if (pathname === prefix || pathname.startsWith(`${prefix}/`) || pathname.startsWith(`${prefix}?`)) {
      if (!bestMatch || prefix.length > bestMatch.length) {
        bestMatch = prefix
      }
    }
  }
  return bestMatch ? BACKEND_ROUTES[bestMatch] : null
}
