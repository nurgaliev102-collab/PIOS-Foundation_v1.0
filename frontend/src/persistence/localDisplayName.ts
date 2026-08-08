/**
 * ADR-055: a passenger's own chosen display name — shown to a driver as
 * "Пассажир: {name}" (`RideRequest.tsx` sends it as Order Management's own
 * `passengerName`). Deliberately device-local and unauthenticated: `identity`
 * has no name field (ADR-055 Decision 5 — the account is `phone`+password
 * only), and this value never proves anything about who is using the
 * device — only the session token (`BackendIdentityProvider`) does that.
 * Replaces `localPassengerIdentity.ts`'s old role of also minting a
 * client-generated id; that id is gone (ADR-055 Decision 5 — a
 * `passengerReference` must now be a real, authenticated `identityId`).
 */

const STORAGE_KEY = 'pios.display-name'

export function getDisplayName(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

export function saveDisplayName(name: string): void {
  try {
    localStorage.setItem(STORAGE_KEY, name)
  } catch {
    // Storage may be unavailable (private browsing, quota exceeded). The
    // name still exists for the current session; it simply will not
    // survive a reload.
  }
}
