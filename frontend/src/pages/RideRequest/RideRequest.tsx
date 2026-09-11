import { useEffect, useRef, useState } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router-dom'
import { Header } from '../../components/Header'
import { Button } from '../../components/Button'
import { LoadingState } from '../../components/LoadingState'
import { ErrorState } from '../../components/ErrorState'
import { StatusMessage } from '../../components/StatusMessage'
import { Heading } from '../../components/Heading'
import { Text } from '../../components/Text'
import { Card } from '../../components/Card'
import { FormField } from '../../components/FormField'
import { Input, Select } from '../../components/Input'
import { Divider } from '../../components/Divider'
import { DriverTrustIndicator } from '../../components/DriverTrustIndicator'
import { RideStatus } from '../../components/RideStatus'
import { getInvitationByDriverCode } from '../PassengerLanding/invitationSource'
import { BackendIdentityProvider } from '../../identity/BackendIdentityProvider'
import type { StoredIdentity } from '../../identity/IdentityProvider'
import { getDisplayName } from '../../persistence/localDisplayName'
import { clearCurrentOrderId, getCurrentOrderId, saveCurrentOrderId } from '../../persistence/localCurrentOrder'
import { request, resolveBackendBaseUrl } from '../../api/apiClient'
import styles from './RideRequest.module.css'

// ADR-038/ADR-039/ADR-055: same module-level provider instance `PassengerLanding.tsx` already uses.
const identityProvider = new BackendIdentityProvider()

// Order Management's own local port (INTERFACE_CONTRACTS.md) — distinct
// from apiClientConfig's default (Driver Management's port), since this
// is the second backend module this frontend now genuinely calls.
//
// Product audit (2026-09-11): resolved via [resolveBackendBaseUrl] -- a
// real remote passenger's own browser must reach this through
// `server/serve.mjs`'s own same-origin reverse proxy, never through a
// `localhost:8083` that only ever meant "this device," not the pilot host.
// See that function's own KDoc (`api/apiClient.ts`) for the full reasoning.
const ORDER_MANAGEMENT_BASE_URL = resolveBackendBaseUrl(import.meta.env.VITE_ORDER_MANAGEMENT_BASE_URL, 'http://localhost:8083')

// Dispatch's own local port (INTERFACE_CONTRACTS.md) — Sprint 7B (Personal
// Network Flow MVP): once the order exists, this page proposes it directly
// to the driver whose link the passenger arrived through, reusing
// Dispatch's already-existing `POST /v1/proposals` exactly as
// `Coordinator.tsx` already does for the general queue -- no Coordinator
// step for this, invited-passenger path.
const DISPATCH_BASE_URL = resolveBackendBaseUrl(import.meta.env.VITE_DISPATCH_BASE_URL, 'http://localhost:8084')

// Passenger Experience's own local port (INTERFACE_CONTRACTS.md) — Sprint
// "My Business + Circle of Trust" (ADR-054): this page now also calls that
// module directly, to read this passenger's own circle of trust before
// asking which driver today's ride goes to.
const PASSENGER_EXPERIENCE_BASE_URL = resolveBackendBaseUrl(import.meta.env.VITE_PASSENGER_EXPERIENCE_BASE_URL, 'http://localhost:8082')

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
  proposalId: string
  status: 'OPEN' | 'PRICE_PROPOSED' | 'ACCEPTED' | 'DECLINED' | 'LAPSED' | 'WITHDRAWN'
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
// Renamed from this file's own former local `RideStatus` type -- the
// name is now taken by the imported `RideStatus` component (design
// system, Task 8's own continuation audit -- not a committed file, see
// that task's own report). Kept as
// its own, narrower alias (not the shared `RideLifecycleStatus` directly)
// since this screen never produces `CREATED` -- the exhaustive switches
// below (`rideStatusLabel`) stay exactly as narrow as before; this type
// is still assignable everywhere `RideLifecycleStatus` is expected
// (the `<RideStatus status={rideStatus} .../>` call site below).
type PassengerRideStatus =
  | 'OPEN'
  | 'PRICE_PROPOSED'
  | 'DECLINED'
  | 'LAPSED'
  | 'WITHDRAWN'
  | 'ACCEPTED'
  | 'ARRIVED'
  | 'IN_PROGRESS'
  | 'COMPLETED'

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

