import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { RideRequest } from './RideRequest'
import { savePassengerIdentity } from '../../persistence/localPassengerIdentity'
import { saveCurrentOrderId } from '../../persistence/localCurrentOrder'
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
})
