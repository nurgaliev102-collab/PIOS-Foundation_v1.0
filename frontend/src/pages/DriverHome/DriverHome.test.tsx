import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { DriverHome } from './DriverHome'
import { request } from '../../api/apiClient'

// ADR-057 (Driver Stated Time to Pickup) / ADR-058 (Scheduled Pickup Time):
// this screen had no test file at all before these two features -- this
// covers only the new behavior they add, mirroring RideRequest.test.tsx's
// own mocking convention (the only other page test in this codebase that
// exercises a real Dispatch/Order Management flow).
vi.mock('../../api/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/apiClient')>()
  return {
    ...actual,
    request: vi.fn(),
  }
})

const mockedRequest = vi.mocked(request)

const TEST_IDENTITY = {
  identityId: 'identity-1',
  driverId: 'driver-1',
  token: 'test-token',
  expiresAt: '2099-01-01T00:00:00.000Z',
}

function seedIdentity() {
  localStorage.setItem('pios.identity', JSON.stringify(TEST_IDENTITY))
}

function renderDriverHome() {
  return render(
    <MemoryRouter>
      <DriverHome />
    </MemoryRouter>
  )
}

describe('DriverHome', () => {
  beforeEach(() => {
    localStorage.clear()
    mockedRequest.mockReset()
    mockedRequest.mockResolvedValue([])
    seedIdentity()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  // --- Driver availability security (Task 25: Orders Cancellation & Driver Availability Security Remediation) ---

  it('sends this driver\'s own Bearer token on POST /v1/drivers/:id/availability', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'UNAVAILABLE', displayName: 'Иван' })

    renderDriverHome()

    const toggleButton = await screen.findByRole('button', { name: 'Выйти на линию' })

    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE' })
    await userEvent.click(toggleButton)

    const availabilityCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/drivers/driver-1/availability')
    expect(availabilityCall).toBeDefined()
    expect((availabilityCall?.[1] as RequestInit).headers as Record<string, string>).toMatchObject({
      Authorization: `Bearer ${TEST_IDENTITY.token}`,
    })
  })

  // --- Order query authorization (ADR-060) ---

  it('sends this driver\'s own Bearer token on both /v1/proposals?driverId= and the chained /v1/orders?ids=', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p1', orderId: 'o1', driverId: 'driver-1', status: 'OPEN', statedPrice: null, statedEtaMinutes: null },
    ])
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/orders?ids=o1

    renderDriverHome()

    await screen.findByLabelText('Через сколько вы приедете')

    const proposalsCall = mockedRequest.mock.calls.find(([path]) => (path as string).startsWith('/v1/proposals?driverId='))
    expect(proposalsCall).toBeDefined()
    expect((proposalsCall?.[1] as RequestInit).headers).toMatchObject({ Authorization: 'Bearer test-token' })

    const ordersCall = mockedRequest.mock.calls.find(([path]) => (path as string).startsWith('/v1/orders?ids='))
    expect(ordersCall).toBeDefined()
    expect(ordersCall?.[0]).toBe('/v1/orders?ids=o1')
    expect((ordersCall?.[1] as RequestInit).headers).toMatchObject({ Authorization: 'Bearer test-token' })
  })

  it('does not call GET /v1/orders at all when there are no proposals -- no unscoped fetch remains', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/proposals?driverId=driver-1 -- no proposals
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections

    renderDriverHome()

    await screen.findByText('Пока нет заказов. Как только клиент оформит поездку, она появится здесь.')

    expect(mockedRequest.mock.calls.some(([path]) => (path as string).startsWith('/v1/orders'))).toBe(false)
  })

  // --- Stated time to pickup (ADR-057) ---

  it('offers the fixed ETA choices for an open proposal, and sending "Принять" includes the chosen value', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' }) // GET /v1/identities/me
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' }) // GET /v1/drivers/driver-1
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p1', orderId: 'o1', driverId: 'driver-1', status: 'OPEN', statedPrice: null, statedEtaMinutes: null },
    ]) // GET /v1/proposals?driverId=driver-1
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections?driverId=driver-1 (fired right after proposals, before it resolves)
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/orders?ids=o1 (chained after proposals resolves -- ADR-060)

    renderDriverHome()

    const select = await screen.findByLabelText('Через сколько вы приедете')
    expect(screen.getByRole('option', { name: '5 мин' })).toBeInTheDocument()

    await userEvent.selectOptions(select, '5')

    mockedRequest.mockResolvedValueOnce({
      proposalId: 'p1',
      orderId: 'o1',
      driverId: 'driver-1',
      status: 'ACCEPTED',
      statedPrice: null,
      statedEtaMinutes: 5,
    })
    await userEvent.click(screen.getByRole('button', { name: 'Принять' }))

    const acceptCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/proposals/p1/accept')
    expect(acceptCall).toBeDefined()
    const init = acceptCall?.[1] as RequestInit
    expect(JSON.parse(init.body as string)).toEqual({ statedEtaMinutes: 5 })
    // Task 21 (Proposal API Security Remediation): accept now requires
    // this driver's own Bearer token, verified server-side against the
    // proposal's own named driver.
    expect((init.headers as Record<string, string>).Authorization).toBe(`Bearer ${TEST_IDENTITY.token}`)
  })

  // --- Proposal API security (Task 21: Proposal API Security Remediation) ---

  it('sends this driver\'s own Bearer token on POST /v1/proposals/:id/decline', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p2', orderId: 'o2', driverId: 'driver-1', status: 'OPEN', statedPrice: null, statedEtaMinutes: null },
    ])
    mockedRequest.mockResolvedValueOnce([])
    mockedRequest.mockResolvedValueOnce([])

    renderDriverHome()

    await screen.findByRole('button', { name: 'Отклонить' })

    mockedRequest.mockResolvedValueOnce({
      proposalId: 'p2',
      orderId: 'o2',
      driverId: 'driver-1',
      status: 'DECLINED',
      statedPrice: null,
      statedEtaMinutes: null,
    })
    await userEvent.click(screen.getByRole('button', { name: 'Отклонить' }))

    const declineCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/proposals/p2/decline')
    expect(declineCall).toBeDefined()
    const init = declineCall?.[1] as RequestInit
    expect((init.headers as Record<string, string>).Authorization).toBe(`Bearer ${TEST_IDENTITY.token}`)
  })

  // --- Assignment API security (Task 23: Assignment API Security Remediation) ---

  it('sends this driver\'s own Bearer token on POST /v1/assignments/:id/arrive', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p3', orderId: 'o3', driverId: 'driver-1', status: 'ACCEPTED', statedPrice: null, statedEtaMinutes: null },
    ])
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections
    mockedRequest.mockResolvedValueOnce([
      { assignmentId: 'a1', orderId: 'o3', driverId: 'driver-1', status: 'CREATED', statusChangedAt: null },
    ]) // GET /v1/assignments?orderId=o3
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/orders?ids=o3

    renderDriverHome()

    await screen.findByRole('button', { name: 'Прибыл' })

    mockedRequest.mockResolvedValueOnce({
      assignmentId: 'a1',
      orderId: 'o3',
      driverId: 'driver-1',
      status: 'ARRIVED',
      statusChangedAt: '2026-08-16T09:03:00Z',
    })
    await userEvent.click(screen.getByRole('button', { name: 'Прибыл' }))

    const arriveCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/assignments/a1/arrive')
    expect(arriveCall).toBeDefined()
    const init = arriveCall?.[1] as RequestInit
    expect((init.headers as Record<string, string>).Authorization).toBe(`Bearer ${TEST_IDENTITY.token}`)
  })

  it('shows the driver\'s own stated ETA once a proposal is accepted', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p1', orderId: 'o1', driverId: 'driver-1', status: 'ACCEPTED', statedPrice: null, statedEtaMinutes: 7 },
    ])
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections (fired right after proposals, before it resolves)
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/assignments?orderId=o1 (loadAssignments, chained after proposals resolves)
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/orders?ids=o1 (chained after proposals resolves -- ADR-060)

    renderDriverHome()

    expect(await screen.findByText('Будет примерно через: 7 мин')).toBeInTheDocument()
  })

  // --- Cancellation (P0-2 Tier 1; ADR-053) ---

  it('shows a withdrawn proposal (passenger cancelled) as its own distinct, non-actionable status', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p1', orderId: 'o1', driverId: 'driver-1', status: 'WITHDRAWN', statedPrice: null, statedEtaMinutes: null },
    ])
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections (fired right after proposals, before it resolves)
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/orders?ids=o1 (chained after proposals resolves -- ADR-060)

    renderDriverHome()

    expect(await screen.findByText('Отменено пассажиром')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Принять' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Отклонить' })).not.toBeInTheDocument()
  })

  // --- Scheduled pickup time (ADR-058) ---

  it('shows the requested pickup time badge for a scheduled order', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p1', orderId: 'o1', driverId: 'driver-1', status: 'OPEN', statedPrice: null, statedEtaMinutes: null },
    ])
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections (fired right after proposals, before it resolves)
    mockedRequest.mockResolvedValueOnce([
      {
        id: 'o1',
        origin: 'passenger-1',
        destination: 'Аэропорт Уфа',
        passengerName: 'Аня',
        createdAt: '2026-08-15T10:00:00Z',
        pickupAddress: 'Агидель',
        requestedPickupAt: '2026-08-25T06:30:00Z',
      },
    ]) // GET /v1/orders?ids=o1 (chained after proposals resolves -- ADR-060)

    renderDriverHome()

    expect(await screen.findByText(/Предварительный заказ/)).toBeInTheDocument()
  })

  it('shows no requested pickup time badge for an immediate order', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p1', orderId: 'o1', driverId: 'driver-1', status: 'OPEN', statedPrice: null, statedEtaMinutes: null },
    ])
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections (fired right after proposals, before it resolves)
    mockedRequest.mockResolvedValueOnce([
      {
        id: 'o1',
        origin: 'passenger-1',
        destination: 'Аэропорт Уфа',
        passengerName: 'Аня',
        createdAt: '2026-08-15T10:00:00Z',
        pickupAddress: 'Агидель',
        requestedPickupAt: null,
      },
    ]) // GET /v1/orders?ids=o1 (chained after proposals resolves -- ADR-060)

    renderDriverHome()

    await screen.findByText('Куда: Аэропорт Уфа')
    expect(screen.queryByText(/Предварительный заказ/)).not.toBeInTheDocument()
  })

  // --- PIOS Install v1 (Product Owner exception) ---

  it('shows the "PIOS всегда под рукой" install card alongside, never instead of, "Как это работает"', async () => {
    localStorage.setItem('pios.onboarding.driver-seen', 'true')
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/proposals
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections

    renderDriverHome()

    await screen.findByText('Мой бизнес')
    expect(screen.getByText('Как это работает')).toBeInTheDocument()
    expect(screen.getByText('PIOS всегда под рукой')).toBeInTheDocument()
    expect(screen.getByText('Добавьте PIOS на экран телефона.')).toBeInTheDocument()
  })

  it('opens the install overlay from the card, and closes it back to the real screen', async () => {
    localStorage.setItem('pios.onboarding.driver-seen', 'true')
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([])
    mockedRequest.mockResolvedValueOnce([])

    renderDriverHome()
    await screen.findByText('Мой бизнес')

    await userEvent.click(screen.getAllByRole('button', { name: 'Установить PIOS' })[0])
    expect(await screen.findByRole('button', { name: 'Закрыть' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Закрыть' }))
    expect(screen.queryByRole('button', { name: 'Закрыть' })).not.toBeInTheDocument()
    expect(screen.getByText('Мой бизнес')).toBeInTheDocument()
  })

  it('chains into the install overlay once, right after this driver\'s very first onboarding completion', async () => {
    // Neither onboarding nor install-help has been seen yet -- the very first ready render.
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([])
    mockedRequest.mockResolvedValueOnce([])

    renderDriverHome()

    expect(await screen.findByText('Как работает PIOS')).toBeInTheDocument() // onboarding auto-shown
    await userEvent.click(screen.getByText('Пропустить'))

    expect(await screen.findByRole('button', { name: 'Закрыть' })).toBeInTheDocument() // install chained in next
  })

  it('does not chain into install after a manual "Как это работает" replay (only after the true first time)', async () => {
    localStorage.setItem('pios.onboarding.driver-seen', 'true')
    localStorage.setItem('pios.install.help-seen', 'true')
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([])
    mockedRequest.mockResolvedValueOnce([])

    renderDriverHome()
    await screen.findByText('Мой бизнес')

    await userEvent.click(screen.getByText('Как это работает'))
    expect(await screen.findByText('Как работает PIOS')).toBeInTheDocument()
    await userEvent.click(screen.getByText('Пропустить'))

    expect(screen.queryByRole('button', { name: 'Закрыть' })).not.toBeInTheDocument()
  })
})