/**
 * Design foundation cleanup (Task 7, docs/PIOS_DESIGN_IMPLEMENTATION_LOG.md):
 * this used to also name the driver inline (e.g. "{name} принял ваш
 * заказ") — that duplicated `DriverTrustIndicator`, which already renders
 * the driver's name prominently above this status on the confirmed-ride
 * screen (Task 5's own placement). This sentence now names only the
 * ride/order state, same real `RideStatus` values, same tone mapping —
 * no status text removed, only the repeated name.
 */
function rideStatusLabel(status: PassengerRideStatus): string {
  switch (status) {
    case 'OPEN':
      return '⏳ Ждём ответа водителя. Мы сообщим, как только он подтвердит заказ.'
    case 'PRICE_PROPOSED':
      return '💰 Водитель назвал цену — подтвердите или откажитесь ниже.'
    case 'DECLINED':
      return '❌ Водитель отклонил ваш заказ.'
    case 'LAPSED':
      return '⌛ Заказ больше не активен — водитель не ответил вовремя.'
    case 'WITHDRAWN':
      return '🚫 Вы отменили этот заказ.'
    case 'ACCEPTED':
      return '✅ Водитель принял ваш заказ и скоро свяжется с вами.'
    case 'ARRIVED':
      return '🚗 Водитель прибыл на место.'
    case 'IN_PROGRESS':
      return '🚕 Поездка началась.'
    case 'COMPLETED':
      return '🏁 Поездка завершена. Спасибо, что выбрали PIOS!'
  }
}

// The status→tone mapping this function used to own now lives in exactly
// one place, the shared `RideStatus` component itself (imported above) —
// removed here rather than left as unused dead code now that the one
// call site below (Section "Design foundation cleanup") passes `status`
// to that component directly instead of calling this function.

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
 *
 * Task 17 (First Refusal Explicit Driver Intent Integration): [handleSubmit]
 * now sends `explicitDriverIntent: true` on every `POST /v1/orders` this
 * screen makes — not a new concept, `Order Management`'s own
 * `SubmitOrderRequest.explicitDriverIntent` (Task 15C) already existed and
 * already threaded end-to-end to `OrderSubmitted`, simply unused by any
 * real caller until now. Correct unconditionally here: this screen only
 * ever exists reached through one specific driver's own `driverCode` (the
 * URL parameter), and always proposes the resulting order to exactly that
 * driver a moment later ([attemptProposal]) — there is no order this
 * screen ever creates without an already-known, specific driver. Setting
 * this stops Dispatch's automatic First Refusal (Task 16) from racing that
 * already-explicit choice for orders created here.
 */
