import { useEffect, useRef } from 'react'

/**
 * P1 UX audit (2026-09-12): `RideRequest.tsx`/`PassengerLanding.tsx` both
 * drive a multi-step scenario through local React state (`step`), not
 * through separate routes -- so the browser's own Back button had no
 * history entry for any intermediate step and instead left the screen
 * entirely, skipping past whatever the user actually expected to step
 * back through.
 *
 * Deliberately narrow: this only ever links **one specific pair** of
 * steps per call site -- [activeStep] and the exact [previousStep] a
 * user should land on pressing Back from it -- never a generic "replay
 * every step transition" mechanism. Each call site is its own, separate
 * decision about which transition is safe to make backable at all (see
 * `RideRequest.tsx`/`PassengerLanding.tsx`'s own call sites for why
 * their one chosen pair has no submitted or side-effecting state
 * between the two steps -- going back must never resurrect a screen
 * that could contradict something already sent to the backend).
 *
 * Mechanism: when [step] transitions *into* [activeStep] (and
 * [enabled]), this pushes exactly one `history` entry on top of
 * whatever already exists -- same URL, no route change, so route
 * navigation and deep links are completely unaffected (a fresh load at
 * any URL never runs this effect before the user does anything). If the
 * browser's Back button is then pressed while still on [activeStep],
 * the resulting `popstate` calls [setStep] back to [previousStep] --
 * consuming exactly the one entry this hook itself pushed, never a
 * page navigation from before this component even mounted.
 */
export function useBackableStep<T>(
  step: T,
  setStep: (step: T) => void,
  activeStep: T,
  previousStep: T,
  enabled: boolean
): void {
  const wasActive = useRef(false)

  useEffect(() => {
    const isActive = step === activeStep
    if (isActive && !wasActive.current && enabled) {
      window.history.pushState({ ...(window.history.state ?? {}), piosBackableStep: activeStep }, '')
    }
    wasActive.current = isActive
  }, [step, activeStep, enabled])

  // Intentionally reads [step]/[enabled] fresh on every popstate via a
  // ref, rather than re-subscribing the listener on every change -- avoids
  // a brief window, right after a real browser back/forward tap, where the
  // listener could be mid-detach/reattach and miss the event.
  const stepRef = useRef(step)
  stepRef.current = step
  const enabledRef = useRef(enabled)
  enabledRef.current = enabled

  useEffect(() => {
    function handlePopState() {
      // [enabled] false means this hook never pushed an entry for the
      // current visit to [activeStep] -- some *other* popstate (a real
      // page navigation, or a different backable step elsewhere on this
      // same screen) must not be mistaken for the one this hook owns.
      if (stepRef.current === activeStep && enabledRef.current) {
        setStep(previousStep)
      }
    }
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [activeStep, previousStep, setStep])
}
