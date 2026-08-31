import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, fireEvent, render, screen } from '@testing-library/react'
import { InstallPIOS } from './InstallPIOS'

const IPHONE_SAFARI_UA =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1'
const IPHONE_CHROME_UA =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/125.0.6422.80 Mobile/15E148 Safari/604.1'
const ANDROID_CHROME_UA =
  'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36'
const DESKTOP_CHROME_UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36'

function setUserAgent(ua: string) {
  Object.defineProperty(navigator, 'userAgent', { value: ua, configurable: true })
}

function makeBeforeInstallPromptEvent(outcome: 'accepted' | 'dismissed' = 'accepted') {
  const event = new Event('beforeinstallprompt', { cancelable: true }) as Event & {
    prompt: () => Promise<void>
    userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
  }
  event.prompt = vi.fn().mockResolvedValue(undefined)
  event.userChoice = Promise.resolve({ outcome })
  return event
}

const originalUserAgent = navigator.userAgent

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
  setUserAgent(originalUserAgent)
  Object.defineProperty(window, 'matchMedia', { value: undefined, configurable: true })
  Object.defineProperty(navigator, 'standalone', { value: undefined, configurable: true })
  // Clears any `beforeinstallprompt` a previous test captured, so it never
  // leaks into the next one -- `installPrompt.ts`'s own module-level
  // state, reset the same way a real completed install would.
  window.dispatchEvent(new Event('appinstalled'))
})

describe('InstallPIOS — iPhone', () => {
  it('shows the 4 Safari steps and the honest closing line, not a claimed install', () => {
    setUserAgent(IPHONE_SAFARI_UA)
    render(<InstallPIOS onClose={vi.fn()} />)
    act(() => vi.advanceTimersByTime(400))

    expect(screen.getByText('Установить PIOS на iPhone')).toBeInTheDocument()
    expect(screen.getByText('Откройте PIOS в Safari')).toBeInTheDocument()
    expect(screen.getByText('Нажмите «Поделиться»')).toBeInTheDocument()
    expect(screen.getByText('Выберите «На экран «Домой»»')).toBeInTheDocument()
    expect(screen.getByText('Нажмите «Добавить»')).toBeInTheDocument()
    expect(screen.getByText('Готово. Значок PIOS появится на экране телефона.')).toBeInTheDocument()
    expect(screen.queryByText('PIOS установлен')).not.toBeInTheDocument()
  })

  it('tells a non-Safari iPhone browser to open Safari instead, with no technical detail', () => {
    setUserAgent(IPHONE_CHROME_UA)
    render(<InstallPIOS onClose={vi.fn()} />)
    act(() => vi.advanceTimersByTime(400))

    expect(screen.getByText('Чтобы установить PIOS на iPhone, откройте эту страницу в Safari.')).toBeInTheDocument()
    expect(screen.queryByText('Откройте PIOS в Safari')).not.toBeInTheDocument()
  })
})

describe('InstallPIOS — Android', () => {
  it('shows the 4 Chrome steps when no native prompt is available', () => {
    setUserAgent(ANDROID_CHROME_UA)
    render(<InstallPIOS onClose={vi.fn()} />)
    act(() => vi.advanceTimersByTime(400))

    expect(screen.getByText('Установить PIOS на Android')).toBeInTheDocument()
    expect(screen.getByText('Откройте PIOS в Chrome')).toBeInTheDocument()
    expect(screen.getByText('Выберите «Установить PIOS» или «Установить приложение»')).toBeInTheDocument()
    expect(screen.getByText('Готово. PIOS появится среди приложений на телефоне.')).toBeInTheDocument()
  })

  it('uses the real native prompt instead of manual steps when the browser offers one', async () => {
    setUserAgent(ANDROID_CHROME_UA)
    const event = makeBeforeInstallPromptEvent('accepted')
    window.dispatchEvent(event)

    render(<InstallPIOS onClose={vi.fn()} />)
    act(() => vi.advanceTimersByTime(400))

    expect(screen.getByText('Установить PIOS на телефон')).toBeInTheDocument()
    const button = screen.getByRole('button', { name: 'Установить PIOS' })

    await act(async () => {
      fireEvent.click(button)
      await Promise.resolve()
      await Promise.resolve()
    })

    expect(event.prompt).toHaveBeenCalledTimes(1)
  })

  it('shows "PIOS установлен" only after the real appinstalled event, not after the click alone', async () => {
    setUserAgent(ANDROID_CHROME_UA)
    const event = makeBeforeInstallPromptEvent('accepted')
    window.dispatchEvent(event)

    render(<InstallPIOS onClose={vi.fn()} />)
    act(() => vi.advanceTimersByTime(400))

    const button = screen.getByRole('button', { name: 'Установить PIOS' })
    await act(async () => {
      fireEvent.click(button)
      await Promise.resolve()
      await Promise.resolve()
    })

    // 'accepted' alone is not enough -- Section 13's own rule.
    expect(screen.queryByText('PIOS установлен')).not.toBeInTheDocument()

    act(() => {
      window.dispatchEvent(new Event('appinstalled'))
    })

    expect(screen.getByText('PIOS установлен')).toBeInTheDocument()
    expect(screen.getByText('Теперь вы можете открывать PIOS прямо с экрана телефона.')).toBeInTheDocument()
  })

  it('falls back to manual instructions if the native prompt turns out unavailable when clicked', async () => {
    setUserAgent(ANDROID_CHROME_UA)
    render(<InstallPIOS onClose={vi.fn()} />)
    act(() => vi.advanceTimersByTime(400))

    // No prompt was ever captured -- straight to manual instructions.
    expect(screen.getByText('Откройте PIOS в Chrome')).toBeInTheDocument()
  })
})

