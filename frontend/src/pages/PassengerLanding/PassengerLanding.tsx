import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Header } from '../../components/Header'
import { ActionButton } from '../../components/ActionButton'
import { Spinner } from '../../components/Spinner'
import { PasswordInput } from '../../components/PasswordInput'
import { getInvitationByDriverCode } from './invitationSource'
import type { InvitationInfo } from './invitationSource'
import { BackendIdentityProvider } from '../../identity/BackendIdentityProvider'
import type { StoredIdentity } from '../../identity/IdentityProvider'
import { saveDisplayName } from '../../persistence/localDisplayName'
import { ApiError, request } from '../../api/apiClient'
import styles from './PassengerLanding.module.css'

// ADR-038/ADR-039/ADR-055: today's only IdentityProvider — see its own
// KDoc for why this is safe to instantiate once, module-level, exactly
// like `DriverHome.tsx`'s own copy already does. The same backend account
// serves both roles this app has; a passenger simply never calls
// `attachDriver`.
const identityProvider = new BackendIdentityProvider()

const MAX_NAME_LENGTH = 50
const MIN_PASSWORD_LENGTH = 8

// Passenger Experience's own local port (INTERFACE_CONTRACTS.md) — Sprint
// 7B (Personal Network Flow MVP): this page now also calls that module
// directly, to record that this passenger reached PIOS through this
// driver's own invitation link.
const PASSENGER_EXPERIENCE_BASE_URL = import.meta.env.VITE_PASSENGER_EXPERIENCE_BASE_URL ?? 'http://localhost:8082'

type Step = 'loading' | 'not-found' | 'error' | 'invited' | 'auth' | 'confirmed' | 'confirm-add'
type AuthMode = 'register' | 'login'

/** The one field this page needs from `GET /v1/connections?passengerReference=...` (ADR-054) — just enough to check membership. */
interface CircleMembership {
  driverId: string
}

const FAQ_ITEMS: Array<{ question: string; answer: string }> = [
  {
    question: 'Что такое PIOS?',
    answer:
      'PIOS помогает вам быстро связываться с вашим водителем и оформлять поездки без звонков.',
  },
  {
    question: 'Нужно ли регистрироваться каждый раз?',
    answer: 'Нет. Достаточно сделать это один раз.',
  },
  {
    question: 'Что будет, если водитель занят?',
    answer: 'Заказ пойдёт только этому водителю — если он не сможет ответить, свяжитесь с ним напрямую.',
  },
]

/**
 * Passenger Landing — Sprint 8 (First User Experience). Rendered at
 * `/i/:driverCode`, this is a first-time passenger's entire understanding
 * of what PIOS is, formed in one screen — per this sprint's own guiding
 * rule, it must answer three questions before any registration form
 * appears: who invited them, what they get, and what to do next.
 *
 * A returning passenger (a real, backend-verified session already exists —
 * ADR-055) never sees any of this again — this page redirects straight to
 * Ride Request instead, per this sprint's own explicit "не показывать
 * инструкцию повторно" rule.
 *
 * No internal term (Connection, Proposal, Assignment, Dispatch,
 * Passenger, Driver ID) appears in any user-facing string on this page —
 * this sprint's own explicit UX rule.
 */
