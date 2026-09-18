import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { DriverHome } from './DriverHome'
import { ApiError, request } from '../../api/apiClient'

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

// ADR-073 (Driver-to-Driver Referral -- Single-Hop Origin Fact), Part 3:
// renders the same `DriverHome` component at the new driver-facing
// `/d/:inviterDriverCode` route, exactly as `routes.tsx` wires it -- a
// plain `<MemoryRouter>` with no `<Route>` never resolves `useParams()`,
// so this needs the real route table shape, not [renderDriverHome]'s own.
function renderDriverHomeAtInviteRoute(inviterDriverCode: string) {
  return render(
    <MemoryRouter initialEntries={[`/d/${inviterDriverCode}`]}>
      <Routes>
        <Route path="/d/:inviterDriverCode" element={<DriverHome />} />
      </Routes>
    </MemoryRouter>
  )
}

function seedIdentityWithNoDriver() {
  localStorage.setItem(
    'pios.identity',
    JSON.stringify({
      identityId: 'identity-1',
      driverId: null,
      token: 'test-token',
      expiresAt: '2099-01-01T00:00:00.000Z',
    })
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

  // E-001 (docs/PIOS_PRODUCT_EVIDENCE.md): a real iPhone registration
  // failed with a misleading "проверьте связь с интернетом" message that
  // was actually a silent backend 400 on an invalid phone format. This
  // guards the fix: an invalid format is now caught before any request is
  // made, with an accurate message.
  it('rejects an invalid phone format before ever calling the backend, with an accurate message', async () => {
    localStorage.clear() // no seeded identity -- this test needs the real welcome/auth screen
    mockedRequest.mockReset()

    renderDriverHome()

    await userEvent.click(await screen.findByRole('button', { name: 'Начать' }))
    await userEvent.type(screen.getByLabelText('Номер телефона'), '89991234567')
    await userEvent.type(screen.getByLabelText('Пароль'), 'password123')

    await userEvent.click(screen.getByRole('button', { name: 'Создать аккаунт' }))

    expect(await screen.findByText(/международном формате/)).toBeInTheDocument()
    expect(mockedRequest).not.toHaveBeenCalled()
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

  // --- Phone verification (ADR-082, D-03.2) ---

  it('offers to verify the phone on Профиль when the account is not yet verified, and hides it once verified', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1', phoneVerified: false })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })

    renderDriverHome()
    await userEvent.click(await screen.findByRole('tab', { name: 'Профиль' }))

    expect(await screen.findByText('Подтвердите номер телефона')).toBeInTheDocument()
  })

  it('does not offer to verify the phone once the account is already verified', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1', phoneVerified: true })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })

    renderDriverHome()
    await userEvent.click(await screen.findByRole('tab', { name: 'Профиль' }))
    await screen.findByRole('heading', { name: 'Профиль' }) // wait for the real render, not the loading state

    expect(screen.queryByText('Подтвердите номер телефона')).not.toBeInTheDocument()
  })

  it('a full request-then-confirm phone verification updates the stored identity and hides the card', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1', phoneVerified: false })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })

    renderDriverHome()
    await userEvent.click(await screen.findByRole('tab', { name: 'Профиль' }))
    await screen.findByText('Подтвердите номер телефона')

    mockedRequest.mockResolvedValueOnce({}) // POST /v1/identities/me/phone/verify/request
    await userEvent.click(screen.getByRole('button', { name: 'Отправить код по SMS' }))
    await screen.findByLabelText('Код из SMS')

    const requestCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/identities/me/phone/verify/request')
    expect(requestCall).toBeDefined()
    expect((requestCall?.[1] as RequestInit).headers as Record<string, string>).toMatchObject({
      Authorization: `Bearer ${TEST_IDENTITY.token}`,
    })

    await userEvent.type(screen.getByLabelText('Код из SMS'), '123456')
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1', phoneVerified: true })
    await userEvent.click(screen.getByRole('button', { name: 'Подтвердить' }))

    expect(await screen.findByText('Номер подтверждён.')).toBeInTheDocument()
    const confirmCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/identities/me/phone/verify/confirm')
    expect(JSON.parse((confirmCall?.[1] as RequestInit).body as string)).toEqual({ code: '123456' })
    expect(localStorage.getItem('pios.identity')).toContain('"phoneVerified":true')
  })

  it('a wrong code on phone verification shows a generic error', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1', phoneVerified: false })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })

    renderDriverHome()
    await userEvent.click(await screen.findByRole('tab', { name: 'Профиль' }))
    await screen.findByText('Подтвердите номер телефона')

    mockedRequest.mockResolvedValueOnce({})
    await userEvent.click(screen.getByRole('button', { name: 'Отправить код по SMS' }))
    await userEvent.type(await screen.findByLabelText('Код из SMS'), '000000')

    mockedRequest.mockRejectedValueOnce(new ApiError(401, '/v1/identities/me/phone/verify/confirm'))
    await userEvent.click(screen.getByRole('button', { name: 'Подтвердить' }))

    expect(await screen.findByText('Код неверен, устарел или уже использован. Запросите новый код.')).toBeInTheDocument()
  })

  // --- Vehicle (PIOS Group and Long-Distance Rides Roadmap, Stage 1) ---

  it('sends this driver\'s own Bearer token and the entered fields on POST /v1/drivers/:id/vehicle', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })

    renderDriverHome()
    await userEvent.click(await screen.findByRole('tab', { name: 'Профиль' }))

    await userEvent.type(await screen.findByLabelText('Марка'), 'Lada')
    await userEvent.type(screen.getByLabelText('Модель'), 'Vesta')
    await userEvent.type(screen.getByLabelText('Количество мест'), '4')

    mockedRequest.mockResolvedValueOnce({
      id: 'driver-1',
      availability: 'AVAILABLE',
      displayName: 'Иван',
      vehicleMake: 'Lada',
      vehicleModel: 'Vesta',
      vehicleSeatCount: 4,
    })
    await userEvent.click(screen.getByRole('button', { name: 'Сохранить машину' }))

    const vehicleCall = await vi.waitUntil(() =>
      mockedRequest.mock.calls.find(([path]) => path === '/v1/drivers/driver-1/vehicle')
    )
    expect(vehicleCall).toBeDefined()
    const init = vehicleCall?.[1] as RequestInit
    expect((init.headers as Record<string, string>).Authorization).toBe(`Bearer ${TEST_IDENTITY.token}`)
    const body = JSON.parse(init.body as string)
    expect(body.make).toBe('Lada')
    expect(body.model).toBe('Vesta')
    expect(body.seatCount).toBe(4)
  })

  // --- Long-distance preference (PIOS Group and Long-Distance Rides Roadmap, Stage 3) ---

  it('sends this driver\'s own Bearer token and the toggled value on POST /v1/drivers/:id/long-distance-preference', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван', acceptsLongDistanceTrips: false })

    renderDriverHome()
    await userEvent.click(await screen.findByRole('tab', { name: 'Профиль' }))

    mockedRequest.mockResolvedValueOnce({
      id: 'driver-1',
      availability: 'AVAILABLE',
      displayName: 'Иван',
      acceptsLongDistanceTrips: true,
    })
    await userEvent.click(await screen.findByLabelText('Беру дальние поездки (вахта, аэропорт, другой город)'))

    const call = await vi.waitUntil(() =>
      mockedRequest.mock.calls.find(([path]) => path === '/v1/drivers/driver-1/long-distance-preference')
    )
    expect(call).toBeDefined()
    const init = call?.[1] as RequestInit
    expect((init.headers as Record<string, string>).Authorization).toBe(`Bearer ${TEST_IDENTITY.token}`)
    const body = JSON.parse(init.body as string)
    expect(body.accepts).toBe(true)
  })

  // --- Order query authorization (ADR-060) ---

  it('sends this driver\'s own Bearer token on both /v1/proposals?driverId= and the chained /v1/orders?ids=', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p1', orderId: 'o1', driverId: 'driver-1', status: 'OPEN', statedPrice: null, statedEtaMinutes: null },
    ])
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 0, currentStreakWeeks: 0 }) // GET /v1/drivers/driver-1/milestones
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers/driver-1/clients
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/orders?ids=o1

    renderDriverHome()

    // Business tabs / bottom nav (product owner request, 2026-09-07): open
    // proposals now live under the "Работа" bottom-nav tab, not the default
    // "Главное".
    await userEvent.click(await screen.findByRole('tab', { name: 'Работа' }))
    await screen.findByLabelText('Когда сможете приехать?')

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

    await userEvent.click(await screen.findByRole('tab', { name: 'Работа' }))
    await screen.findByText('Пока нет заказов. Как только клиент оформит поездку, она появится здесь.')

    expect(mockedRequest.mock.calls.some(([path]) => (path as string).startsWith('/v1/orders'))).toBe(false)
  })

  // --- Stated time to pickup (ADR-057) ---

  it('offers the fixed ETA choices for an open proposal, and sending "Предложить цену" includes the chosen value', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' }) // GET /v1/identities/me
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' }) // GET /v1/drivers/driver-1
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p1', orderId: 'o1', driverId: 'driver-1', status: 'OPEN', statedPrice: null, statedEtaMinutes: null },
    ]) // GET /v1/proposals?driverId=driver-1
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections?driverId=driver-1 (fired right after proposals, before it resolves)
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 0, currentStreakWeeks: 0 }) // GET /v1/drivers/driver-1/milestones
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers/driver-1/clients
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/orders?ids=o1 (chained after proposals resolves -- ADR-060)

    renderDriverHome()

    await userEvent.click(await screen.findByRole('tab', { name: 'Работа' }))
    const select = await screen.findByLabelText('Когда сможете приехать?')
    expect(screen.getByRole('option', { name: '5 мин' })).toBeInTheDocument()

    await userEvent.selectOptions(select, '5')

    // Product Owner instruction, 2026-09-05: a price is now mandatory --
    // the button stays disabled (and clicking it does nothing) without one.
    const proposeButton = screen.getByRole('button', { name: 'Предложить цену' })
    expect(proposeButton).toBeDisabled()
    await userEvent.type(screen.getByLabelText('Ваша цена'), '350')
    expect(proposeButton).toBeEnabled()

    mockedRequest.mockResolvedValueOnce({
      proposalId: 'p1',
      orderId: 'o1',
      driverId: 'driver-1',
      status: 'PRICE_PROPOSED',
      statedPrice: '350',
      statedEtaMinutes: 5,
    })
    await userEvent.click(proposeButton)

    const proposeCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/proposals/p1/propose-price')
    expect(proposeCall).toBeDefined()
    const init = proposeCall?.[1] as RequestInit
    expect(JSON.parse(init.body as string)).toEqual({ statedPrice: '350', statedEtaMinutes: 5 })
    // Task 21 (Proposal API Security Remediation): still requires this
    // driver's own Bearer token, verified server-side against the
    // proposal's own named driver.
    expect((init.headers as Record<string, string>).Authorization).toBe(`Bearer ${TEST_IDENTITY.token}`)
  })

  // Product Owner instruction, 2026-09-05: once a price is proposed, the
  // driver has nothing left to do until the passenger decides -- no
  // Accept/Decline pair, just the waiting state and the stated price.
  //
  // D-06 (Settlement as Evidence), Scenario A: no Assignment/Trip exists
  // yet for a PRICE_PROPOSED proposal, so `proposal.statedPrice` is the
  // only fact on record and is what this test proves gets shown.
  it('shows a PRICE_PROPOSED request as waiting on the client, with no driver action available', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      {
        proposalId: 'p1',
        orderId: 'o1',
        driverId: 'driver-1',
        status: 'PRICE_PROPOSED',
        statedPrice: '350',
        statedEtaMinutes: null,
      },
    ])
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 0, currentStreakWeeks: 0 }) // GET /v1/drivers/driver-1/milestones
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers/driver-1/clients
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/orders?ids=o1

    renderDriverHome()

    await userEvent.click(await screen.findByRole('tab', { name: 'Работа' }))
    expect(await screen.findByText('Ожидает решения клиента')).toBeInTheDocument()
    expect(screen.getByText('Стоимость: 350')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Предложить цену' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Отклонить' })).not.toBeInTheDocument()
  })

  // --- Proposal API security (Task 21: Proposal API Security Remediation) ---

  it('sends this driver\'s own Bearer token on POST /v1/proposals/:id/decline', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p2', orderId: 'o2', driverId: 'driver-1', status: 'OPEN', statedPrice: null, statedEtaMinutes: null },
    ])
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 0, currentStreakWeeks: 0 }) // GET /v1/drivers/driver-1/milestones
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers/driver-1/clients
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/orders?ids=o2

    renderDriverHome()

    await userEvent.click(await screen.findByRole('tab', { name: 'Работа' }))
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
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 0, currentStreakWeeks: 0 }) // GET /v1/drivers/driver-1/milestones
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers/driver-1/clients
    mockedRequest.mockResolvedValueOnce([
      { assignmentId: 'a1', orderId: 'o3', driverId: 'driver-1', status: 'CREATED', statusChangedAt: null },
    ]) // GET /v1/assignments?orderIds=o3
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/orders?ids=o3

    renderDriverHome()

    await userEvent.click(await screen.findByRole('tab', { name: 'Работа' }))
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

  // --- Ride completion confirmation (UX audit, docs/PIOS_DRIVER_HOME_UX_AUDIT.md Section 5/9) ---

  it('shows a brief, honest confirmation when a ride is completed, before the card disappears', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p4', orderId: 'o4', driverId: 'driver-1', status: 'ACCEPTED', statedPrice: null, statedEtaMinutes: null },
    ])
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 0, currentStreakWeeks: 0 }) // GET /v1/drivers/driver-1/milestones
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers/driver-1/clients
    mockedRequest.mockResolvedValueOnce([
      { assignmentId: 'a4', orderId: 'o4', driverId: 'driver-1', status: 'IN_PROGRESS', statusChangedAt: null },
    ]) // GET /v1/assignments?orderIds=o4
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/orders?ids=o4

    renderDriverHome()

    await userEvent.click(await screen.findByRole('tab', { name: 'Работа' }))
    await screen.findByRole('button', { name: 'Завершить поездку' })

    mockedRequest.mockResolvedValueOnce({
      assignmentId: 'a4',
      orderId: 'o4',
      driverId: 'driver-1',
      status: 'COMPLETED',
      statusChangedAt: '2026-08-16T09:30:00Z',
    })
    await userEvent.click(screen.getByRole('button', { name: 'Завершить поездку' }))

    expect(await screen.findByText('Поездка завершена')).toBeInTheDocument()
  })

  it('shows the driver\'s own stated ETA once a proposal is accepted', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p1', orderId: 'o1', driverId: 'driver-1', status: 'ACCEPTED', statedPrice: null, statedEtaMinutes: 7 },
    ])
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections (fired right after proposals, before it resolves)
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 0, currentStreakWeeks: 0 }) // GET /v1/drivers/driver-1/milestones
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers/driver-1/clients
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/assignments?orderIds=o1 (loadAssignments, chained after proposals resolves)
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/orders?ids=o1 (chained after proposals resolves -- ADR-060)

    renderDriverHome()

    await userEvent.click(await screen.findByRole('tab', { name: 'Работа' }))
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
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 0, currentStreakWeeks: 0 }) // GET /v1/drivers/driver-1/milestones
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers/driver-1/clients
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/orders?ids=o1 (chained after proposals resolves -- ADR-060)

    renderDriverHome()

    await userEvent.click(await screen.findByRole('tab', { name: 'Работа' }))
    expect(await screen.findByText('Отменено пассажиром')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Предложить цену' })).not.toBeInTheDocument()
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
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 0, currentStreakWeeks: 0 }) // GET /v1/drivers/driver-1/milestones
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers/driver-1/clients
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

    await userEvent.click(await screen.findByRole('tab', { name: 'Работа' }))
    expect(await screen.findByText(/Предварительный заказ/)).toBeInTheDocument()
  })

  it('shows no requested pickup time badge for an immediate order', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p1', orderId: 'o1', driverId: 'driver-1', status: 'OPEN', statedPrice: null, statedEtaMinutes: null },
    ])
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections (fired right after proposals, before it resolves)
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 0, currentStreakWeeks: 0 }) // GET /v1/drivers/driver-1/milestones
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers/driver-1/clients
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

    await userEvent.click(await screen.findByRole('tab', { name: 'Работа' }))
    await screen.findByText('Куда: Аэропорт Уфа')
    expect(screen.queryByText(/Предварительный заказ/)).not.toBeInTheDocument()
  })

  // --- Ride notes (Product Cycle: Passenger Ride Requirements) ---

  it("shows the passenger's ride notes before the driver names a price", async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p1', orderId: 'o1', driverId: 'driver-1', status: 'OPEN', statedPrice: null, statedEtaMinutes: null },
    ])
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections (fired right after proposals, before it resolves)
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 0, currentStreakWeeks: 0 }) // GET /v1/drivers/driver-1/milestones
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers/driver-1/clients
    mockedRequest.mockResolvedValueOnce([
      {
        id: 'o1',
        origin: 'passenger-1',
        destination: 'Аэропорт Уфа',
        passengerName: 'Аня',
        createdAt: '2026-08-15T10:00:00Z',
        pickupAddress: 'Агидель',
        requestedPickupAt: null,
        notes: 'Детское кресло, встретить у подъезда',
      },
    ]) // GET /v1/orders?ids=o1 (chained after proposals resolves -- ADR-060)

    renderDriverHome()

    await userEvent.click(await screen.findByRole('tab', { name: 'Работа' }))
    const notesText = await screen.findByText('Пожелания: Детское кресло, встретить у подъезда')
    const priceField = screen.getByLabelText('Ваша цена')
    // "до блока цены" (Product Cycle's own explicit requirement): the
    // notes line's DOM position precedes the price input's, so a driver
    // reading top-to-bottom sees it before deciding on a price.
    expect(
      notesText.compareDocumentPosition(priceField) & Node.DOCUMENT_POSITION_FOLLOWING
    ).toBeTruthy()
  })

  it('shows no ride-notes block when the order has none', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p1', orderId: 'o1', driverId: 'driver-1', status: 'OPEN', statedPrice: null, statedEtaMinutes: null },
    ])
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections (fired right after proposals, before it resolves)
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 0, currentStreakWeeks: 0 }) // GET /v1/drivers/driver-1/milestones
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers/driver-1/clients
    mockedRequest.mockResolvedValueOnce([
      {
        id: 'o1',
        origin: 'passenger-1',
        destination: 'Аэропорт Уфа',
        passengerName: 'Аня',
        createdAt: '2026-08-15T10:00:00Z',
        pickupAddress: 'Агидель',
        requestedPickupAt: null,
        notes: null,
      },
    ]) // GET /v1/orders?ids=o1 (chained after proposals resolves -- ADR-060)

    renderDriverHome()

    await userEvent.click(await screen.findByRole('tab', { name: 'Работа' }))
    await screen.findByText('Куда: Аэропорт Уфа')
    expect(screen.queryByText(/Пожелания/)).not.toBeInTheDocument()
  })

  // --- Minimal In-Ride Messaging (Product Cycle) ---

  it("shows the passenger's message on an open proposal, before the driver names a price", async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p1', orderId: 'o1', driverId: 'driver-1', status: 'OPEN', statedPrice: null, statedEtaMinutes: null },
    ])
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections (fired right after proposals, before it resolves)
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 0, currentStreakWeeks: 0 }) // GET /v1/drivers/driver-1/milestones
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers/driver-1/clients
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/orders?ids=o1 (chained after proposals resolves -- ADR-060)
    mockedRequest.mockResolvedValueOnce([
      { id: 'm1', senderRole: 'PASSENGER', body: 'Встречайте у второго подъезда', sentAt: '2026-09-14T10:00:00Z' },
    ]) // GET /v1/proposals/p1/messages

    renderDriverHome()

    await userEvent.click(await screen.findByRole('tab', { name: 'Работа' }))

    const messageText = await screen.findByText('Пассажир: Встречайте у второго подъезда')
    const priceField = screen.getByLabelText('Ваша цена')
    // "до блока цены" -- same requirement this cycle's own passenger-notes
    // block already established: the message appears above the price input.
    expect(messageText.compareDocumentPosition(priceField) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('lets the driver send a reply, which then appears in the thread', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p1', orderId: 'o1', driverId: 'driver-1', status: 'OPEN', statedPrice: null, statedEtaMinutes: null },
    ])
    mockedRequest.mockResolvedValueOnce([])
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 0, currentStreakWeeks: 0 })
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers/driver-1/clients
    mockedRequest.mockResolvedValueOnce([])
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/proposals/p1/messages -- nothing yet

    renderDriverHome()

    await userEvent.click(await screen.findByRole('tab', { name: 'Работа' }))
    await userEvent.type(await screen.findByLabelText('Ответ пассажиру'), 'Уже еду, буду через 5 минут')

    mockedRequest.mockResolvedValueOnce({
      id: 'm2',
      senderRole: 'DRIVER',
      body: 'Уже еду, буду через 5 минут',
      sentAt: '2026-09-14T10:05:00Z',
    }) // POST /v1/proposals/p1/messages

    await userEvent.click(screen.getByRole('button', { name: 'Отправить' }))

    expect(await screen.findByText('Вы: Уже еду, буду через 5 минут')).toBeInTheDocument()
    const sendCall = mockedRequest.mock.calls.find(
      ([path, options]) => path === '/v1/proposals/p1/messages' && (options as RequestInit | undefined)?.method === 'POST'
    )
    expect(sendCall).toBeDefined()
    const body = JSON.parse((sendCall?.[1] as RequestInit).body as string)
    expect(body.body).toBe('Уже еду, буду через 5 минут')
  })

  it('closes messaging (no reply control) for a declined proposal, but keeps any existing history visible', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p1', orderId: 'o1', driverId: 'driver-1', status: 'DECLINED', statedPrice: null, statedEtaMinutes: null },
    ])
    mockedRequest.mockResolvedValueOnce([])
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 0, currentStreakWeeks: 0 })
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers/driver-1/clients
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/orders?ids=o1
    mockedRequest.mockResolvedValueOnce([
      { id: 'm1', senderRole: 'PASSENGER', body: 'Ещё здесь?', sentAt: '2026-09-14T10:00:00Z' },
    ]) // GET /v1/proposals/p1/messages

    renderDriverHome()

    await userEvent.click(await screen.findByRole('tab', { name: 'Работа' }))

    expect(await screen.findByText('Пассажир: Ещё здесь?')).toBeInTheDocument()
    expect(screen.getByText('Обмен сообщениями закрыт.')).toBeInTheDocument()
    expect(screen.queryByLabelText('Ответ пассажиру')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Отправить' })).not.toBeInTheDocument()
  })

  it("does not show another proposal's own messages on this one's card", async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p1', orderId: 'o1', driverId: 'driver-1', status: 'OPEN', statedPrice: null, statedEtaMinutes: null },
      { proposalId: 'p2', orderId: 'o2', driverId: 'driver-1', status: 'OPEN', statedPrice: null, statedEtaMinutes: null },
    ])
    mockedRequest.mockResolvedValueOnce([])
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 0, currentStreakWeeks: 0 })
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers/driver-1/clients
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/orders?ids=o1,o2
    // loadMessages fires one GET per proposal, in the same order as [proposals] --
    // p1's own thread, then p2's own thread.
    mockedRequest.mockResolvedValueOnce([
      { id: 'm1', senderRole: 'PASSENGER', body: 'Для первого заказа', sentAt: '2026-09-14T10:00:00Z' },
    ]) // GET /v1/proposals/p1/messages
    mockedRequest.mockResolvedValueOnce([
      { id: 'm2', senderRole: 'PASSENGER', body: 'Для второго заказа', sentAt: '2026-09-14T10:00:00Z' },
    ]) // GET /v1/proposals/p2/messages

    renderDriverHome()

    await userEvent.click(await screen.findByRole('tab', { name: 'Работа' }))

    expect(await screen.findByText('Пассажир: Для первого заказа')).toBeInTheDocument()
    expect(await screen.findByText('Пассажир: Для второго заказа')).toBeInTheDocument()
  })

  // --- History (MVP completion, §1): "Маршруты" tab, real completed rides ---

  // D-06 (Settlement as Evidence), Scenario B: a committed/completed Trip
  // shows its own `agreedAmount` -- the corrective pass's own fixture
  // (`agreedAmount: '450'`) is what actually proves this now; before
  // that correction this same rendered "450" could equally have come
  // from `proposal.statedPrice`, which is exactly the ambiguity the
  // correction removes (see the two tests directly below, which pin the
  // two values apart).
  it('shows a completed ride in the История tab, built from already-loaded proposal/order/assignment data', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p1', orderId: 'o1', driverId: 'driver-1', status: 'ACCEPTED', statedPrice: '450', statedEtaMinutes: 5 },
    ])
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 1, currentStreakWeeks: 1 }) // GET /v1/drivers/driver-1/milestones
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers/driver-1/clients
    mockedRequest.mockResolvedValueOnce([
      {
        assignmentId: 'a1',
        orderId: 'o1',
        driverId: 'driver-1',
        status: 'COMPLETED',
        statusChangedAt: '2026-09-14T12:30:00Z',
        agreedAmount: '450',
      },
    ]) // GET /v1/assignments?orderIds=o1 -- loadAssignments fires before loadOrderDetails/loadMessages
    mockedRequest.mockResolvedValueOnce([
      {
        id: 'o1',
        origin: 'passenger-1',
        destination: 'ул. Ленина, 10',
        passengerName: 'Мария',
        createdAt: '2026-09-14T12:00:00Z',
        pickupAddress: 'ул. Пушкина, 5',
        requestedPickupAt: null,
      },
    ]) // GET /v1/orders?ids=o1
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/proposals/p1/messages

    renderDriverHome()

    await userEvent.click(await screen.findByRole('tab', { name: 'Бизнес' }))
    await userEvent.click(await screen.findByRole('tab', { name: 'Маршруты' }))

    expect(await screen.findByText('Мария')).toBeInTheDocument()
    expect(screen.getByText('ул. Пушкина, 5 → ул. Ленина, 10')).toBeInTheDocument()
    expect(screen.getByText('450')).toBeInTheDocument()
    expect(screen.getByText('Завершена')).toBeInTheDocument()
    // The completed ride must not also still show in "Ваши заказы" (Работа) --
    // ADR-040's own "экран освобождается" is unchanged by this feature.
    await userEvent.click(await screen.findByRole('tab', { name: 'Главное' }))
    await userEvent.click(await screen.findByRole('tab', { name: 'Работа' }))
    expect(screen.getByText(/Пока нет заказов\./)).toBeInTheDocument()
  })

  // D-06 (Settlement as Evidence), Scenario C: a historical Trip with
  // `agreedAmount: null` (no backfill, D-06 Decision item 7) must never
  // fall back to `proposal.statedPrice` -- even though the proposal's own
  // stated price ("450") is right there in the same response, it must
  // not appear as the ride's price anywhere on this card.
  it('never substitutes proposal.statedPrice for a historical Trip whose agreedAmount is null', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p1', orderId: 'o1', driverId: 'driver-1', status: 'ACCEPTED', statedPrice: '450', statedEtaMinutes: 5 },
    ])
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 1, currentStreakWeeks: 1 }) // GET /v1/drivers/driver-1/milestones
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers/driver-1/clients
    mockedRequest.mockResolvedValueOnce([
      {
        assignmentId: 'a1',
        orderId: 'o1',
        driverId: 'driver-1',
        status: 'COMPLETED',
        statusChangedAt: '2026-09-14T12:30:00Z',
        agreedAmount: null,
      },
    ]) // GET /v1/assignments?orderIds=o1 -- pre-D-06 Trip: no agreedAmount on record
    mockedRequest.mockResolvedValueOnce([
      {
        id: 'o1',
        origin: 'passenger-1',
        destination: 'ул. Ленина, 10',
        passengerName: 'Мария',
        createdAt: '2026-09-14T12:00:00Z',
        pickupAddress: 'ул. Пушкина, 5',
        requestedPickupAt: null,
      },
    ]) // GET /v1/orders?ids=o1
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/proposals/p1/messages

    renderDriverHome()

    await userEvent.click(await screen.findByRole('tab', { name: 'Бизнес' }))
    await userEvent.click(await screen.findByRole('tab', { name: 'Маршруты' }))

    expect(await screen.findByText('Мария')).toBeInTheDocument()
    // D-06: this card's own honest "not stated" caption, never the
    // proposal's own "450".
    expect(screen.getByText('Цена не указана')).toBeInTheDocument()
    expect(screen.queryByText('450')).not.toBeInTheDocument()
  })

  // D-06, Scenario D: with neither an agreedAmount nor a statedPrice
  // anywhere on record, the UI must stay stable -- an honest empty state,
  // never a fabricated value -- and the rest of the card still renders.
  it('renders a stable, honest empty state in История when neither agreedAmount nor statedPrice exists', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p1', orderId: 'o1', driverId: 'driver-1', status: 'ACCEPTED', statedPrice: null, statedEtaMinutes: null },
    ])
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 1, currentStreakWeeks: 1 }) // GET /v1/drivers/driver-1/milestones
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers/driver-1/clients
    mockedRequest.mockResolvedValueOnce([
      {
        assignmentId: 'a1',
        orderId: 'o1',
        driverId: 'driver-1',
        status: 'COMPLETED',
        statusChangedAt: '2026-09-14T12:30:00Z',
        agreedAmount: null,
      },
    ]) // GET /v1/assignments?orderIds=o1 -- manual assignment, no Proposal ever involved
    mockedRequest.mockResolvedValueOnce([
      {
        id: 'o1',
        origin: 'passenger-1',
        destination: 'ул. Ленина, 10',
        passengerName: 'Мария',
        createdAt: '2026-09-14T12:00:00Z',
        pickupAddress: 'ул. Пушкина, 5',
        requestedPickupAt: null,
      },
    ]) // GET /v1/orders?ids=o1
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/proposals/p1/messages

    renderDriverHome()

    await userEvent.click(await screen.findByRole('tab', { name: 'Бизнес' }))
    await userEvent.click(await screen.findByRole('tab', { name: 'Маршруты' }))

    expect(await screen.findByText('Мария')).toBeInTheDocument()
    expect(screen.getByText('ул. Пушкина, 5 → ул. Ленина, 10')).toBeInTheDocument()
    expect(screen.getByText('Завершена')).toBeInTheDocument()
    expect(screen.getByText('Цена не указана')).toBeInTheDocument()
  })

  it('shows an honest empty state in История when nothing has been completed yet', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([]) // no proposals at all
    mockedRequest.mockResolvedValueOnce([])
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 0, currentStreakWeeks: 0 })

    renderDriverHome()

    await userEvent.click(await screen.findByRole('tab', { name: 'Бизнес' }))
    await userEvent.click(await screen.findByRole('tab', { name: 'Маршруты' }))

    expect(await screen.findByText(/Здесь появятся ваши завершённые поездки/)).toBeInTheDocument()
  })

  it('shows an error state in История with a working retry when the underlying request fails', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockRejectedValueOnce(new Error('network error'))
    mockedRequest.mockResolvedValueOnce([])
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 0, currentStreakWeeks: 0 })

    renderDriverHome()

    await userEvent.click(await screen.findByRole('tab', { name: 'Бизнес' }))
    await userEvent.click(await screen.findByRole('tab', { name: 'Маршруты' }))

    expect(await screen.findByText('Не удалось загрузить историю. Проверьте соединение.')).toBeInTheDocument()

    mockedRequest.mockResolvedValueOnce([]) // retry succeeds with nothing yet
    await userEvent.click(screen.getByRole('button', { name: 'Повторить' }))

    expect(await screen.findByText(/Здесь появятся ваши завершённые поездки/)).toBeInTheDocument()
  })

  // --- Referral visibility (ADR-064): lifetime clients via the driver's own link ---

  it("shows the lifetime count of clients who connected through this driver's own link, not just today's", async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/proposals
    mockedRequest.mockResolvedValueOnce([
      { passengerReference: 'passenger-1', createdAt: '2026-07-01T10:00:00Z' },
      { passengerReference: 'passenger-2', createdAt: '2026-08-15T10:00:00Z' },
      { passengerReference: 'passenger-3', createdAt: new Date().toISOString() },
    ]) // GET /v1/connections -- three ever, only one of them today
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 0, currentStreakWeeks: 0, repeatClientsCount: 0 }) // GET /v1/drivers/driver-1/milestones

    renderDriverHome()

    // Business tabs / bottom nav (product owner request, 2026-09-07): the
    // growth tiles live under the "Бизнес" bottom-nav tab (its own default
    // "Обзор" sub-tab), not the default "Главное".
    await userEvent.click(await screen.findByRole('tab', { name: 'Бизнес' }))
    await screen.findByText('Сегодня')

    const totalRow = screen.getByText('Всего пришло по вашей ссылке').closest('div')
    expect(totalRow).toHaveTextContent('3')

    const todayRow = screen.getByText('Новых клиентов').closest('div')
    expect(todayRow).toHaveTextContent('1')
  })

  // --- Per-client share action (product audit follow-up, 2026-09-12) ---
  // "Мои пассажиры" used to be read-only text with no action. This reuses
  // the exact same personal invite link the QR card above already shares
  // (`invitationProvider.linkFor(driver.id)`) -- no new invitation
  // mechanism, no passenger-specific URL. `navigator.share` is undefined in
  // jsdom, so `handleShare` takes its own documented clipboard fallback.

  it('shares this driver\'s own invite link directly from a client row in "Мои пассажиры"', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/proposals
    mockedRequest.mockResolvedValueOnce([
      { passengerReference: 'passenger-1', createdAt: '2026-07-01T10:00:00Z' },
    ]) // GET /v1/connections
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 0, currentStreakWeeks: 0, repeatClientsCount: 0 }) // GET /v1/drivers/driver-1/milestones

    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })

    renderDriverHome()

    await userEvent.click(await screen.findByRole('tab', { name: 'Бизнес' }))
    await userEvent.click(await screen.findByRole('tab', { name: 'Клиенты' }))
    await screen.findByText('Пассажир по вашей ссылке')

    await userEvent.click(
      screen.getByRole('button', { name: 'Поделиться ссылкой с пассажиром по вашей ссылке' })
    )

    expect(writeText).toHaveBeenCalledWith(`${window.location.origin}/i/driver-1`)
    expect(await screen.findByText('Ссылка скопирована')).toBeInTheDocument()
  })

  // --- Client CRM depth ("Мой бизнес -> Клиенты"): ride count, last-ride
  // date, repeat flag -- server-side read model
  // (docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md Part 5), read from
  // `GET /v1/drivers/:id/clients` rather than derived in the browser.

  it('shows ride count, last-ride date, and a repeat-client flag on the "Клиенты" tab, and a real zero for a client with no completed ride yet', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { proposalId: 'p1', orderId: 'o1', driverId: 'driver-1', status: 'ACCEPTED', statedPrice: '300', statedEtaMinutes: null },
      { proposalId: 'p2', orderId: 'o2', driverId: 'driver-1', status: 'ACCEPTED', statedPrice: '350', statedEtaMinutes: null },
    ]) // GET /v1/proposals
    mockedRequest.mockResolvedValueOnce([
      { passengerReference: 'passenger-1', createdAt: '2026-07-01T10:00:00Z' },
      { passengerReference: 'passenger-2', createdAt: '2026-07-02T10:00:00Z' },
    ]) // GET /v1/connections -- passenger-2 connected but never ordered
    mockedRequest.mockResolvedValueOnce({ completedRidesCount: 2, currentStreakWeeks: 1, repeatClientsCount: 1 }) // GET /v1/drivers/driver-1/milestones
    mockedRequest.mockResolvedValueOnce([
      { passengerReference: 'passenger-1', rideCount: 2, lastRideAt: '2026-09-14T12:30:00Z', isRepeat: true },
    ]) // GET /v1/drivers/driver-1/clients -- server-side read model: passenger-1 is a repeat client, passenger-2 has no entry (zero rides)
    mockedRequest.mockResolvedValueOnce([
      { assignmentId: 'a1', orderId: 'o1', driverId: 'driver-1', status: 'COMPLETED', statusChangedAt: '2026-09-10T09:00:00Z' },
      { assignmentId: 'a2', orderId: 'o2', driverId: 'driver-1', status: 'COMPLETED', statusChangedAt: '2026-09-14T12:30:00Z' },
    ]) // GET /v1/assignments?orderIds=o1,o2 -- two completed rides, both for passenger-1
    mockedRequest.mockResolvedValueOnce([
      {
        id: 'o1',
        origin: 'passenger-1',
        destination: null,
        passengerName: 'Мария',
        createdAt: '2026-09-10T08:00:00Z',
        pickupAddress: null,
        requestedPickupAt: null,
      },
      {
        id: 'o2',
        origin: 'passenger-1',
        destination: null,
        passengerName: 'Мария',
        createdAt: '2026-09-14T12:00:00Z',
        pickupAddress: null,
        requestedPickupAt: null,
      },
    ]) // GET /v1/orders?ids=o1,o2
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/proposals/p1/messages
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/proposals/p2/messages

    renderDriverHome()

    await userEvent.click(await screen.findByRole('tab', { name: 'Бизнес' }))
    await userEvent.click(await screen.findByRole('tab', { name: 'Клиенты' }))

    // passenger-1: named (order carries passengerName), two completed
    // rides together -- repeat client, latest ride's own date shown.
    expect(await screen.findByText('Мария')).toBeInTheDocument()
    expect(screen.getByText(/Поездок: 2/)).toBeInTheDocument()
    expect(screen.getByText(/Постоянный клиент/)).toBeInTheDocument()
    // Not asserting the exact formatted date text (timezone-dependent,
    // same reason the existing "История" test above never does either) --
    // only that a last-ride date was actually rendered for this passenger.
    expect(screen.getByText(/Последняя поездка/)).toBeInTheDocument()

    // passenger-2: a real connection with zero completed rides -- shown as
    // an honest zero, not hidden or skipped.
    expect(screen.getByText('Пассажир по вашей ссылке')).toBeInTheDocument()
    expect(screen.getByText('Поездок: 0')).toBeInTheDocument()
  })

  // --- PIOS Install v1 (Product Owner exception) ---

  it('shows the "PIOS всегда под рукой" install card alongside, never instead of, "Как это работает"', async () => {
    localStorage.setItem('pios.onboarding.driver-seen', 'true')
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: 'driver-1' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/proposals
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections

    renderDriverHome()

    // Bottom nav (product owner request, 2026-09-07): "Как это работает"
    // now lives under "Профиль", alongside the install card, not the
    // default "Главное".
    await userEvent.click(await screen.findByRole('tab', { name: 'Профиль' }))
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
    await userEvent.click(await screen.findByRole('tab', { name: 'Профиль' }))

    await userEvent.click(screen.getAllByRole('button', { name: 'Установить PIOS' })[0])
    expect(await screen.findByRole('button', { name: 'Закрыть' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Закрыть' }))
    expect(screen.queryByRole('button', { name: 'Закрыть' })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Профиль' })).toBeInTheDocument()
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
    await userEvent.click(await screen.findByRole('tab', { name: 'Профиль' }))

    await userEvent.click(screen.getByText('Как это работает'))
    expect(await screen.findByText('Как работает PIOS')).toBeInTheDocument()
    await userEvent.click(screen.getByText('Пропустить'))

    expect(screen.queryByRole('button', { name: 'Закрыть' })).not.toBeInTheDocument()
  })

  // --- ADR-073: Driver-to-Driver Referral -- Single-Hop Origin Fact ---

  it('reads the inviter code from /d/:inviterDriverCode and sends it as invitedByDriverId on POST /v1/drivers', async () => {
    localStorage.clear()
    seedIdentityWithNoDriver()
    mockedRequest.mockReset()
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: null }) // GET /v1/identities/me -- no driver linked yet

    renderDriverHomeAtInviteRoute('inviter-driver-1')

    await userEvent.type(await screen.findByLabelText('Ваше имя'), 'Новый водитель')

    mockedRequest.mockResolvedValueOnce({}) // POST /v1/drivers
    mockedRequest.mockResolvedValueOnce({
      identityId: 'identity-1',
      driverId: 'generated-driver-id',
      token: 'test-token-2',
      expiresAt: '2099-01-01T00:00:00.000Z',
    }) // POST /v1/identities/:id/driver (attachDriver)
    mockedRequest.mockResolvedValue([]) // every subsequent poll/list call on the now-ready screen

    await userEvent.click(screen.getByRole('button', { name: 'Создать профиль' }))

    const createCall = await vi.waitUntil(() => mockedRequest.mock.calls.find(([path]) => path === '/v1/drivers'))
    expect(createCall).toBeDefined()
    const body = JSON.parse((createCall?.[1] as RequestInit).body as string)
    expect(body.displayName).toBe('Новый водитель')
    expect(body.invitedByDriverId).toBe('inviter-driver-1')
  })

  it('omits invitedByDriverId on POST /v1/drivers when registering through the plain "/" route (no inviter)', async () => {
    localStorage.clear()
    seedIdentityWithNoDriver()
    mockedRequest.mockReset()
    mockedRequest.mockResolvedValueOnce({ id: 'identity-1', phone: '+70000000000', driverId: null }) // GET /v1/identities/me

    renderDriverHome()

    await userEvent.type(await screen.findByLabelText('Ваше имя'), 'Другой водитель')

    mockedRequest.mockResolvedValueOnce({}) // POST /v1/drivers
    mockedRequest.mockResolvedValueOnce({
      identityId: 'identity-1',
      driverId: 'generated-driver-id-2',
      token: 'test-token-3',
      expiresAt: '2099-01-01T00:00:00.000Z',
    }) // POST /v1/identities/:id/driver
    mockedRequest.mockResolvedValue([])

    await userEvent.click(screen.getByRole('button', { name: 'Создать профиль' }))

    const createCall = await vi.waitUntil(() => mockedRequest.mock.calls.find(([path]) => path === '/v1/drivers'))
    expect(createCall).toBeDefined()
    const body = JSON.parse((createCall?.[1] as RequestInit).body as string)
    expect(body.displayName).toBe('Другой водитель')
    expect(body.invitedByDriverId).toBeUndefined()
  })
})
