import { useEffect, useRef, useState } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router-dom'
import { Header } from '../../components/Header'
import { ActionButton } from '../../components/ActionButton'
import { Spinner } from '../../components/Spinner'
import { getInvitationByDriverCode } from '../PassengerLanding/invitationSource'
import { BackendIdentityProvider } from '../../identity/BackendIdentityProvider'
import type { StoredIdentity } from '../../identity/IdentityProvider'
import { getDisplayName } from '../../persistence/localDisplayName'
import { clearCurrentOrderId, getCurrentOrderId, saveCurrentOrderId } from '../../persistence/localCurrentOrder'
import { request } from '../../api/apiClient'
import styles from './RideRequest.module.css'

// ADR-038/ADR-039/ADR-055: same module-level provider instance `PassengerLanding.tsx` already uses.
const identityProvider = new BackendIdentityProvider()

// Order Management's own local port (INTERFACE_CONTRACTS.md) — distinct
// from apiClientConfig's default (Driver Management's port), since this
// is the second backend module this frontend now genuinely calls.
const ORDER_MANAGEMENT_BASE_URL = import.meta.env.VITE_ORDER_MANAGEMENT_BASE_URL ?? 'http://localhost:8083'

// Dispatch's own local port (INTERFACE_CONTRACTS.md) — Sprint 7B (Personal
// Network Flow MVP): once the order exists, this page proposes it directly
// to the driver whose link the passenger arrived through, reusing
// Dispatch's already-existing `POST /v1/proposals` exactly as
// `Coordinator.tsx` already does for the general queue -- no Coordinator
// step for this, invited-passenger path.
const DISPATCH_BASE_URL = import.meta.env.VITE_DISPATCH_BASE_URL ?? 'http://localhost:8084'

// Passenger Experience's own local port (INTERFACE_CONTRACTS.md) — Sprint
// "My Business + Circle of Trust" (ADR-054): this page now also calls that
// module directly, to read this passenger's own circle of trust before
// asking which driver today's ride goes to.
const PASSENGER_EXPERIENCE_BASE_URL = import.meta.env.VITE_PASSENGER_EXPERIENCE_BASE_URL ?? 'http://localhost:8082'

type Step = 'loading' | 'not-found' | 'error' | 'circle' | 'form' | 'confirmed'
type ProposalStatus = 'proposing' | 'proposed' | 'error'

interface SubmitOrderResponse {
  orderId: string
}

/**
 * ADR-054 Part 4: `GET /v1/connections?passengerReference=...`'s own
 * response shape — this passenger's own circle of trust, each entry
 * carrying whether it is currently the primary designation. No driver name
 * or availability here (Passenger Experience does not own that data, per
 * `DriverReference.kt`'s own reference-not-ownership discipline) — see
 * [EnrichedCircleMember] for where that comes from.
 */
interface CircleMember {
  connectionId: string
  driverId: string
  createdAt: string
  isPrimary: boolean
}

/** `GET /v1/drivers/:id`'s own response shape (Driver Management) — same fields `invitationSource.ts` already reads. */
interface DriverSummary {
  id: string
  availability: 'AVAILABLE' | 'UNAVAILABLE'
  displayName: string | null
}

/**
 * A [CircleMember] joined, client-side, with Driver Management's own
 * `displayName`/`availability` — Passenger Experience's response never
 * carries either (reference-not-ownership), so this page reads Driver
 * Management directly for each member, the same per-driver lookup
 * `invitationSource.ts` already performs for the single driver whose link
 * a passenger arrived through.
 */
interface EnrichedCircleMember extends CircleMember {
  displayName: string
  availability: 'AVAILABLE' | 'UNAVAILABLE'
}

interface ProposalStatusItem {
  status: 'OPEN' | 'ACCEPTED' | 'DECLINED' | 'LAPSED' | 'WITHDRAWN'
  // ADR-042 (Stated Ride Price Minimal Model), Amendment 2026-08-01 (R9):
  // the Product Owner ruled the passenger does see the amount the driver
  // stated on acceptance -- `GET /v1/proposals?orderId=...` (this screen's
  // own poll) has always carried it (`ProposalResponse.kt`'s own KDoc), no
  // backend change accompanies this field being read here for the first
  // time. Display-only, per R10: no response mechanism is added.
  statedPrice: string | null
  // ADR-057 (Driver Stated Time to Pickup): same poll, same display-only
  // treatment as [statedPrice] -- the number of minutes the driver stated
  // it would take to reach the passenger, `null` until accepted or if the
  // driver left it unset.
  statedEtaMinutes: number | null
}

interface AssignmentStatusItem {
  status: 'CREATED' | 'ACCEPTED' | 'ARRIVED' | 'IN_PROGRESS' | 'COMPLETED'
}

/**
 * ADR-058 (Scheduled Pickup Time): the subset of `GET /v1/orders`'s own
 * response shape this screen needs -- the same unfiltered, already-public
 * endpoint `DriverHome.tsx` already reads for its own order details, only
 * matched here to this one order by id client-side. Read from the backend
 * (not kept only in local component state) so a page reload does not lose
 * it -- unlike [statedPrice]/[statedEtaMinutes], nothing about this value
 * comes from the proposal poll.
 */
interface OrderListItem {
  id: string
  requestedPickupAt: string | null
}

