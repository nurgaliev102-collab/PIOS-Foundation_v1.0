import { useEffect, useMemo, useRef, useState } from 'react'
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
import { Input, Select, Textarea } from '../../components/Input'
import { Divider } from '../../components/Divider'
import { DriverTrustIndicator } from '../../components/DriverTrustIndicator'
import { RideStatus } from '../../components/RideStatus'
import { MessageBubble } from '../../components/MessageBubble'
import { getInvitationByDriverCode } from '../PassengerLanding/invitationSource'
import { BackendIdentityProvider } from '../../identity/BackendIdentityProvider'
import type { StoredIdentity } from '../../identity/IdentityProvider'
import { LocalInvitationProvider } from '../../identity/InvitationProvider'
import { getDisplayName } from '../../persistence/localDisplayName'
import { clearCurrentOrderId, getCurrentOrderId, saveCurrentOrderId } from '../../persistence/localCurrentOrder'
import { isSessionExpiredError, request, resolveBackendBaseUrl, SESSION_EXPIRED_MESSAGE } from '../../api/apiClient'
import { useBackableStep } from '../../navigation/useBackableStep'
import {
  NotificationBell,
  useNotificationFacts,
  type NotificationAssignmentSnapshot,
  type NotificationMessageSnapshot,
  type NotificationOrderSnapshot,
  type NotificationProposalSnapshot,
} from '../../features/notifications'
import styles from './RideRequest.module.css'

// ADR-071: this screen has no D5-equivalent fact (Part 3's table has no
// passenger-facing row derived from `Order.status`) -- a single stable
// empty array, not re-created every render, so it never spuriously
// changes [useNotificationFacts]'s own effect dependency identity.
const NO_NOTIFICATION_ORDERS: NotificationOrderSnapshot[] = []

// ADR-038/ADR-039/ADR-055: same module-level provider instance `PassengerLanding.tsx` already uses.
const identityProvider = new BackendIdentityProvider()

// Product audit follow-up (2026-09-12): the same provider `DriverHome.tsx`
// already instantiates module-level for its own QR card / per-client share
// action -- no new invitation mechanism, this screen just becomes a second
// caller of the one that already exists.
const invitationProvider = new LocalInvitationProvider()

// Same duration DriverHome.tsx's own `FEEDBACK_DURATION_MS` already uses
// for an identical copy/share confirmation.
const SHARE_FEEDBACK_DURATION_MS = 2000

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
  // ADR-070 (Channel 1 Discovery Matching), Part 2, Q8: `ProposalResponse.kt`
  // (Dispatch) has always returned this field -- no backend change. Unused
  // by this screen's own driver-linked path (that path already knows the
  // driver from `driverCode`, the URL parameter); read only by the
  // driverless discovery path below, which has no other way to learn which
  // real driver Fallback Dispatch matched this order to.
  driverId: string
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
 * Minimal In-Ride Messaging (Product Cycle): `GET/POST
 * /v1/proposals/:id/messages`'s own response shape (Dispatch) -- a single
 * short message exchanged between this proposal's own passenger and
 * driver. [senderRole] is derived server-side from which identity the
 * caller's own Bearer token names (never a caller-supplied claim), the
 * same authorization discipline every other Proposal action on this
 * screen already relies on.
 */
