/**
 * Sprint 8 (First User Experience): local persistence for whether this
 * browser's driver has already seen the first-time welcome screen
 * (`DriverHome.tsx`) — mirrors `localPassengerIdentity.ts`'s own
 * "only this file touches localStorage" discipline. No server, database,
 * or authentication is involved; this is storage on the driver's own
 * device only, same scope limitation as the passenger identity file.
 */

const STORAGE_KEY = 'pios.driver-onboarding-seen'

export function hasSeenDriverOnboarding(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'true'
  } catch {
    return false
  }
}

export function markDriverOnboardingSeen(): void {
  try {
    localStorage.setItem(STORAGE_KEY, 'true')
  } catch {
    // Storage may be unavailable (private browsing, quota exceeded) — the
    // welcome screen simply reappears next time, not a failure worth
    // surfacing to the driver.
  }
}