// First-pilot feedback: a passenger used to have no way of knowing the
// driver accepted their order short of the driver calling them. Polling
// (not WebSocket, per this pilot fix's own explicit scope) checks Dispatch
// every few seconds for as long as this screen is open — same cadence
// Driver Home's own order-list polling uses.
const STATUS_POLL_INTERVAL_MS = 3000

/**
 * The passenger-facing ride chain (ADR-040, Assignment Ride Lifecycle):
 * "Водитель принял заказ → Водитель прибыл → Поездка началась → Поездка
 * завершена". Combines two Dispatch resources the driver's own screen
 * already uses separately — Proposal (whether a driver accepted at all)
 * and Assignment (ride progress after that) — into one value, since no
 * Assignment exists at all until a Proposal is accepted.
 *
 * Sprint H5 (Entrepreneur Working Cycle Integrity, Truthful Status
 * Rendering) adds 'DECLINED' and 'LAPSED' as their own distinct values —
 * both real [ProposalStatusItem] statuses this screen's own poll already
 * receives. Previously both were folded into 'OPEN', which rendered the
 * same "✅ ... он свяжется с вами" success wording as a genuinely still-open
 * proposal — false for an order a driver has actually declined or that has
 * lapsed. No new status is invented here: this only stops discarding two
 * real ones the backend already sends.
 */
type RideStatus = 'OPEN' | 'DECLINED' | 'LAPSED' | 'WITHDRAWN' | 'ACCEPTED' | 'ARRIVED' | 'IN_PROGRESS' | 'COMPLETED'

/**
 * ADR-058 Decision item 5: PIOS itself asserts nothing about a past
 * requested time -- "the passenger's own screen prevents picking a past
 * time" is this function, feeding a native `<input type="datetime-local">`'s
 * own `min` attribute with this device's own local clock.
 */
