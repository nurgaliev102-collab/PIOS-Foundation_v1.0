import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { RideRequest } from './RideRequest'
import { getCurrentOrderId, saveCurrentOrderId } from '../../persistence/localCurrentOrder'
import { ApiError, request } from '../../api/apiClient'

// Sprint 6 (Passenger Entry-Path Failure Handling): same defect as
// `PassengerLanding.test.tsx` -- this screen's own `getInvitationByDriverCode`
// call also had no `.catch`, leaving `step` stuck at `'loading'` forever on
// any transient failure. Covers the unchanged 404 outcome and the new,
// distinct transient-failure outcome (error + working retry).
vi.mock('../../api/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/apiClient')>()
  return {
    ...actual,
    request: vi.fn(),
  }
})

const mockedRequest = vi.mocked(request)

// ADR-055 ("Final Pre-Pilot Sprint"): this screen now requires a real,
// backend-verified session before showing anything -- `BackendIdentityProvider`
// has no test-only seam, so this writes the exact shape it itself persists
// under its own storage key ('pios.identity'), the same way `saveCurrentOrderId`
// below writes under its own key directly. [seedIdentity] replaces the old
// `savePassengerIdentity('Аня')` call this suite used before real accounts
// existed.
const TEST_IDENTITY = {
  identityId: 'passenger-1',
  driverId: null,
  token: 'test-token',
  expiresAt: '2099-01-01T00:00:00.000Z',
}

function seedIdentity() {
  localStorage.setItem('pios.identity', JSON.stringify(TEST_IDENTITY))
}

/** Every render now opens with `restoreIdentity()`'s own `GET /v1/identities/me` call -- queue its response first, before any test-specific mock. */
function mockMeResponse() {
  mockedRequest.mockResolvedValueOnce({ id: TEST_IDENTITY.identityId, phone: '+70000000000', driverId: null })
}

function renderAt(driverCode: string) {
  return render(
    <MemoryRouter initialEntries={[`/i/${driverCode}/request`]}>
      <Routes>
        <Route path="/i/:driverCode/request" element={<RideRequest />} />
        <Route path="/i/:driverCode" element={<div>passenger-landing-screen</div>} />
        <Route path="/me" element={<div>my-drivers-screen</div>} />
      </Routes>
    </MemoryRouter>
  )
}

