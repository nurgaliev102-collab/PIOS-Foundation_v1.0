import { afterEach, describe, expect, it } from 'vitest'
import { pushSupport } from './pushSupport'

/**
 * `pushSupport()` must never throw and must reflect exactly the three
 * browser APIs `pushSubscription.ts` actually needs -- mirrors
 * `features/install/deviceDetection.test.ts`'s own approach of
 * defining/removing real globals rather than mocking the module itself.
 */

const originalServiceWorker = Object.getOwnPropertyDescriptor(navigator, 'serviceWorker')
const originalPushManager = Object.getOwnPropertyDescriptor(window, 'PushManager')
const originalNotification = Object.getOwnPropertyDescriptor(window, 'Notification')

function restore(descriptor: PropertyDescriptor | undefined, target: object, key: string) {
  if (descriptor) {
    Object.defineProperty(target, key, descriptor)
  } else {
    delete (target as Record<string, unknown>)[key]
  }
}

afterEach(() => {
  restore(originalServiceWorker, navigator, 'serviceWorker')
  restore(originalPushManager, window, 'PushManager')
  restore(originalNotification, window, 'Notification')
})

describe('pushSupport', () => {
  it('is true when serviceWorker, PushManager and Notification are all present', () => {
    Object.defineProperty(navigator, 'serviceWorker', { value: {}, configurable: true })
    Object.defineProperty(window, 'PushManager', { value: function PushManager() {}, configurable: true })
    Object.defineProperty(window, 'Notification', { value: function Notification() {}, configurable: true })

    expect(pushSupport()).toBe(true)
  })

  it('is false when serviceWorker is missing', () => {
    delete (navigator as unknown as Record<string, unknown>).serviceWorker
    Object.defineProperty(window, 'PushManager', { value: function PushManager() {}, configurable: true })
    Object.defineProperty(window, 'Notification', { value: function Notification() {}, configurable: true })

    expect(pushSupport()).toBe(false)
  })

  it('is false when PushManager is missing (e.g. Safari)', () => {
    Object.defineProperty(navigator, 'serviceWorker', { value: {}, configurable: true })
    delete (window as unknown as Record<string, unknown>).PushManager
    Object.defineProperty(window, 'Notification', { value: function Notification() {}, configurable: true })

    expect(pushSupport()).toBe(false)
  })

  it('is false when Notification is missing', () => {
    Object.defineProperty(navigator, 'serviceWorker', { value: {}, configurable: true })
    Object.defineProperty(window, 'PushManager', { value: function PushManager() {}, configurable: true })
    delete (window as unknown as Record<string, unknown>).Notification

    expect(pushSupport()).toBe(false)
  })
})
