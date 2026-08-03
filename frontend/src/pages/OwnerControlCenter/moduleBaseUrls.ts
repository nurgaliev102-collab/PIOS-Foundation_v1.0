/**
 * Owner Control Center — the five pilot modules' own base URLs, reusing
 * exactly the `VITE_*_BASE_URL` constants and defaults already established
 * by `Coordinator.tsx`/`DriverHome.tsx`/`RideRequest.tsx`/`PassengerLanding.tsx`/
 * `BackendIdentityProvider.ts` — no new environment variable, no new
 * proxy/routing convention. `network-management` is deliberately absent:
 * it has no health endpoint and appears nowhere on this surface (ADR-043).
 *
 * Each module is called directly at its own `baseUrl`, the same pattern
 * `Coordinator.tsx` already uses for Order Management and Dispatch. In the
 * pilot deployment all five `VITE_*_BASE_URL` values are deliberately set
 * to the same `PILOT_URL` (`PIOS_PILOT_INFRASTRUCTURE_DECISION.md`), so
 * this resolves to calling the single preview origin for every module —
 * which is why `healthPoll.ts` appends each module's own `key` to the
 * path (`/v1/health/<module-name>`, not a bare `/v1/health`): five
 * identical paths could not be routed to five different ports through
 * that one origin's `preview.proxy` table, which maps one path prefix to
 * one port. `PILOT_INFRASTRUCTURE_ROUTING_DECISION.md` Variant A (Product
 * Owner approved 2026-08-03) is what resolved this; the path suffix here
 * is that decision's only visible trace in this file.
 */
export interface PilotModule {
  key: 'driver-management' | 'passenger-experience' | 'order-management' | 'dispatch' | 'identity'
  baseUrl: string
}

export const DRIVER_MANAGEMENT_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081'
export const PASSENGER_EXPERIENCE_BASE_URL = import.meta.env.VITE_PASSENGER_EXPERIENCE_BASE_URL ?? 'http://localhost:8082'
export const ORDER_MANAGEMENT_BASE_URL = import.meta.env.VITE_ORDER_MANAGEMENT_BASE_URL ?? 'http://localhost:8083'
export const DISPATCH_BASE_URL = import.meta.env.VITE_DISPATCH_BASE_URL ?? 'http://localhost:8084'
export const IDENTITY_BASE_URL = import.meta.env.VITE_IDENTITY_BASE_URL ?? 'http://localhost:8086'

export const PILOT_MODULES: PilotModule[] = [
  { key: 'driver-management', baseUrl: DRIVER_MANAGEMENT_BASE_URL },
  { key: 'passenger-experience', baseUrl: PASSENGER_EXPERIENCE_BASE_URL },
  { key: 'order-management', baseUrl: ORDER_MANAGEMENT_BASE_URL },
  { key: 'dispatch', baseUrl: DISPATCH_BASE_URL },
  { key: 'identity', baseUrl: IDENTITY_BASE_URL },
]