describe('InstallPIOS — desktop', () => {
  it('does not fake an install; offers to continue on a phone instead', () => {
    setUserAgent(DESKTOP_CHROME_UA)
    render(<InstallPIOS onClose={vi.fn()} />)
    act(() => vi.advanceTimersByTime(400))

    expect(screen.getByText('На телефоне PIOS удобнее использовать как приложение.')).toBeInTheDocument()
    expect(screen.queryByText('PIOS установлен')).not.toBeInTheDocument()
  })

  it('uses the real native prompt on desktop when the browser offers one', () => {
    setUserAgent(DESKTOP_CHROME_UA)
    window.dispatchEvent(makeBeforeInstallPromptEvent())
    render(<InstallPIOS onClose={vi.fn()} />)
    act(() => vi.advanceTimersByTime(400))

    expect(screen.getByRole('button', { name: 'Установить PIOS' })).toBeInTheDocument()
  })
})

describe('InstallPIOS — already installed', () => {
  it('never claims to install again; states the honest current fact instead', () => {
    setUserAgent(ANDROID_CHROME_UA)
    Object.defineProperty(window, 'matchMedia', {
      value: (query: string) => ({ matches: query === '(display-mode: standalone)' }),
      configurable: true,
    })

    render(<InstallPIOS onClose={vi.fn()} />)

    expect(screen.getByText('PIOS уже установлен')).toBeInTheDocument()
  })
})

describe('InstallPIOS — dismissal', () => {
  it('calls onClose when the close button is clicked', () => {
    setUserAgent(ANDROID_CHROME_UA)
    const onClose = vi.fn()
    render(<InstallPIOS onClose={onClose} />)
    act(() => vi.advanceTimersByTime(400))

    fireEvent.click(screen.getByRole('button', { name: 'Закрыть' }))

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('offers "Позже" and calls onClose without pretending anything was installed', () => {
    setUserAgent(ANDROID_CHROME_UA)
    const onClose = vi.fn()
    render(<InstallPIOS onClose={onClose} />)
    act(() => vi.advanceTimersByTime(400))

    expect(screen.getByText('Можно продолжить без установки.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Позже' }))

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('renders no close/"Позже" control in page mode (no onClose prop)', () => {
    setUserAgent(ANDROID_CHROME_UA)
    render(<InstallPIOS />)
    act(() => vi.advanceTimersByTime(400))

    expect(screen.queryByRole('button', { name: 'Закрыть' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Позже' })).not.toBeInTheDocument()
  })
})

describe('InstallPIOS — "У меня другое устройство"', () => {
  it('reveals a small iPhone/Android choice instead of forcing an upfront pick', () => {
    setUserAgent(ANDROID_CHROME_UA)
    render(<InstallPIOS onClose={vi.fn()} />)
    act(() => vi.advanceTimersByTime(400))

    expect(screen.queryByRole('button', { name: 'iPhone' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByText('У меня другое устройство'))

    expect(screen.getByRole('button', { name: 'iPhone' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'iPhone' }))

    expect(screen.getByText('Установить PIOS на iPhone')).toBeInTheDocument()
  })
})

describe('InstallPIOS — language policy (no PWA jargon)', () => {
  it('never mentions a technical term anywhere, on any screen this test can reach', () => {
    const forbidden = [
      'PWA',
      'Progressive Web App',
      'manifest',
      'service worker',
      'beforeinstallprompt',
      'веб-приложение',
    ]

    setUserAgent(IPHONE_SAFARI_UA)
    const { container: iosContainer, unmount: unmountIos } = render(<InstallPIOS onClose={vi.fn()} />)
    act(() => vi.advanceTimersByTime(400))
    for (const term of forbidden) {
      expect(iosContainer.textContent ?? '').not.toContain(term)
    }
    unmountIos()

    setUserAgent(ANDROID_CHROME_UA)
    const { container: androidContainer } = render(<InstallPIOS onClose={vi.fn()} />)
    act(() => vi.advanceTimersByTime(400))
    for (const term of forbidden) {
      expect(androidContainer.textContent ?? '').not.toContain(term)
    }
  })
})
