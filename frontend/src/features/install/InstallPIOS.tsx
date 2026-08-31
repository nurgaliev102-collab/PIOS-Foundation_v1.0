import { useEffect, useRef, useState } from 'react'
import { ActionButton } from '../../components/ActionButton'
import { QRCard } from '../../components/QRCard'
import { detectPlatform, isSafari, isStandalone } from './deviceDetection'
import type { Platform } from './deviceDetection'
import { isNativeInstallAvailable, onInstalled, shareInstallLink, triggerNativeInstall } from './installPrompt'
import { markInstallHelpSeen } from '../../persistence/localInstallSeen'
import { InstallInstructions } from './InstallInstructions'
import { InstallSuccess } from './InstallSuccess'
import styles from './InstallPIOS.module.css'

export interface InstallPIOSProps {
  /**
   * Provided only when this renders as an overlay (Driver Home, Passenger
   * Landing, the post-onboarding chain) — shows a close control and lets
   * "Позже"/"Перейти в PIOS" dismiss it. Omitted on the public
   * `/help/install` page, where there is nothing to return to except the
   * browser's own back button (Section 15's own "возможность вернуться
   * назад" is satisfied by the browser chrome itself there).
   */
  onClose?: () => void
}

/**
 * PIOS Install v1 (Product Owner exception, `PIOS_PRODUCT_EVIDENCE.md`
 * gate): drivers and passengers in the real pilot asked, in person, how to
 * put PIOS on their phone's home screen — a real, observed pilot need, but
 * one without a prior `E-NNN` entry (no `PIOS_PILOT_REVIEW_PROTOCOL.md`
 * session has run yet to record it formally) or a pre-registered
 * hypothesis. This Sprint runs on the same kind of explicit, scoped
 * Product Owner exception `DriverOnboarding.tsx`/`PassengerOnboarding.tsx`
 * already used: preparatory pilot infrastructure, not ordinary feature
 * development, and not a precedent for skipping the evidence gate later.
 *
 * The one governing rule (Section "ГЛАВНЫЙ ПРИНЦИП" of the task): a person
 * using this must never need to know what a PWA, manifest, or service
 * worker is. Every string here is reviewed against that — see
 * `InstallPIOS.test.tsx`'s own forbidden-term scan, mirroring
 * `DriverOnboarding.test.tsx`'s identical convention for marketing claims.
 *
 * Network safety (Section 20): this file and everything it imports make
 * zero backend calls — no `api/apiClient`, no `fetch`, no `request`.
 * Everything here is either a browser API (`beforeinstallprompt`,
 * `navigator.share`, `matchMedia`) or `localStorage`.
 */