export function RideRequest() {
  const { driverCode } = useParams<{ driverCode: string }>()
  const navigate = useNavigate()
  const [identity, setIdentity] = useState<StoredIdentity | null>(null)
  const [identityChecked, setIdentityChecked] = useState(false)
  const [step, setStep] = useState<Step>('loading')
  // UX audit (Language Policy): the invited driver's own display name,
  // already fetched by [loadInvitation] below — kept here so the confirmed
  // screen's [DriverTrustIndicator] can show it (Task 7: no longer also
  // repeated inside [rideStatusLabel]'s status sentence).
  const [driverName, setDriverName] = useState<string | null>(null)
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
  // PIOS Group and Long-Distance Rides Roadmap, Stage 2: optional --
  // `null` (never sent) is the existing, unchanged "one passenger, not
  // specified" assumption, same as leaving it blank. No matching or
  // filtering happens on this value anywhere in PIOS (see
  // `Order.passengerCount`'s own KDoc, Order Management); it exists so a
  // driver/Coordinator can see it next to a group's own request.
  const [passengerCount, setPassengerCount] = useState('')
  const [passengerCountError, setPassengerCountError] = useState<string | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [orderId, setOrderId] = useState<string | null>(null)
  const [proposalStatus, setProposalStatus] = useState<ProposalStatus | null>(null)
  const [rideStatus, setRideStatus] = useState<PassengerRideStatus>('OPEN')
  // Product Owner instruction, 2026-09-05: needed to call confirm-price/
  // decline-price on the exact proposal the driver named a price on --
  // read from the same poll every other proposal-derived field already
  // comes from.
  const [proposalId, setProposalId] = useState<string | null>(null)
  const [priceDecisionStatus, setPriceDecisionStatus] = useState<'idle' | 'submitting' | 'error'>('idle')
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
      setDriverName(result.invitation.driverName)
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
      // Proposal Participant Authorization (ADR-066, P0 remediation):
      // `GET /v1/proposals?orderId=` now requires this passenger's own
      // Bearer token -- `currentIdentity` is already guaranteed non-null
      // by this effect's own early return above.
      request<ProposalStatusItem[]>(`/v1/proposals?orderId=${orderId}`, {
        baseUrl: DISPATCH_BASE_URL,
        headers: { Authorization: `Bearer ${currentIdentity.token}` },
      })
        .then((items) => {
          if (!active) {
            return
          }
          const acceptedItem = items.find((item) => item.status === 'ACCEPTED')
          if (!acceptedItem) {
            // Product Owner instruction, 2026-09-05: a driver-named price
            // awaiting this passenger's own decision -- checked before the
            // OPEN/DECLINED/LAPSED/WITHDRAWN priority group below, since
            // it is itself a real, distinct, non-terminal fact, not one of
            // those four.
            const priceProposedItem = items.find((item) => item.status === 'PRICE_PROPOSED')
            if (priceProposedItem) {
              setRideStatus('PRICE_PROPOSED')
              setProposalId(priceProposedItem.proposalId)
              setStatedPrice(priceProposedItem.statedPrice)
              setStatedEtaMinutes(priceProposedItem.statedEtaMinutes)
              return
            }
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
              setRideStatus(status && status !== 'CREATED' ? (status as PassengerRideStatus) : 'ACCEPTED')
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
          <LoadingState label="Загрузка…" />
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

  function handlePassengerCountChange(value: string) {
    setPassengerCount(value)
    if (passengerCountError) {
      setPassengerCountError(null)
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
    // PIOS Group and Long-Distance Rides Roadmap, Stage 2: structural
    // validation only, same as `requestedPickupAt` below -- a value that
    // does not parse, or is zero/negative, is rejected here in the UI;
    // PIOS itself asserts nothing about whether it fits any given car.
    let parsedPassengerCount: number | null = null
    const trimmedPassengerCount = passengerCount.trim()
    if (trimmedPassengerCount) {
      const parsed = Number.parseInt(trimmedPassengerCount, 10)
      if (Number.isNaN(parsed) || !Number.isFinite(parsed) || parsed < 1) {
        setPassengerCountError('Укажите число больше нуля.')
        return
      }
      parsedPassengerCount = parsed
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
        // Order Provenance / Authentication Remediation (P0,
        // `docs/PIOS_DATA_FLOW_CODE_AUDIT.md` Section 5): `POST /v1/orders`
        // now requires this passenger's own Bearer token, whose `sub` must
        // equal `passengerReference` below -- `identity` is already
        // guaranteed non-null here (this component's own early
        // `if (!identity) return ...` above); non-null assertion only
        // because TypeScript's narrowing does not carry into this nested
        // function's own closure, same reasoning already applied elsewhere
        // in this file (e.g. `attemptProposal`'s own `identity!.token`).
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${identity!.token}` },
        body: JSON.stringify({
          passengerReference: passengerId,
          pickupAddress: trimmedPickupAddress,
          destination: trimmedDestination,
          passengerName,
          ...(requestedPickupAt ? { requestedPickupAt } : {}),
          ...(parsedPassengerCount ? { passengerCount: parsedPassengerCount } : {}),
          // Task 17 (First Refusal Explicit Driver Intent Integration):
          // every order this screen ever creates is already tied to one
          // specific, already-known driver -- `driverCode`, taken verbatim
          // from the URL this page is reached through (see this
          // component's own KDoc and `handleChooseCircleMember`: picking
          // any other circle member navigates to *that* driver's own
          // `/i/:driverCode/request` rather than leaving the choice open).
          // `attemptProposal`, right below, proposes this exact order to
          // that exact driver a moment later. `explicitDriverIntent` (Order
          // Management's own field, `SubmitOrderRequest.kt`, unused by any
          // real caller until now) records that already-true fact
          // atomically with submission, so Dispatch's automatic First
          // Refusal (Task 16) never races this screen's own explicit
          // choice for an order created here.
          explicitDriverIntent: true,
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
   *
   * Task 21 (Proposal API Security Remediation): `POST /v1/proposals` now
   * requires an `Authorization` header -- this passenger's own already-held
   * session token (`identity.token`, the same one already sent on this
   * screen's own Circle-of-Trust calls) is sufficient; Dispatch's own new
   * check only requires *some* authenticated caller for `create`, not a
   * verified relationship to this specific order or driver (see
   * `docs/PIOS_TAXI_TASK_20_PROPOSAL_SECURITY_AUDIT.md`).
   *
   * Proposal Participant Authorization (ADR-066, P0 remediation): the
   * request body now also carries `passengerReference`, required to equal
   * this same token's own `sub` (`identity.identityId`) -- Dispatch's own
   * 403 otherwise. Sent as the same value `passengerId` already uses
   * elsewhere on this screen (`submitOrder`'s own `passengerReference`).
   */
  async function attemptProposal(forOrderId: string) {
    setProposalStatus('proposing')
    try {
      await request('/v1/proposals', {
        method: 'POST',
        // Non-null assertion: this function is only ever reachable from a
        // click on JSX that itself only renders once identity is non-null
        // (the component's own early `if (!identity) return ...` above) --
        // same reasoning already applied elsewhere in this codebase (e.g.
        // DriverHome.tsx's own `identity.driverId!`).
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${identity!.token}` },
        body: JSON.stringify({ orderId: forOrderId, driverId: driverCode ?? '', passengerReference: identity!.identityId }),
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
   *
   * Task 25 (Orders Cancellation & Driver Availability Security
   * Remediation): now sends this passenger's own Bearer token --
   * `OrderCancellationController` verifies it names the exact passenger
   * `Order.origin` belongs to before permitting cancellation (see
   * docs/PIOS_TAXI_TASK_24_REMAINING_MUTATION_API_SECURITY_AUDIT.md and
   * docs/PIOS_TAXI_TASK_25_SECURITY_REMEDIATION_REPORT.md). `identity` is
   * guaranteed non-null here for the same reason already established for
   * `attemptProposal` (Task 17/21): this whole screen returns early,
   * before any JSX, when identity is null.
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
   * Product Owner instruction, 2026-09-05: the passenger's own agreement
   * to the price the driver named -- calls Dispatch's own
   * `POST /v1/proposals/{id}/confirm-price`, which also creates the
   * Assignment behind the scenes (mirrors the old direct-accept flow's own
   * effect). Optimistic: sets `rideStatus` to `'ACCEPTED'` immediately
   * rather than waiting for the next poll tick, matching
   * [handleCancelOrder]'s own optimistic-update precedent -- a failure
   * self-corrects on the next poll, since the server-side state never
   * actually changed.
   */
  async function handleConfirmPrice() {
    if (!proposalId || priceDecisionStatus === 'submitting' || !identity) {
      return
    }
    setPriceDecisionStatus('submitting')
    try {
      await request(`/v1/proposals/${proposalId}/confirm-price`, {
        method: 'POST',
        baseUrl: DISPATCH_BASE_URL,
        headers: { Authorization: `Bearer ${identity.token}` },
      })
      setRideStatus('ACCEPTED')
      setPriceDecisionStatus('idle')
    } catch {
      setPriceDecisionStatus('error')
    }
  }

  /**
   * Product Owner instruction, 2026-09-05: the passenger's own refusal of
   * the price the driver named -- calls Dispatch's own
   * `POST /v1/proposals/{id}/decline-price`. Per that same instruction,
   * this simply closes the request: no renegotiation, no automatic
   * reroute to another driver -- the passenger decides what to do next,
   * exactly like an ordinary decline.
   */
  async function handleDeclinePrice() {
    if (!proposalId || priceDecisionStatus === 'submitting' || !identity) {
      return
    }
    setPriceDecisionStatus('submitting')
    try {
      await request(`/v1/proposals/${proposalId}/decline-price`, {
        method: 'POST',
        baseUrl: DISPATCH_BASE_URL,
        headers: { Authorization: `Bearer ${identity.token}` },
      })
      setRideStatus('DECLINED')
      setPriceDecisionStatus('idle')
    } catch {
      setPriceDecisionStatus('error')
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
        {step === 'loading' && <LoadingState label="Загрузка…" />}

        {step === 'not-found' && (
          <StatusMessage tone="warning">
            Ссылка недействительна или водитель ещё не зарегистрирован. Уточните ссылку у водителя, который вас
            пригласил.
          </StatusMessage>
        )}

        {step === 'error' && (
          <ErrorState
            message="Не удалось загрузить приглашение. Проверьте связь с интернетом."
            onRetry={() => loadInvitation(true, driverCode ?? '', identity)}
          />
        )}

        {step === 'circle' && (
          <>
            <Heading level={1}>Кому доверить эту поездку?</Heading>
            {circleError && (
              <Text role="caption" tone="muted">
                Не удалось загрузить часть данных о ваших водителях.
              </Text>
            )}

            {/* This screen's own "known driver" moment (docs/PIOS_DESIGN_SYSTEM.md
                Section 10): the passenger's own recognized/primary driver is
                named, shown first, with a single clear "Вызвать" action —
                never a count, never a rank among the rest of the circle. */}
            {circle
              .filter((member) => member.isPrimary)
              .map((primary) => (
                <Card key={primary.connectionId}>
                  <DriverTrustIndicator
                    name={primary.displayName}
                    availability={primary.availability}
                    isPrimary
                    emphasis="prominent"
                  />
                  <div className={styles.actionRow}>
                    <Button
                      label="Вызвать"
                      variant="primary"
                      onClick={() => handleChooseCircleMember(primary.driverId)}
                      disabled={primary.availability !== 'AVAILABLE'}
                    />
                  </div>
                  {primary.availability !== 'AVAILABLE' && (
                    <Text role="caption" tone="muted">
                      Сейчас недоступен. Вот кому ещё вы доверяете:
                    </Text>
                  )}
                </Card>
              ))}

            {circle.filter((member) => !member.isPrimary).length > 0 && (
              <Card>
                <Text role="label" tone="muted">
                  Другие ваши водители
                </Text>
                {circle
                  .filter((member) => !member.isPrimary)
                  .map((member, index) => (
                    <div key={member.connectionId} className={styles.circleMemberRow}>
                      {index > 0 && <Divider />}
                      <DriverTrustIndicator name={member.displayName} availability={member.availability} emphasis="compact" />
                      <div className={styles.circleMemberActions}>
                        <Button
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
              </Card>
            )}

            {primaryChangeTarget && (
              <Card align="center">
                <Text role="body" tone="secondary">
                  Сделать {circle.find((member) => member.connectionId === primaryChangeTarget)?.displayName}{' '}
                  основным водителем?
                </Text>
                <div className={styles.actionRow}>
                  <Button
                    label="Да, сделать основным"
                    variant="primary"
                    loading={primaryChangeStatus === 'submitting'}
                    onClick={() => void handleConfirmMakePrimary()}
                  />
                  <Button label="Отмена" variant="secondary" onClick={handleCancelMakePrimary} />
                </div>
                {primaryChangeStatus === 'error' && (
                  <StatusMessage tone="error">Не удалось изменить основного предпринимателя. Попробуйте ещё раз.</StatusMessage>
                )}
              </Card>
            )}

            {removeTarget && (
              <Card align="center">
                <Text role="body" tone="secondary">
                  Удалить {circle.find((member) => member.connectionId === removeTarget)?.displayName} из списка
                  водителей?
                </Text>
                <div className={styles.actionRow}>
                  <Button
                    label="Да, удалить"
                    variant="destructive"
                    loading={removeStatus === 'submitting'}
                    onClick={() => void handleConfirmRemove()}
                  />
                  <Button label="Отмена" variant="secondary" onClick={handleCancelRemove} />
                </div>
                {removeStatus === 'error' && <StatusMessage tone="error">Не удалось удалить. Попробуйте ещё раз.</StatusMessage>}
              </Card>
            )}
          </>
        )}

        {step === 'form' && (
          <div className={styles.formStack}>
            <Heading level={1}>Заказать поездку</Heading>

            <FormField label="Откуда" htmlFor="pickupAddress" error={pickupAddressError}>
              <Input
                id="pickupAddress"
                type="text"
                value={pickupAddress}
                placeholder="Укажите адрес"
                invalid={Boolean(pickupAddressError)}
                aria-describedby={pickupAddressError ? 'pickupAddress-error' : undefined}
                onChange={(event) => handlePickupAddressChange(event.target.value)}
              />
            </FormField>

            <FormField label="Куда" htmlFor="destination" error={destinationError}>
              <Input
                id="destination"
                type="text"
                value={destination}
                placeholder="Укажите адрес"
                invalid={Boolean(destinationError)}
                aria-describedby={destinationError ? 'destination-error' : undefined}
                onChange={(event) => handleDestinationChange(event.target.value)}
              />
            </FormField>

            <FormField label="Сколько пассажиров (необязательно)" htmlFor="passengerCount" error={passengerCountError}>
              <Input
                id="passengerCount"
                type="number"
                min={1}
                value={passengerCount}
                placeholder="Например, 4"
                invalid={Boolean(passengerCountError)}
                aria-describedby={passengerCountError ? 'passengerCount-error' : undefined}
                onChange={(event) => handlePassengerCountChange(event.target.value)}
              />
            </FormField>

            <FormField label="Когда" htmlFor="whenScheduled">
              <Select
                id="whenScheduled"
                value={isScheduled ? 'later' : 'now'}
                onChange={(event) => setIsScheduled(event.target.value === 'later')}
              >
                <option value="now">Сейчас</option>
                <option value="later">Заранее</option>
              </Select>
            </FormField>
            {isScheduled && (
              <FormField label="Дата и время подачи" htmlFor="scheduledAt" error={scheduledAtError}>
                <Input
                  id="scheduledAt"
                  type="datetime-local"
                  value={scheduledAt}
                  min={currentDatetimeLocalValue()}
                  invalid={Boolean(scheduledAtError)}
                  aria-describedby={scheduledAtError ? 'scheduledAt-error' : undefined}
                  onChange={(event) => handleScheduledAtChange(event.target.value)}
                />
              </FormField>
            )}

            {submitError && <StatusMessage tone="error">{submitError}</StatusMessage>}

            {/* One clear primary action per screen (docs/PIOS_TAXI_DESIGN_BRIEF.md
                Section 2, Principle 8) — "Заказать поездку" is the only
                primary-weighted control on this step; "Выйти" stays a
                quiet text action below it. */}
            <div className={styles.actionRow}>
              <Button label="Заказать поездку" variant="primary" loading={isSubmitting} onClick={handleSubmit} />
            </div>
            <button type="button" className={styles.textAction} onClick={handleLogout}>
              Выйти
            </button>
          </div>
        )}

        {step === 'confirmed' && orderId && (
          <div className={styles.formStack}>
            {/* DRIVER-AS-FACE (docs/PIOS_TAXI_DESIGN_BRIEF.md Section 2,
                Principle 1): the driver's name is the first, most
                prominent thing on this screen. Task 7 (final design
                foundation cleanup): the status sentence below no longer
                repeats the name a second time — [rideStatusLabel] now
                names only the ride/order state, since this indicator is
                already the one place that names the driver. No
                availability/isPrimary is passed here: this step never
                loads either fact (docs/DRIVER_IDENTITY_DESIGN_DECISION.md
                Section 9), so the component is not asked to assert what
                the code hasn't actually checked. */}
            {driverName && <DriverTrustIndicator name={driverName} emphasis="prominent" />}
            <StatusMessage tone="success">✅ Заказ оформлен.</StatusMessage>
            <Text role="caption" tone="muted">
              Здесь вы увидите, что происходит с вашим заказом — от отправки до завершения поездки.
            </Text>
            {requestedPickupAt && (
              <Text role="body" tone="secondary">
                📅 Заказ на: {formatRequestedPickupAt(requestedPickupAt)}
              </Text>
            )}

            {proposalStatus === 'proposing' && <LoadingState label="Сообщаем водителю…" />}
            {proposalStatus === 'error' && (
              <ErrorState
                message="Не удалось передать заказ водителю. Заказ сохранён — можно попробовать ещё раз."
                retryLabel="Повторить"
                onRetry={handleRetryProposal}
              />
            )}

            {/* First-pilot feedback (ADR-040, ride lifecycle): this
                reflects live, polled status — shown whether this order was
                just submitted (proposalStatus 'proposed') or resumed after
                a reload (proposalStatus never set at all). */}
            {(proposalStatus === 'proposed' || proposalStatus === null) && (
              <>
                <RideStatus status={rideStatus} label={rideStatusLabel(rideStatus)} />
                {/* ADR-042 R9: shown from ACCEPTED onward (never for OPEN/
                    DECLINED/LAPSED, where no acceptance -- and so no stated
                    amount -- exists yet); absent entirely if the driver
                    accepted without typing one, same as DriverHome.tsx's
                    own identical rendering of this field. Rendered with
                    role="numeric" (docs/PIOS_DESIGN_SYSTEM.md Section 3):
                    this is genuinely numeric fare/ETA data, unlike the
                    other status text on this screen. */}
                {/* Product Owner instruction, 2026-09-05: excluded here
                    during PRICE_PROPOSED too -- the dedicated confirm/
                    decline block immediately below already shows the same
                    price as part of that decision, so this generic line
                    stays reserved for ACCEPTED-onward, exactly as before. */}
                {statedPrice &&
                  rideStatus !== 'OPEN' &&
                  rideStatus !== 'PRICE_PROPOSED' &&
                  rideStatus !== 'DECLINED' &&
                  rideStatus !== 'LAPSED' && (
                    <Text role="numeric" tone="primary">
                      Стоимость: {statedPrice}
                    </Text>
                  )}
                {/* ADR-057: same placement and gating as statedPrice immediately above. */}
                {typeof statedEtaMinutes === 'number' &&
                  rideStatus !== 'OPEN' &&
                  rideStatus !== 'PRICE_PROPOSED' &&
                  rideStatus !== 'DECLINED' &&
                  rideStatus !== 'LAPSED' && (
                    <Text role="numeric" tone="primary">
                      Будет примерно через: {statedEtaMinutes} мин
                    </Text>
                  )}
                {/* Product Owner instruction, 2026-09-05: the driver named
                    a price -- the passenger must confirm or decline it
                    before a ride is settled. No renegotiation offered
                    here: a decline simply closes the request, matching
                    that instruction exactly ("заявка просто закрывается"). */}
                {rideStatus === 'PRICE_PROPOSED' && (
                  <>
                    <Text role="numeric" tone="primary" strong>
                      Водитель предлагает: {statedPrice}
                    </Text>
                    {typeof statedEtaMinutes === 'number' && (
                      <Text role="body" tone="secondary">
                        Будет примерно через: {statedEtaMinutes} мин
                      </Text>
                    )}
                    <div className={styles.actionRow}>
                      <Button
                        label="Подтвердить поездку"
                        variant="primary"
                        loading={priceDecisionStatus === 'submitting'}
                        onClick={() => void handleConfirmPrice()}
                      />
                      <Button
                        label="Отказаться"
                        variant="destructive"
                        loading={priceDecisionStatus === 'submitting'}
                        onClick={() => void handleDeclinePrice()}
                      />
                    </div>
                    {priceDecisionStatus === 'error' && (
                      <StatusMessage tone="error">Не удалось отправить решение. Попробуйте ещё раз.</StatusMessage>
                    )}
                  </>
                )}
                {/* P0-2 Tier 1: only while still OPEN -- a driver who has
                    already accepted has committed, and cancelling then is
                    out of this Tier's scope (handleCancelOrder's own
                    KDoc). */}
                {rideStatus === 'OPEN' && (
                  <div className={styles.actionRow}>
                    <Button
                      label="Отменить заказ"
                      variant="destructive"
                      loading={cancelStatus === 'submitting'}
                      onClick={() => void handleCancelOrder()}
                    />
                  </div>
                )}
                {cancelStatus === 'error' && <StatusMessage tone="error">Не удалось отменить заказ. Попробуйте ещё раз.</StatusMessage>}
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
                    <Button label="Заказать ещё раз" variant="primary" onClick={handleOrderAgain} />
                  </div>
                )}
                {/* Product audit (2026-09-12): "Мои водители" (/me) is this
                    product's own designated repeat-a-ride path
                    (MyDrivers.tsx's own KDoc) but had no link to it
                    anywhere in the app -- a returning passenger, right at
                    the exact moment their first ride finished and "what
                    now" matters most, had no way to discover it existed.
                    Placed only here (not the shared, prop-less Header,
                    which DriverHome.tsx also renders -- a passenger-only
                    link there would wrongly surface on a driver's own
                    screen), and only alongside the other terminal-state
                    actions above, for the same reason [handleOrderAgain]
                    is gated the same way: a ride still open or in progress
                    must not distract with "see your other drivers" while
                    this one is the only thing that matters right now. */}
                {(rideStatus === 'DECLINED' ||
                  rideStatus === 'LAPSED' ||
                  rideStatus === 'WITHDRAWN' ||
                  rideStatus === 'COMPLETED') && (
                  <button type="button" className={styles.textAction} onClick={() => navigate('/me')}>
                    Мои водители
                  </button>
                )}
              </>
            )}
          </div>
        )}
      </main>
    </div>
  )
}
