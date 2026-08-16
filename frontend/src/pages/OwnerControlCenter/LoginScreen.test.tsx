import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { LoginScreen } from './LoginScreen'
import { getStoredOwnerCredential } from './ownerCredential'

/**
 * Covers the three failure states `PIOS_OWNER_CONTROL_CENTER_MVP_DESIGN.md`
 * Section 4.3 requires stay textually distinct: wrong credential, cannot
 * verify right now, and (implicitly, by its absence here) success.
 */
describe('LoginScreen', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows "Неверный логин или пароль" when every module answers 401, and does not store a credential', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 401 }))
    const user = userEvent.setup()
    render(<LoginScreen onLoggedIn={vi.fn()} />)

    await user.type(screen.getByLabelText('Логин'), 'owner')
    await user.type(screen.getByLabelText('Пароль'), 'wrong-password')
    await user.click(screen.getByRole('button', { name: 'Войти' }))

    expect(await screen.findByText('Неверный логин или пароль')).toBeInTheDocument()
    expect(getStoredOwnerCredential()).toBeNull()
  })

  it('shows "Сейчас не удаётся проверить пароль..." — never "wrong password" — when no module answers at all', async () => {
    vi.mocked(fetch).mockRejectedValue(new TypeError('Failed to fetch'))
    const user = userEvent.setup()
    render(<LoginScreen onLoggedIn={vi.fn()} />)

    await user.type(screen.getByLabelText('Логин'), 'owner')
    await user.type(screen.getByLabelText('Пароль'), 'owner-password')
    await user.click(screen.getByRole('button', { name: 'Войти' }))

    expect(await screen.findByText('Сейчас не удаётся проверить пароль. Попробуйте через минуту')).toBeInTheDocument()
    expect(screen.queryByText('Неверный логин или пароль')).not.toBeInTheDocument()
  })

  it('stores the credential and calls onLoggedIn when at least one module answers 200', async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({ module: 'driver-management', status: 'UP', database: 'UP', checkedAt: '2026-08-02T15:47:03Z' }),
        { status: 200 }
      )
    )
    const onLoggedIn = vi.fn()
    const user = userEvent.setup()
    render(<LoginScreen onLoggedIn={onLoggedIn} />)

    await user.type(screen.getByLabelText('Логин'), 'owner')
    await user.type(screen.getByLabelText('Пароль'), 'correct-password')
    await user.click(screen.getByRole('button', { name: 'Войти' }))

    await waitFor(() => expect(onLoggedIn).toHaveBeenCalled())
    expect(getStoredOwnerCredential()).toEqual({ username: 'owner', password: 'correct-password' })
  })

  it('the Войти button is disabled until both fields are filled', () => {
    render(<LoginScreen onLoggedIn={vi.fn()} />)

    expect(screen.getByRole('button', { name: 'Войти' })).toBeDisabled()
  })

  it('shows the default "Пульт владельца" subtitle when none is given, and a caller-supplied subtitle when it is (ADR-061)', () => {
    const { rerender } = render(<LoginScreen onLoggedIn={vi.fn()} />)
    expect(screen.getByText('Пульт владельца')).toBeInTheDocument()

    rerender(<LoginScreen onLoggedIn={vi.fn()} subtitle="Координатор" />)
    expect(screen.getByText('Координатор')).toBeInTheDocument()
    expect(screen.queryByText('Пульт владельца')).not.toBeInTheDocument()
  })
})
