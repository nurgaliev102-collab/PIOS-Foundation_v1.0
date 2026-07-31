import { ApiError, request } from '../../api/apiClient'

/**
 * Sprint 7B (Personal Network Flow MVP): resolves a real invitation
 * through Driver Management's already-existing `GET /v1/drivers/:driverId`
 * -- the mocked `MOCK_INVITATIONS` dictionary this file used since Sprint
 * 2 is gone. `driverCode` in the URL (`/i/:driverCode`) is the driver's own
 * real `DriverId`, not a separate invitation code -- `DriverHome.tsx` has
 * always built its own share link this same way (`/i/${driver.id}`).
 *
 * A driver with no `displayName` set (Sprint 3A drivers, created before
 * Sprint 7B) resolves to `not-found` here, same as an unknown driver id --
 * Passenger Landing cannot introduce someone by a name it does not have.
 *
 * Sprint 6 (Passenger Entry-Path Failure Handling): a genuine 404 (unknown
 * driver code) and any other failure (network error, 5xx, timeout) used
 * to be indistinguishable to callers -- 404 resolved to `null`, everything
 * else rethrew, and neither call site caught that rethrow, leaving the
 * passenger stuck on an unrecoverable spinner. This now returns an
 * explicit, typed [InvitationResult] instead: `'not-found'` for a genuine
 * 404 (or an empty/missing driver code, or a driver with no display name --
 * unchanged from before), `'error'` for everything else. Callers must
 * render these as two distinct states -- `'error'` is never allowed to
 * collapse into `'not-found'`'s "link invalid" message, since that would
 * misreport a transient backend problem as a permanently broken link.
 */

export interface InvitationInfo {
  driverName: string
}

export type InvitationResult =
  | { status: 'found'; invitation: InvitationInfo }
  | { status: 'not-found' }
  | { status: 'error' }

interface DriverResponse {
  id: string
  availability: string
  displayName: string | null
}

export async function getInvitationByDriverCode(driverCode: string): Promise<InvitationResult> {
  if (!driverCode) {
    return { status: 'not-found' }
  }
  try {
    const driver = await request<DriverResponse>(`/v1/drivers/${driverCode}`)
    if (!driver.displayName) {
      return { status: 'not-found' }
    }
    return { status: 'found', invitation: { driverName: driver.displayName } }
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return { status: 'not-found' }
    }
    return { status: 'error' }
  }
}
