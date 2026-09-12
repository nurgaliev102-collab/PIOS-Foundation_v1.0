import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, isSessionExpiredError, request, resolveBackendBaseUrl } from './apiClient'

// Sprint "My Business + Circle of Trust" (ADR-054): `DELETE
// /v1/connections/{id}` is this project's first 204 No Content response.
// `request()` used to call `Response.json()` unconditionally, which throws
// on an empty body -- a real bug only surfaced once a 204-returning
// endpoint actually existed. These tests cover the fix directly against
// `fetch`, not through a mocked `request()` (unlike every screen's own
// tests), since this is exactly the boundary the bug lived at.
describe('request', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('parses a normal JSON body on success, unchanged', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(JSON.stringify({ hello: 'world' }), { status: 200 }))
    )

    const result = await request<{ hello: string }>('/v1/example')

    expect(result).toEqual({ hello: 'world' })
  })

  it('returns undefined for a 204 No Content response, instead of throwing on an empty body', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))

    const result = await request<void>('/v1/connections/some-id', { method: 'DELETE' })

    expect(result).toBeUndefined()
  })

  it('still throws ApiError on a non-2xx status, unchanged', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 404 })))

    await expect(request('/v1/example')).rejects.toBeInstanceOf(ApiError)
  })

  // 2026-08-17: an unread body on a non-2xx response is what makes Chrome's
  // Network panel label an already-handled, already-resolved request
  // "(canceled)" -- indistinguishable at a glance from a genuine failure.
  // Draining it here (mirroring healthPoll.ts's own fix) removes that
  // cosmetic confusion for every caller of `request`.
  it('drains the body on a non-2xx status before throwing', async () => {
    const textSpy = vi.fn().mockResolvedValue('{"error":"not found"}')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 404, text: textSpy } as unknown as Response))

    await expect(request('/v1/example')).rejects.toBeInstanceOf(ApiError)
    expect(textSpy).toHaveBeenCalledTimes(1)
  })
})

// Product audit (2026-09-11): a real remote passenger/driver's own device
// has nothing listening on `localhost:PORT` -- every per-module base URL
// in this frontend (RideRequest.tsx, DriverHome.tsx, PassengerLanding.tsx,
// this file's own API_BASE_URL) used to fall back to an absolute
// `http://localhost:PORT`, unconditionally, which only ever worked by
// coincidence for a browser physically running on the backend's own host.
// This is the one place that resolution rule is decided; every call site
// above just supplies its own env var name and its own local-only default.
describe('resolveBackendBaseUrl', () => {
  it('resolves to same-origin ("") in a real browser, regardless of the env value or default supplied', () => {
    // vitest's own jsdom environment provides a real `window`/`window.location`
    // by default -- this test runs in exactly the context a real deployed
    // page does, no stubbing needed for the "is a browser" branch itself.
    expect(resolveBackendBaseUrl('https://example.com', 'http://localhost:9999')).toBe('')
    expect(resolveBackendBaseUrl(undefined, 'http://localhost:9999')).toBe('')
  })

  it('falls back to the env value, then the local default, outside a browser (e.g. non-DOM tooling)', () => {
    const originalWindow = globalThis.window
    // @ts-expect-error -- deliberately simulating a non-browser global scope for this one test.
    delete globalThis.window
    try {
      expect(resolveBackendBaseUrl('https://configured.example.com', 'http://localhost:9999')).toBe(
        'https://configured.example.com'
      )
      expect(resolveBackendBaseUrl(undefined, 'http://localhost:9999')).toBe('http://localhost:9999')
    } finally {
      globalThis.window = originalWindow
    }
  })
})

// P1 UX audit (2026-09-12): the one shared classifier every screen's own
// catch block now calls before falling back to its existing generic error
// handling -- see [isSessionExpiredError]'s own KDoc for why a 401 is only
// ever this for an *already-authenticated* call, never a login/register
// credential check.
describe('isSessionExpiredError', () => {
  it('is true for a 401 ApiError', () => {
    expect(isSessionExpiredError(new ApiError(401, '/v1/connections'))).toBe(true)
  })

  it('is false for any other ApiError status', () => {
    expect(isSessionExpiredError(new ApiError(404, '/v1/connections'))).toBe(false)
    expect(isSessionExpiredError(new ApiError(500, '/v1/connections'))).toBe(false)
    expect(isSessionExpiredError(new ApiError(403, '/v1/connections'))).toBe(false)
  })

  it('is false for a plain network failure (not an ApiError at all)', () => {
    expect(isSessionExpiredError(new TypeError('Failed to fetch'))).toBe(false)
  })

  it('is false for a non-error value', () => {
    expect(isSessionExpiredError(undefined)).toBe(false)
    expect(isSessionExpiredError(null)).toBe(false)
  })
})
