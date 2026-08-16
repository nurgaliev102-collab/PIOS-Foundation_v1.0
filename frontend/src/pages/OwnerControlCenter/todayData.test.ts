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
})
