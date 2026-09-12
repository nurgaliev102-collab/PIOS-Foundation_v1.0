/**
 * API abstraction layer — Sprint 0: Frontend Foundation.
 *
 * This module exists so future features import a single, stable entry
 * point for reaching the backend, rather than calling `fetch` directly
 * throughout the codebase.
 *
 * The base URL is read from Vite's own env mechanism so each deployment
 * (local, pilot) can point at a different backend without a code change.
 * When unset, it defaults to `http://localhost:8081` — Driver
 * Management's own local port (INTERFACE_CONTRACTS.md) — since that is,
 * as of Sprint 5, the only backend module this frontend actually calls.
 * This default is a local-development convenience, not an assumption
 * about any other module's integration; it is overridden entirely by
 * `VITE_API_BASE_URL` once more than one backend module is involved.
 */

/**
 * The single origin-resolution rule every per-module backend base URL in
 * this frontend must follow. First established for Identity alone
 * (`BackendIdentityProvider.ts`'s own `IDENTITY_BASE_URL`, ADR-038/ADR-055);
 * extracted here once a product audit (2026-09-11) found the identical
 * unconditional-`http://localhost:PORT` fallback duplicated, unfixed, in
 * every *other* module's own base URL constant (`RideRequest.tsx`,
 * `DriverHome.tsx`, `PassengerLanding.tsx`, and this file's own
 * [API_BASE_URL]) — a real product gap, not a style issue: a genuine
 * remote passenger or driver's own device has nothing listening on
 * `localhost:808X`; only a browser running on the backend's own host ever
 * reached the real API by coincidence.
 *
 * In a real browser: always same-origin (`''`). `server/serve.mjs`'s own
 * `BACKEND_ROUTES` (`server/backendRoutes.mjs`) already reverse-proxies
 * every path this frontend calls to the correct backend module server-side
 * — exactly the mechanism a real remote device needs, since its own
 * `localhost` is itself, never the pilot host. `vite preview`'s own
 * `preview.proxy` (`vite.config.ts`) shares the identical route table, so
 * this also works unchanged under `npm run preview`.
 *
 * Outside a browser (tests, SSR-style tooling): [envValue] if set, else
 * [localDefault] — `npm run dev` (vite's dev server) has no equivalent
 * proxy configured, so it also falls through to this branch's own
 * `import.meta.env` / literal-default behavior, unchanged from before this
 * function existed (matches [IDENTITY_BASE_URL]'s own pre-existing,
 * already-shipped behavior exactly — this is that same logic, only now
 * shared instead of duplicated).
 */
export function resolveBackendBaseUrl(envValue: string | undefined, localDefault: string): string {
  if (typeof window !== 'undefined' && typeof window.location !== 'undefined') {
    return ''
  }
  return envValue ?? localDefault
}

const API_BASE_URL = resolveBackendBaseUrl(import.meta.env.VITE_API_BASE_URL, 'http://localhost:8081')

export interface ApiClientConfig {
  baseUrl: string
}

export const apiClientConfig: ApiClientConfig = {
  baseUrl: API_BASE_URL,
}

/**
 * Thrown when a backend responds with a non-2xx status, carrying that
 * [status] so callers can distinguish, for example, "not found" (404)
 * from any other failure without parsing response bodies themselves.
 */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, path: string) {
    super(`Request to ${path} failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
  }
}

/**
 * P1 UX audit (2026-09-12): the one fact every screen making an
 * authenticated call must be able to tell apart from a generic network
 * failure or backend error -- a 401 from an endpoint that was sent a
 * Bearer token means that specific credential is no longer valid
 * (expired, or a session secret rotated server-side), not "try again in
 * a moment": retrying the exact same request with the same stale token
 * will never succeed on its own, unlike a transient network blip or a
 * momentary 5xx.
 *
 * Deliberately does NOT fire for a 401 from a credential *check* itself
 * (login/register with a wrong password) -- that call sends no existing
 * session token to begin with, so its own 401 means "wrong credentials",
 * a completely different fact this function must never relabel. Every
 * call site below only applies this to a request that already carried
 * `Authorization: Bearer <token>` for an already-established session.
 */
export function isSessionExpiredError(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401
}

/** The one message every screen shows for [isSessionExpiredError] — see that function's own KDoc. */
export const SESSION_EXPIRED_MESSAGE = 'Сессия истекла. Войдите снова.'

/**
 * Sprint 5 — First Backend Integration: the single function every real
 * backend call in this project goes through. Issues a JSON `fetch`
 * against a base URL + [path], parses the response as `T` on success,
 * and throws [ApiError] on any non-2xx status — it does not interpret
 * what a given status means for a given resource (e.g. whether 404 is a
 * normal "not found" or an error); that judgment belongs to the caller,
 * which knows the resource it asked for.
 *
 * Sprint FR-001 (Connect RideRequest) added the optional [baseUrl]
 * override on [init]: this project now genuinely calls more than one
 * backend module (Driver Management, Order Management), each on its own
 * local port, and [apiClientConfig.baseUrl] can only hold one default —
 * exactly the "once more than one backend module is involved" case this
 * file's own config already anticipated. Omitting it keeps every
 * existing caller (e.g. Driver Home's `GET /v1/drivers/...`) unchanged,
 * defaulting to [apiClientConfig.baseUrl] as before.
 *
 * Sprint "My Business + Circle of Trust" (ADR-054): `DELETE
 * /v1/connections/{id}` is this project's first endpoint to ever answer
 * 204 No Content — every caller before it returned a JSON body on success.
 * `Response.json()` throws on an empty body, so a 204 is returned as
 * `undefined` without attempting to parse one; every existing caller is
 * unaffected, since none of them receives 204 today.
 *
 * 2026-08-17: the non-2xx branch drains the response body before throwing,
 * same reasoning as `healthPoll.ts`'s own fix for the same issue there — an
 * unread body is what makes Chrome's Network panel label a request
 * "(canceled)"/`net::ERR_ABORTED` even though it resolved with a real
 * status and was handled correctly, which cost real time to rule out
 * during that incident's own diagnosis. Every caller of [request] (every
 * screen that hits a real, expected error status — a 404, a 403 from an
 * IDOR check, a stale owner credential) shares this same cosmetic
 * confusion; draining here fixes it in one place rather than at each call
 * site. [ApiError] is still thrown with the same [status]/[path] on every
 * caller, unchanged.
 */
export async function request<T>(path: string, init?: RequestInit & { baseUrl?: string }): Promise<T> {
  const { baseUrl, ...fetchInit } = init ?? {}
  const response = await fetch(`${baseUrl ?? apiClientConfig.baseUrl}${path}`, {
    ...fetchInit,
    headers: {
      Accept: 'application/json',
      ...fetchInit.headers,
    },
  })
  if (!response.ok) {
    await response.text().catch(() => undefined)
    throw new ApiError(response.status, path)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}
