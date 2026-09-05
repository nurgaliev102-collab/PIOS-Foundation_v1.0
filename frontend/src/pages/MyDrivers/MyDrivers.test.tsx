import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { MyDrivers } from './MyDrivers'
import { request } from '../../api/apiClient'

// Same mocking convention DriverHome.test.tsx/PassengerLanding.test.tsx
// already use.
vi.mock('../../api/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/apiClient')>()
  return {
    ...actual,
    request: vi.fn(),
  }
})

const mockedRequest = vi.mocked(request)

const TEST_IDENTITY = {
  identityId: 'passenger-1',
  driverId: null,
  token: 'test-token',
  expiresAt: '2099-01-01T00:00:00.000Z',
}

function seedIdentity() {
  localStorage.setItem('pios.identity', JSON.stringify(TEST_IDENTITY))
}

function renderMyDrivers() {
  return render(
    <MemoryRouter initialEntries={['/me']}>
      <Routes>
        <Route path="/me" element={<MyDrivers />} />
        <Route path="/i/:driverCode/request" element={<div>ride-request-screen</div>} />
      </Routes>
    </MemoryRouter>
  )
}

describe('MyDrivers', () => {
  beforeEach(() => {
    localStorage.clear()
    mockedRequest.mockReset()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows the empty state when no identity exists on this device at all', async () => {
    renderMyDrivers()

    expect(await screen.findByText(/Пока нет сохранённых водителей/)).toBeInTheDocument()
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('shows the empty state when a passenger identity exists but has zero connections', async () => {
    seedIdentity()
    mockedRequest.mockResolvedValueOnce({ id: 'passenger-1', phone: '+70000000000', driverId: null }) // GET /v1/identities/me
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/connections

    renderMyDrivers()

    expect(await screen.findByText(/Пока нет сохранённых водителей/)).toBeInTheDocument()
  })

  it('lists connected drivers, primary first, each with a working "Заказать поездку"', async () => {
    seedIdentity()
    mockedRequest.mockResolvedValueOnce({ id: 'passenger-1', phone: '+70000000000', driverId: null })
    mockedRequest.mockResolvedValueOnce([
      { connectionId: 'c1', driverId: 'driver-1', createdAt: '2026-08-01T00:00:00Z', isPrimary: false },
      { connectionId: 'c2', driverId: 'driver-2', createdAt: '2026-08-05T00:00:00Z', isPrimary: true },
    ])
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockResolvedValueOnce({ id: 'driver-2', availability: 'UNAVAILABLE', displayName: 'Мария' })

    renderMyDrivers()

    expect(await screen.findByText('Мария')).toBeInTheDocument()
    expect(screen.getByText('Иван')).toBeInTheDocument()
    expect(screen.getByText('Основной')).toBeInTheDocument()

    // Primary (Мария) renders first.
    const names = screen.getAllByText(/Иван|Мария/).map((el) => el.textContent)
    expect(names.indexOf('Мария')).toBeLessThan(names.indexOf('Иван'))

    const orderButtons = screen.getAllByRole('button', { name: 'Заказать поездку' })
    await userEvent.click(orderButtons[0])

    expect(await screen.findByText('ride-request-screen')).toBeInTheDocument()
  })

  it('shows one driver even if a sibling driver\'s own lookup fails -- best-effort, not all-or-nothing', async () => {
    seedIdentity()
    mockedRequest.mockResolvedValueOnce({ id: 'passenger-1', phone: '+70000000000', driverId: null })
    mockedRequest.mockResolvedValueOnce([
      { connectionId: 'c1', driverId: 'driver-1', createdAt: '2026-08-01T00:00:00Z', isPrimary: false },
      { connectionId: 'c2', driverId: 'driver-broken', createdAt: '2026-08-05T00:00:00Z', isPrimary: false },
    ])
    mockedRequest.mockResolvedValueOnce({ id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван' })
    mockedRequest.mockRejectedValueOnce(new Error('not found'))

    renderMyDrivers()

    expect(await screen.findByText('Иван')).toBeInTheDocument()
    expect(screen.getByText('Водитель PIOS')).toBeInTheDocument()
  })

  it('shows a distinct error state on a connections-fetch failure, with a working retry', async () => {
    seedIdentity()
    mockedRequest.mockResolvedValueOnce({ id: 'passenger-1', phone: '+70000000000', driverId: null })
    mockedRequest.mockRejectedValueOnce(new Error('network down'))

    renderMyDrivers()

    expect(await screen.findByText(/Не удалось загрузить список водителей/)).toBeInTheDocument()

    mockedRequest.mockResolvedValueOnce({ id: 'passenger-1', phone: '+70000000000', driverId: null })
    mockedRequest.mockResolvedValueOnce([])

    await userEvent.click(screen.getByRole('button', { name: 'Попробовать снова' }))

    expect(await screen.findByText(/Пока нет сохранённых водителей/)).toBeInTheDocument()
  })
})
