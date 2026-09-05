import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ModuleHealth } from './healthPoll'
import type { AssignmentListItem, DriverListItem, OrderListItem, ProposalListItem } from './todayData'

vi.mock('./todayData', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./todayData')>()
  return {
    ...actual,
    fetchDrivers: vi.fn(),
    fetchOrders: vi.fn(),
    fetchProposalsForDriver: vi.fn(),
    fetchAssignmentsForOrder: vi.fn(),
  }
})

import { fetchAssignmentsForOrder, fetchDrivers, fetchOrders, fetchProposalsForDriver } from './todayData'
import {
  calculateAcceptanceRate,
  calculateCancellationRate,
  calculateCompletionRate,
  collectPilotAnalyticsInput,
  computeCurrentDaySnapshot,
  computeDailyHistory,
  type PilotOrderMetrics,
  type PilotProposalMetrics,
} from './pilotAnalytics'

const mockedFetchDrivers = vi.mocked(fetchDrivers)
const mockedFetchOrders = vi.mocked(fetchOrders)
const mockedFetchProposalsForDriver = vi.mocked(fetchProposalsForDriver)
const mockedFetchAssignmentsForOrder = vi.mocked(fetchAssignmentsForOrder)

const OWNER_CREDENTIAL = { username: 'owner', password: 'secret' }

const UP_HEALTHS: ModuleHealth[] = ['driver-management', 'passenger-experience', 'order-management', 'dispatch', 'identity'].map(
  (module) => ({
    module: module as ModuleHealth['module'],
    outcome: 'up',
    outboxPending: null,
    outboxOldestAgeSeconds: null,
    checkedAt: null,
  })
)

function driver(id: string, availability: DriverListItem['availability'] = 'AVAILABLE', isTest = false): DriverListItem {
  return { id, availability, displayName: null, registeredAt: null, isTest }
}

function order(id: string, status: OrderListItem['status'], isTest = false): OrderListItem {
  return { id, status, origin: 'x', destination: null, passengerName: null, createdAt: null, pickupAddress: null, requestedPickupAt: null, isTest }
}

function proposal(
  overrides: Partial<ProposalListItem> & Pick<ProposalListItem, 'proposalId' | 'orderId' | 'driverId' | 'status'>
): ProposalListItem {
  return { statedPrice: null, statedEtaMinutes: null, createdAt: null, respondedAt: null, isTest: false, ...overrides }
}

function assignment(
  overrides: Partial<AssignmentListItem> & Pick<AssignmentListItem, 'assignmentId' | 'orderId' | 'driverId' | 'status'>
): AssignmentListItem {
  return { arrivedAt: null, startedAt: null, completedAt: null, isTest: false, ...overrides }
}

/** Real calendar time relative to "now" -- same style `todayData.test.ts` already uses (`new Date().toISOString()`), never a fixed date the test's own passage of time could silently invalidate. */
function daysAgoIso(daysAgo: number, hour = 12): string {
  const date = new Date()
  date.setDate(date.getDate() - daysAgo)
  date.setHours(hour, 0, 0, 0)
  return date.toISOString()
}

