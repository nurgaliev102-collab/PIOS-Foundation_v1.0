import { beforeEach, describe, expect, it } from 'vitest'
import { hasSeenInstallHelp, markInstallHelpSeen } from './localInstallSeen'
import { hasSeenDriverOnboarding, markDriverOnboardingSeen } from './localOnboardingSeen'

describe('localInstallSeen', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('reports install help as not seen by default', () => {
    expect(hasSeenInstallHelp()).toBe(false)
  })

  it('reports install help as seen after marking it', () => {
    markInstallHelpSeen()

    expect(hasSeenInstallHelp()).toBe(true)
  })

  it('uses its own key, distinct from onboarding persistence (Section 14)', () => {
    markInstallHelpSeen()

    expect(hasSeenDriverOnboarding()).toBe(false)
    expect(localStorage.getItem('pios.install.help-seen')).toBe('true')
    expect(localStorage.getItem('pios.onboarding.driver-seen')).toBeNull()
  })

  it('marking onboarding seen does not mark install help seen', () => {
    markDriverOnboardingSeen()

    expect(hasSeenInstallHelp()).toBe(false)
  })
})
