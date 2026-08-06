import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { RideRequest } from './RideRequest'
import { savePassengerIdentity } from '../../persistence/localPassengerIdentity'
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
    // This screen redirects to Passenger Landing if no local identity
    // exists yet -- these tests exercise the invitation-loading step
    // itself, which requires a returning-passenger identity to already be
    // present.
    savePassengerIdentity('Аня')
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows the existing invalid-link message on a genuine 404, unchanged', async () => {
    mockedRequest.mockRejectedValueOnce(new ApiError(404, '/v1/drivers/unknown-driver'))

    renderAt('unknown-driver')

    expect(
      await screen.findByText(
        'Ссылка недействительна или водитель ещё не зарегистрирован. Уточните ссылку у водителя, который вас пригласил.'
      )
    ).toBeInTheDocument()
  })

  it('shows a distinct connection-error state (not "link invalid") on a transient failure, and recovers on retry', async () => {
    mockedRequest.mockRejectedValueOnce(new Error('network down'))

    renderAt('driver-1')

    expect(await screen.findByText(/Не удалось загрузить приглашение/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Попробовать снова' })).toBeInTheDocument()
    // Never collapses into the 404/"link invalid" outcome.
    expect(screen.queryByText(/Ссылка недействительна/)).not.toBeInTheDocument()

    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })

    await userEvent.click(screen.getByRole('button', { name: 'Попробовать снова' }))

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
  })

  // --- Pickup address (Sprint H5: Entrepreneur Working Cycle Integrity) ---

  it('requires a pickup address, mirroring the existing destination requirement', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })

    renderAt('driver-1')

    expect(await screen.findByRole('heading', { name: 'Заказать поездку' })).toBeInTheDocument()
    expect(screen.getByLabelText('Откуда')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Заказать поездку' }))

    expect(await screen.findByText('Пожалуйста, укажите адрес.')).toBeInTheDocument()
    // Submission never reached Order Management: no second `request` call happened.
    expect(mockedRequest).toHaveBeenCalledTimes(1)
  })

  // --- Truthful status rendering (Sprint H5: Entrepreneur Working Cycle Integrity) ---
  //
  // Previously every non-ACCEPTED proposal status (OPEN, DECLINED, LAPSED)
  // rendered the same "✅ ... он свяжется с вами" success wording. These
  // cover that a passenger resuming a confirmed order now sees an honest,
  // distinct message for each real status the poll already receives.

  it('shows an honest waiting message for a still-open proposal, not success wording', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'OPEN' }])

    renderAt('driver-1')

    expect(await screen.findByText(/Ждём ответа водителя/)).toBeInTheDocument()
  })

  it('shows an honest declined message when the driver declined, not the old success wording', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'DECLINED' }])

    renderAt('driver-1')

    expect(await screen.findByText(/отклонил ваш заказ/)).toBeInTheDocument()
    expect(screen.queryByText(/свяжется с вами/)).not.toBeInTheDocument()
  })

  it('shows an honest lapsed message when the proposal lapsed, not the old success wording', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'LAPSED' }])

    renderAt('driver-1')

    expect(await screen.findByText(/больше не активен/)).toBeInTheDocument()
    expect(screen.queryByText(/свяжется с вами/)).not.toBeInTheDocument()
  })

  // --- P0-1: "Заказать ещё раз" at a terminal ride state (docs/SPRINT_PILOT_BLOCKERS.md) ---

  it('offers "Заказать ещё раз" when the proposal was declined', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'DECLINED' }])

    renderAt('driver-1')

    expect(await screen.findByRole('button', { name: 'Заказать ещё раз' })).toBeInTheDocument()
  })

  it('offers "Заказать ещё раз" when the proposal lapsed', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'LAPSED' }])

    renderAt('driver-1')

    expect(await screen.findByRole('button', { name: 'Заказать ещё раз' })).toBeInTheDocument()
  })

  it('offers "Заказать ещё раз" when the ride completed', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'ACCEPTED' }])
    mockedRequest.mockResolvedValueOnce([{ status: 'COMPLETED' }])

    renderAt('driver-1')

    expect(await screen.findByRole('button', { name: 'Заказать ещё раз' })).toBeInTheDocument()
  })

  it('does not offer "Заказать ещё раз" while still waiting for the driver', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce([{ status: 'OPEN' }])

    renderAt('driver-1')

    await screen.findByText(/Ждём ответа водителя/)
    expect(screen.queryByRole('button', { name: 'Заказать ещё раз' })).not.toBeInTheDocument()
  })

  it('does not offer "Заказать ещё раз" while the ride is accepted but not yet completed', async () => {
    saveCurrentOrderId('driver-1', 'order-1')
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
})
