import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * `installPrompt.ts` registers its `beforeinstallprompt`/`appinstalled`
 * listeners once, at module load, and keeps `deferredPrompt` as private
 * module-level state (by design — see the module's own KDoc). Each test
 * below re-imports the module fresh via `vi.resetModules()` so one test's
 * captured prompt never leaks into the next.
 */

function makeBeforeInstallPromptEvent(outcome: 'accepted' | 'dismissed' = 'accepted') {
  const event = new Event('beforeinstallprompt', { cancelable: true }) as Event & {
    prompt: () => Promise<void>
    userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
  }
  event.prompt = vi.fn().mockResolvedValue(undefined)
  event.userChoice = Promise.resolve({ outcome })
  return event
}

beforeEach(() => {
  vi.resetModules()
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('installPrompt', () => {
  it('has no native install available before any beforeinstallprompt fires', async () => {
    const { isNativeInstallAvailable } = await import('./installPrompt')
    expect(isNativeInstallAvailable()).toBe(false)
  })

  it('captures a real beforeinstallprompt event and calls preventDefault on it', async () => {
    const { isNativeInstallAvailable } = await import('./installPrompt')
    const event = makeBeforeInstallPromptEvent()
    const preventDefaultSpy = vi.spyOn(event, 'preventDefault')

    window.dispatchEvent(event)

    expect(preventDefaultSpy).toHaveBeenCalled()
    expect(isNativeInstallAvailable()).toBe(true)
  })

  it('triggerNativeInstall shows the real captured prompt and returns the real outcome', async () => {
    const { isNativeInstallAvailable, triggerNativeInstall } = await import('./installPrompt')
    const event = makeBeforeInstallPromptEvent('accepted')
    window.dispatchEvent(event)

    const outcome = await triggerNativeInstall()

    expect(event.prompt).toHaveBeenCalledTimes(1)
    expect(outcome).toBe('accepted')
    // A shown prompt is consumed -- Chromium's own one-shot contract.
    expect(isNativeInstallAvailable()).toBe(false)
  })

  it('returns "unavailable" rather than throwing when no prompt was ever captured', async () => {
    const { triggerNativeInstall } = await import('./installPrompt')
    const outcome = await triggerNativeInstall()
    expect(outcome).toBe('unavailable')
  })

  it('onInstalled fires only on the real appinstalled event, and unsubscribes cleanly', async () => {
    const { onInstalled } = await import('./installPrompt')
    const callback = vi.fn()
    const unsubscribe = onInstalled(callback)

    window.dispatchEvent(new Event('appinstalled'))
    expect(callback).toHaveBeenCalledTimes(1)

    unsubscribe()
    window.dispatchEvent(new Event('appinstalled'))
    expect(callback).toHaveBeenCalledTimes(1)
  })

  it('appinstalled clears a captured prompt, so a stale one is never reused', async () => {
    const { isNativeInstallAvailable } = await import('./installPrompt')
    window.dispatchEvent(makeBeforeInstallPromptEvent())
    expect(isNativeInstallAvailable()).toBe(true)

    window.dispatchEvent(new Event('appinstalled'))

    expect(isNativeInstallAvailable()).toBe(false)
  })
})

describe('shareInstallLink', () => {
  afterEach(() => {
    // @ts-expect-error -- test-only cleanup of a property this suite defines below.
    delete navigator.share
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
      configurable: true,
    })
  })

  it('uses the real Web Share API when available', async () => {
    const { shareInstallLink } = await import('./installPrompt')
    const share = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'share', { value: share, configurable: true })

    const result = await shareInstallLink('https://pios.example/help/install')

    expect(share).toHaveBeenCalledWith(
      expect.objectContaining({ url: 'https://pios.example/help/install' })
    )
    expect(result).toBe('shared')
  })

  it('falls back to the clipboard when Web Share is unavailable', async () => {
    // @ts-expect-error -- simulating a browser without the Web Share API.
    delete navigator.share
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })

    const { shareInstallLink } = await import('./installPrompt')
    const result = await shareInstallLink('https://pios.example/help/install')

    expect(writeText).toHaveBeenCalledWith('https://pios.example/help/install')
    expect(result).toBe('copied')
  })
})
