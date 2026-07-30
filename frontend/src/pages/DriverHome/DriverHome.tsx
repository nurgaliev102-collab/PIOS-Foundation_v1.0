import { useEffect, useRef, useState } from 'react'
import { Header } from '../../components/Header'
import { DriverCard } from '../../components/DriverCard'
import { QRCard } from '../../components/QRCard'
import { ActionButton } from '../../components/ActionButton'
import { Spinner } from '../../components/Spinner'
import { ApiError, request } from '../../api/apiClient'
import type { StoredIdentity } from '../../identity/IdentityProvider'
import { BackendIdentityProvider } from '../../identity/BackendIdentityProvider'
import { LocalInvitationProvider } from '../../identity/InvitationProvider'
import styles from './DriverHome.module.css'

// ADR-038/ADR-039: today's only IdentityProvider/InvitationProvider — see
// those files' own KDoc for why this is safe to instantiate once,
// module-level, exactly like `apiClientConfig` already is.
const identityProvider = new BackendIdentityProvider()
const invitationProvider = new LocalInvitationProvider()

const FEEDBACK_DURATION_MS = 2000

// First-pilot feedback: a driver had to remember to tap "Обновить" to see a
// new order — on a real shift that meant missed orders. Polling replaces
// the manual button entirely; 3s sits in the middle of the requested 2-5s
// range.
const PROPOSALS_POLL_INTERVAL_MS = 3000

// Dispatch's own local port (INTERFACE_CONTRACTS.md) — same constant as
// `Coordinator.tsx` (Sprint FR-004): this page now also calls Dispatch
// directly, in addition to Driver Management (Sprint IMPLEMENTATION-005,
// Driver Proposal MVP).
const DISPATCH_BASE_URL = import.meta.env.VITE_DISPATCH_BASE_URL ?? 'http://localhost:8084'

// Order Management's own local port (INTERFACE_CONTRACTS.md) — same
// constant as `Coordinator.tsx`/`RideRequest.tsx` (Sprint 3B: MVR Pilot
// Enablement -- Optional Destination): this page now also calls Order
// Management directly, to read each open proposal's own order destination.
const ORDER_MANAGEMENT_BASE_URL = import.meta.env.VITE_ORDER_MANAGEMENT_BASE_URL ?? 'http://localhost:8083'

const MAX_NAME_LENGTH = 50

type Status = 'loading' | 'error' | 'ready'
type ProposalActionStatus = 'idle' | 'submitting' | 'error'

interface DriverInfo {
  id: string
  availability: 'AVAILABLE' | 'UNAVAILABLE'
  displayName: string | null
}

interface ProposalListItem {
  proposalId: string
  orderId: string
  driverId: string
  status: 'OPEN' | 'ACCEPTED' | 'DECLINED' | 'LAPSED'
}

// Sprint 6A (Human Interface Polish): the raw status values above are this
// screen's own wire format, not driver-facing wording -- MVR_DRIVER_ONBOARDING_GUIDE.md
// promises a driver never has to read a technical term, so this maps each
// one to the plain-language text actually shown. 'LAPSED' has no example in
// that guide's own text; this wording follows the same plain-language
// principle for it.
const PROPOSAL_STATUS_LABEL: Record<ProposalListItem['status'], string> = {
  OPEN: 'Ожидает вашего решения',
  ACCEPTED: 'Вы приняли',
  DECLINED: 'Отклонено',
  LAPSED: 'Больше не активно',
}

type AssignmentStatusValue = 'CREATED' | 'ACCEPTED' | 'ARRIVED' | 'IN_PROGRESS' | 'COMPLETED'
type AssignmentActionStatus = 'idle' | 'submitting' | 'error'

interface AssignmentInfo {
  assignmentId: string
  orderId: string
  driverId: string
  status: AssignmentStatusValue
  statusChangedAt: string | null
}

