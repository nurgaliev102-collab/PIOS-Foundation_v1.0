import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
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

  it('offers "Заказать ещё раз" when the ride completed', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockMeResponse()
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED' }])
    mockedRequest.mockResolvedValueOnce([{ status: 'COMPLETED' }])

    renderAt('driver-1')

    expect(await screen.findByRole('button', { name: 'Заказать ещё раз' })).toBeInTheDocument()
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
    expect(screen.getByText('Основной предприниматель')).toBeInTheDocument()
    expect(screen.getByText('Другие доверенные предприниматели')).toBeInTheDocument()
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
    expect(screen.queryByText(/основным предпринимателем\?/)).not.toBeInTheDocument()
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
    expect(screen.getByText('Сделать Ахмад основным предпринимателем?')).toBeInTheDocument()

    mockedRequest.mockResolvedValueOnce({ connectionId: 'c2', driverId: 'driver-2', createdAt: '2026-08-02T00:00:00Z', isPrimary: true })
    await userEvent.click(screen.getByRole('button', { name: 'Да, сделать основным' }))

    await waitFor(() =>
      expect(screen.queryByText('Сделать Ахмад основным предпринимателем?')).not.toBeInTheDocument()
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
    expect(screen.getByText('Удалить Ахмад из круга доверия?')).toBeInTheDocument()

    mockedRequest.mockResolvedValueOnce(undefined)
    await userEvent.click(screen.getByRole('button', { name: 'Да, удалить' }))

    await waitFor(() => expect(screen.queryByText('Другие доверенные предприниматели')).not.toBeInTheDocument())
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
