import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Header } from '../../components/Header'
import { Button } from '../../components/Button'
import { LoadingState } from '../../components/LoadingState'
import { ErrorState } from '../../components/ErrorState'
import { StatusMessage } from '../../components/StatusMessage'
import { PasswordInput } from '../../components/PasswordInput'
import { DriverTrustIndicator } from '../../components/DriverTrustIndicator'
import { Heading } from '../../components/Heading'
import { FormField } from '../../components/FormField'
import { Card } from '../../components/Card'
import { getInvitationByDriverCode } from './invitationSource'
import type { InvitationInfo } from './invitationSource'
import { BackendIdentityProvider } from '../../identity/BackendIdentityProvider'
import type { StoredIdentity } from '../../identity/IdentityProvider'
import { normalizePhone, isValidPhone, PHONE_FORMAT_HINT } from '../../identity/phoneFormat'
import { saveDisplayName } from '../../persistence/localDisplayName'
import { ApiError, isSessionExpiredError, request, resolveBackendBaseUrl, SESSION_EXPIRED_MESSAGE } from '../../api/apiClient'
import { useBackableStep } from '../../navigation/useBackableStep'
import { PassengerOnboarding } from './PassengerOnboarding'
import { hasSeenPassengerOnboarding, markPassengerOnboardingSeen } from '../../persistence/localOnboardingSeen'
import { InstallPIOS, isStandalone } from '../../features/install'
import { hasSeenInstallHelp } from '../../persistence/localInstallSeen'
import styles from './PassengerLanding.module.css'

// ADR-038/ADR-039/ADR-055: today's only IdentityProvider — see its own
// KDoc for why this is safe to instantiate once, module-level, exactly
// like `DriverHome.tsx`'s own copy already does. The same backend account
// serves both roles this app has; a passenger simply never calls
// `attachDriver`.
const identityProvider = new BackendIdentityProvider()

const MAX_NAME_LENGTH = 50
const MIN_PASSWORD_LENGTH = 10

// Connection reliability audit (2026-09-12): see [bootstrapCircleOfTrust]'s
// own KDoc for why retrying is safe. One initial attempt plus two retries;
// a short, flat delay -- not exponential backoff, which this fix's own
// scope does not call for.
const CONNECTION_BOOTSTRAP_MAX_ATTEMPTS = 3
const CONNECTION_BOOTSTRAP_RETRY_DELAY_MS = 300