interface OrderListItem {
  id: string
  destination: string | null
  // First-pilot feedback: the backend now carries these two (Order
  // Management's own `passengerName`/`createdAt`, both optional — an order
  // submitted before this pilot fix, or by a passenger with no local name
  // set, has neither; rendered conditionally below exactly like
  // `destination` already was).
  passengerName: string | null
  createdAt: string | null
}

/**
 * Pilot readiness fix: a driver should never have to read a raw UUID
 * (`ARCHITECTURE_VERIFICATION_REPORT.md`-adjacent finding: this screen
 * showed `proposal.orderId` verbatim) — it only turns the id itself into a
 * short, stable, still-traceable code a person can actually read and say
 * out loud. The full id remains the real key used for every API call; only
 * its on-screen rendering changes.
 */
function shortOrderCode(orderId: string): string {
  return orderId.replace(/-/g, '').slice(0, 8).toUpperCase()
}

/** Renders an order's own `createdAt` (ISO-8601, server clock) as a plain HH:MM a driver can glance at. */
function formatOrderTime(createdAt: string | null): string | null {
  if (!createdAt) {
    return null
  }
  const parsed = new Date(createdAt)
  if (Number.isNaN(parsed.getTime())) {
    return null
  }
  return parsed.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })
}

/**
 * Generates the internal id a new driver profile needs
 * (`driver-management`'s `POST /v1/drivers` still requires a caller-
 * supplied id — ADR-039 does not change that contract). Never shown to
 * the person creating the profile; they only ever provide a display name.
 * Same generator `persistence/localPassengerIdentity.ts` already uses for
 * the same reason.
 */
