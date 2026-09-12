import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
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

  // --- Vehicle (PIOS Group and Long-Distance Rides Roadmap, Stage 1) ---

  it('shows the driver\'s vehicle when they have declared one', async () => {
    mockedRequest.mockResolvedValueOnce({
      id: 'driver-1',
      availability: 'AVAILABLE',
      displayName: 'Иван',
      vehicleMake: 'Lada',
      vehicleModel: 'Vesta',
      vehicleColor: 'белый',
      vehiclePlateNumber: 'А123БВ102',
    })

    renderAt('driver-1')

    expect(await screen.findByText('Машина: Lada Vesta, белый · А123БВ102')).toBeInTheDocument()
  })

  it('shows no vehicle line when the driver has not declared one', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })

    renderAt('driver-1')

    expect(await screen.findByText(/Вас пригласили лично/)).toBeInTheDocument()
    expect(screen.queryByText(/Машина:/)).not.toBeInTheDocument()
  })

  // --- Availability (Referral funnel friction audit, 2026-09-12) ---
  // This value was already fetched (`GET /v1/drivers/:driverId` already
  // returns it) and silently discarded before this fix -- a brand-new
  // referral used to have zero signal here about whether this driver was
  // even online, before registering or ordering.

  it('shows the driver\'s own live availability on the very first screen a new referral sees', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'UNAVAILABLE', displayName: 'Иван' })

    renderAt('driver-1')

    expect(await screen.findByText('Недоступен')).toBeInTheDocument()
  })

  it('shows the driver as available when they are', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })

    renderAt('driver-1')

    expect(await screen.findByText('Доступен')).toBeInTheDocument()
  })

  // --- Long-distance preference (PIOS Group and Long-Distance Rides Roadmap, Stage 3) ---

  it('shows the long-distance line when the driver has opted in', async () => {
    mockedRequest.mockResolvedValueOnce({
      id: 'driver-1',
      availability: 'AVAILABLE',
      displayName: 'Иван',
      acceptsLongDistanceTrips: true,
    })

    renderAt('driver-1')

    expect(await screen.findByText('Берёт дальние поездки (вахта, аэропорт, другой город)')).toBeInTheDocument()
  })

  it('shows no long-distance line when the driver has not opted in', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })

    renderAt('driver-1')

    expect(await screen.findByText(/Вас пригласили лично/)).toBeInTheDocument()
    expect(screen.queryByText(/Берёт дальние поездки/)).not.toBeInTheDocument()
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

  // --- Connection bootstrap reliability (2026-09-12) ---
  // Retrying the whole `POST /v1/connections` + `POST .../primary`
  // sequence is safe because the real backend already makes both calls
  // idempotent by design (see [bootstrapCircleOfTrust]'s own KDoc for the
  // exact code cited) -- these tests cover the frontend's own retry
  // policy, not the backend guarantee itself.

  /**
   * Fills the registration form and queues [identityProvider.register]'s
   * own response, but does not click submit -- every caller must queue
   * the connection/primary mocks it wants *before* clicking, exactly like
   * the happy-path test above, since [bootstrapCircleOfTrust] starts
   * calling `request('/v1/connections', ...)` in the same microtask
   * `handleRegisterSubmit`'s own state updates resolve in, before
   * `userEvent.click`'s own returned promise resolves. Queuing a
   * connection/primary mock *after* awaiting the click would race an
   * already-in-flight call reading an unconfigured mock instead.
   */
  async function fillRegistrationForm() {
    await userEvent.click(await screen.findByRole('button', { name: 'Начать' }))
    await userEvent.type(screen.getByLabelText('Ваше имя'), 'Аня')
    await userEvent.type(screen.getByLabelText('Номер телефона'), '+70000000001')
    await userEvent.type(screen.getByLabelText('Пароль'), 'password123')
  }

  function queueSuccessfulRegisterResponse() {
    mockedRequest.mockResolvedValueOnce({
      identityId: 'passenger-1',
      driverId: null,
      token: 'test-token',
      expiresAt: '2099-01-01T00:00:00.000Z',
    })
  }

  it('makes exactly one connection call and one primary call when the first attempt succeeds', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    renderAt('driver-1')
    await fillRegistrationForm()

    queueSuccessfulRegisterResponse()
    mockedRequest.mockResolvedValueOnce({ connectionId: 'c1' }) // POST /v1/connections
    mockedRequest.mockResolvedValueOnce({}) // POST /v1/connections/c1/primary

    await userEvent.click(screen.getByRole('button', { name: 'Создать аккаунт' }))
    await screen.findByRole('heading', { name: 'Добро пожаловать!' })

    const connectionCalls = mockedRequest.mock.calls.filter(([path]) => path === '/v1/connections')
    const primaryCalls = mockedRequest.mock.calls.filter(([path]) => path === '/v1/connections/c1/primary')
    expect(connectionCalls).toHaveLength(1)
    expect(primaryCalls).toHaveLength(1)
  })

  it('creates the connection when the first attempt fails and the retry succeeds', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    renderAt('driver-1')
    await fillRegistrationForm()

    queueSuccessfulRegisterResponse()
    mockedRequest.mockRejectedValueOnce(new Error('network down')) // POST /v1/connections, attempt 1
    mockedRequest.mockResolvedValueOnce({ connectionId: 'c1' }) // POST /v1/connections, attempt 2
    mockedRequest.mockResolvedValueOnce({}) // POST /v1/connections/c1/primary, attempt 2

    await userEvent.click(screen.getByRole('button', { name: 'Создать аккаунт' }))
    await screen.findByRole('heading', { name: 'Добро пожаловать!' })
    await waitFor(
      () => {
        const connectionCalls = mockedRequest.mock.calls.filter(([path]) => path === '/v1/connections')
        expect(connectionCalls).toHaveLength(2)
      },
      { timeout: 3000 }
    )
    const primaryCalls = mockedRequest.mock.calls.filter(([path]) => path === '/v1/connections/c1/primary')
    expect(primaryCalls).toHaveLength(1)
  })

  it('retries the whole sequence with the same natural key after a transient failure creating the connection', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    renderAt('driver-1')
    await fillRegistrationForm()

    queueSuccessfulRegisterResponse()
    mockedRequest.mockRejectedValueOnce(new Error('network down')) // attempt 1: POST /v1/connections fails
    mockedRequest.mockResolvedValueOnce({ connectionId: 'c1' }) // attempt 2: POST /v1/connections succeeds
    mockedRequest.mockResolvedValueOnce({}) // attempt 2: POST primary succeeds

    await userEvent.click(screen.getByRole('button', { name: 'Создать аккаунт' }))
    await waitFor(
      () => {
        const connectionCalls = mockedRequest.mock.calls.filter(([path]) => path === '/v1/connections')
        expect(connectionCalls).toHaveLength(2)
      },
      { timeout: 3000 }
    )
    // Same natural key on every attempt -- this is exactly what makes the
    // repeated call safe against the backend's own UNIQUE constraint,
    // rather than accidentally producing a different one.
    const bodies = mockedRequest.mock.calls
      .filter(([path]) => path === '/v1/connections')
      .map(([, init]) => (init as RequestInit).body)
    expect(bodies[0]).toEqual(bodies[1])
  })

  it('retries the whole sequence again after a transient failure setting primary, safely', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    renderAt('driver-1')
    await fillRegistrationForm()

    queueSuccessfulRegisterResponse()
    mockedRequest.mockResolvedValueOnce({ connectionId: 'c1' }) // attempt 1: POST /v1/connections succeeds
    mockedRequest.mockRejectedValueOnce(new Error('network down')) // attempt 1: POST primary fails
    // Attempt 2 redoes the whole sequence -- the backend's own idempotent
    // create returns the same existing connection either way; mocked here
    // returning the same id, matching that real behavior.
    mockedRequest.mockResolvedValueOnce({ connectionId: 'c1' }) // attempt 2: POST /v1/connections
    mockedRequest.mockResolvedValueOnce({}) // attempt 2: POST primary succeeds

    await userEvent.click(screen.getByRole('button', { name: 'Создать аккаунт' }))
    await waitFor(
      () => {
        const primaryCalls = mockedRequest.mock.calls.filter(([path]) => path === '/v1/connections/c1/primary')
        expect(primaryCalls).toHaveLength(2)
      },
      { timeout: 3000 }
    )
    const connectionCalls = mockedRequest.mock.calls.filter(([path]) => path === '/v1/connections')
    expect(connectionCalls).toHaveLength(2)
  })

  it('preserves today\'s silent best-effort behavior once every attempt has failed', async () => {
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    renderAt('driver-1')
    await fillRegistrationForm()

    queueSuccessfulRegisterResponse()
    mockedRequest.mockRejectedValueOnce(new Error('network down')) // attempt 1
    mockedRequest.mockRejectedValueOnce(new Error('network down')) // attempt 2
    mockedRequest.mockRejectedValueOnce(new Error('network down')) // attempt 3

    await userEvent.click(screen.getByRole('button', { name: 'Создать аккаунт' }))

    // The passenger still reaches the confirmed screen -- registration
    // itself succeeded; only the best-effort bootstrap exhausted its
    // attempts, unchanged from before this fix's own tolerance.
    expect(await screen.findByRole('heading', { name: 'Добро пожаловать!' })).toBeInTheDocument()
    await waitFor(
      () => {
        const connectionCalls = mockedRequest.mock.calls.filter(([path]) => path === '/v1/connections')
        expect(connectionCalls).toHaveLength(3)
      },
      { timeout: 3000 }
    )
    expect(screen.queryByText(/Не удалось/)).not.toBeInTheDocument()
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
