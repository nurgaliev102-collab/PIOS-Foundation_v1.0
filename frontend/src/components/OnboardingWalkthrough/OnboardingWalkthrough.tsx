import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { ActionButton } from '../ActionButton'
import styles from './OnboardingWalkthrough.module.css'

export interface OnboardingScene {
  id: string
  title: string
  subtitle?: string
  content?: ReactNode
  /**
   * How long this scene stays on screen before auto-advancing to the next
   * one, in milliseconds. `null` marks the final, user-paced scene: no
   * auto-advance, the walkthrough waits for the CTA button instead.
   */
  durationMs: number | null
}

export interface OnboardingWalkthroughProps {
  scenes: OnboardingScene[]
  ctaLabel: string
  onComplete: () => void
  onSkip: () => void
}

/**
 * PIOS Onboarding v1 — generic walkthrough shell reused by both the driver
 * and passenger onboarding content (`DriverOnboarding.tsx`,
 * `PassengerOnboarding.tsx`). Knows nothing about drivers, passengers,
 * orders, or any backend call — it only sequences whatever `scenes` it is
 * given. No `framer-motion` dependency: scene transitions are a plain CSS
 * `@keyframes` animation retriggered by React's own `key` remount, which is
 * sufficient for this short, linear sequence and adds no new dependency
 * (see implementation report — framer-motion is not installed anywhere in
 * this project and no other animation primitive exists to reuse instead).
 */
export function OnboardingWalkthrough({ scenes, ctaLabel, onComplete, onSkip }: OnboardingWalkthroughProps) {
  const [sceneIndex, setSceneIndex] = useState(0)
  const [isPaused, setIsPaused] = useState(false)
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const scene = scenes[sceneIndex]
  const isFinalScene = scene.durationMs === null

  useEffect(() => {
    if (isPaused || isFinalScene) {
      return
    }

    timeoutRef.current = setTimeout(() => {
      setSceneIndex((current) => Math.min(current + 1, scenes.length - 1))
    }, scene.durationMs ?? 0)

    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current)
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sceneIndex, isPaused])

  function handleReplay() {
    setIsPaused(false)
    setSceneIndex(0)
  }

  return (
    <div className={styles.overlay} role="dialog" aria-modal="true" aria-label="Как работает PIOS">
      <div className={styles.topBar}>
        <div className={styles.progress}>
          {scenes.map((s, index) => (
            <span
              key={s.id}
              className={`${styles.progressDot} ${index <= sceneIndex ? styles.progressDotActive : ''}`}
            />
          ))}
        </div>
        <button type="button" className={styles.skipButton} onClick={onSkip}>
          Пропустить
        </button>
      </div>

      <div key={scene.id} className={styles.sceneContent}>
        <h2 className={styles.title}>{scene.title}</h2>
        {scene.subtitle && <p className={styles.subtitle}>{scene.subtitle}</p>}
        {scene.content && <div className={styles.demoArea}>{scene.content}</div>}
      </div>

      <div className={styles.bottomBar}>
        {!isFinalScene && (
          <button
            type="button"
            className={styles.controlButton}
            onClick={() => setIsPaused((paused) => !paused)}
            aria-label={isPaused ? 'Продолжить' : 'Пауза'}
          >
            {isPaused ? '▶ Продолжить' : '⏸ Пауза'}
          </button>
        )}
        <button type="button" className={styles.controlButton} onClick={handleReplay}>
          ⟲ Заново
        </button>
        {isFinalScene && <ActionButton label={ctaLabel} onClick={onComplete} variant="primary" />}
      </div>
    </div>
  )
}