function generateDriverId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `driver-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

/**
 * Driver Home — Sprint 1: the first real mobile-first screen of PIOS.
 * Sprint 2 — Driver Invitation Flow: Copy and Share are wired to real
 * browser APIs, and the invitation link navigates locally to Passenger
 * Landing.
 *
 * Sprint 5 — First Backend Integration: a driver's own information
 * (id, availability) is now fetched from the real Driver Management
 * backend (`GET /v1/drivers/:driverId`, through `api/apiClient.ts`)
 * instead of `mockDriver.ts`, which is removed.
 *
 * ADR-038 (Identity Module Foundation) removed the hardcoded
 * `CURRENT_DRIVER_ID` constant. ADR-039 (Identity-Driver Association)
 * removes what ADR-038 replaced it with — a screen asking a person to type
 * an internal driver code — entirely. First run is now a real product
 * lifecycle: welcome → an [identityProvider]-issued Identity is created
 * silently → the person provides only their name → a Driver profile is
 * created and linked to that Identity → main app. Reopening the app
 * restores the same identity+driver from this device's own stored
 * pointer (`identityProvider.getStoredIdentity()`), not from a typed code.
 * This still is not authentication (ADR-038/ADR-039's own explicit scope
 * boundary) — it removes the single-driver ceiling and the technical
 * prompt, it does not prove anyone is who they claim to be.
 *
 * Sprint IMPLEMENTATION-005 (Driver Proposal MVP) adds this driver's own
 * open Proposals (`GET /v1/proposals?driverId=...`, Dispatch), each with
 * Accept/Decline actions (`POST /v1/proposals/:id/accept|decline`).
 * Accepting one creates the Assignment it precedes automatically, on the
 * backend, through the already-existing Proposal → Assignment
 * orchestration (Sprint IMPLEMENTATION-004) — this page does not call
 * `/v1/assignments` itself and knows nothing about Assignment directly.
 * The proposals section loads and fails independently of the driver
 * section above it, same pattern Coordinator's own Orders/Drivers
 * sections already established.
 *
 * Sprint 3B (MVR Pilot Enablement — Optional Destination) adds each
 * proposal's own order destination, read through Order Management's
 * already-existing `GET /v1/orders` (the same endpoint `Coordinator.tsx`
 * already calls), matched to a proposal by `orderId` client-side — no
 * change to Dispatch, `Proposal`, or `Assignment` at all; this page
 * simply reads a second module directly, exactly as `Coordinator.tsx`
 * already does for three. Destination lookup is best-effort: a failure
 * loading orders leaves proposals visible and actionable without a
 * destination shown, rather than blocking the section they came from.
 */
export function DriverHome() {
  const [identity, setIdentity] = useState<StoredIdentity | null>(() => identityProvider.getStoredIdentity())
  const [isCreatingIdentity, setIsCreatingIdentity] = useState(false)
  const [onboardingError, setOnboardingError] = useState<string | null>(null)

  const [nameInput, setNameInput] = useState('')
  const [nameError, setNameError] = useState<string | null>(null)
  const [isCreatingDriver, setIsCreatingDriver] = useState(false)
  const pendingDriverId = useRef<string | null>(null)

  const [status, setStatus] = useState<Status>('loading')
  const [driver, setDriver] = useState<DriverInfo | null>(null)
  const [feedback, setFeedback] = useState<string | null>(null)
  const feedbackTimeout = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  const [proposalsStatus, setProposalsStatus] = useState<Status>('loading')
  const [proposals, setProposals] = useState<ProposalListItem[]>([])
  const [proposalActions, setProposalActions] = useState<Record<string, ProposalActionStatus>>({})
  const [orderDetails, setOrderDetails] = useState<Record<string, OrderListItem>>({})
  // ADR-040 (Assignment Ride Lifecycle): keyed by orderId, one entry per
  // ACCEPTED proposal that already has an Assignment — an OPEN proposal
  // has none yet, so never appears here.
  const [assignments, setAssignments] = useState<Record<string, AssignmentInfo>>({})
  const [assignmentActions, setAssignmentActions] = useState<Record<string, AssignmentActionStatus>>({})

  const driverId = identity?.driverId ?? null

  useEffect(() => {
    if (!driverId) {
      return
    }
    let active = true
    loadDriver(active, driverId)
    return () => {
      active = false
    }
  }, [driverId])

  // First-pilot feedback: a driver's order list now refreshes itself —
  // polling every `PROPOSALS_POLL_INTERVAL_MS` replaces the manual
  // "Обновить" button this screen used to require. The first load still
  // shows the loading state; every poll after that is silent (no spinner
  // flicker every few seconds) and simply leaves the last-known list on
  // screen if a single poll happens to fail — the same best-effort
  // tolerance `orderDetails` below already has.
  useEffect(() => {
    if (!driverId) {
      return
    }
    let active = true
    loadProposals(active, driverId, { silent: false })
    loadOrderDetails(active)
    const interval = setInterval(() => {
      loadProposals(active, driverId, { silent: true })
      loadOrderDetails(active)
    }, PROPOSALS_POLL_INTERVAL_MS)
    return () => {
      active = false
      clearInterval(interval)
    }
  }, [driverId])

  function loadOrderDetails(active: boolean) {
    request<OrderListItem[]>('/v1/orders', { baseUrl: ORDER_MANAGEMENT_BASE_URL })
      .then((orders) => {
        if (!active) {
          return
        }
        setOrderDetails(Object.fromEntries(orders.map((order) => [order.id, order])))
      })
      .catch(() => {
        // Best-effort: proposals remain visible and actionable without
        // address/name/time shown (see this component's own KDoc).
      })
  }

  function loadDriver(active: boolean, forDriverId: string) {
    setStatus('loading')
    request<DriverInfo>(`/v1/drivers/${forDriverId}`)
      .then((result) => {
        if (!active) {
          return
        }
        setDriver(result)
        setStatus('ready')
      })
      .catch(() => {
        if (active) {
          setStatus('error')
        }
      })
  }

  function loadProposals(active: boolean, forDriverId: string, opts: { silent: boolean } = { silent: false }) {
    if (!opts.silent) {
      setProposalsStatus('loading')
    }
    request<ProposalListItem[]>(`/v1/proposals?driverId=${forDriverId}`, { baseUrl: DISPATCH_BASE_URL })
      .then((result) => {
        if (!active) {
          return
        }
        setProposals(result)
        setProposalsStatus('ready')
        loadAssignments(active, result)
      })
      .catch(() => {
        // A silent poll failure keeps the last-known list on screen rather
        // than flashing an error every few seconds over a momentary
        // network hiccup; only the initial load surfaces one.
        if (active && !opts.silent) {
          setProposalsStatus('error')
        }
      })
  }

  /**
   * ADR-040 (Assignment Ride Lifecycle): looks up the Assignment for each
   * ACCEPTED proposal in [currentProposals] (Dispatch has no bulk-by-driver
   * assignment query — only `?orderId=` — so this is one request per
   * accepted order; fine at the scale a single driver's own screen ever
   * shows). Reads the freshly-fetched proposals passed in, not the
   * `proposals` state, since this always runs from inside the same
   * `.then` that just resolved them — reading state here would see the
   * previous poll's value.
   */
  function loadAssignments(active: boolean, currentProposals: ProposalListItem[]) {
    const acceptedOrderIds = currentProposals.filter((p) => p.status === 'ACCEPTED').map((p) => p.orderId)
    Promise.all(
      acceptedOrderIds.map((orderId) =>
        request<AssignmentInfo[]>(`/v1/assignments?orderId=${orderId}`, { baseUrl: DISPATCH_BASE_URL }).catch(
          () => [] as AssignmentInfo[]
        )
      )
    ).then((results) => {
      if (!active) {
        return
      }
      setAssignments((current) => {
        const updated = { ...current }
        results.flat().forEach((assignment) => {
          updated[assignment.orderId] = assignment
        })
        return updated
      })
    })
  }

  async function respondToAssignment(assignmentId: string, action: 'arrive' | 'start' | 'complete') {
    if (assignmentActions[assignmentId] === 'submitting') {
      return
    }
    setAssignmentActions((current) => ({ ...current, [assignmentId]: 'submitting' }))
    try {
      const updated = await request<AssignmentInfo>(`/v1/assignments/${assignmentId}/${action}`, {
        method: 'POST',
        baseUrl: DISPATCH_BASE_URL,
      })
      setAssignments((current) => ({ ...current, [updated.orderId]: updated }))
      setAssignmentActions((current) => ({ ...current, [assignmentId]: 'idle' }))
    } catch {
      setAssignmentActions((current) => ({ ...current, [assignmentId]: 'error' }))
    }
  }

  async function handleWelcomeContinue() {
    setOnboardingError(null)
    setIsCreatingIdentity(true)
    try {
      const created = await identityProvider.createIdentity()
      setIdentity(created)
    } catch {
      setOnboardingError('Не удалось начать работу. Проверьте связь с интернетом и попробуйте ещё раз.')
    } finally {
      setIsCreatingIdentity(false)
    }
  }

  async function handleNameSubmit() {
    const trimmed = nameInput.trim()
    if (!trimmed) {
      setNameError('Пожалуйста, введите имя.')
      return
    }
    if (trimmed.length > MAX_NAME_LENGTH) {
      setNameError(`Имя должно быть короче ${MAX_NAME_LENGTH} символов.`)
      return
    }
    setNameError(null)
    setIsCreatingDriver(true)
    try {
      // A retry after a dropped connection must not mint a second driver:
      // reuse whichever id this attempt already committed to, and treat a
      // 409 (the previous attempt's create already landed) as success
      // rather than a failure.
      const newDriverId = pendingDriverId.current ?? generateDriverId()
      pendingDriverId.current = newDriverId
      try {
        await request('/v1/drivers', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ driverId: newDriverId, displayName: trimmed }),
        })
      } catch (error) {
        if (!(error instanceof ApiError && error.status === 409)) {
          throw error
        }
      }
      const updated = await identityProvider.attachDriver(newDriverId)
      pendingDriverId.current = null
      setIdentity(updated)
    } catch {
      setNameError('Не удалось создать профиль. Проверьте связь с интернетом и попробуйте ещё раз.')
    } finally {
      setIsCreatingDriver(false)
    }
  }

  async function respondToProposal(proposalId: string, action: 'accept' | 'decline') {
    if (proposalActions[proposalId] === 'submitting') {
      return
    }
    setProposalActions((current) => ({ ...current, [proposalId]: 'submitting' }))
    try {
      const updated = await request<ProposalListItem>(`/v1/proposals/${proposalId}/${action}`, {
        method: 'POST',
        baseUrl: DISPATCH_BASE_URL,
      })
      setProposals((current) => current.map((proposal) => (proposal.proposalId === proposalId ? updated : proposal)))
      setProposalActions((current) => ({ ...current, [proposalId]: 'idle' }))
    } catch (error) {
      setProposalActions((current) => ({ ...current, [proposalId]: 'error' }))
      // A 404/409 here means another actor already resolved this proposal (or it no
      // longer exists) -- reload the list so this screen reflects its real state.
      if (error instanceof ApiError && (error.status === 404 || error.status === 409) && driverId) {
        loadProposals(true, driverId)
      }
    }
  }

  function showFeedback(message: string) {
    setFeedback(message)
    clearTimeout(feedbackTimeout.current)
    feedbackTimeout.current = setTimeout(() => setFeedback(null), FEEDBACK_DURATION_MS)
  }

  async function handleCopy() {
    if (!driver) {
      return
    }
    try {
      await invitationProvider.copy(invitationProvider.linkFor(driver.id))
      showFeedback('Ссылка скопирована')
    } catch {
      showFeedback('Не удалось скопировать')
    }
  }

  async function handleShare() {
    if (!driver) {
      return
    }
    try {
      await invitationProvider.share(invitationProvider.linkFor(driver.id))
      if (!navigator.share) {
        showFeedback('Ссылка скопирована')
      }
    } catch (error) {
      // A user-cancelled share (AbortError) is not a failure — no feedback needed.
      if (error instanceof Error && error.name !== 'AbortError') {
        showFeedback('Не удалось поделиться')
      }
    }
  }

  // Phase 1: no Identity yet on this device — welcome, explain, and create
  // one silently in the background. No internal id is ever shown here.
  if (!identity) {
    return (
      <div className={styles.screen}>
        <Header />
        <main className={styles.content}>
          <h1 className={styles.welcomeTitle}>Добро пожаловать!</h1>
          <p className={styles.welcomeText}>PIOS помогает вам строить собственную клиентскую сеть.</p>

          <section className={styles.welcomeCard}>
            <p className={styles.welcomeCardTitle}>Ваши постоянные клиенты смогут:</p>
            <p className={styles.welcomeCardItem}>• быстро находить вас;</p>
            <p className={styles.welcomeCardItem}>• заказывать поездки через вашу ссылку;</p>
            <p className={styles.welcomeCardItem}>• оставаться вашими клиентами.</p>
          </section>

          <section className={styles.welcomeCard}>
            <p className={styles.welcomeCardTitle}>Что нужно сделать</p>
            <p className={styles.welcomeCardItem}>1. Создайте свой профиль.</p>
            <p className={styles.welcomeCardItem}>2. Получите свою ссылку.</p>
            <p className={styles.welcomeCardItem}>3. Отправьте её своим постоянным клиентам.</p>
            <p className={styles.welcomeCardItem}>4. Принимайте новые заказы.</p>
          </section>

          {onboardingError && (
            <p className={styles.error} role="alert">
              {onboardingError}
            </p>
          )}

          <div className={styles.actionRow}>
            <ActionButton
              label={isCreatingIdentity ? 'Начинаем…' : 'Начать'}
              variant="primary"
              onClick={handleWelcomeContinue}
              disabled={isCreatingIdentity}
            />
          </div>
        </main>
      </div>
    )
  }

  // Phase 2: Identity exists, no Driver profile linked to it yet — the
  // only thing a person ever types is their own name.
  if (!identity.driverId) {
    return (
      <div className={styles.screen}>
        <Header />
        <main className={styles.content}>
          <h1 className={styles.welcomeTitle}>Как вас зовут?</h1>
          <p className={styles.welcomeText}>Это имя увидят ваши клиенты, когда вы их пригласите.</p>
          <input
            className={styles.driverCodeInput}
            type="text"
            value={nameInput}
            maxLength={MAX_NAME_LENGTH}
            placeholder="Ваше имя"
            aria-label="Ваше имя"
            onChange={(event) => {
              setNameInput(event.target.value)
              if (nameError) {
                setNameError(null)
              }
            }}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                void handleNameSubmit()
              }
            }}
          />
          {nameError && (
            <p className={styles.error} role="alert">
              {nameError}
            </p>
          )}
          <div className={styles.actionRow}>
            <ActionButton
              label={isCreatingDriver ? 'Создаём профиль…' : 'Создать профиль'}
              variant="primary"
              onClick={() => void handleNameSubmit()}
              disabled={isCreatingDriver}
            />
          </div>
        </main>
      </div>
    )
  }

  // ADR-040 (Assignment Ride Lifecycle): a proposal whose ride has
  // completed stops being active — "экран освобождается" per the sprint's
  // own item 5. No history screen exists yet to move it to; it simply
  // stops appearing here.
  const visibleProposals = proposals.filter((p) => assignments[p.orderId]?.status !== 'COMPLETED')

  return (
    <div className={styles.screen}>
      <Header />
      <main className={styles.content}>
        {status === 'loading' && <Spinner label="Загружаем ваш профиль…" />}

        {status === 'error' && (
          <div className={styles.errorBlock}>
            <p className={styles.error} role="alert">
              Не удалось загрузить профиль водителя. Проверьте связь с интернетом.
            </p>
            <ActionButton
              label="Попробовать снова"
              variant="secondary"
              onClick={() => loadDriver(true, identity.driverId!)}
            />
          </div>
        )}

        {status === 'ready' && driver && (
          <>
            <DriverCard
              driverCode={driver.id}
              displayName={driver.displayName}
              availability={driver.availability}
              hideCode
            />
            <p className={styles.hint}>Скоро: подтверждение номера телефона.</p>
            <QRCard
              invitationLink={invitationProvider.linkFor(driver.id)}
              linkTo={`/i/${driver.id}`}
              onCopy={handleCopy}
              onShare={handleShare}
              feedback={feedback}
            />
            <p className={styles.hint}>Отправьте эту ссылку клиенту — он сможет заказать поездку прямо у вас.</p>
          </>
        )}

        <div className={styles.sectionHeader}>
          <h2 className={styles.sectionTitle}>Ваши заказы</h2>
        </div>

        {proposalsStatus === 'loading' && <Spinner label="Загружаем заказы…" />}
        {proposalsStatus === 'error' && (
          <div className={styles.errorBlock}>
            <p className={styles.error} role="alert">
              Не удалось загрузить заказы. Проверьте связь с интернетом.
            </p>
            <ActionButton
              label="Попробовать снова"
              variant="secondary"
              onClick={() => loadProposals(true, identity.driverId!)}
            />
          </div>
        )}
        {proposalsStatus === 'ready' && visibleProposals.length === 0 && (
          <p className={styles.status}>Пока нет заказов. Как только клиент оформит поездку, она появится здесь.</p>
        )}

        {proposalsStatus === 'ready' &&
          visibleProposals.map((proposal) => {
            const order = orderDetails[proposal.orderId]
            const time = formatOrderTime(order?.createdAt ?? null)
            const assignment = assignments[proposal.orderId]
            return (
            <section key={proposal.proposalId} className={styles.proposalRow}>
              <div className={styles.proposalDetails}>
                <span className={styles.proposalOrderId}>Заказ №{shortOrderCode(proposal.orderId)}</span>
                <span
                  className={`${styles.proposalStatus} ${
                    proposal.status === 'OPEN'
                      ? styles.proposalOpen
                      : proposal.status === 'ACCEPTED'
                        ? styles.proposalAccepted
                        : styles.proposalResolved
                  }`}
                >
                  {PROPOSAL_STATUS_LABEL[proposal.status]}
                </span>
              </div>

              {order?.passengerName && <p className={styles.status}>Пассажир: {order.passengerName}</p>}
              {order?.destination && <p className={styles.status}>Куда: {order.destination}</p>}
              {time && <p className={styles.status}>Заказ создан: {time}</p>}

              {proposal.status === 'OPEN' && (
                <div className={styles.proposalActions}>
                  <ActionButton
                    label={proposalActions[proposal.proposalId] === 'submitting' ? 'Принимаем…' : 'Принять'}
                    variant="primary"
                    onClick={() => respondToProposal(proposal.proposalId, 'accept')}
                    disabled={proposalActions[proposal.proposalId] === 'submitting'}
                  />
                  <ActionButton
                    label={proposalActions[proposal.proposalId] === 'submitting' ? 'Отклоняем…' : 'Отклонить'}
                    variant="secondary"
                    onClick={() => respondToProposal(proposal.proposalId, 'decline')}
                    disabled={proposalActions[proposal.proposalId] === 'submitting'}
                  />
                </div>
              )}

              {proposalActions[proposal.proposalId] === 'error' && (
                <p className={styles.error} role="alert">
                  Не удалось обновить заказ. Попробуйте ещё раз.
                </p>
              )}

              {/* ADR-040 (Assignment Ride Lifecycle): one button at a time,
                  matching the assignment's own current status — CREATED and
                  ACCEPTED both offer "Прибыл" (see Assignment.arrive's own
                  KDoc for why CREATED is a valid precondition here). */}
              {assignment && (assignment.status === 'CREATED' || assignment.status === 'ACCEPTED') && (
                <div className={styles.proposalActions}>
                  <ActionButton
                    label={assignmentActions[assignment.assignmentId] === 'submitting' ? 'Отмечаем…' : 'Прибыл'}
                    variant="primary"
                    onClick={() => respondToAssignment(assignment.assignmentId, 'arrive')}
                    disabled={assignmentActions[assignment.assignmentId] === 'submitting'}
                  />
                </div>
              )}
              {assignment?.status === 'ARRIVED' && (
                <div className={styles.proposalActions}>
                  <ActionButton
                    label={assignmentActions[assignment.assignmentId] === 'submitting' ? 'Начинаем…' : 'Начать поездку'}
                    variant="primary"
                    onClick={() => respondToAssignment(assignment.assignmentId, 'start')}
                    disabled={assignmentActions[assignment.assignmentId] === 'submitting'}
                  />
                </div>
              )}
              {assignment?.status === 'IN_PROGRESS' && (
                <div className={styles.proposalActions}>
                  <ActionButton
                    label={assignmentActions[assignment.assignmentId] === 'submitting' ? 'Завершаем…' : 'Завершить поездку'}
                    variant="primary"
                    onClick={() => respondToAssignment(assignment.assignmentId, 'complete')}
                    disabled={assignmentActions[assignment.assignmentId] === 'submitting'}
                  />
                </div>
              )}
              {assignment && assignmentActions[assignment.assignmentId] === 'error' && (
                <p className={styles.error} role="alert">
                  Не удалось обновить статус поездки. Попробуйте ещё раз.
                </p>
              )}
            </section>
            )
          })}
      </main>
    </div>
  )
}
