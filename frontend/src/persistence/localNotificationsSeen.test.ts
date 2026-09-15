import { beforeEach, describe, expect, it } from 'vitest'
import { isNotificationSeen, markAllNotificationsSeen, markNotificationSeen } from './localNotificationsSeen'

describe('localNotificationsSeen', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('reports a fact as not seen by default', () => {
    expect(isNotificationSeen('proposal-1:OPEN')).toBe(false)
  })

  it('reports a fact as seen after marking it', () => {
    markNotificationSeen('proposal-1:OPEN')

    expect(isNotificationSeen('proposal-1:OPEN')).toBe(true)
  })

  it('marking one fact seen does not mark a different fact seen', () => {
    markNotificationSeen('proposal-1:OPEN')

    expect(isNotificationSeen('proposal-2:OPEN')).toBe(false)
  })

  it('uses its own key, distinct from onboarding/install persistence', () => {
    markNotificationSeen('proposal-1:OPEN')

    expect(localStorage.getItem('pios.notifications.seen')).toBe('["proposal-1:OPEN"]')
    expect(localStorage.getItem('pios.install.help-seen')).toBeNull()
    expect(localStorage.getItem('pios.onboarding.driver-seen')).toBeNull()
  })

  it('markAllNotificationsSeen marks every id at once', () => {
    markAllNotificationsSeen(['proposal-1:OPEN', 'assignment-1:ARRIVED', 'message-1'])

    expect(isNotificationSeen('proposal-1:OPEN')).toBe(true)
    expect(isNotificationSeen('assignment-1:ARRIVED')).toBe(true)
    expect(isNotificationSeen('message-1')).toBe(true)
  })

  it('marking the same id twice does not duplicate it in storage', () => {
    markNotificationSeen('proposal-1:OPEN')
    markNotificationSeen('proposal-1:OPEN')

    const stored: unknown = JSON.parse(localStorage.getItem('pios.notifications.seen') ?? '[]')
    expect(stored).toEqual(['proposal-1:OPEN'])
  })

  it('bounds the stored list so a long ride history cannot grow it without limit', () => {
    for (let i = 0; i < 505; i++) {
      markNotificationSeen(`proposal-${i}:OPEN`)
    }

    const stored: unknown = JSON.parse(localStorage.getItem('pios.notifications.seen') ?? '[]')
    expect(Array.isArray(stored)).toBe(true)
    expect((stored as string[]).length).toBe(500)
    // The oldest ids fell off the front (FIFO); the most recent one is kept.
    expect(isNotificationSeen('proposal-0:OPEN')).toBe(false)
    expect(isNotificationSeen('proposal-504:OPEN')).toBe(true)
  })

  it('returns false, not throws, when localStorage holds malformed JSON for this key', () => {
    localStorage.setItem('pios.notifications.seen', 'not-json')

    expect(isNotificationSeen('proposal-1:OPEN')).toBe(false)
  })
})