describe('RideRequest', () => {
  beforeEach(() => {
    localStorage.clear()
    mockedRequest.mockReset()
    // ADR-058: every confirmed-order render now also fires a best-effort
    // `GET /v1/orders` fetch (for `requestedPickupAt`) that most of these
    // tests never explicitly queue a response for -- a low-priority default
    // so that extra call resolves to an empty list, exactly like a genuine
    // best-effort failure would, rather than `undefined` (mockReset's own
    // default), which is not a Promise. Any test-specific `mockResolvedValueOnce`
    // queued below still takes priority over this default.
    mockedRequest.mockResolvedValue([])
    // This screen redirects to Passenger Landing if no session exists yet --
    // these tests exercise the invitation-loading step itself, which
    // requires a returning, authenticated passenger to already be present.
    seedIdentity()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows the existing invalid-link message on a genuine 404, unchanged', async () => {
    mockMeResponse()
    mockedRequest.mockRejectedValueOnce(new ApiError(404, '/v1/drivers/unknown-driver'))

    renderAt('unknown-driver')

    expect(
      await screen.findByText(
        'Ссылка недействительна или водитель ещё не зарегистрирован. Уточните ссылку у водителя, который вас пригласил.'
      )
    ).toBeInTheDocument()
  })

  it('shows a distinct connection-error state (not "link invalid") on a transient failure, and recovers on retry', async () => {
    mockMeResponse()
    mockedRequest.mockRejectedValueOnce(new Error('network down'))

    renderAt('driver-1')

    expect(await screen.findByText(/Не удалось загрузить приглашение/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Попробовать снова' })).toBeInTheDocument()
    // Never collapses into the 404/"link invalid" outcome.
    expect(screen.queryByText(/Ссылка недействительна/)).not.toBeInTheDocument()

    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    // Sprint "My Business + Circle of Trust" (ADR-054): a new order, with no
    // existing order to resume, now also loads this passenger's own circle
    // of trust before showing the form -- an empty circle (no other trusted
    // driver to choose between) skips straight through, same as before this
    // Sprint.
    mockedRequest.mockResolvedValueOnce([])

    await userEvent.click(screen.getByRole('button', { name: 'Попробовать снова' }))

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
  })

  // --- Pickup address (Sprint H5: Entrepreneur Working Cycle Integrity) ---

  it('requires a pickup address, mirroring the existing destination requirement', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    // See the circle-of-trust note above.
    mockedRequest.mockResolvedValueOnce([])

    renderAt('driver-1')

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
    expect(screen.getByLabelText('Откуда')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Заказать поездку' }))

    expect(await screen.findByText('Пожалуйста, укажите адрес.')).toBeInTheDocument()
    // Submission never reached Order Management: still only the three calls
    // from loading (session, invitation, then circle of trust) — no fourth call.
    expect(mockedRequest).toHaveBeenCalledTimes(3)
  })

  // --- Availability on the plain order form (Referral funnel friction audit, 2026-09-12) ---
  // This is the exact path every 0-or-1-relationship referral takes (the
  // circle-of-trust step above is skipped entirely) -- this driver's own
  // live availability used to be fetched and silently discarded, so a
  // brand-new passenger could submit a real order to an offline driver
  // with zero signal beforehand.

  it('shows the driver as unavailable on the order form, with a pointer to scheduling instead of blocking submission', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'UNAVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([])

    renderAt('driver-1')

    expect(await screen.findByText('Недоступен')).toBeInTheDocument()
    expect(screen.getByText(/Вы можете заказать поездку заранее/)).toBeInTheDocument()
    // Not gated, unlike the circle-of-trust step's own per-driver choice --
    // this screen has no alternative driver to offer instead.
    expect(screen.getByRole('button', { name: 'Заказать поездку' })).toBeEnabled()
  })

  it('shows the driver as available on the order form, with no scheduling hint', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([])

    renderAt('driver-1')

    expect(await screen.findByText('Доступен')).toBeInTheDocument()
    expect(screen.queryByText(/Вы можете заказать поездку заранее/)).not.toBeInTheDocument()
  })

  // --- Explicit driver intent (Task 17: First Refusal Explicit Driver Intent Integration) ---

  it('sends explicitDriverIntent: true on POST /v1/orders, since this screen always already knows the driver', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    // Circle of trust, empty -- skips straight to the form (see the existing convention above).
    mockedRequest.mockResolvedValueOnce([])

    renderAt('driver-1')

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Откуда'), 'Агидель')
    await userEvent.type(screen.getByLabelText('Куда'), 'Международный аэропорт Уфа')

    mockedRequest.mockResolvedValueOnce({ orderId: 'order-explicit' }) // POST /v1/orders
    mockedRequest.mockResolvedValueOnce({}) // POST /v1/proposals
    mockedRequest.mockResolvedValue([{ status: 'OPEN' }]) // status poll, from here on

    await userEvent.click(screen.getByRole('button', { name: 'Заказать поездку' }))

    expect(await screen.findByText('✅ Заказ оформлен.')).toBeInTheDocument()

    const submitCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/orders')
    expect(submitCall).toBeDefined()
    const body = JSON.parse((submitCall?.[1] as RequestInit).body as string)
    expect(body.explicitDriverIntent).toBe(true)
  })

  // --- Passenger count (PIOS Group and Long-Distance Rides Roadmap, Stage 2) ---

  it('sends passengerCount on POST /v1/orders when the passenger enters one', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([])

    renderAt('driver-1')

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Откуда'), 'Агидель')
    await userEvent.type(screen.getByLabelText('Куда'), 'Международный аэропорт Уфа')
    await userEvent.type(screen.getByLabelText('Сколько пассажиров (необязательно)'), '4')

    mockedRequest.mockResolvedValueOnce({ orderId: 'order-group' }) // POST /v1/orders
    mockedRequest.mockResolvedValueOnce({}) // POST /v1/proposals
    mockedRequest.mockResolvedValue([{ status: 'OPEN' }])

    await userEvent.click(screen.getByRole('button', { name: 'Заказать поездку' }))

    expect(await screen.findByText('✅ Заказ оформлен.')).toBeInTheDocument()

    const submitCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/orders')
    const body = JSON.parse((submitCall?.[1] as RequestInit).body as string)
    expect(body.passengerCount).toBe(4)
  })

  it('omits passengerCount on POST /v1/orders when left blank -- regression for the existing contract', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([])

    renderAt('driver-1')

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Откуда'), 'Агидель')
    await userEvent.type(screen.getByLabelText('Куда'), 'Международный аэропорт Уфа')

    mockedRequest.mockResolvedValueOnce({ orderId: 'order-solo' }) // POST /v1/orders
    mockedRequest.mockResolvedValueOnce({}) // POST /v1/proposals
    mockedRequest.mockResolvedValue([{ status: 'OPEN' }])

    await userEvent.click(screen.getByRole('button', { name: 'Заказать поездку' }))

    expect(await screen.findByText('✅ Заказ оформлен.')).toBeInTheDocument()

    const submitCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/orders')
    const body = JSON.parse((submitCall?.[1] as RequestInit).body as string)
    expect(body.passengerCount).toBeUndefined()
  })

  it('rejects a zero passenger count before ever calling the backend', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([])

    renderAt('driver-1')

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Откуда'), 'Агидель')
    await userEvent.type(screen.getByLabelText('Куда'), 'Международный аэропорт Уфа')
    await userEvent.type(screen.getByLabelText('Сколько пассажиров (необязательно)'), '0')

    await userEvent.click(screen.getByRole('button', { name: 'Заказать поездку' }))

    expect(await screen.findByText('Укажите число больше нуля.')).toBeInTheDocument()
    expect(mockedRequest.mock.calls.some(([path]) => path === '/v1/orders')).toBe(false)
  })

  // --- Notes (Product Cycle: Passenger Ride Requirements) ---

  it('sends notes on POST /v1/orders when the passenger enters some', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([])

    renderAt('driver-1')

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Откуда'), 'Агидель')
    await userEvent.type(screen.getByLabelText('Куда'), 'Международный аэропорт Уфа')
    await userEvent.type(screen.getByLabelText('Пожелания к поездке'), 'Детское кресло, встретить у подъезда')

    mockedRequest.mockResolvedValueOnce({ orderId: 'order-notes' }) // POST /v1/orders
    mockedRequest.mockResolvedValueOnce({}) // POST /v1/proposals
    mockedRequest.mockResolvedValue([{ status: 'OPEN' }])

    await userEvent.click(screen.getByRole('button', { name: 'Заказать поездку' }))

    expect(await screen.findByText('✅ Заказ оформлен.')).toBeInTheDocument()

    const submitCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/orders')
    const body = JSON.parse((submitCall?.[1] as RequestInit).body as string)
    expect(body.notes).toBe('Детское кресло, встретить у подъезда')
  })

  it('omits notes on POST /v1/orders when left blank', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([])

    renderAt('driver-1')

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Откуда'), 'Агидель')
    await userEvent.type(screen.getByLabelText('Куда'), 'Международный аэропорт Уфа')

    mockedRequest.mockResolvedValueOnce({ orderId: 'order-no-notes' }) // POST /v1/orders
    mockedRequest.mockResolvedValueOnce({}) // POST /v1/proposals
    mockedRequest.mockResolvedValue([{ status: 'OPEN' }])

    await userEvent.click(screen.getByRole('button', { name: 'Заказать поездку' }))

    expect(await screen.findByText('✅ Заказ оформлен.')).toBeInTheDocument()

    const submitCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/orders')
    const body = JSON.parse((submitCall?.[1] as RequestInit).body as string)
    expect(body.notes).toBeUndefined()
  })

  it('truncates notes to 500 characters before sending, defensively, even though the field itself caps input at that length', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([])

    renderAt('driver-1')

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Откуда'), 'Агидель')
    await userEvent.type(screen.getByLabelText('Куда'), 'Международный аэропорт Уфа')

    const notesField = screen.getByLabelText('Пожелания к поездке') as HTMLTextAreaElement
    const tooLong = 'a'.repeat(600)
    // fireEvent bypasses the textarea's own `maxLength` truncation (unlike
    // a real keystroke-by-keystroke userEvent.type, which the browser
    // itself would already cap at 500) -- this is exactly the "some caller
    // bypasses the control" case handleSubmit's own defensive slice exists
    // for; see NOTES_MAX_LENGTH's own KDoc in RideRequest.tsx.
    fireEvent.change(notesField, { target: { value: tooLong } })

    mockedRequest.mockResolvedValueOnce({ orderId: 'order-notes-long' }) // POST /v1/orders
    mockedRequest.mockResolvedValueOnce({}) // POST /v1/proposals
    mockedRequest.mockResolvedValue([{ status: 'OPEN' }])

    await userEvent.click(screen.getByRole('button', { name: 'Заказать поездку' }))

    expect(await screen.findByText('✅ Заказ оформлен.')).toBeInTheDocument()

    const submitCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/orders')
    const body = JSON.parse((submitCall?.[1] as RequestInit).body as string)
    expect(body.notes).toHaveLength(500)
  })

  // --- Order provenance / authentication (P0, docs/PIOS_DATA_FLOW_CODE_AUDIT.md Section 5) ---

  it('sends this passenger\'s own Bearer token on POST /v1/orders, since that endpoint now requires authentication', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([])

    renderAt('driver-1')

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Откуда'), 'Агидель')
    await userEvent.type(screen.getByLabelText('Куда'), 'Международный аэропорт Уфа')

    mockedRequest.mockResolvedValueOnce({ orderId: 'order-provenance' }) // POST /v1/orders
    mockedRequest.mockResolvedValueOnce({}) // POST /v1/proposals
    mockedRequest.mockResolvedValue([{ status: 'OPEN' }])

    await userEvent.click(screen.getByRole('button', { name: 'Заказать поездку' }))

    expect(await screen.findByText('✅ Заказ оформлен.')).toBeInTheDocument()

    const submitCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/orders')
    expect(submitCall).toBeDefined()
    const headers = (submitCall?.[1] as RequestInit).headers as Record<string, string>
    expect(headers.Authorization).toBe(`Bearer ${TEST_IDENTITY.token}`)
  })

  // --- Proposal API security (Task 21: Proposal API Security Remediation) ---

  it('sends this passenger\'s own Bearer token on POST /v1/proposals, since that endpoint now requires authentication', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([])

    renderAt('driver-1')

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Откуда'), 'Агидель')
    await userEvent.type(screen.getByLabelText('Куда'), 'Международный аэропорт Уфа')

    mockedRequest.mockResolvedValueOnce({ orderId: 'order-auth' }) // POST /v1/orders
    mockedRequest.mockResolvedValueOnce({}) // POST /v1/proposals
    mockedRequest.mockResolvedValue([{ status: 'OPEN' }])

    await userEvent.click(screen.getByRole('button', { name: 'Заказать поездку' }))

    expect(await screen.findByText('✅ Заказ оформлен.')).toBeInTheDocument()

    const proposalCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/proposals')
    expect(proposalCall).toBeDefined()
    const headers = (proposalCall?.[1] as RequestInit).headers as Record<string, string>
    expect(headers.Authorization).toBe(`Bearer ${TEST_IDENTITY.token}`)
  })

  // --- Proposal Participant Authorization (ADR-066, P0 remediation) ---

  it('sends passengerReference matching this passenger\'s own identity on POST /v1/proposals', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([])

    renderAt('driver-1')

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Откуда'), 'Агидель')
    await userEvent.type(screen.getByLabelText('Куда'), 'Международный аэропорт Уфа')

    mockedRequest.mockResolvedValueOnce({ orderId: 'order-passenger-ref' }) // POST /v1/orders
    mockedRequest.mockResolvedValueOnce({}) // POST /v1/proposals
    mockedRequest.mockResolvedValue([{ status: 'OPEN' }])

    await userEvent.click(screen.getByRole('button', { name: 'Заказать поездку' }))

    expect(await screen.findByText('✅ Заказ оформлен.')).toBeInTheDocument()

    const proposalCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/proposals')
    const body = JSON.parse((proposalCall?.[1] as RequestInit).body as string)
    expect(body.passengerReference).toBe(TEST_IDENTITY.identityId)
  })

  // --- Cancellation (P0-2 Tier 1, docs/SPRINT_PILOT_BLOCKERS.md; ADR-053) ---

  it('offers to cancel an order still waiting for the driver, and does so on confirmation', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'OPEN' }])

    renderAt('driver-1')

    const cancelButton = await screen.findByRole('button', { name: 'Отменить заказ' })

    mockedRequest.mockResolvedValueOnce({ orderId: 'order-1', status: 'CANCELLED' }) // POST /v1/orders/order-1/cancel
    await userEvent.click(cancelButton)

    expect(await screen.findByText('🚫 Вы отменили этот заказ.')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Заказать ещё раз' })).toBeInTheDocument()
    const cancelCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/orders/order-1/cancel')
    expect(cancelCall).toBeDefined()
    expect((cancelCall?.[1] as RequestInit).method).toBe('POST')
    // Task 25 (Orders Cancellation & Driver Availability Security
    // Remediation): cancel now requires this passenger's own Bearer
    // token, verified server-side against the order's own origin.
    expect((cancelCall?.[1] as RequestInit).headers as Record<string, string>).toMatchObject({
      Authorization: `Bearer ${TEST_IDENTITY.token}`,
    })
  })

  it('does not offer to cancel once the driver has already accepted', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED', statedPrice: null, statedEtaMinutes: null }])
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED' }])

    renderAt('driver-1')

    await screen.findByText(/принял ваш заказ/)
    expect(screen.queryByRole('button', { name: 'Отменить заказ' })).not.toBeInTheDocument()
  })

  it('shows an honest cancelled message and "Заказать ещё раз" when a resumed order was already withdrawn', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'WITHDRAWN' }])

    renderAt('driver-1')

    expect(await screen.findByText('🚫 Вы отменили этот заказ.')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Заказать ещё раз' })).toBeInTheDocument()
  })

  // --- Stated time to pickup (ADR-057) ---

  it('shows the driver\'s own stated ETA once the ride is accepted', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED', statedPrice: null, statedEtaMinutes: 5 }])
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED' }]) // GET /v1/assignments?orderId=order-1

    renderAt('driver-1')

    expect(await screen.findByText('Будет примерно через: 5 мин')).toBeInTheDocument()
  })

  it('shows no ETA line when the driver accepted without stating one', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED', statedPrice: null, statedEtaMinutes: null }])
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED' }])

    renderAt('driver-1')

    await screen.findByText(/принял ваш заказ/)
    expect(screen.queryByText(/Будет примерно через/)).not.toBeInTheDocument()
  })

  // --- Scheduled pickup time (ADR-058) ---

  it('lets a passenger schedule a ride for later, sending requestedPickupAt as an ISO string', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    // Circle of trust, empty -- skips straight to the form (see the existing convention above).
    mockedRequest.mockResolvedValueOnce([])

    renderAt('driver-1')

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Откуда'), 'Агидель')
    await userEvent.type(screen.getByLabelText('Куда'), 'Международный аэропорт Уфа')

    await userEvent.selectOptions(screen.getByLabelText('Когда'), 'later')
    fireEvent.change(screen.getByLabelText('Дата и время подачи'), { target: { value: '2026-08-25T06:30' } })

    mockedRequest.mockResolvedValueOnce({ orderId: 'order-scheduled' }) // POST /v1/orders
    mockedRequest.mockResolvedValueOnce({}) // POST /v1/proposals
    mockedRequest.mockResolvedValue([{ status: 'OPEN' }]) // status poll, from here on

    await userEvent.click(screen.getByRole('button', { name: 'Заказать поездку' }))

    expect(await screen.findByText('✅ Заказ оформлен.')).toBeInTheDocument()

    const submitCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/orders')
    expect(submitCall).toBeDefined()
    const body = JSON.parse((submitCall?.[1] as RequestInit).body as string)
    expect(body.requestedPickupAt).toBe(new Date('2026-08-25T06:30').toISOString())
  })

  it('requires a date and time when "Заранее" is chosen', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([])

    renderAt('driver-1')

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Откуда'), 'Агидель')
    await userEvent.type(screen.getByLabelText('Куда'), 'Аэропорт Уфа')
    await userEvent.selectOptions(screen.getByLabelText('Когда'), 'later')

    await userEvent.click(screen.getByRole('button', { name: 'Заказать поездку' }))

    expect(await screen.findByText('Пожалуйста, укажите дату и время.')).toBeInTheDocument()
    // Never reached Order Management.
    expect(mockedRequest.mock.calls.some(([path]) => path === '/v1/orders')).toBe(false)
  })

  it('shows the requested pickup time on the confirmation screen for a resumed scheduled order', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED', statedPrice: null, statedEtaMinutes: null }])
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED' }]) // GET /v1/assignments?orderId=order-1
    mockedRequest.mockResolvedValueOnce([{ id: 'order-1', requestedPickupAt: '2026-08-25T06:30:00Z' }]) // GET /v1/orders?passengerReference=...

    renderAt('driver-1')

    expect(await screen.findByText(/Заказ на:/)).toBeInTheDocument()
  })

  // --- Order query authorization (ADR-060) ---

  it('sends this passenger\'s own Bearer token and passengerReference on GET /v1/orders', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED', statedPrice: null, statedEtaMinutes: null }])
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED' }])
    mockedRequest.mockResolvedValueOnce([{ id: 'order-1', requestedPickupAt: null }])

    renderAt('driver-1')

    await screen.findByText(/принял ваш заказ/)

    const ordersCall = mockedRequest.mock.calls.find(([path]) => (path as string).startsWith('/v1/orders?passengerReference='))
    expect(ordersCall).toBeDefined()
    expect(ordersCall?.[0]).toBe('/v1/orders?passengerReference=passenger-1')
    expect((ordersCall?.[1] as RequestInit).headers).toMatchObject({ Authorization: 'Bearer test-token' })
  })

  // --- Truthful status rendering (Sprint H5: Entrepreneur Working Cycle Integrity) ---
  //
  // Previously every non-ACCEPTED proposal status (OPEN, DECLINED, LAPSED)
  // rendered the same "✅ ... он свяжется с вами" success wording. These
  // cover that a passenger resuming a confirmed order now sees an honest,
  // distinct message for each real status the poll already receives.

  it('shows an honest waiting message for a still-open proposal, not success wording', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'OPEN' }])

    renderAt('driver-1')

    expect(await screen.findByText(/Ждём ответа водителя/)).toBeInTheDocument()
  })

  it('shows an honest declined message when the driver declined, not the old success wording', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'DECLINED' }])

    renderAt('driver-1')

    expect(await screen.findByText(/отклонил ваш заказ/)).toBeInTheDocument()
    expect(screen.queryByText(/свяжется с вами/)).not.toBeInTheDocument()
  })

  it('shows an honest lapsed message when the proposal lapsed, not the old success wording', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'LAPSED' }])

    renderAt('driver-1')

    expect(await screen.findByText(/больше не активен/)).toBeInTheDocument()
    expect(screen.queryByText(/свяжется с вами/)).not.toBeInTheDocument()
  })

  // --- P0-1: "Заказать ещё раз" at a terminal ride state (docs/SPRINT_PILOT_BLOCKERS.md) ---

  it('offers "Заказать ещё раз" when the proposal was declined', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'DECLINED' }])

    renderAt('driver-1')

    expect(await screen.findByRole('button', { name: 'Заказать ещё раз' })).toBeInTheDocument()
  })

  it('offers "Заказать ещё раз" when the proposal lapsed', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'LAPSED' }])

    renderAt('driver-1')

    expect(await screen.findByRole('button', { name: 'Заказать ещё раз' })).toBeInTheDocument()
  })

  // --- Repeat Ride (Product Cycle) ---
  // The COMPLETED-specific repeat action reads [circle] -- populated only
  // by [loadCircleThenAdvance] (a brand-new order), never re-fetched when
  // resuming an already-placed one (see that function's own KDoc for the
  // one disclosed edge case this accepts) -- so, unlike this file's other
  // COMPLETED tests, these two go through the full fresh-order flow
  // (circle-of-trust fetch -> form -> submit -> poll to COMPLETED) rather
  // than the `saveCurrentOrderId` resume shortcut, to get real circle-of-
  // trust membership in place before the ride completes.

  it('offers "Заказать у этого водителя" when the ride completed and this driver is already in the circle of trust', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { connectionId: 'c1', driverId: 'driver-1', createdAt: '2026-08-01T00:00:00Z', isPrimary: true },
    ])
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' }) // enrichCircle's own per-member lookup

    renderAt('driver-1')

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Откуда'), 'Агидель')
    await userEvent.type(screen.getByLabelText('Куда'), 'Международный аэропорт Уфа')

    mockedRequest.mockResolvedValueOnce({ orderId: 'order-1' }) // POST /v1/orders
    mockedRequest.mockResolvedValueOnce({}) // POST /v1/proposals
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED' }]) // status poll
    mockedRequest.mockResolvedValueOnce([{ status: 'COMPLETED' }]) // GET /v1/assignments?orderId=order-1

    await userEvent.click(screen.getByRole('button', { name: 'Заказать поездку' }))

    expect(await screen.findByRole('button', { name: 'Заказать у этого водителя' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Повторить поездку' })).not.toBeInTheDocument()
  })

  it('offers "Повторить поездку" when the ride completed and this driver is not in the circle of trust', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([]) // circle-of-trust: no relationships at all

    renderAt('driver-1')

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Откуда'), 'Агидель')
    await userEvent.type(screen.getByLabelText('Куда'), 'Международный аэропорт Уфа')

    mockedRequest.mockResolvedValueOnce({ orderId: 'order-1' }) // POST /v1/orders
    mockedRequest.mockResolvedValueOnce({}) // POST /v1/proposals
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED' }]) // status poll
    mockedRequest.mockResolvedValueOnce([{ status: 'COMPLETED' }]) // GET /v1/assignments?orderId=order-1

    await userEvent.click(screen.getByRole('button', { name: 'Заказать поездку' }))

    expect(await screen.findByRole('button', { name: 'Повторить поездку' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Заказать у этого водителя' })).not.toBeInTheDocument()
  })

  // --- Repeat Client Loop (Product Cycle) ---

  it('does not offer "Добавить в мои водители" once this driver is already in the circle of trust', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { connectionId: 'c1', driverId: 'driver-1', createdAt: '2026-08-01T00:00:00Z', isPrimary: true },
    ])
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })

    renderAt('driver-1')

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Откуда'), 'Агидель')
    await userEvent.type(screen.getByLabelText('Куда'), 'Международный аэропорт Уфа')

    mockedRequest.mockResolvedValueOnce({ orderId: 'order-1' })
    mockedRequest.mockResolvedValueOnce({})
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED' }])
    mockedRequest.mockResolvedValueOnce([{ status: 'COMPLETED' }])

    await userEvent.click(screen.getByRole('button', { name: 'Заказать поездку' }))

    await screen.findByRole('button', { name: 'Заказать у этого водителя' })
    expect(screen.queryByRole('button', { name: 'Добавить в мои водители' })).not.toBeInTheDocument()
  })

  it('saving a driver with no existing primary creates the connection and sets it primary, then the repeat label updates', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([]) // circle-of-trust: no relationships at all

    renderAt('driver-1')

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Откуда'), 'Агидель')
    await userEvent.type(screen.getByLabelText('Куда'), 'Международный аэропорт Уфа')

    mockedRequest.mockResolvedValueOnce({ orderId: 'order-1' })
    mockedRequest.mockResolvedValueOnce({})
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED' }])
    mockedRequest.mockResolvedValueOnce([{ status: 'COMPLETED' }])

    await userEvent.click(screen.getByRole('button', { name: 'Заказать поездку' }))
    const saveButton = await screen.findByRole('button', { name: 'Добавить в мои водители' })

    mockedRequest.mockResolvedValueOnce({ connectionId: 'c-new' }) // POST /v1/connections
    mockedRequest.mockResolvedValueOnce({}) // POST /v1/connections/c-new/primary -- no existing primary, so this fires

    await userEvent.click(saveButton)

    expect(await screen.findByRole('button', { name: 'Заказать у этого водителя' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Добавить в мои водители' })).not.toBeInTheDocument()
    // The button vanishing is a subtle cue on its own -- an explicit
    // confirmation names what happened and that this driver became primary.
    expect(await screen.findByText('Водитель сохранён и назначен основным')).toBeInTheDocument()

    const createCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/connections')
    expect(createCall).toBeDefined()
    const createBody = JSON.parse((createCall?.[1] as RequestInit).body as string)
    expect(createBody).toEqual({ driverId: 'driver-1', passengerReference: TEST_IDENTITY.identityId })
    expect(mockedRequest.mock.calls.some(([path]) => path === '/v1/connections/c-new/primary')).toBe(true)
  })

  it('saving a driver while another primary already exists creates the connection but does not touch primary', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { connectionId: 'c-existing', driverId: 'driver-existing-primary', createdAt: '2026-08-01T00:00:00Z', isPrimary: true },
    ])
    mockedRequest.mockResolvedValueOnce({ id: 'driver-existing-primary', availability: 'AVAILABLE', displayName: 'Пётр' })

    renderAt('driver-1')

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Откуда'), 'Агидель')
    await userEvent.type(screen.getByLabelText('Куда'), 'Международный аэропорт Уфа')

    mockedRequest.mockResolvedValueOnce({ orderId: 'order-1' })
    mockedRequest.mockResolvedValueOnce({})
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED' }])
    mockedRequest.mockResolvedValueOnce([{ status: 'COMPLETED' }])

    await userEvent.click(screen.getByRole('button', { name: 'Заказать поездку' }))
    const saveButton = await screen.findByRole('button', { name: 'Добавить в мои водители' })

    mockedRequest.mockResolvedValueOnce({ connectionId: 'c-new' }) // POST /v1/connections -- the only call this click should make

    await userEvent.click(saveButton)

    await screen.findByRole('button', { name: 'Заказать у этого водителя' })
    // Rule 4 (PRODUCT_DECISION_CIRCLE_OF_TRUST.md): an existing primary is
    // never silently replaced -- only the create call happens, never
    // /primary for the newly saved connection.
    expect(mockedRequest.mock.calls.some(([path]) => path === '/v1/connections/c-new/primary')).toBe(false)
    // Confirmation still names what actually happened -- saved, but not
    // made primary, since Пётр already holds that role.
    expect(await screen.findByText('Водитель сохранён в вашем круге доверия')).toBeInTheDocument()
  })

  it('shows a retryable error if saving the driver fails', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([])

    renderAt('driver-1')

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Откуда'), 'Агидель')
    await userEvent.type(screen.getByLabelText('Куда'), 'Международный аэропорт Уфа')

    mockedRequest.mockResolvedValueOnce({ orderId: 'order-1' })
    mockedRequest.mockResolvedValueOnce({})
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED' }])
    mockedRequest.mockResolvedValueOnce([{ status: 'COMPLETED' }])

    await userEvent.click(screen.getByRole('button', { name: 'Заказать поездку' }))
    const saveButton = await screen.findByRole('button', { name: 'Добавить в мои водители' })

    mockedRequest.mockRejectedValueOnce(new Error('network down'))
    await userEvent.click(saveButton)

    expect(await screen.findByText('Не удалось сохранить водителя. Попробуйте ещё раз.')).toBeInTheDocument()
    // Still offered -- nothing was saved, the passenger can just try again.
    const retryButton = screen.getByRole('button', { name: 'Добавить в мои водители' })

    mockedRequest.mockResolvedValueOnce({ connectionId: 'c-new' })
    mockedRequest.mockResolvedValueOnce({})
    await userEvent.click(retryButton)

    expect(await screen.findByRole('button', { name: 'Заказать у этого водителя' })).toBeInTheDocument()
  })

  // Product audit (2026-09-12): "Мои водители" (/me) is PIOS's own
  // designated repeat-a-ride path but had no link to it anywhere in the
  // app -- offered only alongside "Заказать ещё раз", at the same terminal
  // states, for the same reasoning.
  it('offers "Мои водители" when the ride completed, and it navigates to /me', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED' }])
    mockedRequest.mockResolvedValueOnce([{ status: 'COMPLETED' }])

    renderAt('driver-1')

    await userEvent.click(await screen.findByRole('button', { name: 'Мои водители' }))

    expect(await screen.findByText('my-drivers-screen')).toBeInTheDocument()
  })

  it('does not offer "Мои водители" while the ride is accepted but not yet completed', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED' }])
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED' }])

    renderAt('driver-1')

    await screen.findByText(/принял ваш заказ/)
    expect(screen.queryByRole('button', { name: 'Мои водители' })).not.toBeInTheDocument()
  })

  // Product audit follow-up (2026-09-12): word-of-mouth growth
  // (docs/PIOS_PRODUCT_VISION.md §16) used to be driver-initiated only --
  // a passenger who just had a genuinely COMPLETED ride had no in-product
  // way to recommend this same driver to a friend. Reuses the exact same
  // `/i/:driverCode` invite link, same Web Share API / clipboard fallback
  // DriverHome.tsx's own share action already uses.
  it('offers "Поделиться с другом" when the ride completed, sharing this driver\'s own invite link', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED' }])
    mockedRequest.mockResolvedValueOnce([{ status: 'COMPLETED' }])

    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })

    renderAt('driver-1')

    await userEvent.click(await screen.findByRole('button', { name: 'Поделиться с другом' }))

    expect(writeText).toHaveBeenCalledWith(`${window.location.origin}/i/driver-1`)
    expect(await screen.findByText('Ссылка скопирована')).toBeInTheDocument()
  })

  it('does not offer "Поделиться с другом" when the proposal was merely declined -- no real ride to recommend', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'DECLINED' }])

    renderAt('driver-1')

    await screen.findByRole('button', { name: 'Заказать ещё раз' })
    expect(screen.queryByRole('button', { name: 'Поделиться с другом' })).not.toBeInTheDocument()
  })

  // --- Minimal In-Ride Messaging (Product Cycle) ---

  it('a passenger can send a message on an open order, before the driver has even responded', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ proposalId: 'p1', status: 'OPEN' }])
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/proposals/p1/messages -- nothing yet

    renderAt('driver-1')

    await userEvent.type(await screen.findByLabelText('Сообщение водителю'), 'Встречайте у второго подъезда')

    mockedRequest.mockResolvedValueOnce({
      id: 'm1',
      senderRole: 'PASSENGER',
      body: 'Встречайте у второго подъезда',
      sentAt: '2026-09-14T10:00:00Z',
    }) // POST /v1/proposals/p1/messages

    await userEvent.click(screen.getByRole('button', { name: 'Отправить' }))

    expect(await screen.findByText('Вы: Встречайте у второго подъезда')).toBeInTheDocument()
    const sendCall = mockedRequest.mock.calls.find(
      ([path, options]) => path === '/v1/proposals/p1/messages' && (options as RequestInit | undefined)?.method === 'POST'
    )
    expect(sendCall).toBeDefined()
    const body = JSON.parse((sendCall?.[1] as RequestInit).body as string)
    expect(body.body).toBe('Встречайте у второго подъезда')
  })

  it("the passenger sees the driver's reply", async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ proposalId: 'p1', status: 'OPEN' }])
    mockedRequest.mockResolvedValueOnce([
      { id: 'm1', senderRole: 'DRIVER', body: 'Уже еду, буду через 5 минут', sentAt: '2026-09-14T10:05:00Z' },
    ])

    renderAt('driver-1')

    expect(await screen.findByText('Водитель: Уже еду, буду через 5 минут')).toBeInTheDocument()
  })

  it('closes messaging once the ride is COMPLETED, but keeps existing history visible', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ proposalId: 'p1', status: 'ACCEPTED', statedPrice: null, statedEtaMinutes: null }])
    mockedRequest.mockResolvedValueOnce([{ status: 'COMPLETED' }]) // GET /v1/assignments?orderId=order-1
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/orders?passengerReference=... (requestedPickupAt)
    mockedRequest.mockResolvedValueOnce([
      { id: 'm1', senderRole: 'PASSENGER', body: 'Спасибо!', sentAt: '2026-09-14T10:10:00Z' },
    ]) // GET /v1/proposals/p1/messages

    renderAt('driver-1')

    expect(await screen.findByText('Вы: Спасибо!')).toBeInTheDocument()
    expect(screen.getByText('Обмен сообщениями закрыт.')).toBeInTheDocument()
    expect(screen.queryByLabelText('Сообщение водителю')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Отправить' })).not.toBeInTheDocument()
  })

  it('sending a message can be retried after a failure, without losing the typed draft', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ proposalId: 'p1', status: 'OPEN' }])
    mockedRequest.mockResolvedValueOnce([])

    renderAt('driver-1')

    await userEvent.type(await screen.findByLabelText('Сообщение водителю'), 'Уточните адрес')

    mockedRequest.mockRejectedValueOnce(new Error('network down'))
    await userEvent.click(screen.getByRole('button', { name: 'Отправить' }))

    expect(await screen.findByText('Не удалось отправить сообщение. Попробуйте ещё раз.')).toBeInTheDocument()
    // The draft is not cleared on failure -- nothing typed is lost.
    expect(screen.getByLabelText('Сообщение водителю')).toHaveValue('Уточните адрес')

    mockedRequest.mockResolvedValueOnce({ id: 'm1', senderRole: 'PASSENGER', body: 'Уточните адрес', sentAt: '2026-09-14T10:15:00Z' })
    await userEvent.click(screen.getByRole('button', { name: 'Отправить' }))

    expect(await screen.findByText('Вы: Уточните адрес')).toBeInTheDocument()
  })

  // --- Session expiry (P1 UX audit, 2026-09-12) ---
  // A 401 from the status poll's own authenticated call used to be
  // indistinguishable from a transient network blip -- retried silently,
  // forever, with the screen frozen on whatever status it last showed and
  // zero indication anything was wrong.

  it('shows "Сессия истекла. Войдите снова." when the status poll\'s session has expired, with a working way back', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockRejectedValueOnce(new ApiError(401, '/v1/proposals'))

    renderAt('driver-1')

    expect(await screen.findByText('Сессия истекла. Войдите снова.')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Войти снова' }))

    expect(await screen.findByText('passenger-landing-screen')).toBeInTheDocument()
    expect(localStorage.getItem('pios.identity')).toBeNull()
  })

  it('does not treat an ordinary poll failure (not a 401) as a session expiry', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockRejectedValueOnce(new Error('network down'))
    mockedRequest.mockResolvedValue([{ status: 'OPEN' }])

    renderAt('driver-1')

    await screen.findByText(/Ждём ответа водителя/)
    expect(screen.queryByText('Сессия истекла. Войдите снова.')).not.toBeInTheDocument()
  })

  it('does not offer "Заказать ещё раз" while still waiting for the driver', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'OPEN' }])

    renderAt('driver-1')

    await screen.findByText(/Ждём ответа водителя/)
    expect(screen.queryByRole('button', { name: 'Заказать ещё раз' })).not.toBeInTheDocument()
  })

  it('does not offer "Заказать ещё раз" while the ride is accepted but not yet completed', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED' }])
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED' }])

    renderAt('driver-1')

    await screen.findByText(/принял ваш заказ/)
    expect(screen.queryByRole('button', { name: 'Заказать ещё раз' })).not.toBeInTheDocument()
  })

  it('clicking "Заказать ещё раз" clears only this driver\'s stored order id and returns to the form', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    saveCurrentOrderId('driver-2', 'order-2')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'DECLINED' }])

    renderAt('driver-1')

    await userEvent.click(await screen.findByRole('button', { name: 'Заказать ещё раз' }))

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
    expect(getCurrentOrderId('driver-1')).toBeNull()
    // A different driver's own current order, on the same passenger identity, is untouched.
    expect(getCurrentOrderId('driver-2')).toBe('order-2')
  })

  it('submitting a new ride after "Заказать ещё раз" creates a new order, independent of the previous one', async () => {
    saveCurrentOrderId('driver-1', 'order-old')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'DECLINED' }])

    renderAt('driver-1')
    await userEvent.click(await screen.findByRole('button', { name: 'Заказать ещё раз' }))
    await screen.findByRole('heading', { name: 'Заказать поездку' })

    await userEvent.type(screen.getByLabelText('Откуда'), 'Новый адрес отправления')
    await userEvent.type(screen.getByLabelText('Куда'), 'Новый адрес назначения')

    mockedRequest.mockResolvedValueOnce({ orderId: 'order-new' })
    mockedRequest.mockResolvedValueOnce({}) // attemptProposal's own POST /v1/proposals
    mockedRequest.mockResolvedValue([{ status: 'OPEN' }]) // the new order's own status poll, from here on

    await userEvent.click(screen.getByRole('button', { name: 'Заказать поездку' }))

    expect(await screen.findByText('✅ Заказ оформлен.')).toBeInTheDocument()
    expect(getCurrentOrderId('driver-1')).toBe('order-new')
  })

  // --- Circle of trust (Sprint "My Business + Circle of Trust", ADR-054) ---

  it('shows the circle of trust, with the primary and other trusted drivers named, before a new order form', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { connectionId: 'c1', driverId: 'driver-1', createdAt: '2026-08-01T00:00:00Z', isPrimary: true },
      { connectionId: 'c2', driverId: 'driver-2', createdAt: '2026-08-02T00:00:00Z', isPrimary: false },
    ])
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-2', availability: 'AVAILABLE', displayName: 'Ахмад' })

    renderAt('driver-1')

    expect(await screen.findByRole('heading', { name: 'Кому доверить эту поездку?' })).toBeInTheDocument()
    // Task 5 (DriverTrustIndicator): the primary driver's own "Основной"
    // marker now comes from that component itself, replacing the former
    // separate "Основной водитель" section label — same underlying fact
    // (Circle of Trust's own `isPrimary`), rendered by the new shared
    // component instead of page-local markup.
    expect(screen.getByText('Основной')).toBeInTheDocument()
    expect(screen.getByText('Другие ваши водители')).toBeInTheDocument()
    expect(screen.getByText(/Иван/)).toBeInTheDocument()
    expect(screen.getByText(/Ахмад/)).toBeInTheDocument()
  })

  it('a circle of zero or one relationship skips straight to the order form, unchanged from before this Sprint', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { connectionId: 'c1', driverId: 'driver-1', createdAt: '2026-08-01T00:00:00Z', isPrimary: true },
    ])

    renderAt('driver-1')

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Кому доверить эту поездку?' })).not.toBeInTheDocument()
  })

  it('proceeds straight to the order form when choosing the driver whose own link this page is already on', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { connectionId: 'c1', driverId: 'driver-1', createdAt: '2026-08-01T00:00:00Z', isPrimary: true },
      { connectionId: 'c2', driverId: 'driver-2', createdAt: '2026-08-02T00:00:00Z', isPrimary: false },
    ])
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-2', availability: 'AVAILABLE', displayName: 'Ахмад' })

    renderAt('driver-1')
    await screen.findByRole('heading', { name: 'Кому доверить эту поездку?' })

    await userEvent.click(screen.getByRole('button', { name: 'Вызвать' }))

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
  })

  // --- Browser Back (P1 UX audit, 2026-09-12) ---
  // 'form' reached via the circle-of-trust step used to have no way back
  // to it through the browser's own Back button -- it left the whole
  // screen instead, skipping past a step the passenger had just seen.

  it('returns to the circle-of-trust step, not off the screen, when Back is pressed after choosing a driver from it', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { connectionId: 'c1', driverId: 'driver-1', createdAt: '2026-08-01T00:00:00Z', isPrimary: true },
      { connectionId: 'c2', driverId: 'driver-2', createdAt: '2026-08-02T00:00:00Z', isPrimary: false },
    ])
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-2', availability: 'AVAILABLE', displayName: 'Ахмад' })

    renderAt('driver-1')
    await screen.findByRole('heading', { name: 'Кому доверить эту поездку?' })

    await userEvent.click(screen.getByRole('button', { name: 'Вызвать' }))
    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()

    act(() => {
      window.dispatchEvent(new PopStateEvent('popstate'))
    })

    expect(await screen.findByRole('heading', { name: 'Кому доверить эту поездку?' })).toBeInTheDocument()
  })

  it('does not intercept Back on the order form when it was reached directly (0-or-1 relationship, no circle step shown)', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([])

    renderAt('driver-1')
    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()

    act(() => {
      window.dispatchEvent(new PopStateEvent('popstate'))
    })

    // No circle step was ever shown for this passenger -- Back must not
    // invent one to return to.
    expect(screen.queryByRole('heading', { name: 'Кому доверить эту поездку?' })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
  })

  it('choosing a different trusted driver for this specific ride navigates away, without asking to change primary', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { connectionId: 'c1', driverId: 'driver-1', createdAt: '2026-08-01T00:00:00Z', isPrimary: true },
      { connectionId: 'c2', driverId: 'driver-2', createdAt: '2026-08-02T00:00:00Z', isPrimary: false },
    ])
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-2', availability: 'AVAILABLE', displayName: 'Ахмад' })

    renderAt('driver-1')
    await screen.findByRole('heading', { name: 'Кому доверить эту поездку?' })

    await userEvent.click(screen.getByRole('button', { name: 'Выбрать' }))

    // Navigated to Ahmad's own route -- Ivan's own circle screen is gone,
    // and no "make primary" confirmation was ever shown for this action.
    await waitFor(() => expect(screen.queryByRole('heading', { name: 'Кому доверить эту поездку?' })).not.toBeInTheDocument())
    expect(screen.queryByText(/основным водителем\?/)).not.toBeInTheDocument()
  })

  it('lets the passenger make a different trusted driver primary, only after explicit confirmation', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { connectionId: 'c1', driverId: 'driver-1', createdAt: '2026-08-01T00:00:00Z', isPrimary: true },
      { connectionId: 'c2', driverId: 'driver-2', createdAt: '2026-08-02T00:00:00Z', isPrimary: false },
    ])
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-2', availability: 'AVAILABLE', displayName: 'Ахмад' })

    renderAt('driver-1')
    await screen.findByRole('heading', { name: 'Кому доверить эту поездку?' })

    // Clicking "Сделать основным" only asks -- it does not change anything by itself.
    await userEvent.click(screen.getByRole('button', { name: 'Сделать основным' }))
    expect(screen.getByText('Сделать Ахмад основным водителем?')).toBeInTheDocument()

    mockedRequest.mockResolvedValueOnce({ connectionId: 'c2', driverId: 'driver-2', createdAt: '2026-08-02T00:00:00Z', isPrimary: true })
    await userEvent.click(screen.getByRole('button', { name: 'Да, сделать основным' }))

    await waitFor(() =>
      expect(screen.queryByText('Сделать Ахмад основным водителем?')).not.toBeInTheDocument()
    )
  })

  it('lets the passenger remove a trusted driver from their circle, only after explicit confirmation', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { connectionId: 'c1', driverId: 'driver-1', createdAt: '2026-08-01T00:00:00Z', isPrimary: true },
      { connectionId: 'c2', driverId: 'driver-2', createdAt: '2026-08-02T00:00:00Z', isPrimary: false },
    ])
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-2', availability: 'AVAILABLE', displayName: 'Ахмад' })

    renderAt('driver-1')
    await screen.findByRole('heading', { name: 'Кому доверить эту поездку?' })

    await userEvent.click(screen.getByRole('button', { name: 'Удалить' }))
    expect(screen.getByText('Удалить Ахмад из списка водителей?')).toBeInTheDocument()

    mockedRequest.mockResolvedValueOnce(undefined)
    await userEvent.click(screen.getByRole('button', { name: 'Да, удалить' }))

    await waitFor(() => expect(screen.queryByText('Другие ваши водители')).not.toBeInTheDocument())
  })

  it('shows a clear alternative when the primary driver is unavailable (Rule 11)', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([
      { connectionId: 'c1', driverId: 'driver-1', createdAt: '2026-08-01T00:00:00Z', isPrimary: true },
      { connectionId: 'c2', driverId: 'driver-2', createdAt: '2026-08-02T00:00:00Z', isPrimary: false },
    ])
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'UNAVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-2', availability: 'AVAILABLE', displayName: 'Ахмад' })

    renderAt('driver-1')
    await screen.findByRole('heading', { name: 'Кому доверить эту поездку?' })

    expect(screen.getByText('Сейчас недоступен. Вот кому ещё вы доверяете:')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Вызвать' })[0]).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Выбрать' })).not.toBeDisabled()
  })

  // --- Session (ADR-055, "Final Pre-Pilot Sprint") ---

  it('redirects to Passenger Landing when no session exists at all', async () => {
    localStorage.clear()

    renderAt('driver-1')

    expect(await screen.findByText('passenger-landing-screen')).toBeInTheDocument()
    // No session -- restoreIdentity() never even calls the backend.
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('redirects to Passenger Landing when the stored session has already expired, without a network call', async () => {
    localStorage.setItem('pios.identity', JSON.stringify({ ...TEST_IDENTITY, expiresAt: '2000-01-01T00:00:00.000Z' }))

    renderAt('driver-1')

    expect(await screen.findByText('passenger-landing-screen')).toBeInTheDocument()
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('signs out and returns to Passenger Landing, forgetting only this device\'s own session', async () => {
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([])

    renderAt('driver-1')
    await screen.findByRole('heading', { name: 'Заказать поездку' })

    await userEvent.click(screen.getByRole('button', { name: 'Выйти' }))

    expect(await screen.findByText('passenger-landing-screen')).toBeInTheDocument()
    expect(localStorage.getItem('pios.identity')).toBeNull()
  })
})
