import { request } from '../../api/apiClient'
import { DISPATCH_BASE_URL, DRIVER_MANAGEMENT_BASE_URL, ORDER_MANAGEMENT_BASE_URL } from './moduleBaseUrls'
import { toBasicAuthorizationHeader, type OwnerCredential } from './ownerCredential'

/**
 * Owner Control Center's own read of the platform's `GET /v1/drivers`,
 * `GET /v1/orders`, `GET /v1/proposals?driverId=...` and
 * `GET /v1/assignments?orderId=...` (ADR-043 Decision 3: this screen fans
 * out to them exactly as `Coordinator.tsx` already does, one call per
 * driver/order of interest, Section 7.5 steps 2–5). `statedPrice`, present
 * on every `ProposalListItem`, is read here and — for *this module's own use
 * in [loadTodaySnapshot]* — **never rendered, summed, or included in the
 * report** (ADR-043 Decision 6; ADR-042 R4.3): `buildReport.ts` never reads
 * it, and neither [TodayEvent] nor [TodayCounters] ever copies it in. The
 * field is still present on [ProposalListItem] because `fetchProposalsForOrder`
 * below is now also called by `Coordinator.tsx`, which does render it — see
 * ADR-061 Decision 3 for the narrow, screen-scoped exception that permits
 * that one caller to do so.
 *
 * ADR-060 (Order Query Authorization): `GET /v1/orders` and
 * `GET /v1/proposals?driverId=...` now require a credential. This screen
 * already holds the owner's own `Authorization: Basic` credential
 * (`ownerCredential.ts`, already sent to `GET /v1/health` by
 * `healthPoll.ts`) — [loadTodaySnapshot] now takes it and attaches it to
 * both calls (ADR-060 Decision 5's Mode 3 for orders; Decision 4's owner
 * branch for `?driverId=`). `GET /v1/drivers` and
 * `GET /v1/assignments?orderId=...` are not gated by that ADR and keep
 * sending no credential, unchanged.
 *
 * ADR-066 (Proposal Participant Authorization): `GET /v1/proposals?orderId=...`
 * ([fetchProposalsForOrder]) now requires a credential too — the one gap
 * ADR-060 Decision 4 left open, closed once `Proposal` gained a
 * `passengerReference` to authorize against. Same owner credential, same
 * pattern as [fetchProposalsForDriver].
 *
 * ADR-061 (Coordinator Owner-Gated Access), Decision 2: the individual fetch
 * functions below are exported so `Coordinator.tsx` can reuse the same
 * authorized reads rather than re-implementing its own copy of the
 * `Authorization: Basic` header logic. Each function throws on failure;
 * [loadTodaySnapshot] is the one caller that wants "never fail the whole
 * snapshot" and applies its own `.catch(() => [])` at the call site —
 * `Coordinator.tsx` applies its own error handling instead, since it needs
 * to distinguish "failed to load" from "genuinely empty" for its own status
 * display.
 */

export interface DriverListItem {
  id: string
  availability: 'AVAILABLE' | 'UNAVAILABLE'
  displayName: string | null
  registeredAt: string | null
  isTest: boolean
}

export interface OrderListItem {
  id: string
  status: 'SUBMITTED' | 'COMPLETED' | 'CANCELLED'
  origin: string
  destination: string | null
  passengerName: string | null
  createdAt: string | null
  pickupAddress: string | null
  requestedPickupAt: string | null
  isTest: boolean
  commitmentTerminated?: boolean
  /** PIOS Group and Long-Distance Rides Roadmap, Stage 2 -- `Order.passengerCount`, unchanged. */
  passengerCount?: number | null
}

export interface ProposalListItem {
  proposalId: string
  orderId: string
  driverId: string
  status: 'OPEN' | 'ACCEPTED' | 'DECLINED' | 'LAPSED' | 'WITHDRAWN'
  statedPrice: string | null
  statedEtaMinutes: number | null
  createdAt: string | null
  respondedAt: string | null
  isTest: boolean
}

