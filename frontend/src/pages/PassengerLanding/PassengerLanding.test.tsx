import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { PassengerLanding } from './PassengerLanding'
import { ApiError, request } from '../../api/apiClient'

// Sprint 6 (Passenger Entry-Path Failure Handling): this page's own
// `getInvitationByDriverCode` call (via `invitationSource.ts`) used to have
// no `.catch` at all -- a transient failure (anything other than a genuine
// 404) left `step` stuck at `'loading'` forever. These tests cover both the
// unchanged 404 outcome and the new, distinct transient-failure outcome,
// including that retrying actually recovers.
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
    <MemoryRouter initialEntries={[`/i/${driverCode}`]}>
      <Routes>
        <Route path="/i/:driverCode" element={<PassengerLanding />} />
        <Route path="/i/:driverCode/request" element={<div>ride-request-screen</div>} />
      </Routes>
    </MemoryRouter>
  )
}

describe('PassengerLanding', () => {
  beforeEach(() => {
    localStorage.clear()
    mockedRequest.mockReset()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows the existing invalid-link message on a genuine 404, unchanged', async () => {
    mockedRequest.mockRejectedValueOnce(new ApiError(404, '/v1/drivers/unknown-driver'))

    renderAt('unknown-driver')

    expect(
      await screen.findByText('Ссылка недействительна или водитель ещё не зарегистрирован.')
    ).toBeInTheDocument()
  })

  it('shows a distinct connection-error state (not "link invalid") on a transient failure, and recovers on retry', async () => {
    mockedRequest.mockRejectedValueOnce(new Error('network down'))

    renderAt('driver-1')

    expect(await screen.findByText(/Не удалось загрузить приглашение/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Попробовать снова' })).toBeInTheDocument()
    // Never collapses into the 404/"link invalid" outcome.
    expect(
      screen.queryByText('Ссылка недействительна или водитель ещё не зарегистрирован.')
    ).not.toBeInTheDocument()

    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })

    await userEvent.click(screen.getByRole('button', { name: 'Попробовать снова' }))

    expect(await screen.findByText(/Вас пригласил Иван/)).toBeInTheDocument()
  })
})