export function InstallPIOS({ onClose }: InstallPIOSProps) {
  type Phase = 'detecting' | 'auto' | 'manual' | 'desktop-fallback' | 'success' | 'already-installed'

  const [phase, setPhase] = useState<Phase>('detecting')
  const [platformOverride, setPlatformOverride] = useState<Platform | null>(null)
  const [showDeviceChooser, setShowDeviceChooser] = useState(false)
  const [isInstalling, setIsInstalling] = useState(false)
  const [shareFeedback, setShareFeedback] = useState<string | null>(null)
  const feedbackTimeout = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  const platform = platformOverride ?? detectPlatform()

  // Section 14: this screen's own "seen" flag, distinct from onboarding's.
  useEffect(() => {
    markInstallHelpSeen()
  }, [])

  // Section 13: the only trustworthy "installed" signal is the browser's
  // own `appinstalled` event -- subscribed unconditionally, for the
  // lifetime of this component, regardless of which phase led here.
  useEffect(() => onInstalled(() => setPhase('success')), [])

  // Grace period for `beforeinstallprompt`: it may already have fired
  // (captured by `installPrompt.ts` at module load, before this component
  // ever mounted) or may still be pending. iOS never fires it at all
  // (Apple's own platform choice) -- no reason to wait there.
  useEffect(() => {
    if (isStandalone()) {
      setPhase('already-installed')
      return
    }
    if (platform === 'ios') {
      setPhase('manual')
      return
    }
    if (isNativeInstallAvailable()) {
      setPhase('auto')
      return
    }
    const timeout = setTimeout(() => {
      setPhase(isNativeInstallAvailable() ? 'auto' : platform === 'desktop' ? 'desktop-fallback' : 'manual')
    }, 400)
    return () => clearTimeout(timeout)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function handleInstallClick() {
    setIsInstalling(true)
    const outcome = await triggerNativeInstall()
    setIsInstalling(false)
    if (outcome === 'unavailable') {
      // The prompt was consumed or expired between detection and click --
      // the honest fallback is the same manual instructions, not a retry
      // loop or an error message about a technical event.
      setPhase(platform === 'desktop' ? 'desktop-fallback' : 'manual')
    }
    // 'accepted'/'dismissed' both leave the screen as-is: 'accepted' is
    // followed by the real `appinstalled` event (handled above); a person
    // who dismissed the OS-level prompt is still looking at this same
    // screen and can try again or read the manual instructions instead.
  }

  function handleShare() {
    const url = `${window.location.origin}/help/install`
    void shareInstallLink(url).then((result) => {
      if (result === 'shared') {
        return
      }
      setShareFeedback(result === 'copied' ? 'Ссылка скопирована' : 'Не удалось поделиться')
      clearTimeout(feedbackTimeout.current)
      feedbackTimeout.current = setTimeout(() => setShareFeedback(null), 2000)
    })
  }

  const showInstructionsHeading = phase === 'manual' || phase === 'desktop-fallback'

  return (
    <div
      className={`${styles.screen} ${onClose ? styles.overlay : ''}`}
      role={onClose ? 'dialog' : undefined}
      aria-modal={onClose ? true : undefined}
      aria-label={onClose ? 'Установить PIOS' : undefined}
    >
      {onClose && (
        <div className={styles.topBar}>
          <button type="button" className={styles.closeButton} onClick={onClose} aria-label="Закрыть">
            ✕
          </button>
        </div>
      )}

      <div className={styles.content}>
        {phase === 'already-installed' && (
          <div className={styles.centerBlock}>
            <span className={styles.icon} aria-hidden="true">
              📱
            </span>
            <h1 className={styles.title}>PIOS уже установлен</h1>
            <p className={styles.subtitle}>Вы уже открываете PIOS с экрана телефона.</p>
            {onClose && <ActionButton label="Перейти в PIOS" variant="primary" onClick={onClose} />}
          </div>
        )}

        {phase === 'success' && (
          <InstallSuccess onContinue={onClose ?? (() => window.location.assign('/'))} />
        )}

        {phase === 'auto' && (
          <div className={styles.centerBlock}>
            <h1 className={styles.title}>Установить PIOS на телефон</h1>
            <p className={styles.subtitle}>Добавьте PIOS на экран телефона, чтобы открывать его как обычное приложение.</p>
            <ActionButton
              label={isInstalling ? 'Устанавливаем…' : 'Установить PIOS'}
              variant="primary"
              onClick={() => void handleInstallClick()}
              disabled={isInstalling}
            />
          </div>
        )}

        {showInstructionsHeading && (
          <>
            <h1 className={styles.title}>
              {platform === 'ios' && 'Установить PIOS на iPhone'}
              {platform === 'android' && 'Установить PIOS на Android'}
              {platform === 'desktop' && 'Установить PIOS на компьютер'}
            </h1>

            {phase === 'desktop-fallback' ? (
              <div className={styles.desktopFallback}>
                <p className={styles.subtitle}>На телефоне PIOS удобнее использовать как приложение.</p>
                <QRCard invitationLink={`${window.location.origin}/help/install`} label="Ссылка на установку" />
                <p className={styles.hint}>Откройте камеру на телефоне и наведите на код — попадёте прямо сюда.</p>
              </div>
            ) : (
              <InstallInstructions platform={platform === 'android' ? 'android' : 'ios'} isSafari={isSafari()} />
            )}
          </>
        )}

        {phase === 'detecting' && <div className={styles.detecting} aria-hidden="true" />}

        {(phase === 'manual' || phase === 'desktop-fallback') && (
          <div className={styles.secondaryActions}>
            <button type="button" className={styles.textAction} onClick={handleShare}>
              Поделиться инструкцией
            </button>
            {shareFeedback && (
              <p className={styles.feedback} role="status" aria-live="polite">
                {shareFeedback}
              </p>
            )}
            {!showDeviceChooser ? (
              <button type="button" className={styles.textAction} onClick={() => setShowDeviceChooser(true)}>
                У меня другое устройство
              </button>
            ) : (
              <div className={styles.deviceChoice}>
                <button
                  type="button"
                  className={styles.textAction}
                  onClick={() => {
                    setPlatformOverride('ios')
                    setPhase('manual')
                  }}
                >
                  iPhone
                </button>
                <button
                  type="button"
                  className={styles.textAction}
                  onClick={() => {
                    setPlatformOverride('android')
                    setPhase('manual')
                  }}
                >
                  Android
                </button>
              </div>
            )}
          </div>
        )}

        {onClose && phase !== 'success' && (
          <div className={styles.laterBlock}>
            <p className={styles.laterText}>Можно продолжить без установки.</p>
            <button type="button" className={styles.laterButton} onClick={onClose}>
              Позже
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
