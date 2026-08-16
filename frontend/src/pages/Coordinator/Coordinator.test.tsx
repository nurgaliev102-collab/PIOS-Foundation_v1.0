import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Coordinator } from './Coordinator'
import { request } from '../../api/apiClient'
import { storeOwnerCredential, getStoredOwnerCredential } from '../OwnerControlCenter/ownerCredential'

// ADR-060 (Order Query Authorization) broke this screen's order list by
// requiring a credential it never held; ADR-061 (Coordinator Owner-Gated
// Access) restores it behind the same owner `Authorization: Basic`
// credential `OwnerControlCenter.tsx` already uses. These tests cover the
// gate itself (no credential -> no authorized fetch at all) and that the
// credential lands only where ADR-060 actually requires it -- `GET
// /v1/orders` -- and not on the two endpoints ADR-060 deliberately left
// open (`GET /v1/drivers`, `GET /v1/proposals?orderId=`).
vi.mock('../../api/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/apiClient')>()
  return {
    ...actual,
    request: vi.fn(),
  }
})

const mockedRequest = vi.mocked(request)

const OWNER_CREDENTIAL = { username: 'owner', password: 'secret' }
const EXPECTED_BASIC_HEADER = `Basic ${btoa('owner:secret')}`

function seedOwnerCredential() {
  storeOwnerCredential(OWNER_CREDENTIAL)
}

describe('Coordinator', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mockedRequest.mockReset()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows the login screen and makes no request at all when no owner credential is held', () => {
    render(<Coordinator />)

    expect(screen.getByText('Координатор')).toBeInTheDocument()
    expect(screen.getByLabelText('Логин')).toBeInTheDocument()
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('sends the owner Basic credential on GET /v1/orders, no header on GET /v1/drivers, and no header on the per-order GET /v1/proposals?orderId=', async () => {
    seedOwnerCredential()
    mockedRequest.mockResolvedValueOnce([
      { id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван', registeredAt: null },
    ]) // GET /v1/drivers
    mockedRequest.mockResolvedValueOnce([
      {
        id: 'order-1',
        status: 'SUBMITTED',
        origin: 'passenger-1',
        destination: 'Аэропорт Уфа',
        passengerName: 'Аня',
        createdAt: '2026-08-16T09:00:00Z',
        pickupAddress: 'Агидель',
        requestedPickupAt: null,
      },
    ]) // GET /v1/orders
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/proposals?orderId=order-1

    render(<Coordinator />)

    await screen.findByText('Пассажир: Аня')

    const driversCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/drivers')
    expect(driversCall).toBeDefined()
    expect((driversCall?.[1] as RequestInit | undefined)?.headers).toBeUndefined()

    const ordersCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/orders')
    expect(ordersCall).toBeDefined()
    expect((ordersCall?.[1] as RequestInit).headers).toMatchObject({ Authorization: EXPECTED_BASIC_HEADER })

    const proposalsCall = mockedRequest.mock.calls.find(([path]) => (path as string).startsWith('/v1/proposals?orderId='))
    expect(proposalsCall).toBeDefined()
    expect(proposalsCall?.[0]).toBe('/v1/proposals?orderId=order-1')
    expect((proposalsCall?.[1] as RequestInit | undefined)?.headers).toBeUndefined()
  })

  it('renders driver, proposal status, price and ETA for an order once a proposal has been accepted, and the predzakaz badge for a scheduled order', async () => {
    seedOwnerCredential()
    mockedRequest.mockResolvedValueOnce([
      { id: 'driver-1', availability: 'AVAILABLE', displayName: 'Иван', registeredAt: null },
    ]) // GET /v1/drivers
    mockedRequest.mockResolvedValueOnce([
      {
        id: 'order-1',
        status: 'SUBMITTED',
        origin: 'passenger-1',
        destination: 'Аэропорт Уфа',
        passengerName: 'Аня',
        createdAt: '2026-08-16T09:00:00Z',
        pickupAddress: 'Агидель',
        requestedPickupAt: '2026-08-25T06:30:00Z',
      },
    ]) // GET /v1/orders
    mockedRequest.mockResolvedValueOnce([
      {
        proposalId: 'p1',
        orderId: 'order-1',
        driverId: 'driver-1',
        status: 'ACCEPTED',
        statedPrice: '950',
        statedEtaMinutes: 7,
        createdAt: '2026-08-16T09:01:00Z',
        respondedAt: '2026-08-16T09:02:00Z',
      },
    ]) // GET /v1/proposals?orderId=order-1

    render(<Coordinator />)

    expect(await screen.findByText('Водитель: Иван')).toBeInTheDocument()
    expect(screen.getByText('Статус предложения: Принята')).toBeInTheDocument()
    expect(screen.getByText('Стоимость: 950')).toBeInTheDocument()
    expect(screen.getByText('ETA: 7 мин')).toBeInTheDocument()
    expect(screen.getByText(/Предварительный заказ/)).toBeInTheDocument()
  })

  it('shows a withdrawn proposal with its own distinct Russian label', async () => {
    seedOwnerCredential()
    mockedRequest.mockResolvedValueOnce([]) // GET /v1/drivers
    mockedRequest.mockResolvedValueOnce([
      {
        id: 'order-1',
        status: 'CANCELLED',
        origin: 'passenger-1',
        destination: 'Аэропорт Уфа',
        passengerName: 'Аня',
        createdAt: '2026-08-16T09:00:00Z',
        pickupAddress: 'Агидель',
        requestedPickupAt: null,
      },
    ]) // GET /v1/orders
    mockedRequest.mockResolvedValueOnce([
      {
        proposalId: 'p1',
        orderId: 'order-1',
        driverId: 'driver-1',
        status: 'WITHDRAWN',
        statedPrice: null,
        statedEtaMinutes: null,
        createdAt: '2026-08-16T09:01:00Z',
        respondedAt: null,
      },
    ]) // GET /v1/proposals?orderId=order-1

    render(<Coordinator />)

    expect(await screen.findByText('Статус предложения: Отменено пассажиром')).toBeInTheDocument()
  })

  it('renders the console directly with no login prompt when a credential is already held in sessionStorage (page reload)', async () => {
    seedOwnerCredential()
    mockedRequest.mockResolvedValue([])

    render(<Coordinator />)

    expect(await screen.findByText('Заказы')).toBeInTheDocument()
    expect(screen.queryByLabelText('Логин')).not.toBeInTheDocument()
  })

  it('logout clears the credential and reverts to the login screen', async () => {
    seedOwnerCredential()
    mockedRequest.mockResolvedValue([])
    const user = userEvent.setup()

    render(<Coordinator />)
    await screen.findByText('Заказы')

    await user.click(screen.getByRole('button', { name: 'Выйти' }))

    expect(screen.getByLabelText('Логин')).toBeInTheDocument()
    expect(getStoredOwnerCredential()).toBeNull()
  })
})
