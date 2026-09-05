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

    expect(await screen.findByText(/Заказ получает Иван/)).toBeInTheDocument()
  })

  // --- Registration and login (ADR-055, "Final Pre-Pilot Sprint") ---

  it('lets a first-time visitor create a real account and lands on the confirmed screen', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })

    renderAt('driver-1')
    await userEvent.click(await screen.findByRole('button', { name: 'Начать' }))

    await userEvent.type(screen.getByLabelText('Ваше имя'), 'Аня')
    await userEvent.type(screen.getByLabelText('Номер телефона'), '+70000000001')
    await userEvent.type(screen.getByLabelText('Пароль'), 'password123')

    mockedRequest.mockResolvedValueOnce({
      identityId: 'passenger-1',
      driverId: null,
      token: 'test-token',
      expiresAt: '2099-01-01T00:00:00.000Z',
    })
    mockedRequest.mockResolvedValueOnce({ connectionId: 'c1' }) // POST /v1/connections
    mockedRequest.mockResolvedValueOnce({}) // POST /v1/connections/c1/primary

    await userEvent.click(screen.getByRole('button', { name: 'Создать аккаунт' }))

    expect(await screen.findByRole('heading', { name: 'Добро пожаловать!' })).toBeInTheDocument()
    expect(localStorage.getItem('pios.identity')).not.toBeNull()
  })

  it('shows a clear message when the phone number is already registered, and does not sign the visitor in', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })

    renderAt('driver-1')
    await userEvent.click(await screen.findByRole('button', { name: 'Начать' }))

    await userEvent.type(screen.getByLabelText('Ваше имя'), 'Аня')
    await userEvent.type(screen.getByLabelText('Номер телефона'), '+70000000001')
    await userEvent.type(screen.getByLabelText('Пароль'), 'password123')

    mockedRequest.mockRejectedValueOnce(new ApiError(409, '/v1/identities/register'))

    await userEvent.click(screen.getByRole('button', { name: 'Создать аккаунт' }))

    expect(await screen.findByText('Этот номер телефона уже зарегистрирован. Попробуйте войти.')).toBeInTheDocument()
    expect(localStorage.getItem('pios.identity')).toBeNull()
  })

  it('shows a clear message on a wrong password, without saying which part was wrong', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })

    renderAt('driver-1')
    await userEvent.click(await screen.findByRole('button', { name: 'Начать' }))
    await userEvent.click(screen.getByText('Уже есть аккаунт? Войти'))

    await userEvent.type(screen.getByLabelText('Номер телефона'), '+70000000001')
    await userEvent.type(screen.getByLabelText('Пароль'), 'wrong-password')

    mockedRequest.mockRejectedValueOnce(new ApiError(401, '/v1/identities/login'))

    await userEvent.click(screen.getByRole('button', { name: 'Войти' }))

    expect(await screen.findByText('Неверный номер телефона или пароль.')).toBeInTheDocument()
  })

  // E-001 (docs/PIOS_PRODUCT_EVIDENCE.md): a real iPhone registration
  // failed with a misleading "проверьте связь с интернетом" message that
  // was actually a silent backend 400 on an invalid phone format. This
  // guards the fix: an invalid format is now caught before any request is
  // made, with an accurate message.
  it('rejects an invalid phone format before ever calling the backend, with an accurate message', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })

    renderAt('driver-1')
    await userEvent.click(await screen.findByRole('button', { name: 'Начать' }))

    await userEvent.type(screen.getByLabelText('Ваше имя'), 'Аня')
    await userEvent.type(screen.getByLabelText('Номер телефона'), '89991234567')
    await userEvent.type(screen.getByLabelText('Пароль'), 'password123')

    mockedRequest.mockClear()
    await userEvent.click(screen.getByRole('button', { name: 'Создать аккаунт' }))

    expect(await screen.findByText(/международном формате/)).toBeInTheDocument()
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('asks a returning account to confirm before adding a driver whose link is new to them', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'driver-2', availability: 'AVAILABLE', displayName: 'Ахмад' })

    renderAt('driver-2')
    await userEvent.click(await screen.findByRole('button', { name: 'Начать' }))
    await userEvent.click(screen.getByText('Уже есть аккаунт? Войти'))

    await userEvent.type(screen.getByLabelText('Номер телефона'), '+70000000001')
    await userEvent.type(screen.getByLabelText('Пароль'), 'password123')

    mockedRequest.mockResolvedValueOnce({
      identityId: 'passenger-1',
      driverId: null,
      token: 'test-token',
      expiresAt: '2099-01-01T00:00:00.000Z',
    })
    mockedRequest.mockResolvedValueOnce([{ driverId: 'driver-1' }]) // GET /v1/connections?passengerReference= -- driver-2 is not in it yet

    await userEvent.click(screen.getByRole('button', { name: 'Войти' }))

    expect(await screen.findByText('Добавить Ахмад в ваш список водителей?')).toBeInTheDocument()
  })

  // --- Connection reliability (Section 16, "Final Pre-Pilot Sprint") ---

  it('tells the passenger honestly when "Добавить" fails, instead of proceeding as if it worked', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'driver-2', availability: 'AVAILABLE', displayName: 'Ахмад' })

    renderAt('driver-2')
    await userEvent.click(await screen.findByRole('button', { name: 'Начать' }))
    await userEvent.click(screen.getByText('Уже есть аккаунт? Войти'))
    await userEvent.type(screen.getByLabelText('Номер телефона'), '+70000000001')
    await userEvent.type(screen.getByLabelText('Пароль'), 'password123')

    mockedRequest.mockResolvedValueOnce({
      identityId: 'passenger-1',
      driverId: null,
      token: 'test-token',
      expiresAt: '2099-01-01T00:00:00.000Z',
    })
    mockedRequest.mockResolvedValueOnce([])
    await userEvent.click(screen.getByRole('button', { name: 'Войти' }))
    await screen.findByText('Добавить Ахмад в ваш список водителей?')

    mockedRequest.mockRejectedValueOnce(new Error('network down'))
    await userEvent.click(screen.getByRole('button', { name: 'Добавить' }))

    expect(
      await screen.findByText('Не удалось добавить. Проверьте связь с интернетом и попробуйте ещё раз.')
    ).toBeInTheDocument()
    // Still on the confirmation screen -- never silently treated as success.
    expect(screen.getByText('Добавить Ахмад в ваш список водителей?')).toBeInTheDocument()
  })

  // --- PIOS Install v1 (Product Owner exception) ---

  it('shows "Установить PIOS" without blocking or replacing registration', async () => {
    localStorage.setItem('pios.onboarding.passenger-seen', 'true')
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })

    renderAt('driver-1')

    await screen.findByText('Заказ получает Иван')
    expect(screen.getAllByText('Установить PIOS').length).toBeGreaterThan(0)
    // The primary registration action is still present and unobstructed.
    expect(screen.getByRole('button', { name: 'Начать' })).toBeInTheDocument()
  })

  it('opens the install overlay, and "Позже" returns to the real invited screen without registering anything', async () => {
    localStorage.setItem('pios.onboarding.passenger-seen', 'true')
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })

    renderAt('driver-1')
    await screen.findByText('Заказ получает Иван')

    await userEvent.click(screen.getAllByRole('button', { name: 'Установить PIOS' })[0])
    expect(await screen.findByRole('button', { name: 'Позже' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Позже' }))
    expect(screen.queryByRole('button', { name: 'Позже' })).not.toBeInTheDocument()
    expect(screen.getByText('Заказ получает Иван')).toBeInTheDocument()
    // No account-related request was ever made for this.
    expect(mockedRequest.mock.calls.length).toBe(1)
  })

  it('chains into install once, right after this passenger\'s very first onboarding completion', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })

    renderAt('driver-1')

    expect(await screen.findByText('Вас пригласил Артур')).toBeInTheDocument() // onboarding auto-shown
    await userEvent.click(screen.getByText('Пропустить'))

    expect(await screen.findByRole('button', { name: 'Позже' })).toBeInTheDocument() // install chained in next
  })
})
