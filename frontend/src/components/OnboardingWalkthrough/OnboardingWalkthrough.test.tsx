import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, fireEvent, render, screen } from '@testing-library/react'
import { OnboardingWalkthrough } from './OnboardingWalkthrough'
import type { OnboardingScene } from './OnboardingWalkthrough'

const scenes: OnboardingScene[] = [
  { id: 'one', title: 'Первая сцена', durationMs: 1000 },
  { id: 'two', title: 'Вторая сцена', durationMs: 1000 },
  { id: 'three', title: 'Финальная сцена', durationMs: null },
]

// `act` wraps every `vi.advanceTimersByTime` call: the scene-advance timeout
// is scheduled inside a `useEffect` and fires a state update, which React
// needs flushed synchronously before the next assertion runs -- without
// `act`, fake-timer advances resolve but the resulting re-render is not
// guaranteed to have happened yet when the test reads the DOM.
describe('OnboardingWalkthrough', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('shows the first scene initially', () => {
    render(<OnboardingWalkthrough scenes={scenes} ctaLabel="Начать" onComplete={vi.fn()} onSkip={vi.fn()} />)

    expect(screen.getByText('Первая сцена')).toBeInTheDocument()
  })

  it('auto-advances to the next scene after its duration', () => {
    render(<OnboardingWalkthrough scenes={scenes} ctaLabel="Начать" onComplete={vi.fn()} onSkip={vi.fn()} />)

    act(() => {
      vi.advanceTimersByTime(1000)
    })

    expect(screen.getByText('Вторая сцена')).toBeInTheDocument()
  })

  it('stops auto-advancing on the final scene and shows the CTA instead', () => {
    render(<OnboardingWalkthrough scenes={scenes} ctaLabel="Начать работу" onComplete={vi.fn()} onSkip={vi.fn()} />)

    act(() => {
      vi.advanceTimersByTime(1000)
    })
    act(() => {
      vi.advanceTimersByTime(1000)
    })

    expect(screen.getByText('Финальная сцена')).toBeInTheDocument()
    expect(screen.getByText('Начать работу')).toBeInTheDocument()
  })

  it('calls onComplete when the final CTA is clicked', () => {
    const onComplete = vi.fn()
    render(<OnboardingWalkthrough scenes={scenes} ctaLabel="Начать работу" onComplete={onComplete} onSkip={vi.fn()} />)

    act(() => {
      vi.advanceTimersByTime(1000)
    })
    act(() => {
      vi.advanceTimersByTime(1000)
    })
    fireEvent.click(screen.getByText('Начать работу'))

    expect(onComplete).toHaveBeenCalledTimes(1)
  })

  it('calls onSkip immediately when Skip is clicked, without waiting for scenes', () => {
    const onSkip = vi.fn()
    render(<OnboardingWalkthrough scenes={scenes} ctaLabel="Начать" onComplete={vi.fn()} onSkip={onSkip} />)

    fireEvent.click(screen.getByText('Пропустить'))

    expect(onSkip).toHaveBeenCalledTimes(1)
  })

  it('pauses auto-advance when Pause is clicked', () => {
    render(<OnboardingWalkthrough scenes={scenes} ctaLabel="Начать" onComplete={vi.fn()} onSkip={vi.fn()} />)

    fireEvent.click(screen.getByText('⏸ Пауза'))
    act(() => {
      vi.advanceTimersByTime(5000)
    })

    expect(screen.getByText('Первая сцена')).toBeInTheDocument()
  })

  it('resumes auto-advance after Pause is toggled back to Play', () => {
    render(<OnboardingWalkthrough scenes={scenes} ctaLabel="Начать" onComplete={vi.fn()} onSkip={vi.fn()} />)

    fireEvent.click(screen.getByText('⏸ Пауза'))
    fireEvent.click(screen.getByText('▶ Продолжить'))
    act(() => {
      vi.advanceTimersByTime(1000)
    })

    expect(screen.getByText('Вторая сцена')).toBeInTheDocument()
  })

  it('replays from the first scene when Replay is clicked on a later scene', () => {
    render(<OnboardingWalkthrough scenes={scenes} ctaLabel="Начать работу" onComplete={vi.fn()} onSkip={vi.fn()} />)

    act(() => {
      vi.advanceTimersByTime(1000)
    })
    act(() => {
      vi.advanceTimersByTime(1000)
    })
    expect(screen.getByText('Финальная сцена')).toBeInTheDocument()

    fireEvent.click(screen.getByText('⟲ Заново'))

    expect(screen.getByText('Первая сцена')).toBeInTheDocument()
  })
})
