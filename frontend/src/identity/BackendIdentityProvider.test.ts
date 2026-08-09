import { afterEach, describe, expect, it, vi } from 'vitest'
import { BackendIdentityProvider } from './BackendIdentityProvider'

// ADR-055 Decision 6 addendum: a token minted at registration carries
// `drv: null` forever unless `attachDriver` replaces it -- this is the
// regression a real browser E2E run first caught (GET
// /v1/connections?driverId= permanently 403 on the driver's own "Мой
// бизнес" screen). These tests prove the fix at the boundary where it
// actually lived: what this device stores after `attachDriver` resolves.
describe('BackendIdentityProvider.attachDriver', () => {
  const STORAGE_KEY = 'pios.identity'
  const OLD_TOKEN = 'old-registration-token'
  const NEW_TOKEN = 'new-post-attach-token'

  afterEach(() => {
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  function seedExistingSession() {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        identityId: 'identity-1',
        driverId: null,
        token: OLD_TOKEN,
        expiresAt: new Date(Date.now() + 60_000).toISOString(),
      })
    )
  }

  it('replaces the stored token with the fresh one the backend returns, not the old one', async () => {
    seedExistingSession()
    const newExpiresAt = new Date(Date.now() + 120_000).toISOString()
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({ identityId: 'identity-1', driverId: 'driver-1', token: NEW_TOKEN, expiresAt: newExpiresAt }),
          { status: 200 }
        )
      )
    )

    const provider = new BackendIdentityProvider()
    const result = await provider.attachDriver('driver-1')

    expect(result.token).toBe(NEW_TOKEN)
    expect(result.driverId).toBe('driver-1')
    expect(result.expiresAt).toBe(newExpiresAt)
  })

  it('persists the new token to localStorage, so the old one is no longer the current client-side token', async () => {
    seedExistingSession()
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            identityId: 'identity-1',
            driverId: 'driver-1',
            token: NEW_TOKEN,
            expiresAt: new Date(Date.now() + 120_000).toISOString(),
          }),
          { status: 200 }
        )
      )
    )

    const provider = new BackendIdentityProvider()
    await provider.attachDriver('driver-1')

    const stored = provider.getStoredIdentity()
    expect(stored?.token).toBe(NEW_TOKEN)
    expect(stored?.token).not.toBe(OLD_TOKEN)
  })

  it('sends the pre-attach token as the Authorization header on the request itself', async () => {
    seedExistingSession()
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          identityId: 'identity-1',
          driverId: 'driver-1',
          token: NEW_TOKEN,
          expiresAt: new Date(Date.now() + 120_000).toISOString(),
        }),
        { status: 200 }
      )
    )
    vi.stubGlobal('fetch', fetchMock)

    await new BackendIdentityProvider().attachDriver('driver-1')

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const headers = init.headers as Record<string, string>
    expect(headers.Authorization).toBe(`Bearer ${OLD_TOKEN}`)
  })

  it('throws without calling the backend when no session exists yet', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(new BackendIdentityProvider().attachDriver('driver-1')).rejects.toThrow()
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
