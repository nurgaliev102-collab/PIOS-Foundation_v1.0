import type { RouteObject } from 'react-router-dom'
import { DriverHome } from '../pages/DriverHome'
import { PassengerLanding } from '../pages/PassengerLanding'
import { RideRequest } from '../pages/RideRequest'
import { MyDrivers } from '../pages/MyDrivers'
import { Coordinator } from '../pages/Coordinator'
import { NetworkTest } from '../pages/NetworkTest'
import { OwnerControlCenter } from '../pages/OwnerControlCenter'
import { InstallHelp } from '../pages/InstallHelp'
import { NotFound } from '../pages/NotFound'

/**
 * Route table — Sprint FR-002: Driver Availability.
 *
 * Kept as a table, rather than routes scattered through JSX, so a future
 * task can add a route by adding an entry here, without restructuring
 * how routing itself is wired.
 */
export const routes: RouteObject[] = [
  {
    path: '/',
    element: <DriverHome />,
  },
  {
    path: '/i/:driverCode',
    element: <PassengerLanding />,
  },
  {
    // ADR-073 (Driver-to-Driver Referral -- Single-Hop Origin Fact), Part
    // 3: a driver-facing registration link, distinct from the
    // passenger-facing `/i/:driverCode` above -- that URL already means "a
    // passenger is being introduced to this driver by name" and must keep
    // meaning only that. Reuses the existing `DriverHome` component (same
    // reuse-over-duplication precedent `/request` already established for
    // `RideRequest`, ADR-070 Part 1) -- `DriverHome` reads
    // `inviterDriverCode` from the route itself and threads it into its own
    // existing `POST /v1/drivers` call.
    path: '/d/:inviterDriverCode',
    element: <DriverHome />,
  },
  {
    path: '/i/:driverCode/request',
    element: <RideRequest />,
  },
  {
    // ADR-070 (Channel 1 Discovery Matching), Part 1: a passenger-side
    // entry point with no `driverCode` at all -- reuses `RideRequest`
    // itself (see that component's own KDoc for exactly which branches
    // change when `driverCode` is `undefined`, and which stay byte-for-byte
    // identical) rather than a second, parallel component, per that ADR's
    // own preference for reuse over duplication.
    path: '/request',
    element: <RideRequest />,
  },
  {
    // docs/PIOS_PRODUCT_VISION.md §8 (product owner, 2026-09-05): a
    // returning passenger's own way back to a driver they already have a
    // relationship with, without needing that driver's own link again.
    // Public route (same as every passenger-facing route above) -- the
    // page itself has nothing to show a device with no stored identity.
    path: '/me',
    element: <MyDrivers />,
  },
  {
    path: '/coordinator',
    element: <Coordinator />,
  },
  {
    path: '/network-test',
    element: <NetworkTest />,
  },
  {
    // PIOS Install v1 (Product Owner exception): public, unauthenticated
    // by design (Section 7) — the one link an owner/driver can send
    // through Telegram/WhatsApp/SMS to someone with no PIOS session on
    // this device at all yet. See `InstallHelp.tsx`'s own KDoc.
    path: '/help/install',
    element: <InstallHelp />,
  },
  {
    // ADR-044 Decision 5: gated, but not by the router — OwnerControlCenter
    // itself renders the login screen instead of the console until a
    // credential is held (Section 4.2 of the MVP design document). Every
    // route above stays exactly as unauthenticated as before this one was
    // added.
    path: '/owner',
    element: <OwnerControlCenter />,
  },
  {
    path: '*',
    element: <NotFound />,
  },
]