export interface AssignmentListItem {
  assignmentId: string
  orderId: string
  driverId: string
  status: 'CREATED' | 'ACCEPTED' | 'ARRIVED' | 'IN_PROGRESS' | 'COMPLETED'
  arrivedAt: string | null
  startedAt: string | null
  completedAt: string | null
  isTest: boolean
}

/**
 * Test/production data separation (Owner Control Center audit,
 * 2026-08-17): drops every record whose backend-owned `isTest` is `true`,
 * used identically by [loadTodaySnapshot] (counters/EventFeed) and
 * `pilotAnalytics.ts`'s own `collectPilotAnalyticsInput` (AI Advisor) —
 * both already call the exact same `fetch*` functions below, so applying
 * this filter immediately after each fetch, in both places, keeps the
 * whole snapshot consistent: a filtered-out test driver is never fanned
 * out to for proposals, a filtered-out test order never contributes a
 * counter or an event, and nothing downstream ever sees a `isTest: true`
 * record to begin with. Never infers test-ness from a name or id — the
 * field is the backend's own authoritative value, set once at creation
 * (see each domain aggregate's own `isTest` KDoc).
 */
export function excludeTestData<T extends { isTest: boolean }>(items: T[]): T[] {
  return items.filter((item) => !item.isTest)
}

/** `GET /v1/drivers` — unauthenticated, unaffected by ADR-060. */
export function fetchDrivers(credential: OwnerCredential): Promise<DriverListItem[]> {
  return request<DriverListItem[]>('/v1/drivers', {
    headers: { Authorization: toBasicAuthorizationHeader(credential) },
    baseUrl: DRIVER_MANAGEMENT_BASE_URL,
  })
}

/** `GET /v1/orders`, owner Mode (ADR-060 Decision 1's third mode: no parameter, `Authorization: Basic`). */
export function fetchOrders(credential: OwnerCredential): Promise<OrderListItem[]> {
  return request<OrderListItem[]>('/v1/orders', {
    headers: { Authorization: toBasicAuthorizationHeader(credential) },
    baseUrl: ORDER_MANAGEMENT_BASE_URL,
  })
}

/**
 * `GET /v1/proposals?driverId=...`, owner branch (ADR-060 Decision 4).
 * Carries its own [FAN_OUT_REQUEST_TIMEOUT_MS] bound (2026-08-17 incident,
 * see [mapWithConcurrency]'s own KDoc) so one slow response, under whatever
 * load produced this fan-out's own delay in the first place, cannot occupy
 * a worker slot indefinitely and stall every driver queued behind it.
 */
export function fetchProposalsForDriver(driverId: string, credential: OwnerCredential): Promise<ProposalListItem[]> {
  return request<ProposalListItem[]>(`/v1/proposals?driverId=${encodeURIComponent(driverId)}`, {
    headers: { Authorization: toBasicAuthorizationHeader(credential) },
    baseUrl: DISPATCH_BASE_URL,
    signal: AbortSignal.timeout(FAN_OUT_REQUEST_TIMEOUT_MS),
  })
}

/**
 * `GET /v1/proposals?orderId=...` (ADR-066, Proposal Participant
 * Authorization -- P0 remediation). Previously unauthenticated (ADR-060
 * Decision 4 left it open as "an enumeration sink, not a source"); that
 * decision's own named blocker (a `passengerReference` on `Proposal`) is
 * closed by ADR-066, so this call now sends the owner credential, mirroring
 * [fetchProposalsForOrder]'s own sibling [fetchProposalsForDriver] exactly.
 */
export function fetchProposalsForOrder(orderId: string, credential: OwnerCredential): Promise<ProposalListItem[]> {
  return request<ProposalListItem[]>(`/v1/proposals?orderId=${encodeURIComponent(orderId)}`, {
    headers: { Authorization: toBasicAuthorizationHeader(credential) },
    baseUrl: DISPATCH_BASE_URL,
  })
}

/**
 * `GET /v1/assignments?orderId=...` — unauthenticated, unaffected by ADR-060.
 * Carries the same [FAN_OUT_REQUEST_TIMEOUT_MS] bound as
 * [fetchProposalsForDriver], for the same reason.
 */
