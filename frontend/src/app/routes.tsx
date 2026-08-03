import type { RouteObject } from 'react-router-dom'
import { DriverHome } from '../pages/DriverHome'
import { PassengerLanding } from '../pages/PassengerLanding'
import { RideRequest } from '../pages/RideRequest'
import { Coordinator } from '../pages/Coordinator'
import { NetworkTest } from '../pages/NetworkTest'
import { OwnerControlCenter } from '../pages/OwnerControlCenter'
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
    path: '/i/:driverCode/request',
    element: <RideRequest />,
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