function localDateKey(daysAgo: number): string {
  const date = new Date()
  date.setDate(date.getDate() - daysAgo)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

describe('calculateAcceptanceRate', () => {
  it('divides accepted by resolved (accepted + declined + lapsed), excluding still-open proposals', () => {
    const metrics: PilotProposalMetrics = { total: 10, accepted: 3, declined: 2, lapsed: 1, withdrawn: 0, open: 4 }
    expect(calculateAcceptanceRate(metrics)).toBeCloseTo(3 / 6)
  })

  it('returns null when nothing has resolved yet, instead of dividing by zero', () => {
    const metrics: PilotProposalMetrics = { total: 5, accepted: 0, declined: 0, lapsed: 0, withdrawn: 0, open: 5 }
    expect(calculateAcceptanceRate(metrics)).toBeNull()
  })
})

describe('calculateCompletionRate', () => {
  it('divides completed by total orders', () => {
    const metrics: PilotOrderMetrics = { total: 4, completed: 3, cancelled: 1, open: 0 }
    expect(calculateCompletionRate(metrics)).toBeCloseTo(0.75)
  })

  it('returns null for zero orders, never 0 (which would falsely read as "nothing completed")', () => {
    const metrics: PilotOrderMetrics = { total: 0, completed: 0, cancelled: 0, open: 0 }
    expect(calculateCompletionRate(metrics)).toBeNull()
  })
})

describe('calculateCancellationRate', () => {
  it('divides cancelled by total orders', () => {
    const metrics: PilotOrderMetrics = { total: 10, completed: 5, cancelled: 2, open: 3 }
    expect(calculateCancellationRate(metrics)).toBeCloseTo(0.2)
  })

  it('returns null for zero orders', () => {
    const metrics: PilotOrderMetrics = { total: 0, completed: 0, cancelled: 0, open: 0 }
    expect(calculateCancellationRate(metrics)).toBeNull()
  })
})

describe('computeDailyHistory', () => {
  it('returns an empty array when there is no data at all', () => {
    expect(computeDailyHistory([], [], [])).toEqual([])
  })

  it('returns one entry for a single day of activity', () => {
    const orders: OrderListItem[] = [{ ...order('o1', 'COMPLETED'), createdAt: daysAgoIso(1) }]

    const history = computeDailyHistory(orders, [], [])

    expect(history).toHaveLength(1)
    expect(history[0].date).toBe(localDateKey(1))
    expect(history[0].orders).toEqual({ total: 1, completed: 1, cancelled: 0, open: 0 })
  })

  it('returns one entry per distinct calendar day, most recent first', () => {
    const orders: OrderListItem[] = [
      { ...order('o1', 'COMPLETED'), createdAt: daysAgoIso(1) },
      { ...order('o2', 'COMPLETED'), createdAt: daysAgoIso(2) },
    ]

    const history = computeDailyHistory(orders, [], [])

    expect(history.map((h) => h.date)).toEqual([localDateKey(1), localDateKey(2)])
  })

  it('caps history at 7 days, excluding data outside the window', () => {
    const orders: OrderListItem[] = Array.from({ length: 10 }, (_, i) => ({
      ...order(`o${i}`, 'COMPLETED'),
      createdAt: daysAgoIso(i + 1),
    }))

    const history = computeDailyHistory(orders, [], [])

    expect(history).toHaveLength(7)
    expect(history.map((h) => h.date)).toEqual([1, 2, 3, 4, 5, 6, 7].map(localDateKey))
  })

  it('excludes records with a missing createdAt instead of crashing or fabricating a date', () => {
    const orders: OrderListItem[] = [
      { ...order('o1', 'COMPLETED'), createdAt: null },
      { ...order('o2', 'COMPLETED'), createdAt: daysAgoIso(1) },
    ]

    const history = computeDailyHistory(orders, [], [])

    expect(history).toHaveLength(1)
    expect(history[0].orders.total).toBe(1)
  })

  it('groups multiple records on the same calendar day into one snapshot, regardless of time of day', () => {
    const orders: OrderListItem[] = [
      { ...order('o1', 'COMPLETED'), createdAt: daysAgoIso(1, 3) },
      { ...order('o2', 'CANCELLED'), createdAt: daysAgoIso(1, 23) },
    ]

    const history = computeDailyHistory(orders, [], [])

    expect(history).toHaveLength(1)
    expect(history[0].orders).toEqual({ total: 2, completed: 1, cancelled: 1, open: 0 })
  })

  it('never includes today itself -- today is the current snapshot, not history', () => {
    const orders: OrderListItem[] = [{ ...order('o1', 'COMPLETED'), createdAt: daysAgoIso(0) }]

    const history = computeDailyHistory(orders, [], [])

    expect(history).toEqual([])
  })

  it('computes activeDrivers from distinct drivers with proposal activity that day, never from a fabricated availability history', () => {
    const proposals: ProposalListItem[] = [
      proposal({ proposalId: 'p1', orderId: 'o1', driverId: 'd1', status: 'ACCEPTED', createdAt: daysAgoIso(1) }),
      proposal({ proposalId: 'p2', orderId: 'o2', driverId: 'd2', status: 'DECLINED', createdAt: daysAgoIso(1) }),
      proposal({ proposalId: 'p3', orderId: 'o3', driverId: 'd1', status: 'ACCEPTED', createdAt: daysAgoIso(1) }),
    ]

    const history = computeDailyHistory([], proposals, [])

    expect(history).toHaveLength(1)
    expect(history[0].activeDrivers).toBe(2)
  })

  it('reuses computeAssignmentMetrics, bucketing an assignment by arrivedAt when present', () => {
    const assignments: AssignmentListItem[] = [
      assignment({
        assignmentId: 'a1',
        orderId: 'o1',
        driverId: 'd1',
        status: 'COMPLETED',
        arrivedAt: daysAgoIso(1),
        completedAt: daysAgoIso(1),
      }),
    ]

    const history = computeDailyHistory([], [], assignments)

    expect(history).toHaveLength(1)
    expect(history[0].assignments).toEqual({ total: 1, completed: 1, inProgress: 0 })
  })
})

describe('computeCurrentDaySnapshot', () => {
  it('returns null when there is no data for today at all -- never a fabricated all-zero snapshot', () => {
    expect(computeCurrentDaySnapshot([], [], [])).toBeNull()
  })

  it('returns null when the only data present is from before today', () => {
    const orders: OrderListItem[] = [{ ...order('o1', 'COMPLETED'), createdAt: daysAgoIso(1) }]

    expect(computeCurrentDaySnapshot(orders, [], [])).toBeNull()
  })

  it("returns today's own real snapshot, on the same scale as a history entry, when today has dated activity", () => {
    const orders: OrderListItem[] = [
      { ...order('o1', 'COMPLETED'), createdAt: daysAgoIso(0) },
      { ...order('o2', 'CANCELLED'), createdAt: daysAgoIso(0) },
    ]

    const snapshot = computeCurrentDaySnapshot(orders, [], [])

    expect(snapshot).not.toBeNull()
    expect(snapshot!.date).toBe(localDateKey(0))
    expect(snapshot!.orders).toEqual({ total: 2, completed: 1, cancelled: 1, open: 0 })
  })

  it("excludes yesterday's activity from today's snapshot", () => {
    const orders: OrderListItem[] = [
      { ...order('o-today', 'COMPLETED'), createdAt: daysAgoIso(0) },
      { ...order('o-yesterday', 'COMPLETED'), createdAt: daysAgoIso(1) },
    ]

    const snapshot = computeCurrentDaySnapshot(orders, [], [])

    expect(snapshot!.orders).toEqual({ total: 1, completed: 1, cancelled: 0, open: 0 })
  })

  it('computes activeDrivers for today the same way computeDailyHistory does for a past day', () => {
    const proposals: ProposalListItem[] = [
      proposal({ proposalId: 'p1', orderId: 'o1', driverId: 'd1', status: 'ACCEPTED', createdAt: daysAgoIso(0) }),
      proposal({ proposalId: 'p2', orderId: 'o2', driverId: 'd1', status: 'ACCEPTED', createdAt: daysAgoIso(0) }),
    ]

    const snapshot = computeCurrentDaySnapshot([], proposals, [])

    expect(snapshot!.activeDrivers).toBe(1)
  })
})

describe('collectPilotAnalyticsInput', () => {
  beforeEach(() => {
    mockedFetchDrivers.mockReset()
    mockedFetchOrders.mockReset()
    mockedFetchProposalsForDriver.mockReset()
    mockedFetchAssignmentsForOrder.mockReset()
  })
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('computes order/proposal/driver/health metrics from the fetched data, with zero orders yielding total: 0', async () => {
    mockedFetchDrivers.mockResolvedValue([])
    mockedFetchOrders.mockResolvedValue([])

    const result = await collectPilotAnalyticsInput(OWNER_CREDENTIAL, UP_HEALTHS)

    expect(result.orders).toEqual({ total: 0, completed: 0, cancelled: 0, open: 0 })
    expect(result.drivers).toEqual({ total: 0, available: 0, withActivity: 0 })
    expect(result.health).toEqual({ modulesUp: 5, modulesTotal: 5 })
  })

  it('classifies proposal statuses correctly, including lapsed', async () => {
    mockedFetchDrivers.mockResolvedValue([driver('d1')])
    mockedFetchOrders.mockResolvedValue([order('o1', 'SUBMITTED')])
    mockedFetchProposalsForDriver.mockResolvedValue([
      proposal({ proposalId: 'p1', orderId: 'o1', driverId: 'd1', status: 'LAPSED' }),
    ])
    mockedFetchAssignmentsForOrder.mockResolvedValue([])

    const result = await collectPilotAnalyticsInput(OWNER_CREDENTIAL, UP_HEALTHS)

    expect(result.proposals.lapsed).toBe(1)
    expect(result.proposals.total).toBe(1)
    expect(result.drivers.withActivity).toBe(1)
  })

  it('excludes reaction-time samples with missing timestamps, and includes valid ones', async () => {
    mockedFetchDrivers.mockResolvedValue([driver('d1')])
    mockedFetchOrders.mockResolvedValue([])
    mockedFetchProposalsForDriver.mockResolvedValue([
      // ACCEPTED but missing respondedAt -- must not be counted.
      proposal({ proposalId: 'p1', orderId: 'o1', driverId: 'd1', status: 'ACCEPTED', createdAt: '2026-08-17T10:00:00Z', respondedAt: null }),
      // ACCEPTED with both timestamps -- 5 real minutes.
      proposal({
        proposalId: 'p2',
        orderId: 'o2',
        driverId: 'd1',
        status: 'ACCEPTED',
        createdAt: '2026-08-17T10:00:00Z',
        respondedAt: '2026-08-17T10:05:00Z',
      }),
      // still OPEN -- never counted regardless of timestamps.
      proposal({ proposalId: 'p3', orderId: 'o3', driverId: 'd1', status: 'OPEN', createdAt: '2026-08-17T10:00:00Z', respondedAt: null }),
    ])
    mockedFetchAssignmentsForOrder.mockResolvedValue([])

    const result = await collectPilotAnalyticsInput(OWNER_CREDENTIAL, UP_HEALTHS)

    expect(result.reactionTime.sampleSize).toBe(1)
    expect(result.reactionTime.averageMinutes).toBeCloseTo(5)
    expect(result.reactionTime.medianMinutes).toBeCloseTo(5)
  })

  it('reports sampleSize 0 and null average/median when no proposal has both timestamps', async () => {
    mockedFetchDrivers.mockResolvedValue([])
    mockedFetchOrders.mockResolvedValue([])

    const result = await collectPilotAnalyticsInput(OWNER_CREDENTIAL, UP_HEALTHS)

    expect(result.reactionTime).toEqual({ averageMinutes: null, medianMinutes: null, sampleSize: 0 })
  })

  it('never throws when a fan-out source fails -- degrades that source to empty instead', async () => {
    mockedFetchDrivers.mockResolvedValue([driver('d1')])
    mockedFetchOrders.mockRejectedValue(new Error('network error'))
    mockedFetchProposalsForDriver.mockResolvedValue([])

    const result = await collectPilotAnalyticsInput(OWNER_CREDENTIAL, UP_HEALTHS)

    expect(result.orders).toEqual({ total: 0, completed: 0, cancelled: 0, open: 0 })
  })

  // --- isTest (Owner Control Center test/production data separation, 2026-08-17) ---
  // AI Advisor must stay consistent with what Owner Control Center's own
  // counters/EventFeed already exclude -- both read the same fetch functions.

  it('excludes a test driver and a test order from the metrics fed to the AI Advisor', async () => {
    mockedFetchDrivers.mockResolvedValue([driver('d-real', 'AVAILABLE', false), driver('d-e2e', 'AVAILABLE', true)])
    mockedFetchOrders.mockResolvedValue([order('o-real', 'COMPLETED', false), order('o-e2e', 'COMPLETED', true)])
    mockedFetchProposalsForDriver.mockResolvedValue([])

    const result = await collectPilotAnalyticsInput(OWNER_CREDENTIAL, UP_HEALTHS)

    expect(result.drivers.total).toBe(1)
    expect(result.orders).toEqual({ total: 1, completed: 1, cancelled: 0, open: 0 })
    // Only the real driver was ever fanned out to for its own proposals.
    expect(mockedFetchProposalsForDriver).toHaveBeenCalledTimes(1)
    expect(mockedFetchProposalsForDriver).toHaveBeenCalledWith('d-real', OWNER_CREDENTIAL)
  })

  it('excludes a test proposal from proposal/reaction-time metrics, even for a production driver', async () => {
    mockedFetchDrivers.mockResolvedValue([driver('d-real')])
    mockedFetchOrders.mockResolvedValue([])
    mockedFetchProposalsForDriver.mockResolvedValue([
      proposal({
        proposalId: 'p-e2e',
        orderId: 'o-e2e',
        driverId: 'd-real',
        status: 'ACCEPTED',
        createdAt: '2026-08-17T10:00:00Z',
        respondedAt: '2026-08-17T10:05:00Z',
        isTest: true,
      }),
    ])

    const result = await collectPilotAnalyticsInput(OWNER_CREDENTIAL, UP_HEALTHS)

    expect(result.proposals).toEqual({ total: 0, accepted: 0, declined: 0, lapsed: 0, withdrawn: 0, open: 0 })
    expect(result.reactionTime.sampleSize).toBe(0)
    // A test-only-accepted proposal never contributes an order id to the assignments fan-out.
    expect(mockedFetchAssignmentsForOrder).not.toHaveBeenCalled()
  })

  // --- PIOS Intelligence Trend Context v1 (2026-08-17) ---

  it('includes daily history built from the same already-loaded data, without changing the ALL-PERIOD current snapshot fields', async () => {
    mockedFetchDrivers.mockResolvedValue([driver('d1')])
    mockedFetchOrders.mockResolvedValue([
      { ...order('o-today', 'COMPLETED'), createdAt: daysAgoIso(0) },
      { ...order('o-yesterday', 'COMPLETED'), createdAt: daysAgoIso(1) },
    ])
    mockedFetchProposalsForDriver.mockResolvedValue([])

    const result = await collectPilotAnalyticsInput(OWNER_CREDENTIAL, UP_HEALTHS)

    // ALL-PERIOD/cumulative snapshot still counts every order regardless of day -- unchanged by this task.
    expect(result.orders.total).toBe(2)
    expect(result.history).toHaveLength(1)
    expect(result.history[0].date).toBe(localDateKey(1))
    expect(result.history[0].orders).toEqual({ total: 1, completed: 1, cancelled: 0, open: 0 })
  })

  // --- Trend Context data-quality fix: overall/currentDay/history/live separation (2026-08-17) ---

  it('includes a currentDay snapshot, separate from both the ALL-PERIOD snapshot and history', async () => {
    mockedFetchDrivers.mockResolvedValue([driver('d1')])
    mockedFetchOrders.mockResolvedValue([
      { ...order('o-today', 'COMPLETED'), createdAt: daysAgoIso(0) },
      { ...order('o-yesterday', 'COMPLETED'), createdAt: daysAgoIso(1) },
    ])
    mockedFetchProposalsForDriver.mockResolvedValue([])

    const result = await collectPilotAnalyticsInput(OWNER_CREDENTIAL, UP_HEALTHS)

    expect(result.orders.total).toBe(2) // ALL-PERIOD: both orders
    expect(result.currentDay).not.toBeNull()
    expect(result.currentDay!.orders).toEqual({ total: 1, completed: 1, cancelled: 0, open: 0 }) // CURRENT DAY: only today's
    expect(result.history).toHaveLength(1) // HISTORICAL DAILY: only yesterday's
    expect(result.history[0].orders.total).toBe(1)
  })

  it('reports currentDay as null (not a fabricated snapshot) when nothing has a dated timestamp for today', async () => {
    mockedFetchDrivers.mockResolvedValue([driver('d1')])
    mockedFetchOrders.mockResolvedValue([order('o-undated', 'COMPLETED')])
    mockedFetchProposalsForDriver.mockResolvedValue([])

    const result = await collectPilotAnalyticsInput(OWNER_CREDENTIAL, UP_HEALTHS)

    expect(result.currentDay).toBeNull()
  })
})
