import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ModuleHealth } from './healthPoll'
import type { DriverListItem, OrderListItem, ProposalListItem } from './todayData'

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
})