interface ProposalMessageItem {
  id: string
  senderRole: 'PASSENGER' | 'DRIVER'
  body: string
  sentAt: string
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

// Product Cycle (Passenger Ride Requirements): the one bound this screen
// itself enforces on "Пожелания к поездке" -- matches Order.MAX_NOTES_LENGTH
// (Order Management) exactly, so a value this form lets a passenger type
// never gets silently rejected by the backend after submission. Enforced
// twice, deliberately: the textarea's own `maxLength` stops most input at
// the source, and [handleSubmit] truncates defensively before sending, in
// case any caller bypasses the control itself (e.g. a pasted value some
// browser does not truncate on paste).
const NOTES_MAX_LENGTH = 500

// Minimal In-Ride Messaging (Product Cycle): matches
// ProposalMessage.MAX_BODY_LENGTH (Dispatch) exactly, for the same reason
// NOTES_MAX_LENGTH mirrors Order.MAX_NOTES_LENGTH -- so nothing typed here
// is ever silently rejected by the backend after submission. Shorter than
// NOTES_MAX_LENGTH: this is a short, chat-like message exchanged mid-ride,
// not the one-time trip context [notes] already covers.
const MESSAGE_MAX_LENGTH = 300

// ADR-070 (Channel 1 Discovery Matching), Part 1: `localCurrentOrder.ts`'s
// own map is keyed by `driverCode` (one entry per invitation link a
// passenger has used) -- the driverless entry point this ADR authorizes has
// no `driverCode` to key by, so it uses this one fixed key instead. Safe
// from collision: every real `driverCode` this map is otherwise keyed by is
// a backend-issued driver id, never this literal string.
const DISCOVERY_ORDER_KEY = '__discovery__'

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
 * ADR-070 (Channel 1 Discovery Matching), Part 2, Q8: the driverless
 * (`/request`) path's only way to learn which real driver Fallback
 * Dispatch matched this order to -- the `driverCode` path never calls
 * this (it already knows its driver from the URL). Picks whichever
 * [ProposalStatusItem] is most "current" for this order, in the same
 * priority the polling effect below already establishes for rendering
 * (ACCEPTED, the driver who is actually doing this ride, first) -- a
 * historical DECLINED/LAPSED entry from an earlier fallback attempt (the
 * pre-existing lapse/retry mechanism this ADR does not touch) is only
 * used as a last resort, so this never names a driver who is no longer
 * the one attached to this order once a later attempt exists.
 */
function pickProposalDriverId(items: ProposalStatusItem[]): string | null {
  const priority: ProposalStatusItem['status'][] = ['ACCEPTED', 'PRICE_PROPOSED', 'OPEN', 'DECLINED', 'LAPSED', 'WITHDRAWN']
  for (const status of priority) {
    const found = items.find((item) => item.status === status)
    if (found) {
      return found.driverId
    }
  }
  return null
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
 * Product Cycle (Passenger Ride Requirements) reintroduces a "Пожелания к
 * поездке" field under the same local variable name ([notes]) the removed
 * one above used — deliberately not the same mistake twice: Order
 * Management's own contract now genuinely accepts and persists `notes`
 * (`SubmitOrderRequest.kt`/`Order.kt`/`PostgreSQLOrderRepository.kt`, a new
 * `notes` column via `V13__add_order_notes.sql`), and it is read back by
 * `DriverHome.tsx`'s own `GET /v1/orders?ids=` the exact same way
 * `pickupAddress`/`destination`/`passengerCount` already are, rendered in
 * `ProposalDetails` before a driver names a price. Bounded to
 * `NOTES_MAX_LENGTH` (500) both here and, independently, in
 * `Order.MAX_NOTES_LENGTH` on the backend.
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
 *
 * Repeat Ride (Product Cycle): the COMPLETED branch of the repeat button
 * (`rideStatus === 'COMPLETED'`, below) reads [circle] to choose its label
 * -- "Заказать у этого водителя" when this driver is already in the
 * passenger's circle of trust, "Повторить поездку" otherwise. No new
 * backend/API surface: [circle] is exactly the same state
 * [loadCircleThenAdvance] already populates for a brand-new order (see that
 * function's own KDoc for the one disclosed edge case this reuse accepts).
 * Preserving the route/details across a repeat ("сохранением маршрута и
 * основных параметров") needed no new code at all: [handleOrderAgain]
 * already never reset `pickupAddress`/`destination`/`passengerCount`/
 * `notes`, only the order-specific state (id, status, price, schedule).
 * "Primary Driver gets the first right of offer, not exclusivity" (PIOS's
 * own product rule): repeating defaults to the driver just ridden with, but
 * "Мои водители" — rendered alongside, unconditionally, for every terminal
 * status — remains the passenger's own way to choose someone else instead.
 *
 * ADR-070 (Channel 1 Discovery Matching, Part 1/2): also rendered,
 * behavior-unchanged for every `driverCode`-keyed branch above, at
 * `/request` -- a route with no `driverCode` segment, so `driverCode` is
 * `undefined` throughout this render. That path (every `driverCode`/
 * `!driverCode` branch below) submits `POST /v1/orders` with
 * `explicitDriverIntent` omitted entirely (defaults to `false` --
 * `SubmitOrderRequest.kt`) and never calls [attemptProposal] -- the driver
 * is chosen by Dispatch's existing First Refusal -> Fallback Dispatch chain
 * (ADR-068/ADR-069), not this client. Since no driver is known at
 * submission time, this screen learns which real driver Dispatch matched by
 * reading the existing [ProposalStatusItem] poll's own `driverId` field
 * (see that interface's own KDoc) and reuses the exact same
 * [driverName]/[driverAvailability] state and `DriverTrustIndicator`
 * rendering the `driverCode` path already has. Until a Proposal exists at
 * all, this is the honest `FallbackDispatchOutcome.NoAvailableDriver` case
 * (Part 7, Q9) -- no retry/timeout rule invented, just the plain fact
 * stated once the existing poll has run. [handleSaveDriver] (Option B) is
 * unchanged in mechanism, only in which driver id it targets -- see that
 * function's own KDoc.
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
  // Referral funnel friction audit (2026-09-12): same source
  // ([loadInvitation]'s own `InvitationInfo`, see that interface's own
  // KDoc) as [driverName] above -- kept separately so the plain order
  // form (the one path every 0-or-1-relationship referral actually takes,
  // circle step skipped entirely) can finally show it before submission,
  // not only after.
  const [driverAvailability, setDriverAvailability] = useState<'AVAILABLE' | 'UNAVAILABLE' | null>(null)
  const [circle, setCircle] = useState<EnrichedCircleMember[]>([])
  const [circleError, setCircleError] = useState(false)
  // P1 UX audit (2026-09-12): session expiry, centrally classified by
  // [isSessionExpiredError] (`api/apiClient.ts`) -- see this screen's own
  // [handleSessionExpiredError] for the one place every catch block below
  // now checks it, instead of a stale token's 401 silently reading as a
  // generic/best-effort failure the way it used to.
  const [sessionExpired, setSessionExpired] = useState(false)
  // P1 UX audit (2026-09-12): the browser's own Back button used to leave
  // this whole screen from 'form' -- reached only by choosing a member in
  // the circle-of-trust step -- rather than returning to that step, as a
  // user would expect. Enabled only when the circle step was actually
  // shown (`circle.length > 1`, [loadCircleThenAdvance]'s own gate below):
  // a passenger who skipped straight to 'form' (0 or 1 relationship) has
  // no real circle screen to return to. See [useBackableStep]'s own KDoc
  // for why this never touches the route/URL or affects a deep link.
  useBackableStep(step, setStep, 'form', 'circle', circle.length > 1)
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
  // Product Cycle (Passenger Ride Requirements): "Пожелания к поездке" --
  // free text a passenger controls, kept in plain component state only
  // (no `localCurrentOrder.ts`/localStorage entry, unlike `orderId` itself)
  // so it never resumes across a page reload or a repeat order the way
  // that file's own per-driver order id does; it behaves exactly like the
  // pre-existing `pickupAddress`/`destination` fields, which
  // [handleOrderAgain] already leaves untouched for the same reason (a
  // passenger reordering the same trip should not have to retype
  // everything, but nothing here is persisted beyond this one page visit).
  const [notes, setNotes] = useState('')
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
  // ADR-071 (In-App, Poll-Derived Notification Surface): the raw
  // `items`/assignment-status this screen's own poll already fetches
  // (see the `[step, orderId, identity, driverCode]` effect below), kept
  // verbatim rather than only the single derived [rideStatus] value --
  // `deriveNotificationFacts` needs the full proposal list (an order can
  // carry more than one proposal over its lifetime, e.g. WITHDRAWN then a
  // fresh OPEN via Fallback Dispatch re-selection) and the assignment's
  // own status, not this screen's own already-collapsed summary. No new
  // request: both are already fetched by the same effect for [rideStatus]
  // itself.
  const [proposalItemsForNotifications, setProposalItemsForNotifications] = useState<ProposalStatusItem[]>([])
  const [assignmentStatusForNotifications, setAssignmentStatusForNotifications] = useState<
    AssignmentStatusItem['status'] | null
  >(null)
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
  // Product audit follow-up (2026-09-12): [handleShareWithFriend]'s own
  // copy/share confirmation -- same transient-feedback shape as
  // DriverHome.tsx's own [feedback]/[feedbackTimeout].
  const [shareFeedback, setShareFeedback] = useState<string | null>(null)
  const shareFeedbackTimeout = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  // Minimal In-Ride Messaging (Product Cycle): scoped to [proposalId], not
  // [orderId] or any passenger-wide inbox -- "коммуникация принадлежит
  // конкретной поездке, а не платформе в целом." Refreshed by the same poll
  // that already refreshes every other proposal-derived field on this
  // screen (see [loadMessages]'s own call sites), so a driver's reply
  // appears without a reload, the same way accepting a ride already does.
  const [messages, setMessages] = useState<ProposalMessageItem[]>([])
  const [messageDraft, setMessageDraft] = useState('')
  const [sendMessageStatus, setSendMessageStatus] = useState<'idle' | 'submitting' | 'error'>('idle')
  // Repeat Client Loop (Product Cycle): the passenger's own explicit act of
  // saving the driver they just completed a ride with, when that driver is
  // not already in their circle of trust -- see [handleSaveDriver]'s own
  // KDoc for the full reasoning and exactly which existing endpoints this
  // reuses.
  const [saveDriverStatus, setSaveDriverStatus] = useState<'idle' | 'submitting' | 'error'>('idle')
  // ADR-070 (Channel 1 Discovery Matching), Part 2, Q8: the driverless
  // (`/request`) path's own equivalent of `driverCode` -- the real driver
  // Fallback Dispatch matched this order to, learned from the existing
  // proposal poll's own `driverId` field (never set, and never read, on the
  // `driverCode` path -- that path already knows its driver from the URL).
  // See [handleSaveDriver]'s own KDoc for where this is used.
  const [matchedDriverId, setMatchedDriverId] = useState<string | null>(null)
  const matchedDriverIdRef = useRef<string | null>(null)
  // ADR-070 Part 7, Q9: distinguishes "no Proposal exists yet for this
  // order" (the honest `FallbackDispatchOutcome.NoAvailableDriver` case)
  // from every other `rideStatus === 'OPEN'` cause on the `driverCode` path
  // (a real, just-created Proposal still awaiting a response) -- both poll
  // to the same `rideStatus`, but only the driverless path needs to tell
  // them apart in its own rendering (see the 'confirmed' step JSX below).
  const [hasProposal, setHasProposal] = useState(false)

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
      setDriverAvailability(result.invitation.availability)
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
   * ADR-070 (Channel 1 Discovery Matching), Part 1: the driverless
   * (`/request`) path's own equivalent of [loadInvitation] -- mirrors only
   * that function's own "resume an already-placed order" check
   * (lines 573-581 above), since there is no invitation to fetch and no
   * circle-of-trust step to offer (see the mount effect's own comment for
   * why). A passenger with no order in flight lands directly on the form;
   * one with an order already placed on this device resumes it, exactly
   * like the `driverCode` path's own resume behavior.
   */
  function loadDiscoveryEntry(active: boolean) {
    const existingOrderId = getCurrentOrderId(DISCOVERY_ORDER_KEY)
    if (!active) {
      return
    }
    if (existingOrderId) {
      setOrderId(existingOrderId)
      setStep('confirmed')
      return
    }
    setStep('form')
  }

  /**
   * ADR-054 / `PRODUCT_DECISION_CIRCLE_OF_TRUST.md`: before starting a new
   * order, a passenger with more than one trusted driver sees their circle
   * of trust first (Section 8 of the Sprint brief) and picks who today's
   * ride goes to — a passenger with zero or one relationship has nothing to
   * choose, so this skips straight to the form, unchanged from before this
   * Sprint. Best-effort throughout: a failure here never blocks ordering
   * with the driver whose link this page was already opened through.
   *
   * Repeat Ride (Product Cycle): this is also the only place [circle] is
   * ever populated — deliberately not re-fetched again when
   * [loadInvitation] instead resumes an already-placed order (that branch,
   * just above, returns before reaching this function). This keeps this
   * addition's own footprint to a pure read of already-loaded state (no new
   * network call on the far more common "still on this screen" path the
   * COMPLETED repeat button actually appears on) at the cost of one
   * disclosed edge case: a passenger who reloads the page while an order
   * with this driver is already COMPLETED sees the generic "Повторить
   * поездку" wording rather than "Заказать у этого водителя", since
   * [circle] resets to empty on remount and is never told to refetch. Never
   * a functional regression — the button still repeats the same ride with
   * the same driver either way, see the `rideStatus === 'COMPLETED'`
   * render branch's own KDoc — only the label is momentarily less precise.
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
        // ADR-070 Part 1: the driverless (`/request`) path has no
        // `driverCode` to look up an invitation for, and deliberately never
        // shows the circle-of-trust step ([loadCircleThenAdvance]'s own
        // job) -- Fallback Dispatch already tries this passenger's trusted
        // drivers first, server-side (ADR-068 Tier 1), before a stranger
        // (Tier 3), so this screen does not need to ask "who do you trust"
        // before submitting.
        if (driverCode) {
          loadInvitation(active, driverCode, restored)
        } else {
          loadDiscoveryEntry(active)
        }
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
          // ADR-071: kept verbatim for `deriveNotificationFacts` -- see
          // [proposalItemsForNotifications]'s own KDoc above.
          setProposalItemsForNotifications(items)
          // ADR-070 Part 2, Q8: additive, gated entirely by `!driverCode` --
          // the `driverCode` path already knows its driver and never enters
          // this branch, so its own polling behavior below is unaffected.
          if (!driverCode) {
            // Part 7, Q9: an empty `items` array here is the honest
            // `FallbackDispatchOutcome.NoAvailableDriver` case -- today that
            // outcome just means "no proposal exists for this order," and
            // that is exactly what this reflects, tick to tick, with no
            // retry/timeout rule invented on top of it.
            setHasProposal(items.length > 0)
            const pickedDriverId = pickProposalDriverId(items)
            if (pickedDriverId && pickedDriverId !== matchedDriverIdRef.current) {
              matchedDriverIdRef.current = pickedDriverId
              setMatchedDriverId(pickedDriverId)
              // Reuses [driverName]/[driverAvailability] -- the exact same
              // state, and the exact same `DriverTrustIndicator` render,
              // the `driverCode` path already has -- rather than adding a
              // second driver-identity display for this path.
              request<DriverSummary>(`/v1/drivers/${pickedDriverId}`)
                .then((driver) => {
                  if (!active) {
                    return
                  }
                  setDriverName(driver.displayName ?? pickedDriverId)
                  setDriverAvailability(driver.availability)
                })
                .catch(() => {
                  // Best-effort, same tolerance [enrichCircle] already
                  // applies to an identical per-driver lookup.
                  if (!active) {
                    return
                  }
                  setDriverName(pickedDriverId)
                  setDriverAvailability('UNAVAILABLE')
                })
            }
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
              loadMessages(active, priceProposedItem.proposalId, currentIdentity.token)
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
            const openItem = items.find((item) => item.status === 'OPEN')
            const declinedItem = items.find((item) => item.status === 'DECLINED')
            const lapsedItem = items.find((item) => item.status === 'LAPSED')
            const withdrawnItem = items.find((item) => item.status === 'WITHDRAWN')
            if (openItem) {
              setRideStatus('OPEN')
              // Minimal In-Ride Messaging (Product Cycle): [proposalId] used
              // to only ever be captured in the PRICE_PROPOSED branch above
              // -- every other status here left it `null` for the entire
              // rest of a ride, even though a real proposal already exists
              // the moment this order is proposed to a driver. The message
              // thread is scoped to this proposal from the start (a
              // passenger can message before the driver has even named a
              // price), so it needs this id at every status, not only one.
              setProposalId(openItem.proposalId)
              loadMessages(active, openItem.proposalId, currentIdentity.token)
            } else if (declinedItem) {
              setRideStatus('DECLINED')
              setProposalId(declinedItem.proposalId)
              loadMessages(active, declinedItem.proposalId, currentIdentity.token)
            } else if (lapsedItem) {
              setRideStatus('LAPSED')
              setProposalId(lapsedItem.proposalId)
              loadMessages(active, lapsedItem.proposalId, currentIdentity.token)
            } else if (withdrawnItem) {
              setRideStatus('WITHDRAWN')
              setProposalId(withdrawnItem.proposalId)
              loadMessages(active, withdrawnItem.proposalId, currentIdentity.token)
            } else {
              // No proposal recorded yet (e.g., the propose call is still
              // in flight) -- honestly "waiting", not yet knowable as
              // anything else.
              setRideStatus('OPEN')
            }
            return
          }
          // Minimal In-Ride Messaging (Product Cycle): see the [openItem]
          // branch's own KDoc above -- captured here too so the thread
          // stays addressable through ACCEPTED/ARRIVED/IN_PROGRESS/
          // COMPLETED, not only while still OPEN or PRICE_PROPOSED. The
          // actual [loadMessages] call is issued further below, after the
          // assignments/orders requests -- see that call site's own
          // comment for why the position matters here specifically.
          setProposalId(acceptedItem.proposalId)
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
              // ADR-071: same read, kept for `deriveNotificationFacts` --
              // see [assignmentStatusForNotifications]'s own KDoc above.
              setAssignmentStatusForNotifications(status ?? 'CREATED')
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
          // Minimal In-Ride Messaging (Product Cycle): issued last,
          // deliberately after the assignments/orders requests just above
          // -- mirrors the exact reasoning [hasFetchedRequestedPickupAt]'s
          // own comment already gives for why *that* request is chained
          // after assignments rather than in parallel with it: this poll's
          // own call order stays deterministic tick to tick (proposals,
          // assignments, [orders once], messages) rather than racing
          // whichever of these happens to settle first.
          loadMessages(active, acceptedItem.proposalId, currentIdentity.token)
        })
        .catch((error) => {
          // P1 UX audit (2026-09-12): a stale/invalidated session token
          // making this exact request 401 forever is not a transient
          // failure "next tick" will fix -- unlike every other failure
          // here, which keeps its own pre-existing best-effort tolerance
          // completely unchanged.
          if (active) {
            handleSessionExpiredError(error)
          }
          // Best-effort otherwise: a failed poll simply tries again next tick.
        })
    }
    poll()
    const interval = setInterval(poll, STATUS_POLL_INTERVAL_MS)
    return () => {
      active = false
      clearInterval(interval)
    }
  }, [step, orderId, identity, driverCode])

  /**
   * Minimal In-Ride Messaging (Product Cycle): best-effort, same tolerance
   * every other proposal-derived fetch on this screen already has -- a
   * failed load simply tries again on the next poll tick, never blocking
   * or degrading anything else this screen shows. Scoped entirely by
   * [forProposalId] (`GET /v1/proposals/:id/messages`, Dispatch): a
   * different order's or a different proposal's own messages are never
   * requested, let alone rendered, here.
   */
  function loadMessages(active: boolean, forProposalId: string, token: string) {
    request<ProposalMessageItem[]>(`/v1/proposals/${forProposalId}/messages`, {
      baseUrl: DISPATCH_BASE_URL,
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((items) => {
        if (active) {
          setMessages(items)
        }
      })
      .catch(() => {
        // Best-effort -- see this function's own KDoc.
      })
  }

  /**
   * Minimal In-Ride Messaging (Product Cycle): the passenger's own act of
   * sending a short message to the driver of this specific proposal.
   * Guarded against double-submit like every other mutating action on this
   * screen ([isSubmitting]/[cancelStatus]/[priceDecisionStatus]'s own
   * precedent). Appends the server's own returned message optimistically
   * (Dispatch's response already carries the real, server-assigned [id]/
   * [sentAt] -- not a locally-fabricated placeholder) rather than waiting
   * for the next poll tick, mirroring [handleConfirmPrice]'s own optimistic-
   * update precedent for this screen.
   */
  async function handleSendMessage() {
    const trimmed = messageDraft.trim()
    if (!trimmed || !proposalId || !identity || sendMessageStatus === 'submitting') {
      return
    }
    setSendMessageStatus('submitting')
    try {
      const created = await request<ProposalMessageItem>(`/v1/proposals/${proposalId}/messages`, {
        method: 'POST',
        baseUrl: DISPATCH_BASE_URL,
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${identity.token}` },
        body: JSON.stringify({ body: trimmed }),
      })
      setMessages((current) => [...current, created])
      setMessageDraft('')
      setSendMessageStatus('idle')
    } catch (error) {
      if (handleSessionExpiredError(error)) {
        return
      }
      setSendMessageStatus('error')
    }
  }

  // ADR-071 (In-App, Poll-Derived Notification Surface): reshapes this
  // screen's own already-polled state into `deriveNotificationFacts`'s
  // own snapshot shape -- no new request (see
  // [proposalItemsForNotifications]/[assignmentStatusForNotifications]'s
  // own KDoc above). Placed here, before every conditional early return
  // below -- Rules of Hooks requires every hook this component calls,
  // including [useNotificationFacts]'s own internal ones, to run in the
  // same order on every render.
  const notificationProposals = useMemo<NotificationProposalSnapshot[]>(() => {
    if (!orderId) {
      return []
    }
    return proposalItemsForNotifications.map((item) => ({
      proposalId: item.proposalId,
      orderId,
      status: item.status,
      statedPrice: item.statedPrice,
    }))
  }, [proposalItemsForNotifications, orderId])
  const notificationAssignments = useMemo<NotificationAssignmentSnapshot[]>(() => {
    if (!orderId || !assignmentStatusForNotifications) {
      return []
    }
    // `statusChangedAt` is honestly `null` -- this screen's own
    // `AssignmentStatusItem` does not fetch it (see this file's own
    // KDoc); never fabricated (this file's own top-of-file rule).
    return [{ orderId, status: assignmentStatusForNotifications, statusChangedAt: null }]
  }, [orderId, assignmentStatusForNotifications])
  const notificationMessages = useMemo<NotificationMessageSnapshot[]>(() => {
    if (!orderId || !proposalId) {
      return []
    }
    return messages.map((message) => ({
      id: message.id,
      proposalId,
      orderId,
      senderRole: message.senderRole,
      sentAt: message.sentAt,
    }))
  }, [messages, proposalId, orderId])
  const {
    facts: notificationFacts,
    unseenCount: notificationUnseenCount,
    markAllSeen: markAllNotificationsSeen,
  } = useNotificationFacts(
    'passenger',
    notificationProposals,
    notificationAssignments,
    NO_NOTIFICATION_ORDERS,
    notificationMessages
  )

  if (identityChecked && !identity) {
    // ADR-070 Part 1: the driverless (`/request`) path has no `driverCode`
    // to build a `/i/:driverCode` redirect from -- that route only exists
    // per-driver-invitation, and this entry point deliberately has none.
    // An honest "you need a session" message, not a broken navigation to
    // `/i/` (which would fall through to `NotFound`).
    if (!driverCode) {
      return (
        <div className={styles.screen}>
          <Header />
          <main className={styles.content}>
            <Text role="body" tone="secondary">
              Чтобы отправить заказ, войдите в аккаунт.
            </Text>
          </main>
        </div>
      )
    }
    return <Navigate to={`/i/${driverCode}`} replace />
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

  // P1 UX audit (2026-09-12): once any call above has confirmed the
  // session itself is invalid, nothing else on this screen can succeed
  // either (every remaining action reuses the exact same stale token) --
  // replacing the whole screen, rather than layering a banner over
  // whatever `step` last showed, is the honest reflection of that: the
  // order/ride status underneath may no longer even be currently visible
  // to this passenger. "Войти снова" reuses [handleLogout] exactly.
  if (sessionExpired) {
    return (
      <div className={styles.screen}>
        <Header />
        <main className={styles.content}>
          <StatusMessage tone="error">{SESSION_EXPIRED_MESSAGE}</StatusMessage>
          <div className={styles.actionRow}>
            <Button label="Войти снова" variant="primary" onClick={handleLogout} />
          </div>
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

  function handleNotesChange(value: string) {
    setNotes(value)
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
    // Product Cycle (Passenger Ride Requirements): trimmed and defensively
    // truncated (see [NOTES_MAX_LENGTH]'s own KDoc) -- omitted from the
    // payload entirely when blank, same optional-field convention
    // [parsedPassengerCount] just above already establishes, rather than
    // sending an empty string Order Management would have to treat as
    // "notes: ''" vs "no notes at all."
    const trimmedNotes = notes.trim().slice(0, NOTES_MAX_LENGTH)

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
          ...(trimmedNotes ? { notes: trimmedNotes } : {}),
          // Task 17 (First Refusal Explicit Driver Intent Integration):
          // every order the `driverCode` path creates is already tied to
          // one specific, already-known driver -- `driverCode`, taken
          // verbatim from the URL this page is reached through (see this
          // component's own KDoc and `handleChooseCircleMember`: picking
          // any other circle member navigates to *that* driver's own
          // `/i/:driverCode/request` rather than leaving the choice open).
          // `attemptProposal`, right below, proposes this exact order to
          // that exact driver a moment later. `explicitDriverIntent` (Order
          // Management's own field, `SubmitOrderRequest.kt`) records that
          // already-true fact atomically with submission, so Dispatch's
          // automatic First Refusal (Task 16) never races this screen's own
          // explicit choice for an order created here.
          //
          // ADR-070 (Channel 1 Discovery Matching) Part 1, constraint 1: the
          // driverless (`/request`) path omits this field entirely rather
          // than sending `false` explicitly -- both are equivalent to
          // `SubmitOrderRequest.kt`'s own default, but omitting it is the
          // literal, auditable difference this ADR names between the two
          // entry points, not something a future edit could accidentally
          // flip back to `true`.
          ...(driverCode ? { explicitDriverIntent: true } : {}),
        }),
        baseUrl: ORDER_MANAGEMENT_BASE_URL,
      })
      setOrderId(response.orderId)
      saveCurrentOrderId(driverCode ?? DISCOVERY_ORDER_KEY, response.orderId)
      setStep('confirmed')
      // ADR-070 Part 1, constraint 2: the driverless path must not call
      // [attemptProposal] -- that would propose this order to a specific
      // driver directly, which only makes sense when one is already known
      // (`driverCode`). A driverless order's driver is chosen by Dispatch's
      // own First Refusal -> Fallback Dispatch chain, never by this client.
      if (driverCode) {
        void attemptProposal(response.orderId)
      }
    } catch (error) {
      if (handleSessionExpiredError(error)) {
        return
      }
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
    } catch (error) {
      if (handleSessionExpiredError(error)) {
        return
      }
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
    } catch (error) {
      if (handleSessionExpiredError(error)) {
        return
      }
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
    } catch (error) {
      if (handleSessionExpiredError(error)) {
        return
      }
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
    } catch (error) {
      if (handleSessionExpiredError(error)) {
        return
      }
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
    // ADR-070 Part 1: the driverless (`/request`) path has no `driverCode`
    // to build a `/i/:driverCode` redirect from -- `/me` is the one other
    // route in this app that already tolerates "no identity" gracefully
    // (`MyDrivers.tsx`'s own `status === 'empty'` branch), rather than
    // inventing a new post-logout destination for this one path.
    navigate(driverCode ? `/i/${driverCode}` : '/me', { replace: true })
  }

  /**
   * P1 UX audit (2026-09-12): the one place every catch block below checks
   * whether a failure was actually an expired/invalidated session
   * ([isSessionExpiredError], `api/apiClient.ts`) before falling back to
   * its own existing, unchanged generic-error handling. Returns `true`
   * when it was, so the caller can `return` immediately rather than also
   * setting its own (now misleading) generic error state. "Войти снова"
   * reuses [handleLogout] exactly -- there is nothing session-specific to
   * clean up beyond what signing out already does.
   */
  function handleSessionExpiredError(error: unknown): boolean {
    if (isSessionExpiredError(error)) {
      setSessionExpired(true)
      return true
    }
    return false
  }

  function handleOrderAgain() {
    clearCurrentOrderId(driverCode ?? DISCOVERY_ORDER_KEY)
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
    // ADR-070 Part 2, Q8: the driverless path's own matched-driver state
    // belongs to the order that just ended -- a repeat submission goes
    // through discovery matching again (Part 1 constraint 2 applies to
    // every submission this screen makes, not only the first) and may
    // match a different driver, or none yet. The `driverCode` path never
    // reaches this branch: its [driverName]/[driverAvailability] come from
    // the fixed invitation this screen was opened through, not from a
    // Proposal, and correctly stay unchanged across a repeat, exactly as
    // before this ADR.
    if (!driverCode) {
      matchedDriverIdRef.current = null
      setMatchedDriverId(null)
      setHasProposal(false)
      setDriverName(null)
      setDriverAvailability(null)
    }
    setStep('form')
  }

  /**
   * Product audit follow-up (2026-09-12): every existing invite/growth
   * mechanism in this codebase (`DriverHome.tsx`'s QR card, and its own
   * newly-added per-client share action) is driver-initiated only -- a
   * passenger who just had a genuinely good, COMPLETED ride had no
   * in-product way to recommend this same driver to a friend, despite
   * word-of-mouth being the vision's own named growth mechanism
   * (`docs/PIOS_PRODUCT_VISION.md` §16: "The link/QR is the first
   * mechanism for building a provider's own network"). No new invitation
   * system: this shares the exact same `/i/:driverCode` link
   * (`invitationProvider.linkFor`) a friend would reach this same driver
   * through directly -- identical mechanism, just handed to the other
   * side of the relationship for the first time.
   */
  async function handleShareWithFriend() {
    if (!driverCode) {
      return
    }
    try {
      await invitationProvider.share(invitationProvider.linkFor(driverCode))
      if (!navigator.share) {
        showShareFeedback('Ссылка скопирована')
      }
    } catch (error) {
      // A user-cancelled share (AbortError) is not a failure -- same
      // tolerance DriverHome.tsx's own handleShare already established.
      if (error instanceof Error && error.name !== 'AbortError') {
        showShareFeedback('Не удалось поделиться')
      }
    }
  }

  function showShareFeedback(message: string) {
    setShareFeedback(message)
    clearTimeout(shareFeedbackTimeout.current)
    shareFeedbackTimeout.current = setTimeout(() => setShareFeedback(null), SHARE_FEEDBACK_DURATION_MS)
  }

  /**
   * Repeat Client Loop (Product Cycle): closes a real gap the COMPLETED
   * screen's own Repeat Ride label already exposes -- a passenger who just
   * had a genuinely good ride with a driver *not* in their circle of trust
   * (declined the "confirm-add" step earlier, or `bootstrapCircleOfTrust`'s
   * own best-effort attempt at registration never landed) had no in-product
   * way to fix that short of navigating away to "Мои водители", which does
   * not even show a driver they were never connected to in the first
   * place. No new API, no new model: reuses exactly the same two Passenger
   * Experience endpoints `handleAddToCircle`
   * (`PassengerLanding.tsx`)/[bootstrapCircleOfTrust] already call --
   * `POST /v1/connections` (create), then, only if this passenger has no
   * primary driver at all yet, `POST /v1/connections/:id/primary` — mirrors
   * [bootstrapCircleOfTrust]'s own "a brand-new account's first connection
   * needs no separate confirmation to become primary" reasoning exactly,
   * generalized to "no primary yet" rather than "brand-new account", since
   * both describe the identical real fact this method must not assume: an
   * *existing* primary is never silently replaced (Rule 4,
   * `PRODUCT_DECISION_CIRCLE_OF_TRUST.md` -- only [handleConfirmMakePrimary]'s
   * own explicit, confirmed act may do that). A successful save updates
   * [circle] locally, which is also the exact state the COMPLETED button's
   * own label already reads — so on success this driver's own repeat
   * action immediately relabels itself "Заказать у этого водителя" with no
   * separate flag needed.
   *
   * ADR-070 (Channel 1 Discovery Matching), Part 2 (Option B), Q8: on the
   * driverless (`/request`) path there is no `driverCode` to save -- the
   * real driver this ride was matched to is [matchedDriverId], learned from
   * the proposal poll (see that state's own KDoc). Everything else here is
   * unchanged: same endpoint, same idempotency, same "primary only if none
   * exists" rule (Rule 4) -- this function does not know or care which path
   * it was reached from, only which driver id to target.
   */
  async function handleSaveDriver() {
    const targetDriverId = driverCode ?? matchedDriverId
    if (!identity || !targetDriverId || saveDriverStatus === 'submitting') {
      return
    }
    setSaveDriverStatus('submitting')
    try {
      const created = await request<{ connectionId: string }>('/v1/connections', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${identity.token}` },
        body: JSON.stringify({ driverId: targetDriverId, passengerReference: identity.identityId }),
        baseUrl: PASSENGER_EXPERIENCE_BASE_URL,
      })
      const hasExistingPrimary = circle.some((member) => member.isPrimary)
      if (!hasExistingPrimary) {
        await request(`/v1/connections/${created.connectionId}/primary`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${identity.token}` },
          baseUrl: PASSENGER_EXPERIENCE_BASE_URL,
        })
      }
      setCircle((current) => [
        ...current,
        {
          connectionId: created.connectionId,
          driverId: targetDriverId,
          createdAt: new Date().toISOString(),
          isPrimary: !hasExistingPrimary,
          displayName: driverName ?? targetDriverId,
          availability: driverAvailability ?? 'UNAVAILABLE',
        },
      ])
      setSaveDriverStatus('idle')
      // The button that triggered this disappears once [circle] updates
      // above (same condition its own render already reads), but that is
      // a subtle cue on its own -- unlike [handleConfirmRemove]'s own list
      // item vanishing right in front of the passenger, nothing else on
      // this screen visibly moves. Reuses [showShareFeedback]'s own
      // transient-message mechanism (already rendered just below) rather
      // than adding a second success banner.
      showShareFeedback(hasExistingPrimary ? 'Водитель сохранён в вашем круге доверия' : 'Водитель сохранён и назначен основным')
    } catch (error) {
      if (handleSessionExpiredError(error)) {
        return
      }
      setSaveDriverStatus('error')
    }
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
    } catch (error) {
      if (handleSessionExpiredError(error)) {
        return
      }
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
    } catch (error) {
      if (handleSessionExpiredError(error)) {
        return
      }
      setRemoveStatus('error')
    }
  }

  // Minimal In-Ride Messaging (Product Cycle): mirrors Dispatch's own
  // gating exactly (`ProposalMessagingApplicationService.isOpen`, backend) --
  // a completed ride, or a proposal that never resulted in one
  // (DECLINED/LAPSED/WITHDRAWN), has nothing left to coordinate. Every
  // other rideStatus (OPEN, PRICE_PROPOSED, ACCEPTED, ARRIVED, IN_PROGRESS)
  // still permits sending, matching the backend's own check against the
  // Assignment's status rather than the Proposal's own (which stays
  // ACCEPTED for the rest of the ride and would otherwise never reflect
  // that the ride actually finished).
  const isMessagingOpen =
    rideStatus !== 'COMPLETED' && rideStatus !== 'DECLINED' && rideStatus !== 'LAPSED' && rideStatus !== 'WITHDRAWN'

  // ADR-070 (Channel 1 Discovery Matching), Part 2 (Option B), Q8: the
  // driver [handleSaveDriver] would target -- `driverCode` on the
  // `driverCode` path (unchanged), [matchedDriverId] (from the proposal
  // poll, `null` until a Proposal exists) on the driverless path.
  const saveDriverTargetId = driverCode ?? matchedDriverId

  return (
    <div className={styles.screen}>
      <Header />
      {/* ADR-071: same floating-overlay placement as `DriverHome.tsx`'s
          own -- see `.notificationBellSlot`'s own CSS comment there. */}
      <div className={styles.notificationBellSlot}>
        <NotificationBell
          facts={notificationFacts}
          unseenCount={notificationUnseenCount}
          onOpen={markAllNotificationsSeen}
        />
      </div>
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

            {/* Referral funnel friction audit (2026-09-12): this exact
                step is where a brand-new referral (0 or 1 relationship,
                the circle-of-trust step above skipped entirely) used to
                fill out and submit a real order with zero visibility into
                whether this one specific driver was even online -- the
                backend's own [availability] was fetched by [loadInvitation]
                and silently discarded before this fix. Shown, not gated:
                unlike the circle step's own `disabled={...}` (which picks
                *among* several saved drivers), this screen has no
                alternative driver to offer instead -- the passenger came
                here through this one driver's own link. The caption below
                points at the "Заранее" scheduling option that already
                exists, rather than inventing a new one. */}
            {driverName && (
              <>
                <DriverTrustIndicator name={driverName} availability={driverAvailability ?? undefined} emphasis="compact" />
                {driverAvailability === 'UNAVAILABLE' && (
                  <Text role="caption" tone="muted">
                    Сейчас недоступен. Вы можете заказать поездку заранее, выбрав время ниже.
                  </Text>
                )}
              </>
            )}

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

            {/* Product Cycle (Passenger Ride Requirements): optional, free
                text -- a child seat, extra luggage, a pet, help boarding, a
                meeting-point landmark, or anything else this form has no
                dedicated field for. Placed last among the structured trip
                fields, right before submission: everything else on this
                form is a specific fact (address, count, timing) the driver
                needs to plan the trip at all; this is supplementary context
                for a driver already deciding on a price. */}
            <FormField label="Пожелания к поездке" htmlFor="notes">
              <Textarea
                id="notes"
                value={notes}
                maxLength={NOTES_MAX_LENGTH}
                rows={3}
                placeholder="Например: детское кресло, много багажа, встретить у подъезда…"
                onChange={(event) => handleNotesChange(event.target.value)}
              />
            </FormField>

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
            {driverName && (
              <DriverTrustIndicator
                name={driverName}
                availability={driverAvailability ?? undefined}
                emphasis="prominent"
              />
            )}
            {/* UI/UX redesign, Stage 2 (2026-09-12): used to be a
                `StatusMessage tone="success"` -- a second, permanently-
                green status banner sitting right above [RideStatus] below,
                which already carries the one real, currently-correct
                status (and its own correct color -- e.g. red for
                DECLINED, appearing directly under a green "success" line
                otherwise). Demoted to a quiet caption, same unchanged
                text, so there is exactly one prominent status indicator on
                screen at a time -- [RideStatus]. */}
            <Text role="caption" tone="muted">
              ✅ Заказ оформлен.
            </Text>
            {/* UI/UX redesign, Stage 2: shown only while genuinely true --
                once a real status is known ([rideStatus] past 'OPEN'),
                this orientation sentence is stale ("here's what you'll
                see happen" after it already happened); [RideStatus] itself
                is by then the honest, current answer. */}
            {rideStatus === 'OPEN' && (
              <Text role="caption" tone="muted">
                Здесь вы увидите, что происходит с вашим заказом — от отправки до завершения поездки.
              </Text>
            )}
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
                {/* ADR-070 (Channel 1 Discovery Matching), Part 7, Q9: on
                    the driverless path, `rideStatus === 'OPEN'` with no
                    Proposal at all is the honest
                    `FallbackDispatchOutcome.NoAvailableDriver` case, not a
                    driver silently taking time to respond -- today that
                    outcome just means "no proposal exists for this order,"
                    so that is exactly what this states, plainly, with no
                    fake "matching in progress" spinner and no invented
                    retry/timeout rule -- the existing poll above (unchanged
                    cadence) is what actually keeps checking. The
                    `driverCode` path never reaches this branch: it always
                    has a real, just-created Proposal by the time `rideStatus`
                    can be 'OPEN' (see [attemptProposal]), so [hasProposal]
                    is irrelevant to it.

                    QA finding, 2026-09-15: the original copy here said "Мы
                    продолжаем искать и сообщим" ("we're still searching and
                    will let you know") -- untrue. Dispatch runs First
                    Refusal -> Fallback Dispatch exactly once, at
                    submission; nothing on the backend retries a
                    `NoAvailableDriver` order, and there is no notification
                    of any kind (confirmed against
                    `FallbackDispatchApplicationService`'s own "no retry on
                    decline/lapse" KDoc and `DriverAvailabilityChangedListener`,
                    which never re-attempts matching for an existing order).
                    The only thing actually true is that this screen's own
                    poll will pick up a Proposal if one appears while the
                    passenger stays on it -- so that's the only claim made
                    below. No "we're searching" promise, no notification
                    guarantee. */}
                {!driverCode && rideStatus === 'OPEN' && !hasProposal ? (
                  <Text role="body" tone="secondary">
                    Сейчас нет доступного водителя. Если кто-то станет доступен, пока вы на этом экране, вы увидите
                    предложение здесь — либо попробуйте отправить заказ ещё раз позже.
                  </Text>
                ) : (
                  <RideStatus status={rideStatus} label={rideStatusLabel(rideStatus)} />
                )}
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
                {/* ADR-057, narrowed in UI/UX redesign Stage 2: unlike
                    [statedPrice] just above (a fact that stays true as a
                    receipt for the rest of the ride), an ETA is a promise
                    about arrival specifically -- stale and potentially
                    confusing once the driver has already arrived
                    (ARRIVED/IN_PROGRESS/COMPLETED all already say so via
                    [RideStatus] itself) or the order is no longer live
                    (WITHDRAWN, which the previous, wider condition did not
                    exclude). Shown only for ACCEPTED, the one status where
                    "will arrive in ~N min" is still a genuinely current fact. */}
                {typeof statedEtaMinutes === 'number' && rideStatus === 'ACCEPTED' && (
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
                    'WITHDRAWN') is exactly where this driver's link
                    otherwise dead-ended forever -- 'OPEN', 'ACCEPTED',
                    'ARRIVED', 'IN_PROGRESS' keep today's behavior
                    unchanged, since a ride still in progress must not
                    offer a second, concurrent order with the same
                    driver. 'COMPLETED' has its own, differently-labelled
                    branch immediately below (Repeat Ride) -- none of these
                    three ever produced an actual ride, so they keep the
                    plain, unconditional "Заказать ещё раз" wording. */}
                {(rideStatus === 'DECLINED' || rideStatus === 'LAPSED' || rideStatus === 'WITHDRAWN') && (
                  <div className={styles.actionRow}>
                    <Button label="Заказать ещё раз" variant="primary" onClick={handleOrderAgain} />
                  </div>
                )}
                {/* Repeat Ride (Product Cycle): a completed ride is the one
                    terminal state that actually happened, so the repeat
                    action here is framed around the specific driver the
                    passenger just rode with, not a generic retry. Same
                    underlying action as the plain "Заказать ещё раз" above
                    ([handleOrderAgain] itself is completely unchanged --
                    same driver, same lifecycle, pickupAddress/destination/
                    passengerCount/notes all already survive it unmodified,
                    since that function never resets them) -- only the label
                    differs, chosen from [circle] ([loadCircleThenAdvance]'s
                    own state, populated for the unrelated circle-of-trust
                    step -- reused here rather than fetched again; see that
                    function's own KDoc for the one disclosed edge case):
                    - This driver is already in the passenger's circle of
                      trust -- "Заказать у этого водителя". This is the
                      common case (Circle of Trust is normally bootstrapped
                      at registration or explicit confirm-add, before an
                      order can even be placed with them) and is exactly the
                      "Primary Driver gets the first right of offer" product
                      rule: repeating defaults to the driver already ridden
                      with, one tap, no re-typing.
                    - This driver is not (or no longer) in the circle --
                      "Повторить поездку", an ordinary repeat with no implied
                      relationship.
                    Neither branch is exclusive: "Мои водители" right below
                    remains the passenger's own, always-available way to
                    choose a different driver instead -- this never locks
                    anyone in, matching "не эксклюзивность и не владение
                    пассажиром". */}
                {rideStatus === 'COMPLETED' && (
                  <div className={styles.actionRow}>
                    <Button
                      label={
                        circle.some((member) => member.driverId === driverCode)
                          ? 'Заказать у этого водителя'
                          : 'Повторить поездку'
                      }
                      variant="primary"
                      onClick={handleOrderAgain}
                    />
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
                    this one is the only thing that matters right now.
                    UI/UX redesign, Stage 2: grouped with "Поделиться с
                    другом" into one row (reusing `.circleMemberActions`,
                    the exact same secondary-actions-cluster treatment the
                    'circle' step above already establishes) instead of two
                    separately stacked full-width lines -- one quiet
                    secondary cluster, not a second list of options
                    competing with "Заказать ещё раз" above it. */}
                {(rideStatus === 'DECLINED' ||
                  rideStatus === 'LAPSED' ||
                  rideStatus === 'WITHDRAWN' ||
                  rideStatus === 'COMPLETED') && (
                  <div className={styles.circleMemberActions}>
                    <button type="button" className={styles.textAction} onClick={() => navigate('/me')}>
                      Мои водители
                    </button>
                    {/* Product audit follow-up (2026-09-12): only on an
                        actual COMPLETED ride -- there is a real experience
                        to recommend at this specific status;
                        DECLINED/LAPSED/WITHDRAWN never got a ride at all,
                        so prompting a referral there would not reflect
                        anything real. See [handleShareWithFriend]'s own
                        KDoc for why this reuses the existing invite link
                        unchanged.
                        ADR-070 (Channel 1 Discovery Matching), Part 6
                        (Negative), Section 10 of the design companion
                        document: recommending a discovery-matched driver to
                        someone else still needs that driver's own
                        `driverCode` link, which this path never has -- not
                        solved by this Sprint, not invented here. Gated on
                        `driverCode` so this never renders a dead button
                        ([handleShareWithFriend] itself already no-ops
                        without one). */}
                    {driverCode && rideStatus === 'COMPLETED' && (
                      <button type="button" className={styles.textAction} onClick={() => void handleShareWithFriend()}>
                        Поделиться с другом
                      </button>
                    )}
                    {/* Repeat Client Loop (Product Cycle): only while this
                        driver is genuinely not yet in the circle of trust --
                        once [handleSaveDriver] succeeds, [circle] already
                        contains this driver, so this condition (the same one
                        the COMPLETED button's own label already reads)
                        naturally stops rendering it -- no separate "saved"
                        flag needed.
                        ADR-070 Part 2 (Option B), Q8: on the driverless path
                        `driverCode` is `undefined`, so `saveDriverTargetId`
                        falls back to [matchedDriverId] -- `null` until a
                        Proposal has actually named a driver, which this
                        condition also requires (nothing to save otherwise).
                        Known, disclosed limitation: unlike the `driverCode`
                        path (whose [circle] is fetched up front by
                        [loadCircleThenAdvance]), the driverless path never
                        fetches [circle] at all (Part 1 -- no circle-of-trust
                        step), so this can render even if the matched driver
                        happens to already be a saved connection. Harmless,
                        not a correctness bug: [handleSaveDriver] -> `POST
                        /v1/connections` is idempotent by both application
                        logic and the `UNIQUE (driver_id, passenger_reference)`
                        constraint (ADR-070 Part 2). */}
                    {rideStatus === 'COMPLETED' &&
                      saveDriverTargetId &&
                      !circle.some((member) => member.driverId === saveDriverTargetId) && (
                        <button
                          type="button"
                          className={styles.textAction}
                          onClick={() => void handleSaveDriver()}
                          disabled={saveDriverStatus === 'submitting'}
                        >
                          Добавить в мои водители
                        </button>
                      )}
                  </div>
                )}
                {shareFeedback && <StatusMessage>{shareFeedback}</StatusMessage>}
                {saveDriverStatus === 'error' && (
                  <StatusMessage tone="error">Не удалось сохранить водителя. Попробуйте ещё раз.</StatusMessage>
                )}
                {/* Minimal In-Ride Messaging (Product Cycle): "коммуникация
                    принадлежит конкретной поездке, а не платформе в целом"
                    -- rendered inside this exact order's own confirmation
                    card, directly under its own status, never as a
                    separate inbox/chat screen elsewhere in the app. Shown
                    once [proposalId] is known -- every status from OPEN
                    onward already sets it (see the poll effect's own
                    KDoc) -- so a passenger can reach the driver about this
                    specific ride from the moment it exists, exactly as
                    "после создания заказа" states, not gated behind
                    acceptance. Hidden entirely when there is nothing to
                    show (no history and no longer open): an empty card
                    with only a "closed" caption would be noise, not a
                    feature. Read-only once [isMessagingOpen] is false --
                    a completed, declined, lapsed, or withdrawn ride has
                    nothing left to coordinate; existing messages stay
                    visible as a record, but no new one can be sent. */}
                {proposalId && (isMessagingOpen || messages.length > 0) && (
                  <Card>
                    <Text role="label" tone="muted">
                      Сообщения по этой поездке
                    </Text>
                    {messages.length > 0 && (
                      <div className={styles.messageThread}>
                        {messages.map((message) => (
                          <MessageBubble
                            key={message.id}
                            own={message.senderRole === 'PASSENGER'}
                            text={`${message.senderRole === 'PASSENGER' ? 'Вы' : 'Водитель'}: ${message.body}`}
                            sentAt={message.sentAt}
                          />
                        ))}
                      </div>
                    )}
                    {isMessagingOpen ? (
                      <>
                        <FormField label="Сообщение водителю" htmlFor="message-to-driver">
                          <Textarea
                            id="message-to-driver"
                            value={messageDraft}
                            maxLength={MESSAGE_MAX_LENGTH}
                            rows={2}
                            placeholder="Например: буду у подъезда через 2 минуты"
                            onChange={(event) => setMessageDraft(event.target.value)}
                          />
                        </FormField>
                        <div className={styles.actionRow}>
                          <Button
                            label="Отправить"
                            variant="secondary"
                            loading={sendMessageStatus === 'submitting'}
                            disabled={!messageDraft.trim()}
                            onClick={() => void handleSendMessage()}
                          />
                        </div>
                        {sendMessageStatus === 'error' && (
                          <StatusMessage tone="error">Не удалось отправить сообщение. Попробуйте ещё раз.</StatusMessage>
                        )}
                      </>
                    ) : (
                      <Text role="caption" tone="muted">
                        Обмен сообщениями закрыт.
                      </Text>
                    )}
                  </Card>
                )}
              </>
            )}
          </div>
        )}
      </main>
    </div>
  )
}
