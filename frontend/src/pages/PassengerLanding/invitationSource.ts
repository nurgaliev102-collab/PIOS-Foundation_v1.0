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
 * Sprint 7B) resolves to `null` here, same as an unknown driver id --
 * Passenger Landing cannot introduce someone by a name it does not have.
 */

export interface InvitationInfo {
  driverName: string
}

interface DriverResponse {
  id: string
  availability: string
  displayName: string | null
}

export async function getInvitationByDriverCode(driverCode: string): Promise<InvitationInfo | null> {
  if (!driverCode) {
    return null
  }
  try {
    const driver = await request<DriverResponse>(`/v1/drivers/${driverCode}`)
    if (!driver.displayName) {
      return null
    }
    return { driverName: driver.displayName }
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return null
    }
    throw error
  }
}
