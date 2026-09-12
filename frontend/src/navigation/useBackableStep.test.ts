import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useState } from 'react'
import { useBackableStep } from './useBackableStep'

/**
 * P1 UX audit (2026-09-12): expected behavior, written before the fix
 * (`RideRequest.tsx`/`PassengerLanding.tsx` did not yet call this hook at
 * the time these tests were written) -- pressing the browser Back button
 * while on [activeStep] must return to [previousStep] instead of leaving
 * the screen, and must never do so for any other step or for a
 * transition this hook was not told about.
 *
 * Drives the hook through a real component (via [renderHook], which
 * still mounts one) so its own effects actually run, and exercises the
 * real, global `window.history`/`popstate` -- not a mock -- since that is
 * the exact browser mechanism a real Back press uses, unaffected by
 * whichever router wrapper (or none) sits above it in a real app.
 */
function useStepWithBack(initial: string, activeStep: string, previousStep: string, enabled = true) {
  const [step, setStep] = useState(initial)
  useBackableStep(step, setStep, activeStep, previousStep, enabled)
  return { get step() { return step }, setStep }
}

describe('useBackableStep', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    // Each test pushes its own history entries -- clean slate for the next.
    window.history.replaceState(null, '')
  })

  it('returns to [previousStep] when Back is pressed while on [activeStep]', () => {
    const { result } = renderHook(() => useStepWithBack('circle', 'form', 'circle'))

    act(() => {
      result.current.setStep('form')
    })
    expect(result.current.step).toBe('form')

    act(() => {
      window.dispatchEvent(new PopStateEvent('popstate'))
    })

    expect(result.current.step).toBe('circle')
  })

  it('does nothing on Back when the current step is not [activeStep]', () => {
    const { result } = renderHook(() => useStepWithBack('circle', 'form', 'circle'))

    // Never advanced to 'form' at all -- still on the initial step.
    act(() => {
      window.dispatchEvent(new PopStateEvent('popstate'))
    })

    expect(result.current.step).toBe('circle')
  })

  it('does not push a history entry, and Back has no effect, when [enabled] is false', () => {
    const pushStateSpy = vi.spyOn(window.history, 'pushState')
    const { result } = renderHook(() => useStepWithBack('circle', 'form', 'circle', false))

    act(() => {
      result.current.setStep('form')
    })
    expect(pushStateSpy).not.toHaveBeenCalled()

    act(() => {
      window.dispatchEvent(new PopStateEvent('popstate'))
    })
    // No entry was pushed for this transition, so a real browser would
    // never fire popstate here at all -- but even if some *other* entry's
    // popstate fired while still on 'form', this hook must not react to
    // it, since it was never told this transition was backable.
    expect(result.current.step).toBe('form')
  })

  it('supports pressing Back again after re-entering [activeStep] a second time', () => {
    const { result } = renderHook(() => useStepWithBack('circle', 'form', 'circle'))

    act(() => {
      result.current.setStep('form')
    })
    act(() => {
      window.dispatchEvent(new PopStateEvent('popstate'))
    })
    expect(result.current.step).toBe('circle')

    // Forward again (e.g. the user chose a circle member a second time).
    act(() => {
      result.current.setStep('form')
    })
    expect(result.current.step).toBe('form')

    act(() => {
      window.dispatchEvent(new PopStateEvent('popstate'))
    })
    expect(result.current.step).toBe('circle')
  })

  it('does not react to popstate while on a step other than [activeStep], even after visiting it once', () => {
    const { result } = renderHook(() => useStepWithBack('circle', 'form', 'circle'))

    act(() => {
      result.current.setStep('form')
    })
    act(() => {
      result.current.setStep('confirmed')
    })
    expect(result.current.step).toBe('confirmed')

    act(() => {
      window.dispatchEvent(new PopStateEvent('popstate'))
    })

    // Already moved on from 'form' by an explicit forward action (not
    // Back) -- a stray popstate here must not yank the user back to
    // 'circle' out from under whatever they are looking at now.
    expect(result.current.step).toBe('confirmed')
  })
})
