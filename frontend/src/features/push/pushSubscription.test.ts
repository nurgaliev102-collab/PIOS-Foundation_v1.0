import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * `pushSubscription.ts` wraps three real browser mechanisms
 * (`PushManager.subscribe`, `Notification.requestPermission`,
 * `navigator.serviceWorker.ready`) and Dispatch's own
 * `/v1/driver-push-subscriptions` endpoints. Each is mocked at the
 * narrowest possible boundary -- `../../api/apiClient`'s own `request` for
 * the network calls, real (but stubbed) globals for the browser APIs --
 * mirrors `features/install/installPrompt.test.ts`'s own
 * `vi.resetModules()`-per-test discipline so no test's stub leaks into the
 * next.
 */

const requestMock = vi.fn()

vi.mock('../../api/apiClient', () => ({
  request: (...args: unknown[]) => requestMock(...args),
  resolveBackendBaseUrl: (_envValue: string | undefined, localDefault: string) => localDefault,
}))

function definePushSupport(navigatorHasServiceWorker: boolean) {
  if (navigatorHasServiceWorker) {
    Object.defineProperty(navigator, 'serviceWorker', {
      value: {
        ready: Promise.resolve({
          pushManager: {
            subscribe: vi.fn(),
            getSubscription: vi.fn(),
          },
        }),
      },
      configurable: true,
    })
  } else {
    delete (navigator as unknown as Record<string, unknown>).serviceWorker
  }
  Object.defineProperty(window, 'PushManager', { value: function PushManager() {}, configurable: true })
  Object.defineProperty(window, 'Notification', {
    value: Object.assign(function Notification() {}, { requestPermission: vi.fn() }),
    configurable: true,
  })
}

beforeEach(() => {
  vi.resetModules()
  requestMock.mockReset()
})

afterEach(() => {
  vi.restoreAllMocks()
  delete (navigator as unknown as Record<string, unknown>).serviceWorker
  delete (window as unknown as Record<string, unknown>).PushManager
  delete (window as unknown as Record<string, unknown>).Notification
})

describe('fetchPushPublicKey', () => {
  it('returns the real public key on a successful response', async () => {
    requestMock.mockResolvedValue({ publicKey: 'abc123' })
    const { fetchPushPublicKey } = await import('./pushSubscription')

    const result = await fetchPushPublicKey('token-1')

    expect(result).toBe('abc123')
    expect(requestMock).toHaveBeenCalledWith(
      '/v1/driver-push-subscriptions/public-key',
      expect.objectContaining({ headers: { Authorization: 'Bearer token-1' } })
    )
  })

  it('returns null rather than throwing on a 404 (VAPID not configured, fail-closed)', async () => {
    requestMock.mockRejectedValue(new Error('404'))
    const { fetchPushPublicKey } = await import('./pushSubscription')

    const result = await fetchPushPublicKey('token-1')

    expect(result).toBeNull()
  })
})

