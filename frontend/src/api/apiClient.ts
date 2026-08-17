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

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081'

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