function currentDatetimeLocalValue(): string {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}`
}

/** Mirrors `DriverHome.tsx`'s own `formatRequestedPickupAt` -- this device's own local time, no timezone concept in the contract. */
function formatRequestedPickupAt(requestedPickupAt: string | null): string | null {
  if (!requestedPickupAt) {
    return null
  }
  const parsed = new Date(requestedPickupAt)
  if (Number.isNaN(parsed.getTime())) {
    return null
  }
  return parsed.toLocaleString('ru-RU', { day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' })
}

const RIDE_STATUS_LABEL: Record<RideStatus, string> = {
  OPEN: '⏳ Ждём ответа водителя. Мы сообщим, как только он подтвердит заказ.',
  DECLINED: '❌ Водитель отклонил ваш заказ.',
  LAPSED: '⌛ Заказ больше не активен — водитель не ответил вовремя.',
  WITHDRAWN: '🚫 Вы отменили этот заказ.',
  ACCEPTED: '✅ Водитель принял ваш заказ и скоро свяжется с вами.',
  ARRIVED: '🚗 Водитель прибыл на место.',
  IN_PROGRESS: '🚕 Поездка началась.',
  COMPLETED: '🏁 Поездка завершена. Спасибо, что выбрали PIOS!',
}

/**
 * Ride Request — Sprint 4: the first passenger action after onboarding,
 * rendered at `/i/:driverCode/request`.
 *
 * Reachable only from Passenger Landing's "Request a Ride" button, which
 * only appears once a local identity exists. If this page is opened
 * directly without one, it redirects back to `/i/:driverCode` rather
 * than asking for a name here too.
 *
 * Sprint FR-001 (Connect RideRequest): submitting now calls the real,
 * existing `POST /v1/orders` (Order Management) directly, mirroring
 * Driver Home's own direct-module-call precedent (Sprint 5).
 *
 * Sprint 3B (MVR Pilot Enablement — Optional Destination): the real
 * backend contract now also accepts an optional `destination` (see
 * `backend/order-management/.../api/SubmitOrderRequest.kt`), sent here as
 * plain text — no geocoding, no coordinates.
 *
 * First-user-test UX audit: this form used to also collect a "Комментарий"
 * field that was never actually sent anywhere (`notes` stayed local-only,
 * a known, disclosed limitation this KDoc used to describe). Collecting
 * input from a first-time passenger and silently discarding it is worse
 * than not asking at all — it reads as broken, not as a disclosed
 * limitation, to someone who has no way to know that. Removed rather than
 * kept as a known gap; the API and data model are unchanged, only what
 * this screen renders.
 *
 * Submission is guarded against double-clicks
 * (`isSubmitting`): Order Management's own docs flag Submit Order as not
 * idempotent, so a duplicate request could create a duplicate order.
 *
 * First-pilot feedback adds three fixes: submission now also sends this
 * passenger's own local display name as Order Management's new optional
 * `passengerName` (a driver's order card had no way to show one); the
 * confirmed screen now polls Dispatch (`?orderId=...`) so acceptance is
 * reflected without a reload; and `localCurrentOrder.ts` remembers this
 * order so reloading the page resumes it instead of showing a blank form.
 *
 * ADR-040 (Assignment Ride Lifecycle) extends that same polling loop past
 * acceptance: once a Proposal is accepted, the loop also reads the
 * resulting Assignment's own status (`/v1/assignments?orderId=...`) to
 * show "Водитель прибыл" / "Поездка началась" / "Поездка завершена" as
 * the driver's own screen advances them — see [RideStatus].
 *
 * Sprint H5 (Entrepreneur Working Cycle Integrity): this form now also
 * collects "Откуда" (pickup address), sent as Order Management's new
 * optional `pickupAddress` (see
 * `backend/order-management/.../api/SubmitOrderRequest.kt`) alongside the
 * existing `destination`. Plain text, exactly like `destination` — no
 * geocoding, no coordinates. This closes the gap the old copy on this form
 * used to describe ("Водитель свяжется с вами, чтобы уточнить место
 * посадки"): the address is now collected directly, so that copy is
 * removed rather than left inaccurate.
 *
 * P0-1 (`docs/SPRINT_PILOT_BLOCKERS.md`): `localCurrentOrder.ts` never
 * cleared its own entry, so once this driver's current order reached
 * `'DECLINED'`, `'LAPSED'`, or `'COMPLETED'`, this screen showed the
 * correct terminal status but had no way back to the order form — ever,
 * for this driver, from this device. [handleOrderAgain] is that way back:
 * shown only for those three terminal `rideStatus` values, it clears this
 * driver's own stored order id and resets straight to `'form'`. `'OPEN'`,
 * `'ACCEPTED'`, `'ARRIVED'`, `'IN_PROGRESS'` are unchanged — a ride still
 * pending or under way must not offer a second, concurrent order with the
 * same driver.
 */
export function RideRequest() {
  const { driverCode } = useParams<{ driverCode: string }>()
  const navigate = useNavigate()
  const [identity, setIdentity] = useState<StoredIdentity | null>(null)
  const [identityChecked, setIdentityChecked] = useState(false)
  const [step, setStep] = useState<Step>('loading')
  const [circle, setCircle] = useState<EnrichedCircleMember[]>([])
  const [circleError, setCircleError] = useState(false)
  const [primaryChangeTarget, setPrimaryChangeTarget] = useState<string | null>(null)
  const [primaryChangeStatus, setPrimaryChangeStatus] = useState<'idle' | 'submitting' | 'error'>('idle')
  const [removeTarget, setRemoveTarget] = useState<string | null>(null)
  const [removeStatus, setRemoveStatus] = useState<'idle' | 'submitting' | 'error'>('idle')
  const [pickupAddress, setPickupAddress] = useState('')
  const [pickupAddressError, setPickupAddressError] = useState<string | null>(null)
  const [destination, setDestination] = useState('')
  const [destinationError, setDestinationError] = useState<string | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [orderId, setOrderId] = useState<string | null>(null)
  const [proposalStatus, setProposalStatus] = useState<ProposalStatus | null>(null)
  const [rideStatus, setRideStatus] = useState<RideStatus>('OPEN')
  // ADR-042 R9: the amount the driver stated on accepting this order, read
  // from the same poll [rideStatus] already uses -- null until a proposal
  // is actually ACCEPTED, and whenever the driver accepted with no amount
  // typed (`acceptRequestInit` in DriverHome.tsx sends no body at all then).
  const [statedPrice, setStatedPrice] = useState<string | null>(null)
  // ADR-057: the driver's own stated time to pickup, read from the same
  // poll -- mirrors [statedPrice] exactly.
  const [statedEtaMinutes, setStatedEtaMinutes] = useState<number | null>(null)
  // ADR-058 (Scheduled Pickup Time): whether this passenger is booking for
  // "сейчас" (default, sends nothing) or a chosen future date/time.
  const [isScheduled, setIsScheduled] = useState(false)
  const [scheduledAt, setScheduledAt] = useState('')
  const [scheduledAtError, setScheduledAtError] = useState<string | null>(null)
  // ADR-058: this order's own requested pickup instant, read from Order
  // Management -- see [OrderListItem]'s own KDoc for why this is fetched
  // rather than kept only in [scheduledAt] (which a page reload loses).
  const [requestedPickupAt, setRequestedPickupAt] = useState<string | null>(null)
  const hasFetchedRequestedPickupAt = useRef(false)
  // P0-2 Tier 1 (`docs/SPRINT_PILOT_BLOCKERS.md`; ADR-053): the passenger's
  // own way to stop waiting on an order no driver has accepted yet -- see
  // [handleCancelOrder]'s own KDoc.
  const [cancelStatus, setCancelStatus] = useState<'idle' | 'submitting' | 'error'>('idle')

  // Sprint 6 (Passenger Entry-Path Failure Handling): pulled out of the
  // effect (mirrors DriverHome.tsx's own loadDriver) so the same fetch can
  // also be re-run by the "Попробовать снова" retry action below, without
  // duplicating this logic.
  function loadInvitation(active: boolean, forDriverCode: string, forIdentity: StoredIdentity) {
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
      // First-pilot feedback: a passenger who reloads this page must land
      // back on their current order, not a blank form — same driver, same
      // browser, an order already placed through `localCurrentOrder.ts`.
      const existingOrderId = getCurrentOrderId(forDriverCode)
      if (existingOrderId) {
        setOrderId(existingOrderId)
        setStep('confirmed')
        return
      }
      loadCircleThenAdvance(active, forIdentity)
    })
  }

  /**
   * ADR-054 / `PRODUCT_DECISION_CIRCLE_OF_TRUST.md`: before starting a new
   * order, a passenger with more than one trusted driver sees their circle
   * of trust first (Section 8 of the Sprint brief) and picks who today's
   * ride goes to — a passenger with zero or one relationship has nothing to
   * choose, so this skips straight to the form, unchanged from before this
   * Sprint. Best-effort throughout: a failure here never blocks ordering
   * with the driver whose link this page was already opened through.
   */
  function loadCircleThenAdvance(active: boolean, forIdentity: StoredIdentity) {
    request<CircleMember[]>(`/v1/connections?passengerReference=${forIdentity.identityId}`, {
      headers: { Authorization: `Bearer ${forIdentity.token}` },
      baseUrl: PASSENGER_EXPERIENCE_BASE_URL,
    })
      .then((members) => enrichCircle(members))
      .then((enriched) => {
        if (!active) {
          return
        }
        setCircle(enriched)
        setStep(enriched.length > 1 ? 'circle' : 'form')
      })
      .catch(() => {
        if (!active) {
          return
        }
        setCircleError(true)
        setStep('form')
      })
  }

  async function enrichCircle(members: CircleMember[]): Promise<EnrichedCircleMember[]> {
    const enriched = await Promise.all(
      members.map(async (member): Promise<EnrichedCircleMember> => {
        try {
          const driver = await request<DriverSummary>(`/v1/drivers/${member.driverId}`)
          return { ...member, displayName: driver.displayName ?? member.driverId, availability: driver.availability }
        } catch {
          // Best-effort per member: an unreachable Driver Management record
          // still shows up in the circle, just without a real name or a
          // known availability -- never dropped silently.
          return { ...member, displayName: member.driverId, availability: 'UNAVAILABLE' }
        }
      })
    )
    return enriched.sort((a, b) => Number(b.isPrimary) - Number(a.isPrimary))
  }

  useEffect(() => {
    let active = true
    identityProvider.restoreIdentity().then((restored) => {
      if (!active) {
        return
      }
      setIdentity(restored)
      setIdentityChecked(true)
      if (restored) {
        loadInvitation(active, driverCode ?? '', restored)
      }
    })
    return () => {
      active = false
    }
  }, [driverCode])

  // First-pilot feedback: while this screen shows a confirmed order, poll
  // Dispatch for whether the driver has accepted it — no reload needed to
  // see the change. Runs identically whether this order was just submitted
  // or resumed after a page reload (proposalStatus is only ever set by a
  // fresh submission's own attemptProposal, never by this effect).
  useEffect(() => {
    if (step !== 'confirmed' || !orderId || !identity) {
      return
    }
    const currentIdentity = identity
    let active = true
    function poll() {
      request<ProposalStatusItem[]>(`/v1/proposals?orderId=${orderId}`, { baseUrl: DISPATCH_BASE_URL })
        .then((items) => {
          if (!active) {
            return
          }
          const acceptedItem = items.find((item) => item.status === 'ACCEPTED')
          if (!acceptedItem) {
            // Sprint H5 (Truthful Status Rendering): reflect whichever real
            // proposal status this order actually has -- OPEN, DECLINED, or
            // LAPSED are three different facts and must not all render as
            // the same "✅ ... он свяжется с вами" success message. Priority
            // (OPEN > DECLINED > LAPSED > WITHDRAWN) only matters if more
            // than one proposal somehow exists for this order; in the
            // normal single-proposal case exactly one of these is true.
            // WITHDRAWN (ADR-053) is what this same proposal becomes once
            // [handleCancelOrder] below cancels the order -- Dispatch
            // withdraws the open proposal automatically, asynchronously, so
            // this poll is also what confirms a cancellation actually took
            // effect, not only [handleCancelOrder]'s own optimistic update.
            if (items.some((item) => item.status === 'OPEN')) {
              setRideStatus('OPEN')
            } else if (items.some((item) => item.status === 'DECLINED')) {
              setRideStatus('DECLINED')
            } else if (items.some((item) => item.status === 'LAPSED')) {
              setRideStatus('LAPSED')
            } else if (items.some((item) => item.status === 'WITHDRAWN')) {
              setRideStatus('WITHDRAWN')
            } else {
              // No proposal recorded yet (e.g., the propose call is still
              // in flight) -- honestly "waiting", not yet knowable as
              // anything else.
              setRideStatus('OPEN')
            }
            return
          }
          // ADR-042 R9: read back here, not only in the branch above,
          // since this same poll keeps running through ARRIVED/IN_PROGRESS/
          // COMPLETED too -- the amount was fixed at accept time and never
          // changes again, so re-setting it every tick is harmless and
          // keeps this the single place [statedPrice] is ever written.
          setStatedPrice(acceptedItem.statedPrice)
          // ADR-057: same read-back pattern as statedPrice immediately above.
          setStatedEtaMinutes(acceptedItem.statedEtaMinutes)
          // Accepted -- an Assignment now exists (created in the same
          // step Dispatch accepts the Proposal); check its own ride
          // progress. CREATED and ACCEPTED both read as "принял" to a
          // passenger -- see Assignment.arrive's own KDoc for why the
          // Assignment itself may still say CREATED here.
          request<AssignmentStatusItem[]>(`/v1/assignments?orderId=${orderId}`, { baseUrl: DISPATCH_BASE_URL })
            .then((assignments) => {
              if (!active) {
                return
              }
              const status = assignments[0]?.status
              setRideStatus(status && status !== 'CREATED' ? (status as RideStatus) : 'ACCEPTED')
            })
            .catch(() => {
              if (active) {
                setRideStatus('ACCEPTED')
              }
            })
          // ADR-058: fetched once per confirmed order, chained after the
          // assignments request above so this poll's own call order stays
          // deterministic -- requestedPickupAt is fixed at submission and
          // never changes, so [hasFetchedRequestedPickupAt] guards against
          // refetching it on every subsequent tick.
          if (!hasFetchedRequestedPickupAt.current) {
            hasFetchedRequestedPickupAt.current = true
            // ADR-060 (Order Query Authorization): GET /v1/orders now
            // requires a Bearer token and exactly one scoping parameter --
            // `?passengerReference=` returns only this passenger's own
            // orders, and only when it equals the token's own subject.
            request<OrderListItem[]>(`/v1/orders?passengerReference=${currentIdentity.identityId}`, {
              headers: { Authorization: `Bearer ${currentIdentity.token}` },
              baseUrl: ORDER_MANAGEMENT_BASE_URL,
            })
              .then((orders) => {
                if (!active) {
                  return
                }
                setRequestedPickupAt(orders.find((order) => order.id === orderId)?.requestedPickupAt ?? null)
              })
              .catch(() => {
                // Best-effort: the confirmation simply omits this line.
              })
          }
        })
        .catch(() => {
          // Best-effort: a failed poll simply tries again next tick.
        })
    }
    poll()
    const interval = setInterval(poll, STATUS_POLL_INTERVAL_MS)
    return () => {
      active = false
      clearInterval(interval)
    }
  }, [step, orderId, identity])

  if (identityChecked && !identity) {
    return <Navigate to={`/i/${driverCode ?? ''}`} replace />
  }
  if (!identity) {
    // Still checking this device's own session (ADR-055) -- `step` stays
    // 'loading' below until this resolves one way or the other.
    return (
      <div className={styles.screen}>
        <Header />
        <main className={styles.content}>
          <Spinner label="Загрузка…" />
        </main>
      </div>
    )
  }

  const passengerId = identity.identityId
  const passengerName = getDisplayName()

  function handlePickupAddressChange(value: string) {
    setPickupAddress(value)
    if (pickupAddressError) {
      setPickupAddressError(null)
    }
  }

  function handleDestinationChange(value: string) {
    setDestination(value)
    if (destinationError) {
      setDestinationError(null)
    }
  }

  function handleScheduledAtChange(value: string) {
    setScheduledAt(value)
    if (scheduledAtError) {
      setScheduledAtError(null)
    }
  }

  async function handleSubmit() {
    if (isSubmitting) {
      return
    }
    const trimmedPickupAddress = pickupAddress.trim()
    if (!trimmedPickupAddress) {
      setPickupAddressError('Пожалуйста, укажите адрес.')
      return
    }
    const trimmedDestination = destination.trim()
    if (!trimmedDestination) {
      setDestinationError('Пожалуйста, укажите адрес.')
      return
    }
    // ADR-058 Decision item 5: validation is structural only -- a value
    // that does not parse (or is simply missing while "Заранее" is chosen)
    // is rejected here in the UI; PIOS itself asserts nothing about it.
    let requestedPickupAt: string | null = null
    if (isScheduled) {
      if (!scheduledAt) {
        setScheduledAtError('Пожалуйста, укажите дату и время.')
        return
      }
      const parsed = new Date(scheduledAt)
      if (Number.isNaN(parsed.getTime())) {
        setScheduledAtError('Неверная дата или время.')
        return
      }
      requestedPickupAt = parsed.toISOString()
    }

    setSubmitError(null)
    setIsSubmitting(true)
    try {
      const response = await request<SubmitOrderResponse>('/v1/orders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          passengerReference: passengerId,
          pickupAddress: trimmedPickupAddress,
          destination: trimmedDestination,
          passengerName,
          ...(requestedPickupAt ? { requestedPickupAt } : {}),
        }),
        baseUrl: ORDER_MANAGEMENT_BASE_URL,
      })
      setOrderId(response.orderId)
      saveCurrentOrderId(driverCode ?? '', response.orderId)
      setStep('confirmed')
      void attemptProposal(response.orderId)
    } catch {
      setSubmitError('Не удалось связаться с сервером. Попробуйте ещё раз через несколько секунд.')
    } finally {
      setIsSubmitting(false)
    }
  }

  /**
   * Sprint 7B (Personal Network Flow MVP): proposes the just-created order
   * to the driver whose link the passenger arrived through. The order
   * itself already exists and is confirmed regardless of what happens
   * here — a failure never deletes it or blocks the confirmation screen,
   * per this sprint's own explicit requirement; it only offers a Retry.
   */
  async function attemptProposal(forOrderId: string) {
    setProposalStatus('proposing')
    try {
      await request('/v1/proposals', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ orderId: forOrderId, driverId: driverCode ?? '' }),
        baseUrl: DISPATCH_BASE_URL,
      })
      setProposalStatus('proposed')
    } catch {
      setProposalStatus('error')
    }
  }

  function handleRetryProposal() {
    if (orderId && proposalStatus !== 'proposing') {
      void attemptProposal(orderId)
    }
  }

  /**
   * P0-2 Tier 1 (`docs/SPRINT_PILOT_BLOCKERS.md`; ADR-053, Proposal
   * Resolution on Order Cancellation): calls the already-existing, already-
   * tested `POST /v1/orders/{id}/cancel` (`OrderCancellationController.kt`)
   * -- this button is the only thing that was missing; the backend
   * capability itself predates this change. Shown only while `rideStatus`
   * is `'OPEN'` (no driver has accepted yet), matching that ADR's own Tier
   * 1 scope ("cancel while no live Assignment exists") rather than
   * inventing a rule about cancelling mid-ride.
   *
   * Optimistically sets `rideStatus` to `'WITHDRAWN'` on success rather
   * than waiting for the next poll: Order Management's own cancellation is
   * synchronous and already confirmed by the 200 response, even though
   * Dispatch's own proposal withdrawal (which is what the poll actually
   * observes) happens moments later, asynchronously, via the outbox relay
   * (ADR-053). The next poll tick then confirms the same fact from the
   * server, so a failed optimistic update self-corrects within one tick.
   */
  async function handleCancelOrder() {
    if (!orderId || cancelStatus === 'submitting') {
      return
    }
    setCancelStatus('submitting')
    try {
      await request(`/v1/orders/${orderId}/cancel`, {
        method: 'POST',
        baseUrl: ORDER_MANAGEMENT_BASE_URL,
        headers: { Authorization: `Bearer ${identity!.token}` },
      })
      setRideStatus('WITHDRAWN')
      setCancelStatus('idle')
    } catch {
      setCancelStatus('error')
    }
  }

  /**
   * P0-1 (`docs/SPRINT_PILOT_BLOCKERS.md`): the passenger's own way back to
   * the order form once this driver's current order has reached a state
   * from which no further server-side progress is possible ('DECLINED',
   * 'LAPSED', 'COMPLETED' -- see the `rideStatus` gate below). Clears only
   * this driver's own stored order id (`localCurrentOrder.ts`'s own
   * per-driver scope) -- a current order held with a different driver, on
   * this same passenger identity, is untouched.
   */
  /** Section 6 ("Final Pre-Pilot Sprint"): a passenger must be able to sign out — the account itself is untouched, only this device forgets its own session. */
  function handleLogout() {
    identityProvider.logout()
    navigate(`/i/${driverCode ?? ''}`, { replace: true })
  }

  function handleOrderAgain() {
    clearCurrentOrderId(driverCode ?? '')
    setOrderId(null)
    setProposalStatus(null)
    setRideStatus('OPEN')
    setStatedPrice(null)
    setStatedEtaMinutes(null)
    setIsScheduled(false)
    setScheduledAt('')
    setScheduledAtError(null)
    setRequestedPickupAt(null)
    hasFetchedRequestedPickupAt.current = false
    setCancelStatus('idle')
    setStep('form')
  }

  /**
   * Rule 12/13 (`PRODUCT_DECISION_CIRCLE_OF_TRUST.md`): choosing a driver
   * for *this* ride only ever changes which driver this specific order goes
   * to -- never who is primary. Picking the driver whose own link this page
   * is already open through simply proceeds in place; picking anyone else
   * in the circle navigates to that driver's own `/i/:driverCode/request`,
   * reusing this same component fresh for them rather than teaching this
   * page to serve two drivers' state at once.
   */
  function handleChooseCircleMember(chosenDriverId: string) {
    if (chosenDriverId === driverCode) {
      setStep('form')
    } else {
      navigate(`/i/${chosenDriverId}/request`)
    }
  }

  function handleRequestMakePrimary(connectionId: string) {
    setPrimaryChangeTarget(connectionId)
    setPrimaryChangeStatus('idle')
  }

  function handleCancelMakePrimary() {
    setPrimaryChangeTarget(null)
    setPrimaryChangeStatus('idle')
  }

  /** Rule 4: only this explicit, passenger-confirmed action ever changes who is primary. */
  async function handleConfirmMakePrimary() {
    if (!identity || !primaryChangeTarget || primaryChangeStatus === 'submitting') {
      return
    }
    setPrimaryChangeStatus('submitting')
    try {
      await request(`/v1/connections/${primaryChangeTarget}/primary`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${identity.token}` },
        baseUrl: PASSENGER_EXPERIENCE_BASE_URL,
      })
      setCircle((current) =>
        current
          .map((member) => ({ ...member, isPrimary: member.connectionId === primaryChangeTarget }))
          .sort((a, b) => Number(b.isPrimary) - Number(a.isPrimary))
      )
      setPrimaryChangeTarget(null)
      setPrimaryChangeStatus('idle')
    } catch {
      setPrimaryChangeStatus('error')
    }
  }

  function handleRequestRemove(connectionId: string) {
    setRemoveTarget(connectionId)
    setRemoveStatus('idle')
  }

  function handleCancelRemove() {
    setRemoveTarget(null)
    setRemoveStatus('idle')
  }

  /** Rule 8: a passenger may remove any driver, primary or not, from their own circle at any time. */
  async function handleConfirmRemove() {
    if (!identity || !removeTarget || removeStatus === 'submitting') {
      return
    }
    setRemoveStatus('submitting')
    try {
      await request(`/v1/connections/${removeTarget}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${identity.token}` },
        baseUrl: PASSENGER_EXPERIENCE_BASE_URL,
      })
      setCircle((current) => current.filter((member) => member.connectionId !== removeTarget))
      setRemoveTarget(null)
      setRemoveStatus('idle')
    } catch {
      setRemoveStatus('error')
    }
  }

  return (
    <div className={styles.screen}>
      <Header />
      <main className={styles.content}>
        {step === 'loading' && <Spinner label="Загрузка…" />}

        {step === 'not-found' && (
          <p className={styles.status}>
            Ссылка недействительна или водитель ещё не зарегистрирован. Уточните ссылку у водителя, который вас
            пригласил.
          </p>
        )}

        {step === 'error' && (
          <div className={styles.errorBlock}>
            <p className={styles.error} role="alert">
              Не удалось загрузить приглашение. Проверьте связь с интернетом.
            </p>
            <ActionButton
              label="Попробовать снова"
              variant="secondary"
              onClick={() => loadInvitation(true, driverCode ?? '', identity)}
            />
          </div>
        )}

        {step === 'circle' && (
          <>
            <h1 className={styles.title}>Кому доверить эту поездку?</h1>
            {circleError && <p className={styles.hint}>Не удалось загрузить часть данных о ваших предпринимателях.</p>}

            {circle
              .filter((member) => member.isPrimary)
              .map((primary) => (
                <section key={primary.connectionId} className={styles.circleCard}>
                  <p className={styles.circleSectionLabel}>Основной предприниматель</p>
                  <p className={styles.circleName}>
                    {primary.displayName} {primary.availability === 'AVAILABLE' ? '🟢' : '🔴'}
                  </p>
                  <ActionButton
                    label="Вызвать"
                    variant="primary"
                    onClick={() => handleChooseCircleMember(primary.driverId)}
                    disabled={primary.availability !== 'AVAILABLE'}
                  />
                  {primary.availability !== 'AVAILABLE' && (
                    <p className={styles.hint}>Сейчас недоступен. Вот кому ещё вы доверяете:</p>
                  )}
                </section>
              ))}

            {circle.filter((member) => !member.isPrimary).length > 0 && (
              <section className={styles.circleCard}>
                <p className={styles.circleSectionLabel}>Другие доверенные предприниматели</p>
                {circle
                  .filter((member) => !member.isPrimary)
                  .map((member) => (
                    <div key={member.connectionId} className={styles.circleMemberRow}>
                      <span className={styles.circleName}>
                        {member.displayName} {member.availability === 'AVAILABLE' ? '🟢' : '🔴'}
                      </span>
                      <div className={styles.circleMemberActions}>
                        <ActionButton
                          label="Выбрать"
                          variant="secondary"
                          onClick={() => handleChooseCircleMember(member.driverId)}
                          disabled={member.availability !== 'AVAILABLE'}
                        />
                        <button
                          type="button"
                          className={styles.textAction}
                          onClick={() => handleRequestMakePrimary(member.connectionId)}
                        >
                          Сделать основным
                        </button>
                        <button
                          type="button"
                          className={styles.textAction}
                          onClick={() => handleRequestRemove(member.connectionId)}
                        >
                          Удалить
                        </button>
                      </div>
                    </div>
                  ))}
              </section>
            )}

            {primaryChangeTarget && (
              <div className={styles.confirmBox}>
                <p className={styles.status}>
                  Сделать {circle.find((member) => member.connectionId === primaryChangeTarget)?.displayName}{' '}
                  основным предпринимателем?
                </p>
                <div className={styles.actionRow}>
                  <ActionButton
                    label={primaryChangeStatus === 'submitting' ? 'Сохраняем…' : 'Да, сделать основным'}
                    variant="primary"
                    onClick={() => void handleConfirmMakePrimary()}
                    disabled={primaryChangeStatus === 'submitting'}
                  />
                  <ActionButton label="Отмена" variant="secondary" onClick={handleCancelMakePrimary} />
                </div>
                {primaryChangeStatus === 'error' && (
                  <p className={styles.error} role="alert">
                    Не удалось изменить основного предпринимателя. Попробуйте ещё раз.
                  </p>
                )}
              </div>
            )}

            {removeTarget && (
              <div className={styles.confirmBox}>
                <p className={styles.status}>
                  Удалить {circle.find((member) => member.connectionId === removeTarget)?.displayName} из круга
                  доверия?
                </p>
                <div className={styles.actionRow}>
                  <ActionButton
                    label={removeStatus === 'submitting' ? 'Удаляем…' : 'Да, удалить'}
                    variant="primary"
                    onClick={() => void handleConfirmRemove()}
                    disabled={removeStatus === 'submitting'}
                  />
                  <ActionButton label="Отмена" variant="secondary" onClick={handleCancelRemove} />
                </div>
                {removeStatus === 'error' && (
                  <p className={styles.error} role="alert">
                    Не удалось удалить. Попробуйте ещё раз.
                  </p>
                )}
              </div>
            )}
          </>
        )}

        {step === 'form' && (
          <>
            <h1 className={styles.title}>Заказать поездку</h1>

            <label className={styles.label} htmlFor="pickupAddress">
              Откуда
            </label>
            <input
              id="pickupAddress"
              className={styles.input}
              type="text"
              value={pickupAddress}
              placeholder="Укажите адрес"
              onChange={(event) => handlePickupAddressChange(event.target.value)}
            />
            {pickupAddressError && (
              <p className={styles.error} role="alert">
                {pickupAddressError}
              </p>
            )}

            <label className={styles.label} htmlFor="destination">
              Куда
            </label>
            <input
              id="destination"
              className={styles.input}
              type="text"
              value={destination}
              placeholder="Укажите адрес"
              onChange={(event) => handleDestinationChange(event.target.value)}
            />
            {destinationError && (
              <p className={styles.error} role="alert">
                {destinationError}
              </p>
            )}

            <label className={styles.label} htmlFor="whenScheduled">
              Когда
            </label>
            <select
              id="whenScheduled"
              className={styles.input}
              value={isScheduled ? 'later' : 'now'}
              onChange={(event) => setIsScheduled(event.target.value === 'later')}
            >
              <option value="now">Сейчас</option>
              <option value="later">Заранее</option>
            </select>
            {isScheduled && (
              <>
                <input
                  className={styles.input}
                  type="datetime-local"
                  value={scheduledAt}
                  min={currentDatetimeLocalValue()}
                  aria-label="Дата и время подачи"
                  onChange={(event) => handleScheduledAtChange(event.target.value)}
                />
                {scheduledAtError && (
                  <p className={styles.error} role="alert">
                    {scheduledAtError}
                  </p>
                )}
              </>
            )}

            {submitError && (
              <p className={styles.error} role="alert">
                {submitError}
              </p>
            )}

            <div className={styles.actionRow}>
              <ActionButton
                label={isSubmitting ? 'Отправляем…' : 'Заказать поездку'}
                variant="primary"
                onClick={handleSubmit}
                disabled={isSubmitting}
              />
            </div>
            <button type="button" className={styles.textAction} onClick={handleLogout}>
              Выйти
            </button>
          </>
        )}

        {step === 'confirmed' && orderId && (
          <>
            <p className={styles.confirmed}>✅ Заказ оформлен.</p>
            {requestedPickupAt && (
              <p className={styles.status}>📅 Заказ на: {formatRequestedPickupAt(requestedPickupAt)}</p>
            )}

            {proposalStatus === 'proposing' && <Spinner label="Сообщаем водителю…" />}
            {proposalStatus === 'error' && (
              <>
                <p className={styles.error} role="alert">
                  Не удалось передать заказ водителю. Заказ сохранён — можно попробовать ещё раз.
                </p>
                <div className={styles.actionRow}>
                  <ActionButton label="Повторить" variant="secondary" onClick={handleRetryProposal} />
                </div>
              </>
            )}

            {/* First-pilot feedback (ADR-040, ride lifecycle): this
                reflects live, polled status — shown whether this order was
                just submitted (proposalStatus 'proposed') or resumed after
                a reload (proposalStatus never set at all). */}
            {(proposalStatus === 'proposed' || proposalStatus === null) && (
              <>
                <p className={styles.status}>{RIDE_STATUS_LABEL[rideStatus]}</p>
                {/* ADR-042 R9: shown from ACCEPTED onward (never for OPEN/
                    DECLINED/LAPSED, where no acceptance -- and so no stated
                    amount -- exists yet); absent entirely if the driver
                    accepted without typing one, same as DriverHome.tsx's
                    own identical rendering of this field. */}
                {statedPrice && rideStatus !== 'OPEN' && rideStatus !== 'DECLINED' && rideStatus !== 'LAPSED' && (
                  <p className={styles.status}>Стоимость: {statedPrice}</p>
                )}
                {/* ADR-057: same placement and gating as statedPrice immediately above. */}
                {typeof statedEtaMinutes === 'number' &&
                  rideStatus !== 'OPEN' &&
                  rideStatus !== 'DECLINED' &&
                  rideStatus !== 'LAPSED' && (
                    <p className={styles.status}>Будет примерно через: {statedEtaMinutes} мин</p>
                  )}
                {/* P0-2 Tier 1: only while still OPEN -- a driver who has
                    already accepted has committed, and cancelling then is
                    out of this Tier's scope (handleCancelOrder's own
                    KDoc). */}
                {rideStatus === 'OPEN' && (
                  <div className={styles.actionRow}>
                    <ActionButton
                      label={cancelStatus === 'submitting' ? 'Отменяем…' : 'Отменить заказ'}
                      variant="secondary"
                      onClick={() => void handleCancelOrder()}
                      disabled={cancelStatus === 'submitting'}
                    />
                  </div>
                )}
                {cancelStatus === 'error' && (
                  <p className={styles.error} role="alert">
                    Не удалось отменить заказ. Попробуйте ещё раз.
                  </p>
                )}
                {/* P0-1: a terminal ride state ('DECLINED', 'LAPSED',
                    'WITHDRAWN', 'COMPLETED') is exactly where this driver's
                    link otherwise dead-ended forever -- 'OPEN', 'ACCEPTED',
                    'ARRIVED', 'IN_PROGRESS' keep today's behavior
                    unchanged, since a ride still in progress must not
                    offer a second, concurrent order with the same
                    driver. */}
                {(rideStatus === 'DECLINED' ||
                  rideStatus === 'LAPSED' ||
                  rideStatus === 'WITHDRAWN' ||
                  rideStatus === 'COMPLETED') && (
                  <div className={styles.actionRow}>
                    <ActionButton label="Заказать ещё раз" variant="primary" onClick={handleOrderAgain} />
                  </div>
                )}
              </>
            )}
          </>
        )}
      </main>
    </div>
  )
}
