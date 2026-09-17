import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { Recovery } from './Recovery'
import { ApiError, request } from '../../api/apiClient'

// Same mocking convention every other page test in this codebase already
// uses (DriverHome.test.tsx/MyDrivers.test.tsx/PassengerLanding.test.tsx).
vi.mock('../../api/apiClient', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/apiClient')>()
  return {
    ...actual,
    request: vi.fn(),
  }
})

const mockedRequest = vi.mocked(request)

function renderRecovery() {
  return render(
    <MemoryRouter initialEntries={['/recovery']}>
      <Routes>
        <Route path="/recovery" element={<Recovery />} />
        <Route path="/" element={<div>driver-home-screen</div>} />
      </Routes>
    </MemoryRouter>
  )
}

describe('Recovery', () => {
  beforeEach(() => {
    localStorage.clear()
    mockedRequest.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  // ADR-082 D-03.7: the request step must never let a UI branch reveal
  // whether the phone resolved to anything -- both cases render byte-for-byte
  // the same confirm-step message.
  it('shows the identical generic message for an eligible phone and for an unknown one', async () => {
    mockedRequest.mockResolvedValueOnce({}) // POST /v1/identities/recovery/request

    renderRecovery()
    await userEvent.type(screen.getByLabelText('Номер телефона'), '+79991234567')
    await userEvent.click(screen.getByRole('button', { name: 'Отправить код' }))

    expect(
      await screen.findByText(
        'Если этот номер зарегистрирован и телефон подтверждён, мы отправили код подтверждения по SMS. Проверьте телефон.'
      )
    ).toBeInTheDocument()

    const call = mockedRequest.mock.calls.find(([path]) => path === '/v1/identities/recovery/request')
    expect(call).toBeDefined()
    expect(JSON.parse((call?.[1] as RequestInit).body as string)).toEqual({ phone: '+79991234567' })
  })

  it('rejects a malformed phone before ever calling the backend', async () => {
    renderRecovery()
    await userEvent.type(screen.getByLabelText('Номер телефона'), '12345')
    await userEvent.click(screen.getByRole('button', { name: 'Отправить код' }))

    expect(await screen.findByText(/международном формате/)).toBeInTheDocument()
    expect(mockedRequest).not.toHaveBeenCalled()
  })

  it('still moves to the confirm step -- with the same generic message -- when the request itself fails on the network', async () => {
    mockedRequest.mockRejectedValueOnce(new ApiError(500, '/v1/identities/recovery/request'))

    renderRecovery()
    await userEvent.type(screen.getByLabelText('Номер телефона'), '+79991234567')
    await userEvent.click(screen.getByRole('button', { name: 'Отправить код' }))

    expect(await screen.findByText(/Если этот номер зарегистрирован/)).toBeInTheDocument()
  })

  it('a correct code replaces the password and offers a way back to the home screen', async () => {
    mockedRequest.mockResolvedValueOnce({}) // POST /v1/identities/recovery/request
    mockedRequest.mockResolvedValueOnce({
      identityId: 'identity-1',
      driverId: 'driver-1',
      token: 'new-token',
      expiresAt: '2099-01-01T00:00:00.000Z',
    }) // POST /v1/identities/recovery/confirm

    renderRecovery()
    await userEvent.type(screen.getByLabelText('Номер телефона'), '+79991234567')
    await userEvent.click(screen.getByRole('button', { name: 'Отправить код' }))
    await screen.findByText(/Если этот номер зарегистрирован/)

    await userEvent.type(screen.getByLabelText('Код из SMS'), '123456')
    await userEvent.type(screen.getByLabelText('Новый пароль'), 'brand-new-password-1')
    await userEvent.type(screen.getByLabelText('Повторите новый пароль'), 'brand-new-password-1')
    await userEvent.click(screen.getByRole('button', { name: 'Восстановить доступ' }))

    expect(await screen.findByText(/Пароль изменён/)).toBeInTheDocument()
    // ADR-082 §8: must never claim every session everywhere was invalidated.
    expect(screen.queryByText(/больше не действительны/)).not.toBeInTheDocument()

    const confirmCall = mockedRequest.mock.calls.find(([path]) => path === '/v1/identities/recovery/confirm')
    expect(JSON.parse((confirmCall?.[1] as RequestInit).body as string)).toEqual({
      phone: '+79991234567',
      code: '123456',
      newPassword: 'brand-new-password-1',
    })
    expect(localStorage.getItem('pios.identity')).toContain('new-token')

    await userEvent.click(screen.getByRole('button', { name: 'На главную' }))
    expect(await screen.findByText('driver-home-screen')).toBeInTheDocument()
  })

  it('a wrong code shows a generic error, never distinguishing the reason', async () => {
    mockedRequest.mockResolvedValueOnce({}) // POST /v1/identities/recovery/request
    mockedRequest.mockRejectedValueOnce(new ApiError(401, '/v1/identities/recovery/confirm'))

    renderRecovery()
    await userEvent.type(screen.getByLabelText('Номер телефона'), '+79991234567')
    await userEvent.click(screen.getByRole('button', { name: 'Отправить код' }))
    await screen.findByText(/Если этот номер зарегистрирован/)

    await userEvent.type(screen.getByLabelText('Код из SMS'), '000000')
    await userEvent.type(screen.getByLabelText('Новый пароль'), 'brand-new-password-2')
    await userEvent.type(screen.getByLabelText('Повторите новый пароль'), 'brand-new-password-2')
    await userEvent.click(screen.getByRole('button', { name: 'Восстановить доступ' }))

    expect(await screen.findByText('Код неверен, устарел или уже использован. Запросите новый код.')).toBeInTheDocument()
  })

  it('rejects a too-short new password before calling confirm', async () => {
    mockedRequest.mockResolvedValueOnce({}) // POST /v1/identities/recovery/request

    renderRecovery()
    await userEvent.type(screen.getByLabelText('Номер телефона'), '+79991234567')
    await userEvent.click(screen.getByRole('button', { name: 'Отправить код' }))
    await screen.findByText(/Если этот номер зарегистрирован/)

    await userEvent.type(screen.getByLabelText('Код из SMS'), '123456')
    await userEvent.type(screen.getByLabelText('Новый пароль'), 'short')
    await userEvent.type(screen.getByLabelText('Повторите новый пароль'), 'short')
    await userEvent.click(screen.getByRole('button', { name: 'Восстановить доступ' }))

    expect(await screen.findByText('Пароль должен содержать не менее 10 символов.')).toBeInTheDocument()
    expect(mockedRequest.mock.calls.find(([path]) => path === '/v1/identities/recovery/confirm')).toBeUndefined()
  })

  it('rejects mismatched password confirmation before calling confirm', async () => {
    mockedRequest.mockResolvedValueOnce({}) // POST /v1/identities/recovery/request

    renderRecovery()
    await userEvent.type(screen.getByLabelText('Номер телефона'), '+79991234567')
    await userEvent.click(screen.getByRole('button', { name: 'Отправить код' }))
    await screen.findByText(/Если этот номер зарегистрирован/)

    await userEvent.type(screen.getByLabelText('Код из SMS'), '123456')
    await userEvent.type(screen.getByLabelText('Новый пароль'), 'brand-new-password-3')
    await userEvent.type(screen.getByLabelText('Повторите новый пароль'), 'a-different-password')
    await userEvent.click(screen.getByRole('button', { name: 'Восстановить доступ' }))

    expect(await screen.findByText('Пароли не совпадают.')).toBeInTheDocument()
    expect(mockedRequest.mock.calls.find(([path]) => path === '/v1/identities/recovery/confirm')).toBeUndefined()
  })

  it('"Запросить код ещё раз" returns to the request step, which can be resubmitted', async () => {
    mockedRequest.mockResolvedValueOnce({}) // first request

    renderRecovery()
    await userEvent.type(screen.getByLabelText('Номер телефона'), '+79991234567')
    await userEvent.click(screen.getByRole('button', { name: 'Отправить код' }))
    await screen.findByText(/Если этот номер зарегистрирован/)

    await userEvent.click(screen.getByRole('button', { name: 'Запросить код ещё раз' }))
    expect(screen.getByLabelText('Номер телефона')).toBeInTheDocument()

    mockedRequest.mockResolvedValueOnce({}) // second request
    await userEvent.click(screen.getByRole('button', { name: 'Отправить код' }))
    await screen.findByText(/Если этот номер зарегистрирован/)

    expect(mockedRequest.mock.calls.filter(([path]) => path === '/v1/identities/recovery/request')).toHaveLength(2)
  })
})