// Passenger Experience's own local port (INTERFACE_CONTRACTS.md) — Sprint
// 7B (Personal Network Flow MVP): this page now also calls that module
// directly, to record that this passenger reached PIOS through this
// driver's own invitation link.
//
// Product audit (2026-09-11): resolved via [resolveBackendBaseUrl] -- see
// that function's own KDoc (`api/apiClient.ts`). A real passenger's own
// phone must reach this through `server/serve.mjs`'s same-origin reverse
// proxy, not a `localhost:8082` that only ever meant the machine running
// this browser, not the pilot host.
const PASSENGER_EXPERIENCE_BASE_URL = resolveBackendBaseUrl(import.meta.env.VITE_PASSENGER_EXPERIENCE_BASE_URL, 'http://localhost:8082')

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
  const [guestSessionStatus, setGuestSessionStatus] = useState<'idle' | 'submitting' | 'error'>('idle')
  const [addStatus, setAddStatus] = useState<'idle' | 'submitting' | 'error'>('idle')
  // P1 UX audit (2026-09-12): see [handleSessionExpiredError]'s own KDoc.
  const [sessionExpired, setSessionExpired] = useState(false)
  // P1 UX audit (2026-09-12): 'auth' is only ever reached from 'invited'
  // (via [handleContinue], the "Начать" button) -- unconditionally
  // enabled since no other path reaches it. See [useBackableStep]'s own
  // KDoc for why this never touches the route/URL or affects a deep link.
  useBackableStep(step, setStep, 'auth', 'invited', true)

  // PIOS Onboarding v1 (Product Owner exception, PIOS_PRODUCT_EVIDENCE.md
  // gate): shown once, automatically, the first time this passenger reaches
  // the real "invited" screen -- mirrors DriverHome.tsx's own approach.
  const [showOnboarding, setShowOnboarding] = useState(false)
  const hasAutoShownOnboarding = useRef(false)

  // PIOS Install v1 (Product Owner exception, same gate/note as
  // `DriverHome.tsx`'s own identical addition): a separate overlay and a
  // separate "seen" flag (`localInstallSeen.ts`, never
  // `localOnboardingSeen.ts` — Section 14). Chained once after this
  // passenger's very first onboarding completion, never on a manual "Как
  // это работает" replay.
  const [showInstall, setShowInstall] = useState(false)

  useEffect(() => {
    if (step === 'invited' && !hasAutoShownOnboarding.current && !hasSeenPassengerOnboarding()) {
      hasAutoShownOnboarding.current = true
      setShowOnboarding(true)
    }
  }, [step])

  function handleOnboardingDismiss() {
    const isFirstOnboarding = !hasSeenPassengerOnboarding()
    markPassengerOnboardingSeen()
    setShowOnboarding(false)
    if (isFirstOnboarding && !hasSeenInstallHelp() && !isStandalone()) {
      setShowInstall(true)
    }
  }

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
    } catch (error) {
      if (handleSessionExpiredError(error)) {
        return
      }
      setAddStatus('error')
    }
  }

  /**
   * P1 UX audit (2026-09-12): the one place this screen checks whether a
   * failure was actually an expired/invalidated session
   * ([isSessionExpiredError], `api/apiClient.ts`) before falling back to
   * existing, unchanged generic-error handling. Clears the stale identity
   * and returns to 'invited' -- this screen's own starting point, where
   * "Начать" leads back into a fresh register/login -- since there is no
   * separate login route to send a passenger to on this specific screen.
   */
  function handleSessionExpiredError(error: unknown): boolean {
    if (isSessionExpiredError(error)) {
      identityProvider.logout()
      setIdentity(null)
      setSessionExpired(true)
      return true
    }
    return false
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

  async function handleGuestOrder() {
    if (guestSessionStatus === 'submitting') {
      return
    }
    setGuestSessionStatus('submitting')
    try {
      const guest = await identityProvider.createGuest()
      setIdentity(guest)
      navigate(`/i/${driverCode ?? ''}/request`)
    } catch {
      setGuestSessionStatus('error')
    }
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
   * Connection reliability audit (2026-09-12): [bootstrapCircleOfTrust]
   * used to be a single, unretried attempt -- a transient network failure
   * silently and permanently lost the one relationship an entire referral
   * exists to create. Retrying is unconditionally safe here, verified by
   * reading the real backend code, not assumed: `POST /v1/connections`
   * (`CreateConnectionApplicationService.kt`) is check-then-create with a
   * `UNIQUE(driver_id, passenger_reference)` database constraint
   * (`V1__create_connections.sql`) as the actual race guard -- calling it
   * again with the same pair returns the same existing row, never a
   * duplicate. `POST /v1/connections/:id/primary`
   * (`SetPrimaryConnectionApplicationService.kt` ->
   * `PostgreSQLPrimaryConnectionRepository.setPrimary`) is a true
   * `INSERT ... ON CONFLICT (passenger_reference) DO UPDATE` upsert --
   * calling it again with the same `connectionId` is a no-op. Neither
   * endpoint, the database schema, nor any new idempotency-key mechanism
   * is touched by this fix; the natural key already made both calls safe
   * to repeat.
   *
   * [CONNECTION_BOOTSTRAP_MAX_ATTEMPTS] = 3 (one initial attempt, two
   * retries), [CONNECTION_BOOTSTRAP_RETRY_DELAY_MS] a short, flat pause
   * between them -- deliberately not exponential backoff or a configurable
   * policy, which this fix's own scope does not call for. Retries the
   * *whole* two-call sequence on any failure of either call, rather than
   * trying to resume from whichever call failed -- simpler, and just as
   * safe, since both calls are independently idempotent.
   *
   * Still best-effort once attempts are exhausted -- this does not change
   * the existing successful scenario, and does not add a new user-facing
   * failure indicator (a deliberately separate, not-yet-decided
   * follow-up); it only makes the already-intended outcome survive a
   * transient blip instead of any single dropped request losing it
   * forever. The existing, already-honest recovery path (reopening the
   * same invitation link routes an unconnected returning passenger to the
   * 'confirm-add' screen) is unchanged.
   */
  async function bootstrapCircleOfTrust(identity: StoredIdentity, forDriverCode: string): Promise<void> {
    for (let attempt = 1; attempt <= CONNECTION_BOOTSTRAP_MAX_ATTEMPTS; attempt += 1) {
      try {
        const connection = await request<{ connectionId: string }>('/v1/connections', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${identity.token}` },
          body: JSON.stringify({ driverId: forDriverCode, passengerReference: identity.identityId }),
          baseUrl: PASSENGER_EXPERIENCE_BASE_URL,
        })
        await request(`/v1/connections/${connection.connectionId}/primary`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${identity.token}` },
          baseUrl: PASSENGER_EXPERIENCE_BASE_URL,
        })
        return
      } catch {
        if (attempt === CONNECTION_BOOTSTRAP_MAX_ATTEMPTS) {
          // Best-effort bootstrap -- see this function's own KDoc. All
          // attempts exhausted; the passenger still proceeds (see call
          // site), unchanged from before this fix.
          return
        }
        await new Promise((resolve) => setTimeout(resolve, CONNECTION_BOOTSTRAP_RETRY_DELAY_MS))
      }
    }
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
   * failure, not this implicit one. See [bootstrapCircleOfTrust]'s own
   * KDoc for the retry behavior added on top of that same tolerance.
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
    const trimmedPhone = normalizePhone(phone.trim())
    if (!trimmedPhone) {
      setAuthError('Пожалуйста, укажите номер телефона.')
      return
    }
    // E-001 fix (docs/PIOS_PRODUCT_EVIDENCE.md) -- see DriverHome.tsx's own
    // identical guard and phoneFormat.ts's own KDoc for the full incident.
    if (!isValidPhone(trimmedPhone)) {
      setAuthError(PHONE_FORMAT_HINT)
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

      await bootstrapCircleOfTrust(created, driverCode ?? '')
    } catch (error) {
      setAuthError(
        error instanceof ApiError && error.status === 409
          ? 'Этот номер телефона уже зарегистрирован. Попробуйте войти.'
          : error instanceof ApiError
            ? 'Не удалось создать аккаунт. Проверьте введённые данные и попробуйте ещё раз.'
            : 'Не удалось создать аккаунт. Проверьте связь с интернетом и попробуйте ещё раз.'
      )
    } finally {
      setIsSubmittingAuth(false)
    }
  }

  async function handleLoginSubmit() {
    const trimmedPhone = normalizePhone(phone.trim())
    if (!trimmedPhone) {
      setAuthError('Пожалуйста, укажите номер телефона.')
      return
    }
    if (!isValidPhone(trimmedPhone)) {
      setAuthError(PHONE_FORMAT_HINT)
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

  // P1 UX audit (2026-09-12): checked ahead of every `step` branch below --
  // once a session is known to be invalid, nothing else on this screen
  // (which is only ever reached with an identity already needed for
  // `confirm-add`/`handleAddToCircle`) can succeed with the same stale
  // token. See [handleSessionExpiredError]'s own KDoc for why this
  // specific screen resets to 'invited' rather than a separate login route.
  if (sessionExpired) {
    return (
      <div className={styles.screen}>
        <Header />
        <main className={styles.content}>
          <StatusMessage tone="error">{SESSION_EXPIRED_MESSAGE}</StatusMessage>
          <div className={styles.actionRow}>
            <Button
              label="Войти снова"
              variant="primary"
              onClick={() => {
                setSessionExpired(false)
                setStep('invited')
              }}
            />
          </div>
        </main>
      </div>
    )
  }

  return (
    <div className={styles.screen}>
      <Header />
      <main className={styles.content}>
        {step === 'loading' && <LoadingState label="Загрузка…" />}

        {step === 'not-found' && (
          <StatusMessage tone="warning">Ссылка недействительна или водитель ещё не зарегистрирован.</StatusMessage>
        )}

        {step === 'error' && (
          <ErrorState
            message="Не удалось загрузить приглашение. Проверьте связь с интернетом."
            onRetry={() => loadInvitation(true, driverCode ?? '')}
          />
        )}

        {step === 'invited' && invitation && (
          <>
            {/* DRIVER-AS-FACE, PIOS-AS-FRAME
                (docs/PASSENGER_INVITATION_DESIGN_DECISION.md): one
                identity element, not four separate name treatments
                (the former .heroAvatar + .heroCaption + emoji heading
                repeated the same fact three times before this task).
                The <h1> below carries the same, unmodified sentence the
                page always showed — now sized as a supporting line under
                the driver's own name, not competing with it, and kept as
                a real <h1> for document structure/accessibility even
                though DriverTrustIndicator is the larger visual element. */}
            {/* The name is established once, here — DriverTrustIndicator's
                own name is the introduction. The heading and subtitle
                below deliberately no longer repeat it
                (docs/PASSENGER_INVITATION_DESIGN_DECISION.md Section 2:
                "rendered once, unambiguously, at the top") — this trims
                what was 4 separate name mentions on this step down to 2
                (this one, and the steps card's own distinct "the order
                goes specifically to them" fact below, which explains a
                different thing and stays). */}
            {/* Referral funnel friction audit (2026-09-12): [availability]
                (see [InvitationInfo]'s own KDoc) shown at the very first
                moment a brand-new referral sees this driver at all — before
                registering, before ordering. No backend change: the value
                was already fetched and silently dropped. */}
            <DriverTrustIndicator
              name={invitation.driverName}
              availability={invitation.availability}
              emphasis="prominent"
              showAvatar
            />
            <Heading level={1} visual="heading">
              Вас пригласили лично
            </Heading>
            <p className={styles.subtitle}>Теперь вы можете быстро заказывать поездки через личный профиль.</p>
            {/* PIOS Group and Long-Distance Rides Roadmap, Stage 1: shown
                only once the driver has declared a make/model -- a driver
                with no vehicle on file simply shows nothing here, the same
                graceful-degradation convention this screen already applies
                to every other optional fact. */}
            {(invitation.vehicleMake || invitation.vehicleModel) && (
              <p className={styles.subtitle}>
                Машина: {[invitation.vehicleMake, invitation.vehicleModel].filter(Boolean).join(' ')}
                {invitation.vehicleColor ? `, ${invitation.vehicleColor}` : ''}
                {invitation.vehiclePlateNumber ? ` · ${invitation.vehiclePlateNumber}` : ''}
              </p>
            )}
            {/* PIOS Group and Long-Distance Rides Roadmap, Stage 3: shown
                only when this driver has opted in -- a plain fact, no
                automatic matching against anything the passenger typed. */}
            {invitation.acceptsLongDistanceTrips && (
              <p className={styles.subtitle}>Берёт дальние поездки (вахта, аэропорт, другой город)</p>
            )}
            <button type="button" className={styles.linkAction} onClick={() => setShowOnboarding(true)}>
              Как это работает
            </button>

            {/* Primary action moved up, directly under the identity and
                its one-line explanation — docs/PASSENGER_INVITATION_DESIGN_DECISION.md
                Section 5: reachable without scrolling past two full
                explanatory sections first, unchanged action/handler. */}
            <div className={styles.actionRow}>
              <Button
                label="Заказать без регистрации"
                variant="primary"
                loading={guestSessionStatus === 'submitting'}
                onClick={() => void handleGuestOrder()}
              />
              <Button label="Начать" variant="secondary" onClick={handleContinue} />
            </div>
            {guestSessionStatus === 'error' && (
              <StatusMessage tone="error">Не удалось начать. Проверьте связь и попробуйте ещё раз.</StatusMessage>
            )}

            <Card>
              <h2 className={styles.stepsTitle}>Как работает PIOS</h2>

              <div className={styles.stepRow}>
                <span className={styles.stepIcon} aria-hidden="true">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M4 16v-3.5l1.7-4A2 2 0 0 1 7.6 7h8.8a2 2 0 0 1 1.9 1.4l1.4 4.1V16" />
                    <path d="M4 16h16" />
                    <circle cx="7.5" cy="16" r="1.6" />
                    <circle cx="16.5" cy="16" r="1.6" />
                  </svg>
                </span>
                <div>
                  <p className={styles.stepTitle}>Заказывайте поездки</p>
                  <p className={styles.stepDescription}>Создавайте заказ прямо в приложении.</p>
                </div>
              </div>

              <div className={styles.stepRow}>
                <span className={styles.stepIcon} aria-hidden="true">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                    <circle cx="12" cy="8.5" r="3.25" />
                    <path d="M5 20c1-3.5 4-5.5 7-5.5s6 2 7 5.5" />
                  </svg>
                </span>
                <div>
                  <p className={styles.stepTitle}>Заказ получает {invitation.driverName}</p>
                  <p className={styles.stepDescription}>Заказ приходит напрямую ему — и больше никому.</p>
                </div>
              </div>
            </Card>

            {/* PIOS Install v1 (Product Owner exception): additive, placed
                after the primary "Начать" action so it never competes with
                registration (Section 9's own explicit rule) — hidden once
                PIOS is already running installed. */}
            {!isStandalone() && (
              <Card>
                <p className={styles.installCardTitle}>Установить PIOS</p>
                <p className={styles.installCardText}>
                  Добавьте PIOS на экран телефона, чтобы в следующий раз быстро заказать поездку.
                </p>
                <Button label="Установить PIOS" variant="secondary" onClick={() => setShowInstall(true)} />
              </Card>
            )}

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
            <DriverTrustIndicator
              name={invitation.driverName}
              availability={invitation.availability}
              emphasis="prominent"
              showAvatar
            />
            <Heading level={1} visual="heading">
              Добавить {invitation.driverName} в ваш список водителей?
            </Heading>
            <p className={styles.subtitle}>
              Вы сможете заказывать поездки у {invitation.driverName} и в следующий раз — он останется в списке ваших
              водителей.
            </p>
            <div className={styles.actionRow}>
              <Button
                label="Добавить"
                variant="primary"
                loading={addStatus === 'submitting'}
                onClick={() => void handleAddToCircle()}
              />
              <Button label="Не сейчас" variant="secondary" onClick={handleSkipAdd} />
            </div>
            {addStatus === 'error' && (
              <StatusMessage tone="error">Не удалось добавить. Проверьте связь с интернетом и попробуйте ещё раз.</StatusMessage>
            )}
          </>
        )}

        {step === 'auth' && (
          <>
            <Heading level={1} visual="display">
              {authMode === 'register' ? 'Создайте свой аккаунт PIOS' : 'Войти в PIOS'}
            </Heading>
            {authMode === 'register' && (
              <FormField label="Ваше имя" htmlFor="passenger-name">
                <input
                  id="passenger-name"
                  className={styles.input}
                  type="text"
                  value={name}
                  maxLength={MAX_NAME_LENGTH}
                  onChange={(event) => handleFieldChange(setName)(event.target.value)}
                />
              </FormField>
            )}
            <FormField label="Номер телефона" htmlFor="passenger-phone">
              <input
                id="passenger-phone"
                className={styles.input}
                type="tel"
                value={phone}
                onChange={(event) => handleFieldChange(setPhone)(event.target.value)}
              />
            </FormField>
            <FormField label="Пароль" htmlFor="passenger-password">
              <PasswordInput
                id="passenger-password"
                className={styles.input}
                value={password}
                onChange={handleFieldChange(setPassword)}
                autoComplete={authMode === 'register' ? 'new-password' : 'current-password'}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    void (authMode === 'register' ? handleRegisterSubmit() : handleLoginSubmit())
                  }
                }}
              />
            </FormField>
            {authError && <StatusMessage tone="error">{authError}</StatusMessage>}
            <div className={styles.actionRow}>
              <Button
                label={authMode === 'register' ? 'Создать аккаунт' : 'Войти'}
                variant="primary"
                loading={isSubmittingAuth}
                onClick={() => void (authMode === 'register' ? handleRegisterSubmit() : handleLoginSubmit())}
              />
            </div>
            <button type="button" className={styles.linkAction} onClick={toggleAuthMode}>
              {authMode === 'register' ? 'Уже есть аккаунт? Войти' : 'Ещё нет аккаунта? Создать'}
            </button>
          </>
        )}

        {step === 'confirmed' && identity && (
          <>
            <Heading level={1} visual="display">
              Добро пожаловать!
            </Heading>
            <p className={styles.subtitle}>Вы успешно подключены к PIOS.</p>

            <Card>
              <p className={styles.checklistTitle}>Теперь вы можете:</p>
              <p className={styles.checklistItem}>✅ заказать поездку</p>
              <p className={styles.checklistItem}>✅ пользоваться личной ссылкой {invitation?.driverName}</p>
              <p className={styles.checklistItem}>✅ не искать его номер телефона</p>
            </Card>

            <div className={styles.actionRow}>
              <Button label="Создать первый заказ" variant="primary" onClick={handleCreateFirstOrder} />
            </div>
          </>
        )}
      </main>
      {showOnboarding && (
        <PassengerOnboarding
          driverName={invitation?.driverName}
          onComplete={handleOnboardingDismiss}
          onSkip={handleOnboardingDismiss}
        />
      )}
      {showInstall && <InstallPIOS onClose={() => setShowInstall(false)} />}
    </div>
  )
}
