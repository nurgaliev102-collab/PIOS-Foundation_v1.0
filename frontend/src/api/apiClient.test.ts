import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, request } from './apiClient'

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
})
