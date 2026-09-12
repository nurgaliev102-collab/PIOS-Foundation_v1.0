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

/**
 * [vehicleMake]/[vehicleModel]/[vehicleColor]/[vehiclePlateNumber]
 * (PIOS Group and Long-Distance Rides Roadmap, Stage 1): the same public
 * `GET /v1/drivers/:driverId` fields `DriverHome.tsx`'s own "Моя машина"
 * card writes -- shown here so a passenger knows which car to look for
 * from the very first screen, the same trust-building role [driverName]
 * already plays. `null`/absent for a driver who has not declared one yet
 * -- not required for an invitation to resolve, unlike [driverName].
 *
 * [acceptsLongDistanceTrips] (PIOS Group and Long-Distance Rides Roadmap,
 * Stage 3): the same public `GET /v1/drivers/:driverId` field
 * `DriverHome.tsx`'s own long-distance checkbox writes -- shown here so a
 * passenger planning a vakhta/airport/another-city trip knows to ask this
 * driver about it, before any login exists to gate behind.
 *
 * [availability] (Referral funnel friction audit, 2026-09-12): the same
 * public `GET /v1/drivers/:driverId` field `RideRequest.tsx`'s own circle-
 * of-trust step already reads for its own `DriverTrustIndicator` -- this
 * function already fetched it and silently discarded it, so the one path
 * every new referral actually takes on their very first visit (0 or 1
 * existing relationship skips the circle step entirely, straight to
 * Passenger Landing's 'invited' screen and then the plain order form) never
 * saw it at all. A passenger could fill out and submit a real order to a
 * driver who has been OFFLINE the entire time, with literally no signal
 * anywhere before submission -- exactly the "поездка так и не случилась"
 * dead end this audit's own instruction named. No backend change: the
 * field was always in the response, only unused here.
 */
export interface InvitationInfo {
  driverName: string
  availability: 'AVAILABLE' | 'UNAVAILABLE'
  vehicleMake: string | null
  vehicleModel: string | null
  vehicleColor: string | null
  vehiclePlateNumber: string | null
  acceptsLongDistanceTrips: boolean
}

export type InvitationResult =
  | { status: 'found'; invitation: InvitationInfo }
  | { status: 'not-found' }
  | { status: 'error' }

interface DriverResponse {
  id: string
  availability: 'AVAILABLE' | 'UNAVAILABLE'
  displayName: string | null
  vehicleMake?: string | null
  vehicleModel?: string | null
  vehicleColor?: string | null
  vehiclePlateNumber?: string | null
  acceptsLongDistanceTrips?: boolean
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
    return {
      status: 'found',
      invitation: {
        driverName: driver.displayName,
        availability: driver.availability,
        vehicleMake: driver.vehicleMake ?? null,
        vehicleModel: driver.vehicleModel ?? null,
        vehicleColor: driver.vehicleColor ?? null,
        vehiclePlateNumber: driver.vehiclePlateNumber ?? null,
        acceptsLongDistanceTrips: driver.acceptsLongDistanceTrips ?? false,
      },
    }
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return { status: 'not-found' }
    }
    return { status: 'error' }
  }
}
