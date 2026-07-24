/**
 * Sprint 2 — Driver Invitation Flow: invitation-resolution abstraction.
 *
 * Passenger Landing renders through this function, not through a
 * hardcoded mock object, so a future task can replace the implementation
 * with a real API call (e.g. `GET /v1/invitations/:driverCode`) without
 * changing PassengerLanding.tsx at all — only this file, and only its
 * body, would change. The Promise-returning shape already matches what
 * a real network call looks like from the caller's side.
 *
 * No backend endpoint exists yet; this resolves from a fixed, local
 * mock directory only.
 */

export interface InvitationInfo {
  driverName: string
  driverType: string
}

const MOCK_INVITATIONS: Record<string, InvitationInfo> = {
  ILDAR001: { driverName: 'Ильдар', driverType: 'Independent driver' },
}

export function getInvitationByDriverCode(driverCode: string): Promise<InvitationInfo | null> {
  return Promise.resolve(MOCK_INVITATIONS[driverCode] ?? null)
}