describe('enablePush', () => {
  it('resolves false immediately when the browser lacks push support', async () => {
    definePushSupport(false)
    const { enablePush } = await import('./pushSubscription')

    const result = await enablePush('token-1')

    expect(result).toBe(false)
    expect(requestMock).not.toHaveBeenCalled()
  })

  it('resolves false without prompting for permission when no VAPID key is configured', async () => {
    definePushSupport(true)
    requestMock.mockRejectedValue(new Error('404'))
    const { enablePush } = await import('./pushSubscription')

    const result = await enablePush('token-1')

    expect(result).toBe(false)
    expect((window.Notification as unknown as { requestPermission: ReturnType<typeof vi.fn> }).requestPermission).not.toHaveBeenCalled()
  })

  it('resolves false when the driver denies the permission prompt', async () => {
    definePushSupport(true)
    requestMock.mockResolvedValue({ publicKey: 'YUJj' })
    ;(window.Notification as unknown as { requestPermission: ReturnType<typeof vi.fn> }).requestPermission.mockResolvedValue('denied')
    const { enablePush } = await import('./pushSubscription')

    const result = await enablePush('token-1')

    expect(result).toBe(false)
    // Only the public-key GET happened -- never a POST registering a subscription.
    expect(requestMock).toHaveBeenCalledTimes(1)
  })

  it('subscribes and registers with Dispatch on a fully completed flow', async () => {
    definePushSupport(true)
    requestMock.mockImplementation((path: string) => {
      if (path.endsWith('/public-key')) {
        return Promise.resolve({ publicKey: 'YUJj' })
      }
      return Promise.resolve(undefined)
    })
    ;(window.Notification as unknown as { requestPermission: ReturnType<typeof vi.fn> }).requestPermission.mockResolvedValue('granted')
    const subscription = {
      toJSON: () => ({ endpoint: 'https://push.example/ep', keys: { p256dh: 'p', auth: 'a' } }),
    }
    const registration = {
      pushManager: { subscribe: vi.fn().mockResolvedValue(subscription), getSubscription: vi.fn() },
    }
    Object.defineProperty(navigator, 'serviceWorker', { value: { ready: Promise.resolve(registration) }, configurable: true })

    const { enablePush } = await import('./pushSubscription')
    const result = await enablePush('token-1')

    expect(result).toBe(true)
    expect(requestMock).toHaveBeenCalledWith(
      '/v1/driver-push-subscriptions',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ endpoint: 'https://push.example/ep', keys: { p256dh: 'p', auth: 'a' } }),
      })
    )
  })

  it('resolves false rather than throwing when the browser subscribe call rejects', async () => {
    definePushSupport(true)
    requestMock.mockResolvedValue({ publicKey: 'YUJj' })
    ;(window.Notification as unknown as { requestPermission: ReturnType<typeof vi.fn> }).requestPermission.mockResolvedValue('granted')
    const registration = {
      pushManager: { subscribe: vi.fn().mockRejectedValue(new Error('boom')), getSubscription: vi.fn() },
    }
    Object.defineProperty(navigator, 'serviceWorker', { value: { ready: Promise.resolve(registration) }, configurable: true })

    const { enablePush } = await import('./pushSubscription')
    const result = await enablePush('token-1')

    expect(result).toBe(false)
  })
})

describe('disablePush', () => {
  it('is a no-op when the browser lacks push support', async () => {
    definePushSupport(false)
    const { disablePush } = await import('./pushSubscription')

    await disablePush('token-1')

    expect(requestMock).not.toHaveBeenCalled()
  })

  it('is a no-op when there is no active subscription', async () => {
    definePushSupport(true)
    const registration = { pushManager: { subscribe: vi.fn(), getSubscription: vi.fn().mockResolvedValue(null) } }
    Object.defineProperty(navigator, 'serviceWorker', { value: { ready: Promise.resolve(registration) }, configurable: true })
    const { disablePush } = await import('./pushSubscription')

    await disablePush('token-1')

    expect(requestMock).not.toHaveBeenCalled()
  })

  it('deletes the Dispatch-side subscription then unsubscribes the browser', async () => {
    definePushSupport(true)
    requestMock.mockResolvedValue(undefined)
    const unsubscribe = vi.fn().mockResolvedValue(true)
    const subscription = { endpoint: 'https://push.example/ep', unsubscribe }
    const registration = { pushManager: { subscribe: vi.fn(), getSubscription: vi.fn().mockResolvedValue(subscription) } }
    Object.defineProperty(navigator, 'serviceWorker', { value: { ready: Promise.resolve(registration) }, configurable: true })

    const { disablePush } = await import('./pushSubscription')
    await disablePush('token-1')

    expect(requestMock).toHaveBeenCalledWith(
      `/v1/driver-push-subscriptions?endpoint=${encodeURIComponent('https://push.example/ep')}`,
      expect.objectContaining({ method: 'DELETE' })
    )
    expect(unsubscribe).toHaveBeenCalledTimes(1)
  })

  it('still unsubscribes the browser even when the DELETE call fails (logout must complete)', async () => {
    definePushSupport(true)
    requestMock.mockRejectedValue(new Error('network error'))
    const unsubscribe = vi.fn().mockResolvedValue(true)
    const subscription = { endpoint: 'https://push.example/ep', unsubscribe }
    const registration = { pushManager: { subscribe: vi.fn(), getSubscription: vi.fn().mockResolvedValue(subscription) } }
    Object.defineProperty(navigator, 'serviceWorker', { value: { ready: Promise.resolve(registration) }, configurable: true })

    const { disablePush } = await import('./pushSubscription')
    await disablePush('token-1')

    expect(unsubscribe).toHaveBeenCalledTimes(1)
  })
})
