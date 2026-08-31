const DRIVER_KEY = 'pios.onboarding.driver-seen'
const PASSENGER_KEY = 'pios.onboarding.passenger-seen'

/**
 * PIOS Onboarding v1 — one flag per role, following the same
 * one-file-per-concern, try/catch-wrapped convention as
 * `localDisplayName.ts` and `localCurrentOrder.ts`. Driver and passenger
 * onboarding are shown and dismissed independently, so a single shared key
 * would incorrectly suppress one role's walkthrough after the other role's
 * was seen on the same device (e.g. a coordinator testing both).
 */

export function hasSeenDriverOnboarding(): boolean {
  try {
    return localStorage.getItem(DRIVER_KEY) === 'true'
  } catch {
    return false
  }
}

export function markDriverOnboardingSeen(): void {
  try {
    localStorage.setItem(DRIVER_KEY, 'true')
  } catch {
    // Best-effort only — a driver who can't persist this simply sees the
    // walkthrough again next time, which is harmless.
  }
}

export function hasSeenPassengerOnboarding(): boolean {
  try {
    return localStorage.getItem(PASSENGER_KEY) === 'true'
  } catch {
    return false
  }
}

export function markPassengerOnboardingSeen(): void {
  try {
    localStorage.setItem(PASSENGER_KEY, 'true')
  } catch {
    // Best-effort only, same reasoning as above.
  }
}
