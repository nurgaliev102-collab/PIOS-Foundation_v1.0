/**
 * First-pilot feedback: a passenger who reloaded the page after ordering
 * landed back on a blank "Заказать поездку" form, with no way back to the
 * order they had just placed. This is the only file that persists which
 * order is "current" for a given driver's invitation link — mirrors
 * `localPassengerIdentity.ts`'s own scope-and-limitations note exactly
 * (this device's own storage only, no server, no cross-device sync).
 *
 * Keyed by `driverCode` rather than a single value: the same passenger
 * identity can hold a current order with more than one driver (one per
 * invitation link they have used), and each must resume independently.
 */

const STORAGE_KEY = 'pios.current-order'

type CurrentOrderMap = Record<string, string>

export function getCurrentOrderId(driverCode: string): string | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return null
    }
    const parsed: unknown = JSON.parse(raw)
    if (typeof parsed !== 'object' || parsed === null) {
      return null
    }
    const value = (parsed as Record<string, unknown>)[driverCode]
    return typeof value === 'string' ? value : null
  } catch {
    return null
  }
}

export function saveCurrentOrderId(driverCode: string, orderId: string): void {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const parsed: CurrentOrderMap = raw ? JSON.parse(raw) : {}
    parsed[driverCode] = orderId
    localStorage.setItem(STORAGE_KEY, JSON.stringify(parsed))
  } catch {
    // Storage may be unavailable (private browsing, quota exceeded). The
    // order still exists on the backend; only "resume after reload" for it
    // is lost, same accepted limitation `localPassengerIdentity.ts` already
    // documents for its own case.
  }
}

/**
 * P0-1 (`docs/SPRINT_PILOT_BLOCKERS.md`): the missing half of this file --
 * every order this file ever remembered was permanent, with no way for a
 * passenger to reach a second ride with the same driver once the first one
 * resolved (`RideRequest.tsx` had no path back to the order form). Removes
 * only [driverCode]'s own entry -- every other driver's stored order id in
 * the same map is untouched, mirroring [saveCurrentOrderId]'s own
 * per-driver scope exactly.
 */
export function clearCurrentOrderId(driverCode: string): void {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return
    }
    const parsed: CurrentOrderMap = JSON.parse(raw)
    delete parsed[driverCode]
    localStorage.setItem(STORAGE_KEY, JSON.stringify(parsed))
  } catch {
    // Storage may be unavailable -- same accepted limitation as above.
  }
}
