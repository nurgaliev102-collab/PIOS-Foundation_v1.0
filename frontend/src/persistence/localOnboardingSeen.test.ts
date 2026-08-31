import { beforeEach, describe, expect, it } from 'vitest'
import {
  hasSeenDriverOnboarding,
  hasSeenPassengerOnboarding,
  markDriverOnboardingSeen,
  markPassengerOnboardingSeen,
} from './localOnboardingSeen'

describe('localOnboardingSeen', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('reports driver onboarding as not seen by default', () => {
    expect(hasSeenDriverOnboarding()).toBe(false)
  })

  it('reports driver onboarding as seen after marking it', () => {
    markDriverOnboardingSeen()

    expect(hasSeenDriverOnboarding()).toBe(true)
  })

  it('reports passenger onboarding as not seen by default', () => {
    expect(hasSeenPassengerOnboarding()).toBe(false)
  })

  it('reports passenger onboarding as seen after marking it', () => {
    markPassengerOnboardingSeen()

    expect(hasSeenPassengerOnboarding()).toBe(true)
  })

  it('marking driver onboarding seen does not affect passenger onboarding', () => {
    markDriverOnboardingSeen()

    expect(hasSeenPassengerOnboarding()).toBe(false)
  })

  it('marking passenger onboarding seen does not affect driver onboarding', () => {
    markPassengerOnboardingSeen()

    expect(hasSeenDriverOnboarding()).toBe(false)
  })
})
