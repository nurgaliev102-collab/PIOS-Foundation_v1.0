import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import { InstallHelp } from './InstallHelp'

const ANDROID_CHROME_UA =
  'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36'

beforeEach(() => {
  vi.useFakeTimers()
  Object.defineProperty(navigator, 'userAgent', { value: ANDROID_CHROME_UA, configurable: true })
})

afterEach(() => {
  vi.useRealTimers()
})

describe('InstallHelp (public /help/install route)', () => {
  it('renders without requiring any identity/session — no auth gate of any kind', () => {
    render(<InstallHelp />)
    act(() => vi.advanceTimersByTime(400))

    expect(screen.getByText('Установить PIOS на Android')).toBeInTheDocument()
  })

  it('renders as a real page, not an overlay -- no close/"Позже" control', () => {
    render(<InstallHelp />)
    act(() => vi.advanceTimersByTime(400))

    expect(screen.queryByRole('button', { name: 'Закрыть' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Позже' })).not.toBeInTheDocument()
  })

  it('shows the PIOS header, same as every other screen', () => {
    render(<InstallHelp />)
    expect(screen.getByText('PIOS')).toBeInTheDocument()
  })
})
