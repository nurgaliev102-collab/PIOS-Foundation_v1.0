import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { request } from '../../api/apiClient'
import { loadTodaySnapshot } from './todayData'

// ADR-061 (Coordinator Owner-Gated Access) Decision 2 extracted this
// module's inline fetch calls into named exports (`fetchDrivers`,
// `fetchOrders`, `fetchProposalsForDriver`, `fetchProposalsForOrder`,
// `fetchAssignmentsForOrder`) so `Coordinator.tsx` can reuse them. This
// covers the one thing that refactor must not change: `loadTodaySnapshot`'s
// own behaviour for `OwnerControlCenter.tsx` -- which calls get the owner's
// `Authorization: Basic` credential (ADR-060 Decisions 4-5) and which don't,
// and that a failure on any one source still yields a snapshot rather than
// throwing (ADR-043's "a partial event feed is more useful than none").
vi.mock('../../api/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/apiClient')>()
  return {
    ...actual,
    request: vi.fn(),
  }
})

const mockedRequest = vi.mocked(request)
const OWNER_CREDENTIAL = { username: 'owner', password: 'secret' }
const EXPECTED_BASIC_HEADER = `Basic ${btoa('owner:secret')}`

describe('loadTodaySnapshot', () => {
  beforeEach(() => {
    mockedRequest.mockReset()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('sends the owner Basic credential on GET /v1/orders and GET /v1/proposals?driverId=, and no header on GET /v1/drivers', async () => {
    mockedRequest.mockResolvedValueOnce([
      { id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван', registeredAt: null },
    ]) // GET /v1/drivers
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/orders
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/proposals?driverId=driver-1

    await loadTodaySnapshot(OWNER_CREDENTIAL)

    const driversCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/drivers')
    expect((driversCall?.[1] as RequestInit | undefined)?.headers).toBeUndefined()

    const ordersCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/orders')
    expect((ordersCall?.[1] as RequestInit).headers).toMatchObject({ Authorization: EXPECTED_BASIC_HEADER })

    const proposalsCall = mockedRequest.mock.calls.find(([path]) => (path as string).startsWith('/v1/proposals?driverId='))
    expect((proposalsCall?.[1] as RequestInit).headers).toMatchObject({ Authorization: EXPECTED_BASIC_HEADER })
  })

  it('still returns a snapshot, not a rejection, when GET /v1/orders fails (e.g. a stale credential)', async () => {
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers
    mockedRequest.mockRejectedValueOnce(new Error('401')) // GET /v1/orders

    const snapshot = await loadTodaySnapshot(OWNER_CREDENTIAL)

    expect(snapshot.counters.ordersCreated).toBe(0)
    expect(snapshot.events).toEqual([])
  })

  // 2026-08-17 incident: this session's own accumulated test data left 240
  // driver records in the live database, and the driver-proposals fan-out
  // below used to fire all of them as one unbounded `Promise.all` burst,
  // sharing the same HTTP/2 connection as the five health checks -- proven
  // live via Playwright against the public Funnel to starve those health
  // requests past their own 10s client-side timeout. This asserts the fix's
  // actual contract: bounded concurrency, not a request count or a specific
  // batch size (an implementation detail this test does not pin down).
  it('never has more than a bounded number of GET /v1/proposals?driverId= requests in flight at once, however many drivers exist', async () => {
    const driverCount = 75
    const drivers = Array.from({ length: driverCount }, (_, i) => ({
      id: `driver-${i}`,
      availability: 'AVAILABLE' as const,
      displayName: null,
      registeredAt: null,
    }))

    let inFlight = 0
    let maxInFlight = 0
    mockedRequest.mockImplementation(async (path: string) => {
      if (path === '/v1/drivers') return drivers
      if (path === '/v1/orders') return []
      if ((path as string).startsWith('/v1/proposals?driverId=')) {
        inFlight++
        maxInFlight = Math.max(maxInFlight, inFlight)
        await new Promise((resolve) => setTimeout(resolve, 1))
        inFlight--
        return []
      }
      return []
    })

    await loadTodaySnapshot(OWNER_CREDENTIAL)

    expect(maxInFlight).toBeGreaterThan(0)
    expect(maxInFlight).toBeLessThan(driverCount)
    expect(maxInFlight).toBeLessThanOrEqual(5)
  })

  // --- isTest (Owner Control Center test/production data separation, 2026-08-17) ---
  //
  // Never filters by displayName/passengerName/id text -- every fixture
  // below uses ordinary-looking names on purpose, so a name-based heuristic
  // would get these wrong; only the backend's own `isTest` field decides.

  it('counts a production driver in driversTotal/driversAvailable', async () => {
    mockedRequest.mockResolvedValueOnce([
      { id: 'driver-real', availability: 'AVAILABLE', displayName: 'Иван', registeredAt: null, isTest: false },
    ]) // GET /v1/drivers
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/orders
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/proposals?driverId=driver-real

    const snapshot = await loadTodaySnapshot(OWNER_CREDENTIAL)

    expect(snapshot.counters.driversTotal).toBe(1)
    expect(snapshot.counters.driversAvailable).toBe(1)
  })

  it('excludes a test driver from driversTotal/driversAvailable, and never fans out to it for proposals', async () => {
    mockedRequest.mockResolvedValueOnce([
      { id: 'driver-e2e', availability: 'AVAILABLE', displayName: 'Test Driver', registeredAt: null, isTest: true },
    ]) // GET /v1/drivers
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/orders

    const snapshot = await loadTodaySnapshot(OWNER_CREDENTIAL)

    expect(snapshot.counters.driversTotal).toBe(0)
    expect(snapshot.counters.driversAvailable).toBe(0)
    expect(mockedRequest.mock.calls.some(([path]) => (path as string).startsWith('/v1/proposals?driverId='))).toBe(false)
  })

  it('counts a production order created today in ordersCreated', async () => {
    const today = new Date().toISOString()
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers
    mockedRequest.mockResolvedValueOnce([
      { id: 'order-real', status: 'SUBMITTED', origin: 'passenger-1', destination: null, passengerName: 'Мария', createdAt: today, pickupAddress: null, requestedPickupAt: null, isTest: false },
    ]) // GET /v1/orders

    const snapshot = await loadTodaySnapshot(OWNER_CREDENTIAL)

    expect(snapshot.counters.ordersCreated).toBe(1)
    expect(snapshot.events.some((event) => event.text.includes('Мария'))).toBe(true)
  })

  it('excludes a test order created today from ordersCreated and from EventFeed', async () => {
    const today = new Date().toISOString()
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers
    mockedRequest.mockResolvedValueOnce([
      { id: 'order-e2e', status: 'COMPLETED', origin: 'e2e-passenger', destination: null, passengerName: 'Lifecycle E2E Passenger', createdAt: today, pickupAddress: null, requestedPickupAt: null, isTest: true },
    ]) // GET /v1/orders

    const snapshot = await loadTodaySnapshot(OWNER_CREDENTIAL)

    expect(snapshot.counters.ordersCreated).toBe(0)
    expect(snapshot.counters.ordersCompleted).toBe(0)
    expect(snapshot.events.some((event) => event.text.includes('Lifecycle E2E Passenger'))).toBe(false)
  })

  it('excludes a test proposal from EventFeed even for a production driver', async () => {
    const now = new Date().toISOString()
    mockedRequest.mockResolvedValueOnce([
      { id: 'driver-real-2', availability: 'AVAILABLE', displayName: 'Артур', registeredAt: null, isTest: false },
    ]) // GET /v1/drivers
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/orders
    mockedRequest.mockResolvedValueOnce([
      {
        proposalId: 'proposal-e2e',
        orderId: 'order-x',
        driverId: 'driver-real-2',
        status: 'ACCEPTED',
        statedPrice: null,
        statedEtaMinutes: null,
        createdAt: now,
        respondedAt: now,
        isTest: true,
      },
    ]) // GET /v1/proposals?driverId=driver-real-2

    const snapshot = await loadTodaySnapshot(OWNER_CREDENTIAL)

    expect(snapshot.events.some((event) => event.text.includes('принял заказ'))).toBe(false)
    // A test proposal never contributes an order id to the assignments fan-out either.
    expect(mockedRequest.mock.calls.some(([path]) => (path as string).startsWith('/v1/assignments?orderId='))).toBe(false)
  })

  it('excludes a test assignment from EventFeed while a production event for the same poll still appears', async () => {
    const now = new Date().toISOString()
    mockedRequest.mockResolvedValueOnce([
      { id: 'driver-real-3', availability: 'AVAILABLE', displayName: 'Самира', registeredAt: now, isTest: false },
    ]) // GET /v1/drivers
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/orders
    mockedRequest.mockResolvedValueOnce([
      {
        proposalId: 'proposal-real',
        orderId: 'order-real-2',
        driverId: 'driver-real-3',
        status: 'ACCEPTED',
        statedPrice: null,
        statedEtaMinutes: null,
        createdAt: now,
        respondedAt: now,
        isTest: false,
      },
    ]) // GET /v1/proposals?driverId=driver-real-3
    mockedRequest.mockResolvedValueOnce([
      {
        assignmentId: 'assignment-e2e',
        orderId: 'order-real-2',
        driverId: 'driver-real-3',
        status: 'COMPLETED',
        arrivedAt: now,
        startedAt: now,
        completedAt: now,
        isTest: true,
      },
    ]) // GET /v1/assignments?orderId=order-real-2

    const snapshot = await loadTodaySnapshot(OWNER_CREDENTIAL)

    // The real driver's own registration event still appears -- production
    // data is never suppressed just because a test assignment shares its poll.
    expect(snapshot.events.some((event) => event.text.includes('Самира') && event.text.includes('новый водитель'))).toBe(true)
    expect(snapshot.events.some((event) => event.text.includes('завершил поездку'))).toBe(false)
  })
})
