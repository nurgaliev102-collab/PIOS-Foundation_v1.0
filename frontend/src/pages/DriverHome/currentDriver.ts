/**
 * Sprint 5 — First Backend Integration: replaces `mockDriver.ts`.
 *
 * No driver authentication or session exists yet (explicitly out of
 * scope), so "which driver this app instance represents" remains a
 * fixed local constant rather than real session data — this is the one
 * thing about Driver Home still hardcoded. Everything else the driver's
 * own information used to provide (name, availability) now comes from
 * the real backend, looked up by this id.
 */
export const CURRENT_DRIVER_ID = 'ILDAR001'

/**
 * The invitation link itself remains mock (invitation logic stays
 * mocked per this sprint's own scope) — it is not backed by any real
 * invitation mechanism, only reused as before to reach Passenger
 * Landing locally.
 */
export function invitationLinkFor(driverId: string): string {
  return `https://pios.local/i/${driverId}`
}
