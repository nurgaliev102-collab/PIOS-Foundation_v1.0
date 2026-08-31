import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, fireEvent, render, screen } from '@testing-library/react'
import { PassengerOnboarding } from './PassengerOnboarding'

describe('PassengerOnboarding', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('opens on the invitation scene', () => {
    render(<PassengerOnboarding onComplete={vi.fn()} onSkip={vi.fn()} />)

    expect(screen.getByText('Вас пригласил Артур')).toBeInTheDocument()
  })

  it('reaches the conclusion scene with its call to action after all scenes elapse', () => {
    render(<PassengerOnboarding onComplete={vi.fn()} onSkip={vi.fn()} />)

    for (const durationMs of [5000, 5000, 5000, 6000]) {
      act(() => {
        vi.advanceTimersByTime(durationMs)
      })
    }

    expect(screen.getByText('Вы видите, что происходит с вашим заказом')).toBeInTheDocument()
    expect(screen.getByText('Продолжить')).toBeInTheDocument()
  })

  it('calls onComplete when the final CTA is clicked', () => {
    const onComplete = vi.fn()
    render(<PassengerOnboarding onComplete={onComplete} onSkip={vi.fn()} />)

    for (const durationMs of [5000, 5000, 5000, 6000]) {
      act(() => {
        vi.advanceTimersByTime(durationMs)
      })
    }
    fireEvent.click(screen.getByText('Продолжить'))

    expect(onComplete).toHaveBeenCalledTimes(1)
  })

  it('calls onSkip when Skip is clicked', () => {
    const onSkip = vi.fn()
    render(<PassengerOnboarding onComplete={vi.fn()} onSkip={onSkip} />)

    fireEvent.click(screen.getByText('Пропустить'))

    expect(onSkip).toHaveBeenCalledTimes(1)
  })

  it('never mentions any forbidden claim, on any scene', () => {
    const { container } = render(<PassengerOnboarding onComplete={vi.fn()} onSkip={vi.fn()} />)
    const forbidden = [
      '100% дохода',
      'без скрытых комиссий',
      'безопасно',
      'верифицированный водитель',
      'автоматически найдём',
      'оплата через PIOS',
      'AI управляет заказом',
    ]
    const sceneDurationsMs = [5000, 5000, 5000, 6000]

    for (const claim of forbidden) {
      expect(container.textContent ?? '').not.toContain(claim)
    }
    for (const durationMs of sceneDurationsMs) {
      act(() => {
        vi.advanceTimersByTime(durationMs)
      })
      for (const claim of forbidden) {
        expect(container.textContent ?? '').not.toContain(claim)
      }
    }
  })
})