export function PassengerLanding() {
  const { driverCode } = useParams<{ driverCode: string }>()
  const navigate = useNavigate()
  const [step, setStep] = useState<Step>('loading')
  const [invitation, setInvitation] = useState<InvitationInfo | null>(null)
  const [identity, setIdentity] = useState<StoredIdentity | null>(null)
  const [authMode, setAuthMode] = useState<AuthMode>('register')
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [authError, setAuthError] = useState<string | null>(null)
  const [isSubmittingAuth, setIsSubmittingAuth] = useState(false)
  const [addStatus, setAddStatus] = useState<'idle' | 'submitting' | 'error'>('idle')

  // Sprint 6 (Passenger Entry-Path Failure Handling): pulled out of the
  // effect (mirrors DriverHome.tsx's own loadDriver) so the same fetch can
  // also be re-run by the "Попробовать снова" retry action below, without
  // duplicating this logic.
  function loadInvitation(active: boolean, forDriverCode: string) {
    setStep('loading')
    getInvitationByDriverCode(forDriverCode).then((result) => {
      if (!active) {
        return
      }
      if (result.status === 'not-found') {
        setStep('not-found')
        return
      }
      if (result.status === 'error') {
        setStep('error')
        return
      }
      setInvitation(result.invitation)
      identityProvider.restoreIdentity().then((restored) => {
        if (!active) {
          return
        }
        if (restored) {
          // Returning passenger: the welcome screen and its instructions
          // already did their job on a previous visit. Whether this specific
          // driver is already in their circle of trust still needs checking
          // (Rule 9, `PRODUCT_DECISION_CIRCLE_OF_TRUST.md`: adding one is
          // never silent) — see [checkCircleThenAdvance].
          setIdentity(restored)
          checkCircleThenAdvance(active, restored, forDriverCode)
          return
        }
        setStep('invited')
      })
    })
  }

  /**
   * Rule 9 (`PRODUCT_DECISION_CIRCLE_OF_TRUST.md`): a returning passenger
   * opening a driver's link for the first time must not be silently added
   * to that driver's circle — only opening a link they already recognize
   * (already a member) skips straight through, exactly as before this
   * Sprint. Best-effort: if membership can't be determined, this falls back
   * to today's own behavior rather than blocking the passenger from
   * ordering at all.
   */
  function checkCircleThenAdvance(active: boolean, forIdentity: StoredIdentity, forDriverCode: string) {
    request<CircleMembership[]>(`/v1/connections?passengerReference=${forIdentity.identityId}`, {
      headers: { Authorization: `Bearer ${forIdentity.token}` },
      baseUrl: PASSENGER_EXPERIENCE_BASE_URL,
    })
      .then((members) => {
        if (!active) {
          return
        }
        const alreadyConnected = members.some((member) => member.driverId === forDriverCode)
        if (alreadyConnected) {
          navigate(`/i/${forDriverCode}/request`, { replace: true })
        } else {
          setStep('confirm-add')
        }
      })
      .catch(() => {
        if (active) {
          navigate(`/i/${forDriverCode}/request`, { replace: true })
        }
      })
  }

  /**
   * P0 (Section 16, "Final Pre-Pilot Sprint"): this used to be fire-and-
   * forget — a failed create silently sent the passenger straight to the
   * order form as if it had worked. A passenger explicitly asked to add
   * someone to their circle of trust must be told honestly whether that
   * actually happened.
   */
  async function handleAddToCircle() {
    if (!identity) {
      return
    }
    setAddStatus('submitting')
    try {
      await request('/v1/connections', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${identity.token}` },
        body: JSON.stringify({ driverId: driverCode ?? '', passengerReference: identity.identityId }),
        baseUrl: PASSENGER_EXPERIENCE_BASE_URL,
      })
      navigate(`/i/${driverCode ?? ''}/request`)
    } catch {
      setAddStatus('error')
    }
  }

  function handleSkipAdd() {
    navigate(`/i/${driverCode ?? ''}/request`)
  }

  useEffect(() => {
    let active = true
    loadInvitation(active, driverCode ?? '')
    return () => {
      active = false
    }
  }, [driverCode, navigate])

  function handleContinue() {
    setStep('auth')
  }

  function handleFieldChange(setter: (value: string) => void) {
    return (value: string) => {
      setter(value)
      if (authError) {
        setAuthError(null)
      }
    }
  }

  function toggleAuthMode() {
    setAuthMode((current) => (current === 'register' ? 'login' : 'register'))
    setAuthError(null)
  }

  /**
   * ADR-055: a brand-new account, created specifically through this
   * driver's own invitation link — the intent to connect with them is
   * already obvious, so this bootstraps the connection (and, since it is
   * necessarily this account's first, sets it primary — Rule 4 governs
   * *changing* an existing primary, and there is none yet here) without a
   * separate "Добавить" confirmation. Best-effort, same tolerance
   * `PRODUCT_DECISION_CIRCLE_OF_TRUST.md` already accepted for this
   * bootstrap step; the *explicit* "Добавить в круг доверия" action
   * ([handleAddToCircle]) is the one Section 16 requires to be honest about
   * failure, not this implicit one.
   */
  async function handleRegisterSubmit() {
    const trimmedName = name.trim()
    if (!trimmedName) {
      setAuthError('Пожалуйста, введите имя.')
      return
    }
    if (trimmedName.length > MAX_NAME_LENGTH) {
      setAuthError(`Имя должно быть короче ${MAX_NAME_LENGTH} символов.`)
      return
    }
    const trimmedPhone = phone.trim()
    if (!trimmedPhone) {
      setAuthError('Пожалуйста, укажите номер телефона.')
      return
    }
    if (password.length < MIN_PASSWORD_LENGTH) {
      setAuthError(`Пароль должен быть не короче ${MIN_PASSWORD_LENGTH} символов.`)
      return
    }
    setAuthError(null)
    setIsSubmittingAuth(true)
    try {
      const created = await identityProvider.register(trimmedPhone, password)
      saveDisplayName(trimmedName)
      setIdentity(created)
      setStep('confirmed')

      try {
        const connection = await request<{ connectionId: string }>('/v1/connections', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${created.token}` },
          body: JSON.stringify({ driverId: driverCode ?? '', passengerReference: created.identityId }),
          baseUrl: PASSENGER_EXPERIENCE_BASE_URL,
        })
        await request(`/v1/connections/${connection.connectionId}/primary`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${created.token}` },
          baseUrl: PASSENGER_EXPERIENCE_BASE_URL,
        })
      } catch {
        // Best-effort bootstrap -- see this function's own KDoc.
      }
    } catch (error) {
      setAuthError(
        error instanceof ApiError && error.status === 409
          ? 'Этот номер телефона уже зарегистрирован. Попробуйте войти.'
          : 'Не удалось создать аккаунт. Проверьте связь с интернетом и попробуйте ещё раз.'
      )
    } finally {
      setIsSubmittingAuth(false)
    }
  }

  async function handleLoginSubmit() {
    const trimmedPhone = phone.trim()
    if (!trimmedPhone) {
      setAuthError('Пожалуйста, укажите номер телефона.')
      return
    }
    if (!password) {
      setAuthError('Пожалуйста, введите пароль.')
      return
    }
    setAuthError(null)
    setIsSubmittingAuth(true)
    try {
      const loggedIn = await identityProvider.login(trimmedPhone, password)
      setIdentity(loggedIn)
      checkCircleThenAdvance(true, loggedIn, driverCode ?? '')
    } catch (error) {
      setAuthError(
        error instanceof ApiError && error.status === 401
          ? 'Неверный номер телефона или пароль.'
          : 'Не удалось войти. Проверьте связь с интернетом и попробуйте ещё раз.'
      )
    } finally {
      setIsSubmittingAuth(false)
    }
  }

  function handleCreateFirstOrder() {
    navigate(`/i/${driverCode ?? ''}/request`)
  }

  return (
    <div className={styles.screen}>
      <Header />
      <main className={styles.content}>
        {step === 'loading' && <Spinner label="Загрузка…" />}

        {step === 'not-found' && (
          <p className={styles.status}>Ссылка недействительна или водитель ещё не зарегистрирован.</p>
        )}

        {step === 'error' && (
          <div className={styles.errorBlock}>
            <p className={styles.error} role="alert">
              Не удалось загрузить приглашение. Проверьте связь с интернетом.
            </p>
            <ActionButton
              label="Попробовать снова"
              variant="secondary"
              onClick={() => loadInvitation(true, driverCode ?? '')}
            />
          </div>
        )}

        {step === 'invited' && invitation && (
          <>
            <div className={styles.heroAvatar} aria-hidden="true">
              {invitation.driverName.trim().charAt(0).toUpperCase()}
            </div>
            <p className={styles.heroCaption}>Ваш водитель — {invitation.driverName}</p>

            <h1 className={styles.title}>👋 Вас пригласил {invitation.driverName}</h1>
            <p className={styles.subtitle}>
              Теперь вы можете быстро заказывать поездки через личный профиль {invitation.driverName}.
            </p>

            <section className={styles.stepsCard}>
              <h2 className={styles.stepsTitle}>Как работает PIOS</h2>

              <div className={styles.stepRow}>
                <span className={styles.stepEmoji} aria-hidden="true">
                  🚖
                </span>
                <div>
                  <p className={styles.stepTitle}>Заказывайте поездки</p>
                  <p className={styles.stepDescription}>Создавайте заказ прямо в приложении.</p>
                </div>
              </div>

              <div className={styles.stepRow}>
                <span className={styles.stepEmoji} aria-hidden="true">
                  👤
                </span>
                <div>
                  <p className={styles.stepTitle}>Заказ получает {invitation.driverName}</p>
                  <p className={styles.stepDescription}>Заказ приходит напрямую ему — и больше никому.</p>
                </div>
              </div>

              <div className={styles.stepRow}>
                <span className={styles.stepEmoji} aria-hidden="true">
                  📵
                </span>
                <div>
                  <p className={styles.stepTitle}>Если {invitation.driverName} не отвечает</p>
                  <p className={styles.stepDescription}>
                    Свяжитесь с ним напрямую — заказ не передаётся другому водителю.
                  </p>
                </div>
              </div>
            </section>

            <div className={styles.actionRow}>
              <ActionButton label="Начать" variant="primary" onClick={handleContinue} />
            </div>

            <section className={styles.faqSection}>
              {FAQ_ITEMS.map((item) => (
                <div key={item.question} className={styles.faqItem}>
                  <p className={styles.faqQuestion}>{item.question}</p>
                  <p className={styles.faqAnswer}>{item.answer}</p>
                </div>
              ))}
            </section>
          </>
        )}

        {step === 'confirm-add' && invitation && (
          <>
            <h1 className={styles.title}>Добавить {invitation.driverName} в круг доверия?</h1>
            <p className={styles.subtitle}>
              Вы сможете заказывать поездки у {invitation.driverName} — он останется в списке ваших доверенных
              предпринимателей, и вы сможете выбрать его снова в любой момент.
            </p>
            <div className={styles.actionRow}>
              <ActionButton
                label={addStatus === 'submitting' ? 'Добавляем…' : 'Добавить'}
                variant="primary"
                onClick={() => void handleAddToCircle()}
                disabled={addStatus === 'submitting'}
              />
              <ActionButton label="Не сейчас" variant="secondary" onClick={handleSkipAdd} />
            </div>
            {addStatus === 'error' && (
              <p className={styles.error} role="alert">
                Не удалось добавить. Проверьте связь с интернетом и попробуйте ещё раз.
              </p>
            )}
          </>
        )}

        {step === 'auth' && (
          <>
            <h1 className={styles.question}>
              {authMode === 'register' ? 'Создайте свой аккаунт PIOS' : 'Войти в PIOS'}
            </h1>
            {authMode === 'register' && (
              <input
                className={styles.input}
                type="text"
                value={name}
                maxLength={MAX_NAME_LENGTH}
                placeholder="Ваше имя"
                aria-label="Ваше имя"
                onChange={(event) => handleFieldChange(setName)(event.target.value)}
              />
            )}
            <input
              className={styles.input}
              type="tel"
              value={phone}
              placeholder="Номер телефона"
              aria-label="Номер телефона"
              onChange={(event) => handleFieldChange(setPhone)(event.target.value)}
            />
            <PasswordInput
              className={styles.input}
              value={password}
              onChange={handleFieldChange(setPassword)}
              placeholder="Пароль"
              ariaLabel="Пароль"
              autoComplete={authMode === 'register' ? 'new-password' : 'current-password'}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  void (authMode === 'register' ? handleRegisterSubmit() : handleLoginSubmit())
                }
              }}
            />
            {authError && (
              <p className={styles.error} role="alert">
                {authError}
              </p>
            )}
            <div className={styles.actionRow}>
              <ActionButton
                label={
                  isSubmittingAuth
                    ? authMode === 'register'
                      ? 'Создаём…'
                      : 'Входим…'
                    : authMode === 'register'
                      ? 'Создать аккаунт'
                      : 'Войти'
                }
                variant="primary"
                onClick={() => void (authMode === 'register' ? handleRegisterSubmit() : handleLoginSubmit())}
                disabled={isSubmittingAuth}
              />
            </div>
            <button type="button" className={styles.linkAction} onClick={toggleAuthMode}>
              {authMode === 'register' ? 'Уже есть аккаунт? Войти' : 'Ещё нет аккаунта? Создать'}
            </button>
          </>
        )}

        {step === 'confirmed' && identity && (
          <>
            <h1 className={styles.greeting}>Добро пожаловать!</h1>
            <p className={styles.subtitle}>Вы успешно подключены к PIOS.</p>

            <section className={styles.checklist}>
              <p className={styles.checklistTitle}>Теперь вы можете:</p>
              <p className={styles.checklistItem}>✅ заказать поездку</p>
              <p className={styles.checklistItem}>✅ пользоваться личной ссылкой {invitation?.driverName}</p>
              <p className={styles.checklistItem}>✅ не искать его номер телефона</p>
            </section>

            <div className={styles.actionRow}>
              <ActionButton label="Создать первый заказ" variant="primary" onClick={handleCreateFirstOrder} />
            </div>
          </>
        )}
      </main>
    </div>
  )
}