export function fetchAssignmentsForOrder(orderId: string, credential: OwnerCredential): Promise<AssignmentListItem[]> {
  return request<AssignmentListItem[]>(`/v1/assignments?orderId=${encodeURIComponent(orderId)}`, {
    headers: { Authorization: toBasicAuthorizationHeader(credential) },
    baseUrl: DISPATCH_BASE_URL,
    signal: AbortSignal.timeout(FAN_OUT_REQUEST_TIMEOUT_MS),
  })
}

/**
 * Runs [fn] over [items] with at most [limit] calls in flight at once,
 * instead of firing every call in one `Promise.all` burst.
 *
 * 2026-08-17 incident: [loadTodaySnapshot]'s own driver fan-out
 * (`drivers.map(fetchProposalsForDriver)`) used to be one unbounded
 * `Promise.all` — harmless at pilot-launch driver counts, but this session's
 * own repeated E2E test runs left 240 disposable driver records in the live
 * database (`GET /v1/drivers` on the public Funnel confirmed the count; no
 * delete endpoint exists to remove them — `DriverController` only exposes
 * create/read/declare-availability). At that count, every 15-second poll
 * fired 240 simultaneous credentialed requests to Dispatch on the same
 * shared-origin HTTP/2 connection the five `GET /v1/health/<module>` calls
 * also use. When one poll's 240-way fan-out was still draining as the next
 * poll's tick fired (plausible once the fan-out itself takes longer than
 * the 15s interval under that much concurrent load), the interleaved health
 * requests were the ones observed starved past their own 10s
 * `AbortSignal.timeout` in `healthPoll.ts` — reproduced live via Playwright
 * against the public Funnel, not merely theorized.
 *
 * Chunking bounds the concurrent request count regardless of how large
 * `drivers`/`orders` grows, without touching any endpoint's contract,
 * without adding a request-cancellation/dedup layer, and without changing
 * what data this screen shows — same reasoning applied to the
 * orders-with-an-accepted-proposal fan-out just below it, which has the
 * same unbounded shape and would hit the same ceiling as order volume grows.
 */
async function mapWithConcurrency<T, R>(items: T[], limit: number, fn: (item: T) => Promise<R>): Promise<R[]> {
  const results: R[] = new Array(items.length)
  let nextIndex = 0
  async function worker() {
    while (nextIndex < items.length) {
      const index = nextIndex++
      results[index] = await fn(items[index])
    }
  }
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker))
  return results
}

/**
 * Fan-out batch size for [loadTodaySnapshot]'s two per-item polls — see
 * [mapWithConcurrency]'s own KDoc. Chosen from live measurement, not a
 * round number: 5 concurrent, credentialed, DB-touching requests through
 * the public Funnel completed in under 2.1s total in the same incident's
 * own verification; 20 concurrent still left a live 240-driver fan-out
 * competing long enough to starve an unrelated second poll's health checks
 * past their own 10s timeout (reproduced live via Playwright before this
 * value was lowered from 20 to 5).
 */
const FAN_OUT_CONCURRENCY_LIMIT = 5

/** Per-item timeout for the same two fan-outs — see [fetchProposalsForDriver]'s own KDoc. */
const FAN_OUT_REQUEST_TIMEOUT_MS = 10_000

export interface TodayCounters {
  driversTotal: number
  driversAvailable: number
  ordersCreated: number
  ordersCompleted: number
  ordersInProgress: number
  ordersCancelled: number
}

export interface TodayEvent {
  at: string
  text: string
}

export interface TodaySnapshot {
  counters: TodayCounters
  events: TodayEvent[]
}

function isToday(isoTimestamp: string | null): boolean {
  if (!isoTimestamp) {
    return false
  }
  const date = new Date(isoTimestamp)
  const now = new Date()
  return (
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  )
}

/** Resolves a driver's display name, falling back to its id — shared with `Coordinator.tsx` (ADR-061 Decision 2). */
export function driverLabel(driverId: string, drivers: DriverListItem[]): string {
  return drivers.find((driver) => driver.id === driverId)?.displayName ?? driverId
}

