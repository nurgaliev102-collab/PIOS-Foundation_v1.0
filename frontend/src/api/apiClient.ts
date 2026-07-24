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
    throw new ApiError(response.status, path)
  }
  return (await response.json()) as T
}
