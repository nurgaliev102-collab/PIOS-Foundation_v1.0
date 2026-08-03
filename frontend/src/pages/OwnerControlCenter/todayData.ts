import { request } from '../../api/apiClient'
import { DISPATCH_BASE_URL, DRIVER_MANAGEMENT_BASE_URL, ORDER_MANAGEMENT_BASE_URL } from './moduleBaseUrls'

/**
 * Owner Control Center's own read of the platform's existing, unauthenticated
 * `GET /v1/drivers`, `GET /v1/orders`, `GET /v1/proposals?driverId=...` and
 * `GET /v1/assignments?orderId=...` (ADR-043 Decision 3: those contracts are
 * not loosened — this screen fans out to them exactly as `Coordinator.tsx`
 * already does, one call per driver/order of interest, Section 7.5 steps
 * 2–5). `statedPrice`, present on every `ProposalListItem`, is read here
 * and **never rendered, summed, or included in the report** (ADR-043
 * Decision 6; ADR-042 R4.3) — this module deliberately never even copies
 * it into [TodayEvent] or [TodayCounters].
 */

interface DriverListItem {
  id: string
  availability: 'AVAILABLE' | 'UNAVAILABLE'
  displayName: string | null
  registeredAt: string | null
}

interface OrderListItem {
  id: string
  status: 'SUBMITTED' | 'COMPLETED' | 'CANCELLED'
  origin: string
  destination: string | null
  passengerName: string | null
  createdAt: string | null
  pickupAddress: string | null
}

interface ProposalListItem {
  proposalId: string
  orderId: string
  driverId: string
  status: 'OPEN' | 'ACCEPTED' | 'DECLINED' | 'LAPSED'
  createdAt: string | null
  respondedAt: string | null
}

interface AssignmentListItem {
  assignmentId: string
  orderId: string
  driverId: string
  status: 'CREATED' | 'ACCEPTED' | 'ARRIVED' | 'IN_PROGRESS' | 'COMPLETED'
  arrivedAt: string | null
  startedAt: string | null
  completedAt: string | null
}

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

function driverLabel(driverId: string, drivers: DriverListItem[]): string {
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
export async function loadTodaySnapshot(): Promise<TodaySnapshot> {
  const [drivers, orders] = await Promise.all([
    request<DriverListItem[]>('/v1/drivers', { baseUrl: DRIVER_MANAGEMENT_BASE_URL }).catch(() => [] as DriverListItem[]),
    request<OrderListItem[]>('/v1/orders', { baseUrl: ORDER_MANAGEMENT_BASE_URL }).catch(() => [] as OrderListItem[]),
  ])

  const proposalLists = await Promise.all(
    drivers.map((driver) =>
      request<ProposalListItem[]>(`/v1/proposals?driverId=${encodeURIComponent(driver.id)}`, {
        baseUrl: DISPATCH_BASE_URL,
      }).catch(() => [] as ProposalListItem[])
    )
  )
  const proposals = proposalLists.flat()

  const ordersWithAcceptedProposal = new Set(
    proposals.filter((proposal) => proposal.status === 'ACCEPTED').map((proposal) => proposal.orderId)
  )
  const assignmentLists = await Promise.all(
    [...ordersWithAcceptedProposal].map((orderId) =>
      request<AssignmentListItem[]>(`/v1/assignments?orderId=${encodeURIComponent(orderId)}`, {
        baseUrl: DISPATCH_BASE_URL,
      }).catch(() => [] as AssignmentListItem[])
    )
  )
  const assignments = assignmentLists.flat()

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