function passengerLabel(orderId: string, orders: OrderListItem[]): string {
  return orders.find((order) => order.id === orderId)?.passengerName ?? 'Пассажир'
}

/**
 * Loads today's counters and event feed (Section 7.5 steps 2–5). Never
 * throws: a failure at any of the four fan-out reads leaves that source's
 * own contribution empty rather than failing the whole snapshot, since a
 * partial event feed is still more useful than none, and the main status
 * card (driven by `GET /v1/health` alone) is what tells the owner whether
 * this data can be trusted.
 */
export async function loadTodaySnapshot(credential: OwnerCredential): Promise<TodaySnapshot> {
  const [driversRaw, ordersRaw] = await Promise.all([
    fetchDrivers(credential).catch(() => [] as DriverListItem[]),
    fetchOrders(credential).catch(() => [] as OrderListItem[]),
  ])
  const drivers = excludeTestData(driversRaw)
  const orders = excludeTestData(ordersRaw)

  const proposalLists = await mapWithConcurrency(drivers, FAN_OUT_CONCURRENCY_LIMIT, (driver) =>
    fetchProposalsForDriver(driver.id, credential).catch(() => [] as ProposalListItem[])
  )
  const proposals = excludeTestData(proposalLists.flat())

  const ordersWithAcceptedProposal = new Set(
    proposals.filter((proposal) => proposal.status === 'ACCEPTED').map((proposal) => proposal.orderId)
  )
  const assignmentLists = await mapWithConcurrency([...ordersWithAcceptedProposal], FAN_OUT_CONCURRENCY_LIMIT, (orderId) =>
    fetchAssignmentsForOrder(orderId, credential).catch(() => [] as AssignmentListItem[])
  )
  const assignments = excludeTestData(assignmentLists.flat())

  const todaysOrders = orders.filter((order) => isToday(order.createdAt))

  const counters: TodayCounters = {
    driversTotal: drivers.length,
    driversAvailable: drivers.filter((driver) => driver.availability === 'AVAILABLE').length,
    ordersCreated: todaysOrders.length,
    ordersCompleted: todaysOrders.filter((order) => order.status === 'COMPLETED').length,
    ordersInProgress: todaysOrders.filter((order) => order.status === 'SUBMITTED').length,
    ordersCancelled: todaysOrders.filter((order) => order.status === 'CANCELLED').length,
  }

  const events: TodayEvent[] = []

  for (const driver of drivers) {
    if (driver.registeredAt && isToday(driver.registeredAt)) {
      events.push({ at: driver.registeredAt, text: `${driver.displayName ?? driver.id} — новый водитель` })
    }
  }

  for (const order of orders) {
    if (order.createdAt && isToday(order.createdAt)) {
      const where = order.pickupAddress ? `, ${order.pickupAddress}` : ''
      events.push({ at: order.createdAt, text: `Новый заказ. ${passengerLabel(order.id, orders)}${where}` })
    }
  }

  for (const proposal of proposals) {
    const driver = driverLabel(proposal.driverId, drivers)
    if (proposal.createdAt && isToday(proposal.createdAt)) {
      events.push({
        at: proposal.createdAt,
        text: `${driver} получил заказ от ${passengerLabel(proposal.orderId, orders)}`,
      })
    }
    if (proposal.respondedAt && isToday(proposal.respondedAt)) {
      if (proposal.status === 'ACCEPTED') {
        events.push({ at: proposal.respondedAt, text: `${driver} принял заказ` })
      } else if (proposal.status === 'DECLINED') {
        events.push({ at: proposal.respondedAt, text: `${driver} отклонил заказ` })
      }
    }
  }

  for (const assignment of assignments) {
    const driver = driverLabel(assignment.driverId, drivers)
    if (assignment.arrivedAt && isToday(assignment.arrivedAt)) {
      events.push({ at: assignment.arrivedAt, text: `${driver} на месте` })
    }
    if (assignment.completedAt && isToday(assignment.completedAt)) {
      events.push({ at: assignment.completedAt, text: `${driver} завершил поездку` })
    }
  }

  events.sort((a, b) => new Date(b.at).getTime() - new Date(a.at).getTime())

  return { counters, events }
}
