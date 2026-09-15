import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Header } from '../../components/Header'
import { BottomNav } from '../../components/BottomNav'
import { DriverCard } from '../../components/DriverCard'
import { QRCard } from '../../components/QRCard'
import { Button } from '../../components/Button'
import { Spinner } from '../../components/Spinner'
import { PasswordInput } from '../../components/PasswordInput'
import { AvailabilityStatus } from '../../components/AvailabilityStatus'
import { RequestCard } from '../../components/RequestCard'
import { ActiveRidePanel } from '../../components/ActiveRidePanel'
import { RideStatus, type RideLifecycleStatus } from '../../components/RideStatus'
import { Card } from '../../components/Card'
import { Text } from '../../components/Text'
import { Heading } from '../../components/Heading'
import { FormField } from '../../components/FormField'
import { Input, Select, Textarea } from '../../components/Input'
import { StatusMessage } from '../../components/StatusMessage'
import { MessageBubble } from '../../components/MessageBubble'
import { RideHistoryCard } from '../../components/RideHistoryCard'
import { ApiError, request, resolveBackendBaseUrl } from '../../api/apiClient'
import type { StoredIdentity } from '../../identity/IdentityProvider'
import { BackendIdentityProvider } from '../../identity/BackendIdentityProvider'
import { LocalInvitationProvider } from '../../identity/InvitationProvider'
import { normalizePhone, isValidPhone, PHONE_FORMAT_HINT } from '../../identity/phoneFormat'
import { DriverOnboarding } from './DriverOnboarding'
import { hasSeenDriverOnboarding, markDriverOnboardingSeen } from '../../persistence/localOnboardingSeen'
import { InstallPIOS, isStandalone } from '../../features/install'
import { hasSeenInstallHelp } from '../../persistence/localInstallSeen'
import {
  NotificationBell,
  useNotificationFacts,
  type NotificationAssignmentSnapshot,
  type NotificationMessageSnapshot,
  type NotificationOrderSnapshot,
  type NotificationProposalSnapshot,
} from '../../features/notifications'
import styles from './DriverHome.module.css'

const MIN_PASSWORD_LENGTH = 8

// ADR-038/ADR-039: today's only IdentityProvider/InvitationProvider — see
// those files' own KDoc for why this is safe to instantiate once,
// module-level, exactly like `apiClientConfig` already is.
const identityProvider = new BackendIdentityProvider()
const invitationProvider = new LocalInvitationProvider()

const FEEDBACK_DURATION_MS = 2000

// UX audit (docs/PIOS_DRIVER_HOME_UX_AUDIT.md Section 5/9, "COMPLETE"):
// slightly longer than FEEDBACK_DURATION_MS -- a completed ride is a more
// significant moment than a copy/share toast, worth a beat longer on
// screen, still brief (brief Section 6: "no interstitial platform
// messaging").
const RIDE_COMPLETED_FEEDBACK_DURATION_MS = 3000

// First-pilot feedback: a driver had to remember to tap "Обновить" to see a
// new order — on a real shift that meant missed orders. Polling replaces
// the manual button entirely; 3s sits in the middle of the requested 2-5s
// range.
const PROPOSALS_POLL_INTERVAL_MS = 3000

// Dispatch's own local port (INTERFACE_CONTRACTS.md) — same constant as
// `Coordinator.tsx` (Sprint FR-004): this page now also calls Dispatch
// directly, in addition to Driver Management (Sprint IMPLEMENTATION-005,
// Driver Proposal MVP).
//
// Product audit (2026-09-11): resolved via [resolveBackendBaseUrl] -- see
// that function's own KDoc (`api/apiClient.ts`). A real driver's own phone
// must reach this through `server/serve.mjs`'s same-origin reverse proxy,
// not a `localhost:8084` that only ever meant the machine running this
// browser, not the pilot host.
const DISPATCH_BASE_URL = resolveBackendBaseUrl(import.meta.env.VITE_DISPATCH_BASE_URL, 'http://localhost:8084')

// Order Management's own local port (INTERFACE_CONTRACTS.md) — same
// constant as `Coordinator.tsx`/`RideRequest.tsx` (Sprint 3B: MVR Pilot
// Enablement -- Optional Destination): this page now also calls Order
// Management directly, to read each open proposal's own order destination.
const ORDER_MANAGEMENT_BASE_URL = resolveBackendBaseUrl(import.meta.env.VITE_ORDER_MANAGEMENT_BASE_URL, 'http://localhost:8083')

// Passenger Experience's own local port (INTERFACE_CONTRACTS.md) — same
// constant as `PassengerLanding.tsx` (Sprint "Driver Growth Snapshot", H6):
// this page now also calls that module directly, to read how many
// passengers connected through this driver's own invitation link.
const PASSENGER_EXPERIENCE_BASE_URL = resolveBackendBaseUrl(import.meta.env.VITE_PASSENGER_EXPERIENCE_BASE_URL, 'http://localhost:8082')

const MAX_NAME_LENGTH = 50

type Status = 'loading' | 'error' | 'ready'
type ProposalActionStatus = 'idle' | 'submitting' | 'error'

interface DriverInfo {
  id: string
  availability: 'AVAILABLE' | 'UNAVAILABLE'
  displayName: string | null
  vehicleMake?: string | null
  vehicleModel?: string | null
  vehicleColor?: string | null
  vehiclePlateNumber?: string | null
  vehicleSeatCount?: number | null
  acceptsLongDistanceTrips?: boolean
}

/** `POST /v1/drivers/:id/vehicle`'s own response shape (`DriverController.updateVehicle`, Driver Management, PIOS Group and Long-Distance Rides Roadmap Stage 1) -- the full driver record, same shape `GET /v1/drivers/:id` already returns. */
type VehicleResponse = DriverInfo

/**
 * `POST /v1/drivers/:id/availability`'s own response shape
 * (`DriverController.declareAvailability`, Driver Management) — note it
 * omits `displayName`/`registeredAt` (both default to `null` on that
 * endpoint specifically), unlike `GET /v1/drivers/:id`'s full
 * [DriverInfo]. [toggleAvailability] below merges only `availability` from
 * this response into the existing [DriverInfo] state for exactly that
 * reason — replacing the whole object would silently wipe a real driver's
 * own `displayName` back to `null` on screen after every toggle.
 */
interface AvailabilityResponse {
  availability: 'AVAILABLE' | 'UNAVAILABLE'
}

type AvailabilityActionStatus = 'idle' | 'submitting' | 'error'

interface ProposalListItem {
  proposalId: string
  orderId: string
  driverId: string
  status: 'OPEN' | 'PRICE_PROPOSED' | 'ACCEPTED' | 'DECLINED' | 'LAPSED' | 'WITHDRAWN'
  // ADR-042 (Stated Ride Price Minimal Model): the amount this driver
  // stated when accepting, if any. Always `null` for a proposal that is
  // not yet ACCEPTED or was accepted with no price entered — PIOS never
  // fills this in on its own (Decision Revised R4.1).
  statedPrice: string | null
  // ADR-057 (Driver Stated Time to Pickup): the number of minutes this
  // driver stated it would take to reach the passenger, if any. Same
  // null-until-accepted rule as [statedPrice].
  statedEtaMinutes: number | null
}

/**
 * Minimal In-Ride Messaging (Product Cycle): `GET/POST
 * /v1/proposals/:id/messages`'s own response shape (Dispatch) -- mirrors
 * `RideRequest.tsx`'s own identical interface exactly (same backend
 * contract, read from both ends of the same proposal).
 */
interface ProposalMessageItem {
  id: string
  senderRole: 'PASSENGER' | 'DRIVER'
  body: string
  sentAt: string
}

// Minimal In-Ride Messaging (Product Cycle): matches
// ProposalMessage.MAX_BODY_LENGTH (Dispatch) and RideRequest.tsx's own
// identical constant exactly, for the same reason that file's own KDoc
// gives -- nothing typed here is ever silently rejected by the backend
// after submission.
const MESSAGE_MAX_LENGTH = 300

// ADR-057 Decision item 4: the fixed choice set lives in the UI, not the
// domain -- this is a presentation choice about what is easy to tap, so it
// can change without a backend contract change.
const ETA_OPTIONS_MINUTES = [2, 5, 7, 10, 15] as const

// Sprint 6A (Human Interface Polish): the raw status values above are this
// screen's own wire format, not driver-facing wording -- MVR_DRIVER_ONBOARDING_GUIDE.md
// promises a driver never has to read a technical term, so this maps each
// one to the plain-language text actually shown. 'LAPSED' has no example in
// that guide's own text; this wording follows the same plain-language
// principle for it.
const PROPOSAL_STATUS_LABEL: Record<ProposalListItem['status'], string> = {
  OPEN: 'Ожидает вашего решения',
  // Product Owner instruction, 2026-09-05: the driver has named a price;
  // the ride is not yet confirmed until the passenger agrees to it.
  PRICE_PROPOSED: 'Ожидает решения клиента',
  ACCEPTED: 'Вы приняли',
  DECLINED: 'Отклонено',
  LAPSED: 'Больше не активно',
  // P0-2 Tier 1 (`docs/SPRINT_PILOT_BLOCKERS.md`; ADR-053): what an OPEN
  // proposal becomes when the passenger cancels the order it belongs to,
  // before this driver responded to it.
  WITHDRAWN: 'Отменено пассажиром',
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
  // ADR-071 (In-App, Poll-Derived Notification Surface), Part 3, D5's own
  // sole enabler: `OrderResponse.status` (Order Management,
  // `OrderResponse.kt` line 54) has always been returned by `GET
  // /v1/orders?ids=` -- this is a type-only declaration of a field
  // already present in every response this screen already receives, not
  // a new request or a backend change. Used only to notice a proposal's
  // own order becoming `CANCELLED` (see `features/notifications`'s own
  // `deriveNotificationFacts.ts`); not rendered directly anywhere.
  status: string
  // Sprint "My Business + Circle of Trust": `GET /v1/orders`'s own
  // `OrderResponse.origin` already carries the order's passenger reference
  // (`OrderOrigin`, Order Management -- see `Coordinator.tsx`'s own note on
  // the same field) -- this page reads it only to build a best-effort
  // passengerReference -> passengerName map for [MyPassengersSection] below,
  // not to render it directly anywhere.
  origin: string
  destination: string | null
  // First-pilot feedback: the backend now carries these two (Order
  // Management's own `passengerName`/`createdAt`, both optional — an order
  // submitted before this pilot fix, or by a passenger with no local name
  // set, has neither; rendered conditionally below exactly like
  // `destination` already was).
  passengerName: string | null
  createdAt: string | null
  // Sprint H5 (Entrepreneur Working Cycle Integrity): Order Management's
  // own optional `pickupAddress` -- rendered conditionally below exactly
  // like `destination`, since an order submitted before this sprint has
  // none. Closes the gap this screen used to have no way to fill: a driver
  // previously had no way to know where to pick a passenger up short of
  // calling them.
  pickupAddress: string | null
  // ADR-058 (Scheduled Pickup Time): the passenger's own requested pickup
  // instant (ISO-8601), for a pre-booked ride -- `null` means "as soon as
  // possible", same as every order before this field existed.
  requestedPickupAt: string | null
  // PIOS Group and Long-Distance Rides Roadmap, Stage 2 -- the passenger's
  // own optional statement of group size, rendered conditionally below
  // exactly like `pickupAddress`. Compared against nothing automatically:
  // this driver reads it next to their own declared vehicle seat count
  // ("Моя машина", Profile tab) and judges it themselves.
  passengerCount?: number | null
  // Product Cycle (Passenger Ride Requirements) -- "Пожелания к поездке":
  // the passenger's own free-text statement of anything about this
  // specific ride PIOS has no dedicated field for (a child seat, extra
  // luggage, a pet, help boarding, a meeting-point landmark). Rendered
  // conditionally in [ProposalDetails] below exactly like `pickupAddress`
  // -- an order submitted before this field existed, or with nothing
  // typed, has none, and the block is omitted entirely rather than shown
  // empty.
  notes?: string | null
}

/**
 * `GET /v1/connections?driverId=...`'s own response shape (Passenger
 * Experience, Sprint 7B) -- one entry per passenger who has ever opened
 * this driver's invitation link. [createdAt] (Sprint "Driver Growth
 * Snapshot", H6) is what lets [todaysNewClientCount] below tell today's
 * connections from every earlier one.
 */
interface ConnectionListItem {
  passengerReference: string
  createdAt: string
}

/** Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2): `GET /v1/drivers/:id/milestones`'s own response shape.
 * `totalStatedEarnings`/`unpricedRidesCount` (ADR-065) are optional -- undefined until the backend ships them, in
 * which case the earnings tile simply does not render rather than showing a fabricated zero. */
interface DriverMilestonesInfo {
  completedRidesCount: number
  currentStreakWeeks: number
  repeatClientsCount: number
  totalStatedEarnings?: number
  unpricedRidesCount?: number
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
 * History (MVP completion): renders a completed ride's own real "when" --
 * the Assignment/Trip's own `statusChangedAt` at the moment it reached
 * COMPLETED (falls back to the order's own `createdAt` only if that is
 * somehow absent, which should not happen for a ride that reached this
 * state). Same `toLocaleString('ru-RU', ...)` shape as
 * [formatRequestedPickupAt] immediately below -- one date/time convention
 * for this whole screen, not a second one invented for this list.
 */
function formatHistoryDateTime(instant: string | null): string | null {
  if (!instant) {
    return null
  }
  const parsed = new Date(instant)
  if (Number.isNaN(parsed.getTime())) {
    return null
  }
  return parsed.toLocaleString('ru-RU', { day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' })
}

/**
 * History (MVP completion): "Откуда → Куда", either side present or not --
 * an order submitted before Sprint H5 (pickupAddress) or with no
 * destination typed has neither, in which case this returns `null` (no
 * fabricated route text) rather than an empty arrow.
 */
function formatHistoryRoute(order: OrderListItem | undefined): string | null {
  if (!order) {
    return null
  }
  if (order.pickupAddress && order.destination) {
    return `${order.pickupAddress} → ${order.destination}`
  }
  return order.pickupAddress ?? order.destination ?? null
}

/**
 * ADR-058 (Scheduled Pickup Time): renders an order's own `requestedPickupAt`
 * (ISO-8601 instant) in this device's own local time, so a driver reads it
 * exactly like [formatOrderTime] -- the browser resolves the offset, no
 * timezone concept exists in the contract itself.
 */
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
 * The detail lines every proposal card shows, regardless of which of the
 * three presentational shells (`RequestCard`/`ActiveRidePanel`/plain
 * `Card`) renders it — extracted so all three stay pixel-consistent
 * without hand-repeating the same conditions three times. Pure
 * presentation: every condition here is copied unchanged from the JSX
 * this replaces, not a new rule.
 */
function ProposalDetails({
  proposal,
  order,
  time,
  messages,
  isMessagingOpen,
  messageDraft,
  messageStatus,
  onMessageDraftChange,
  onSendMessage,
}: {
  proposal: ProposalListItem
  order: OrderListItem | undefined
  time: string | null
  messages: ProposalMessageItem[]
  isMessagingOpen: boolean
  messageDraft: string
  messageStatus: ProposalActionStatus
  onMessageDraftChange: (value: string) => void
  onSendMessage: () => void
}) {
  return (
    <>
      {/* ADR-058 (Scheduled Pickup Time): shown whenever this order
          carries a passenger-requested pickup instant, regardless of
          proposal status -- a driver deciding whether to accept needs to
          know it is a pre-booking, not only a driver who already has. */}
      {order?.requestedPickupAt && (
        <Text role="body">📅 Предварительный заказ: {formatRequestedPickupAt(order.requestedPickupAt)}</Text>
      )}
      {order?.passengerName && <Text role="body">Пассажир: {order.passengerName}</Text>}
      {order?.pickupAddress && <Text role="body">Откуда: {order.pickupAddress}</Text>}
      {order?.destination && <Text role="body">Куда: {order.destination}</Text>}
      {order?.passengerCount && <Text role="body">Пассажиров: {order.passengerCount}</Text>}
      {/* Product Cycle (Passenger Ride Requirements): the passenger's own
          "Пожелания к поездке" -- shown only when actually present (an
          order submitted before this field existed, or with nothing
          typed, has none: no empty block, per this cycle's own explicit
          requirement). Placed among the other trip-context facts, above
          [proposal.statedPrice]/[proposal.statedEtaMinutes] below -- this
          function is itself rendered before the price/ETA inputs in
          `RequestCard` for an OPEN proposal (see that call site), so a
          driver always reads this before naming a price, never after.
          `strong`, like the "Водитель предлагает" price line elsewhere in
          this file: this can be the one fact that decides whether the
          driver can even take the ride (a child seat they don't have, a
          pet, help boarding), not a passive detail to skim past. */}
      {order?.notes && (
        <Text role="body" strong>
          Пожелания: {order.notes}
        </Text>
      )}
      {time && (
        <Text role="caption" tone="secondary">
          Заказ создан: {time}
        </Text>
      )}
      {/* ADR-042 (Stated Ride Price Minimal Model): read-back of the
          amount this driver themselves stated -- from the propose-price
          response and/or the same 3s poll that already refreshes every
          other field on this card, so it survives a reload (R7.2).
          Checked by presence, not by a specific status: since 2026-09-05
          a price is stated at PRICE_PROPOSED and carries through
          unchanged to ACCEPTED (and even a passenger's own price decline,
          which keeps it for the historical record) -- gating on ACCEPTED
          alone would hide it during the very state it most needs to be
          visible in. */}
      {proposal.statedPrice && <Text role="body">Стоимость: {proposal.statedPrice}</Text>}
      {/* ADR-057 (Driver Stated Time to Pickup): same read-back pattern
          as statedPrice immediately above. */}
      {typeof proposal.statedEtaMinutes === 'number' && (
        <Text role="body">Будет примерно через: {proposal.statedEtaMinutes} мин</Text>
      )}
      {/* Minimal In-Ride Messaging (Product Cycle): "коммуникация
          принадлежит конкретной поездке, а не платформе в целом" --
          rendered inside this exact proposal's own card, alongside its
          route/price, never as a separate inbox screen. No "closed after
          COMPLETED" branch is needed for that status specifically (unlike
          RideRequest.tsx's own mirror of this block): once an Assignment
          for this order reaches COMPLETED, [visibleProposals] (this
          file's own filter, above) already removes this whole card from
          the screen. DECLINED/LAPSED/WITHDRAWN proposals, however, *do*
          stay visible here (the terminal-status branch just below this
          function's own call site) -- [isMessagingOpen] closes sending for
          exactly those three, mirroring Dispatch's own
          `ProposalMessagingApplicationService.isMessagingOpen` gating.
          Hidden entirely when there is nothing to show at all (closed and
          no history) -- an empty card section would be noise. */}
      {(isMessagingOpen || messages.length > 0) && (
        <>
          <Text role="label" tone="muted">
            Сообщения по этой поездке
          </Text>
          {messages.length > 0 && (
            <div className={styles.messageThread}>
              {messages.map((message) => (
                <MessageBubble
                  key={message.id}
                  own={message.senderRole === 'DRIVER'}
                  text={`${message.senderRole === 'DRIVER' ? 'Вы' : 'Пассажир'}: ${message.body}`}
                  sentAt={message.sentAt}
                />
              ))}
            </div>
          )}
          {isMessagingOpen ? (
            <>
              <FormField label="Ответ пассажиру" htmlFor={`message-${proposal.proposalId}`}>
                <Textarea
                  id={`message-${proposal.proposalId}`}
                  value={messageDraft}
                  maxLength={MESSAGE_MAX_LENGTH}
                  rows={2}
                  placeholder="Например: уже еду, буду через 5 минут"
                  onChange={(event) => onMessageDraftChange(event.target.value)}
                />
              </FormField>
              <Button
                label="Отправить"
                variant="secondary"
                loading={messageStatus === 'submitting'}
                disabled={!messageDraft.trim()}
                onClick={onSendMessage}
              />
              {messageStatus === 'error' && (
                <StatusMessage tone="error">Не удалось отправить сообщение. Попробуйте ещё раз.</StatusMessage>
              )}
            </>
          ) : (
            <Text role="caption" tone="muted">
              Обмен сообщениями закрыт.
            </Text>
          )}
        </>
      )}
    </>
  )
}

/**
 * Sprint "My Business + Circle of Trust", journey item 8 ("Просмотр своих
 * пассажиров"): best-effort passengerReference -> passengerName lookup,
 * built entirely from this driver's own already-fetched orders (no new
 * backend call) -- `Order.passengerName` and `Order.origin` (the
 * passenger's own reference, see [OrderListItem]'s own KDoc) are both
 * already returned by `GET /v1/orders`. A passenger who connected but has
 * not yet placed an order with this driver has no name here yet -- that is
 * a real, honest gap, not something to paper over with a placeholder name.
 */
function passengerNamesByReference(orders: Record<string, OrderListItem>): Record<string, string> {
  const names: Record<string, string> = {}
  for (const order of Object.values(orders)) {
    if (order.passengerName) {
      names[order.origin] = order.passengerName
    }
  }
  return names
}

/** Per-passenger ride stats [clientRideStatsByReference] below produces -- see that function's own KDoc. */
interface ClientRideStats {
  rideCount: number
  /** The most recent completed ride's own `Assignment.statusChangedAt` (ISO-8601), or `null` if this passenger has none yet -- format with [formatHistoryDateTime], same as [completedRides] already does. */
  lastRideAt: string | null
  /** Mirrors the backend's own `driver_client_rides.ride_count >= 2` threshold verbatim (see this function's own KDoc) -- not a new business rule invented here. */
  isRepeat: boolean
}

/** The honest zero [ClientRideStats] a connection with no completed ride yet renders -- see [clientRideStatsByReference]'s own KDoc on why this is a real zero, not a hidden/skipped row. */
const ZERO_CLIENT_RIDE_STATS: ClientRideStats = { rideCount: 0, lastRideAt: null, isRepeat: false }

/**
 * Client CRM depth ("Мой бизнес -> Клиенты"): per-passenger ride count,
 * last-ride date, and repeat flag, built entirely from this screen's own
 * already-loaded state -- [completedRides] (itself derived from
 * [proposals]/[assignments], this file's own existing derivation, below),
 * plus [orderDetails] to resolve each order's own passengerReference
 * (`OrderListItem.origin`, exactly like [passengerNamesByReference]
 * immediately above). No new request, no new backend endpoint.
 *
 * [completedRides] is already sorted newest-first (its own definition,
 * below), so the first entry seen for a given passengerReference is
 * already that passenger's most recent ride -- no separate max/sort step
 * is needed here.
 *
 * `isRepeat` reaching `true` at `rideCount >= 2` mirrors driver-management's
 * own `driver_client_rides.ride_count` threshold verbatim
 * (`V7__driver_milestones_repeat_clients.sql`: "ride_count reaching 2 is
 * the moment this pair becomes a 'repeat client'") -- the same rule
 * already used server-side to increment `driver_milestones.repeat_clients_count`,
 * not a new one invented on this screen.
 *
 * A passenger with a connection but no completed ride yet has no entry in
 * the returned record -- callers default to `{ rideCount: 0, lastRideAt:
 * null, isRepeat: false }` rather than this function inventing a
 * placeholder, so that default renders as a real, honest zero (this
 * file's own "Сегодня" tiles' no-hiding-a-zero convention) rather than
 * being hidden.
 */
function clientRideStatsByReference(
  completedRides: ProposalListItem[],
  orderDetails: Record<string, OrderListItem>,
  assignments: Record<string, AssignmentInfo>
): Record<string, ClientRideStats> {
  const stats: Record<string, ClientRideStats> = {}
  for (const proposal of completedRides) {
    const order = orderDetails[proposal.orderId]
    if (!order) {
      continue
    }
    const passengerReference = order.origin
    const existing = stats[passengerReference]
    if (existing) {
      existing.rideCount += 1
    } else {
      stats[passengerReference] = {
        rideCount: 1,
        lastRideAt: assignments[proposal.orderId]?.statusChangedAt ?? null,
        isRepeat: false,
      }
    }
  }
  for (const passengerReference of Object.keys(stats)) {
    stats[passengerReference].isRepeat = stats[passengerReference].rideCount >= 2
  }
  return stats
}

/**
 * H6 ("Driver Growth Snapshot"): how many passengers connected through this
 * driver's own link today, by this device's own local calendar day --
 * deliberately simple (no timezone reconciliation with the server) since
 * this is a same-day glance, not a report. Same, one true number every
 * time; a day with none shows 0, not hidden or softened.
 */
function todaysNewClientCount(connections: ConnectionListItem[]): number {
  const today = new Date().toDateString()
  return connections.filter((connection) => new Date(connection.createdAt).toDateString() === today).length
}

/**
 * Generates the internal id a new driver profile needs
 * (`driver-management`'s `POST /v1/drivers` still requires a caller-
 * supplied id — ADR-039 does not change that contract). Never shown to
 * the person creating the profile; they only ever provide a display name.
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
 * an internal driver code — entirely. ADR-055 ("Final Pre-Pilot Sprint")
 * replaces the identity this used to create silently with a real account
 * (phone + password): welcome → register or log in → the person provides
 * their own name → a Driver profile is created and linked to that account
 * → main app. Reopening the app restores the same account+driver from this
 * device's own stored, backend-verified session, not from a typed code or
 * a credential-less pointer.
 *
 * Sprint 2 (Identity MVP): re-entry now calls
 * `identityProvider.restoreIdentity()`, not the synchronous
 * `getStoredIdentity()` cache read alone — this device's own pointer is
 * re-confirmed against the real Identity backend on every reopen (a brief
 * [isRestoringIdentity] loading state covers the round trip), so a stale
 * or server-reset pointer does not leave this screen stuck showing a
 * driver profile the backend no longer recognizes.
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
 *
 * Sprint H5 (Entrepreneur Working Cycle Integrity) adds each order's own
 * `pickupAddress`, read from the same `GET /v1/orders` response, rendered
 * next to `destination` exactly like it.
 *
 * Pilot-readiness fix: this screen previously only *displayed* a driver's
 * own state (via [DriverCard], read-only) — there was no way for a driver
 * to change it themselves from here at all. A toggle at the top of the
 * ready screen now calls the existing, already-tested
 * `POST /v1/drivers/:id/availability` (see [toggleAvailability]'s own
 * KDoc). No new backend endpoint or state was introduced by this fix.
 */
export function DriverHome() {
  // ADR-073 (Driver-to-Driver Referral -- Single-Hop Origin Fact), Part 3:
  // `undefined` on every route other than `/d/:inviterDriverCode` (the
  // existing `/` and `/i/:driverCode/...` routes render this same
  // component with no such param), so every existing caller of this
  // screen is unaffected.
  const { inviterDriverCode } = useParams<{ inviterDriverCode: string }>()
  const [identity, setIdentity] = useState<StoredIdentity | null>(null)
  // Sprint 2 (Identity MVP): true until the mount-time restoreIdentity()
  // round trip resolves, so a returning driver never sees a flash of the
  // welcome/onboarding screen before their real, backend-confirmed
  // identity is known.
  const [isRestoringIdentity, setIsRestoringIdentity] = useState(true)
  const [authStep, setAuthStep] = useState<'intro' | 'auth'>('intro')
  const [authMode, setAuthMode] = useState<'register' | 'login'>('register')
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [authError, setAuthError] = useState<string | null>(null)
  const [isSubmittingAuth, setIsSubmittingAuth] = useState(false)

  const [nameInput, setNameInput] = useState('')
  const [nameError, setNameError] = useState<string | null>(null)
  const [isCreatingDriver, setIsCreatingDriver] = useState(false)
  const pendingDriverId = useRef<string | null>(null)

  const [status, setStatus] = useState<Status>('loading')
  const [driver, setDriver] = useState<DriverInfo | null>(null)
  // Pilot-readiness fix: this driver's own control over whether they
  // currently receive new orders — previously this screen had no way to
  // change it at all, only `Coordinator.tsx` (an operator-facing screen)
  // could. Idle/submitting/error mirrors [proposalActions]'s own
  // convention for a single in-flight action.
  const [availabilityAction, setAvailabilityAction] = useState<AvailabilityActionStatus>('idle')
  const [feedback, setFeedback] = useState<string | null>(null)
  const feedbackTimeout = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  // PIOS Group and Long-Distance Rides Roadmap, Stage 1: this driver's own
  // vehicle details -- form inputs seeded from `driver` once it loads (see
  // the seeding effect near the fetch below), never auto-reset while the
  // driver is actively editing (mirrors [nameInput]'s own "seed once,
  // never overwrite mid-edit" convention for this same screen).
  const [vehicleMakeInput, setVehicleMakeInput] = useState('')
  const [vehicleModelInput, setVehicleModelInput] = useState('')
  const [vehicleColorInput, setVehicleColorInput] = useState('')
  const [vehiclePlateInput, setVehiclePlateInput] = useState('')
  const [vehicleSeatsInput, setVehicleSeatsInput] = useState('')
  const [vehicleAction, setVehicleAction] = useState<AvailabilityActionStatus>('idle')
  const [vehicleSeeded, setVehicleSeeded] = useState(false)

  // PIOS Group and Long-Distance Rides Roadmap, Stage 3: this driver's own
  // willingness to take long-distance trips (vakhta/airport/another city) --
  // seeded alongside the vehicle inputs above (same effect, same "seed
  // once" rule), but saved immediately on toggle rather than through a
  // separate "Save" button, since it is a single flag, not a multi-field
  // record.
  const [longDistanceInput, setLongDistanceInput] = useState(false)
  const [longDistanceAction, setLongDistanceAction] = useState<AvailabilityActionStatus>('idle')

  const [proposalsStatus, setProposalsStatus] = useState<Status>('loading')
  const [proposals, setProposals] = useState<ProposalListItem[]>([])
  const [proposalActions, setProposalActions] = useState<Record<string, ProposalActionStatus>>({})
  // ADR-042 (Stated Ride Price Minimal Model): the price a driver is
  // typing for a given open proposal, keyed by proposalId, before they
  // tap "Принять". Sent only on the accept action (never on decline) and
  // only if non-blank -- see `respondToProposal`.
  const [priceInputs, setPriceInputs] = useState<Record<string, string>>({})
  // ADR-057 (Driver Stated Time to Pickup): the ETA a driver has chosen for
  // a given open proposal, keyed by proposalId, before they tap "Принять" --
  // mirrors [priceInputs] exactly. `null`/unset means "not chosen".
  const [etaInputs, setEtaInputs] = useState<Record<string, number | null>>({})
  const [orderDetails, setOrderDetails] = useState<Record<string, OrderListItem>>({})
  // Minimal In-Ride Messaging (Product Cycle): all three keyed by
  // proposalId, mirroring [priceInputs]/[etaInputs]'s own per-card
  // convention exactly -- each visible proposal card gets its own
  // independent draft/status, never shared across cards.
  const [messagesByProposal, setMessagesByProposal] = useState<Record<string, ProposalMessageItem[]>>({})
  const [messageDrafts, setMessageDrafts] = useState<Record<string, string>>({})
  const [messageActions, setMessageActions] = useState<Record<string, ProposalActionStatus>>({})
  const [connections, setConnections] = useState<ConnectionListItem[]>([])
  const [milestones, setMilestones] = useState<DriverMilestonesInfo | null>(null)
  // Product owner request, 2026-09-07: "Мой бизнес" as three separate
  // screens (Обзор/Клиенты/Маршруты), not one long scroll -- see the
  // BUSINESS_TABS section below for what deliberately stayed outside the
  // tabs and why. Defaults to "overview", matching the mockup's own
  // default tab; never auto-switches on its own, so a driver who has
  // navigated to another tab is never yanked back to it mid-shift.
  const [activeBusinessTab, setActiveBusinessTab] = useState<'overview' | 'clients' | 'routes'>('overview')
  // Product owner request, 2026-09-07: the app-shell-level bottom nav
  // (Главное/Работа/Бизнес/Профиль) from the same concept mockup. Defaults
  // to "home" -- the leftmost tab, matching the mockup's own house icon --
  // never auto-switches, same reasoning as [activeBusinessTab] above.
  const [activeMainTab, setActiveMainTab] = useState<'home' | 'work' | 'business' | 'profile'>('home')
  // ADR-040 (Assignment Ride Lifecycle): keyed by orderId, one entry per
  // ACCEPTED proposal that already has an Assignment — an OPEN proposal
  // has none yet, so never appears here.
  const [assignments, setAssignments] = useState<Record<string, AssignmentInfo>>({})
  const [assignmentActions, setAssignmentActions] = useState<Record<string, AssignmentActionStatus>>({})
  // UX audit (docs/PIOS_DRIVER_HOME_UX_AUDIT.md Section 5/9, "COMPLETE"):
  // a dedicated, transient acknowledgment for ride completion -- its own
  // state, separate from [feedback] (copy/share), so a completion message
  // can never be silently overwritten by an unrelated toast, or vice
  // versa. Same shape as [feedback]/[feedbackTimeout] below on purpose.
  const [completionFeedback, setCompletionFeedback] = useState<string | null>(null)
  const completionFeedbackTimeout = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  // PIOS Onboarding v1 (Product Owner exception, PIOS_PRODUCT_EVIDENCE.md
  // gate): shown once, automatically, the first time this driver's real
  // profile finishes loading -- not during the auth/name-entry steps above,
  // which have their own explanatory copy already. `hasSeenDriverOnboarding`
  // is only consulted here, at the point of first render; replay is a
  // manual, explicit action (see the "Как это работает" control below), not
  // re-triggered by this effect.
  const [showOnboarding, setShowOnboarding] = useState(false)
  const hasAutoShownOnboarding = useRef(false)

  // PIOS Install v1 (Product Owner exception, same gate/note as onboarding
  // above): a separate overlay and a separate "seen" flag
  // (`localInstallSeen.ts`, never `localOnboardingSeen.ts` — Section 14).
  // Chained once after this driver's very first onboarding completion
  // (Section 10's own "правильная последовательность"), never on a manual
  // "Как это работает" replay — [handleOnboardingDismiss] below only
  // chains when `hasSeenDriverOnboarding()` was still false the moment
  // dismiss fired, which is true only the first time ever.
  const [showInstall, setShowInstall] = useState(false)

  const driverId = identity?.driverId ?? null

  useEffect(() => {
    if (status === 'ready' && driver && !hasAutoShownOnboarding.current && !hasSeenDriverOnboarding()) {
      hasAutoShownOnboarding.current = true
      setShowOnboarding(true)
    }
  }, [status, driver])

  function handleOnboardingDismiss() {
    const isFirstOnboarding = !hasSeenDriverOnboarding()
    markDriverOnboardingSeen()
    setShowOnboarding(false)
    if (isFirstOnboarding && !hasSeenInstallHelp() && !isStandalone()) {
      setShowInstall(true)
    }
  }

  // Sprint 2 (Identity MVP): runs once, on mount only — re-entry always
  // re-confirms this device's own identity against the real backend
  // instead of trusting `getStoredIdentity()`'s local cache indefinitely.
  useEffect(() => {
    let active = true
    identityProvider.restoreIdentity().then((restored) => {
      if (!active) {
        return
      }
      setIdentity(restored)
      setIsRestoringIdentity(false)
    })
    return () => {
      active = false
    }
  }, [])

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

  // PIOS Group and Long-Distance Rides Roadmap, Stage 1: seed the vehicle
  // form inputs from `driver` exactly once, the moment it first loads --
  // never again afterwards, so a driver actively editing the form is never
  // overwritten by a background refresh (e.g. the availability poll above
  // re-fetching `driver` for an unrelated reason).
  useEffect(() => {
    if (!driver || vehicleSeeded) {
      return
    }
    setVehicleMakeInput(driver.vehicleMake ?? '')
    setVehicleModelInput(driver.vehicleModel ?? '')
    setVehicleColorInput(driver.vehicleColor ?? '')
    setVehiclePlateInput(driver.vehiclePlateNumber ?? '')
    setVehicleSeatsInput(driver.vehicleSeatCount != null ? String(driver.vehicleSeatCount) : '')
    setLongDistanceInput(driver.acceptsLongDistanceTrips ?? false)
    setVehicleSeeded(true)
  }, [driver, vehicleSeeded])

  // First-pilot feedback: a driver's order list now refreshes itself —
  // polling every `PROPOSALS_POLL_INTERVAL_MS` replaces the manual
  // "Обновить" button this screen used to require. The first load still
  // shows the loading state; every poll after that is silent (no spinner
  // flicker every few seconds) and simply leaves the last-known list on
  // screen if a single poll happens to fail — the same best-effort
  // tolerance `orderDetails` below already has.
  useEffect(() => {
    // ADR-060 (Order Query Authorization): `driverId` is only ever derived
    // from `identity?.driverId` (below), so `driverId` truthy already
    // implies `identity` truthy at runtime -- this second check exists so
    // TypeScript narrows `identity` to non-null for `identity.token` below,
    // now that both `loadProposals` (its own `?driverId=`) and, through it,
    // `loadOrderDetails` (its own `?ids=`) require a Bearer token.
    if (!driverId || !identity) {
      return
    }
    let active = true
    loadProposals(active, driverId, identity.token, { silent: false })
    loadConnections(active, driverId, identity.token)
    loadMilestones(active, driverId, identity.token)
    const interval = setInterval(() => {
      loadProposals(active, driverId, identity.token, { silent: true })
      loadConnections(active, driverId, identity.token)
      loadMilestones(active, driverId, identity.token)
    }, PROPOSALS_POLL_INTERVAL_MS)
    return () => {
      active = false
      clearInterval(interval)
    }
  }, [driverId, identity])

  /**
   * ADR-060 Decision 1/Mode 2: `GET /v1/orders?ids=...` now requires a
   * driver-linked Bearer token, and returns only the named orders -- this
   * screen only ever needs orders it already has a proposal for, which
   * [loadProposals] below already knows once its own request resolves.
   * [orderIds] empty means no proposals exist yet, so there is nothing to
   * look up (unchanged in effect from this driver's own screen showing
   * nothing, before this ADR fetched every order in the system to get
   * there).
   */
  function loadOrderDetails(active: boolean, orderIds: string[], token: string) {
    if (orderIds.length === 0) {
      setOrderDetails({})
      return
    }
    request<OrderListItem[]>(`/v1/orders?ids=${orderIds.join(',')}`, {
      headers: { Authorization: `Bearer ${token}` },
      baseUrl: ORDER_MANAGEMENT_BASE_URL,
    })
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

  /**
   * Minimal In-Ride Messaging (Product Cycle): mirrors [loadOrderDetails]'s
   * own shape exactly, one level down -- a message thread is scoped to a
   * proposal, not an order (see `ProposalMessage.kt`'s own KDoc for why),
   * so this fetches per [proposalId] rather than per order id. No batch
   * endpoint exists for this MVP (a driver's own visible proposal count is
   * always small), so each proposal's own thread is requested independently
   * and merged back together; one proposal's own failure never blocks
   * another's -- same best-effort tolerance [loadOrderDetails] already
   * establishes.
   */
  function loadMessages(active: boolean, proposalIds: string[], token: string) {
    if (proposalIds.length === 0) {
      setMessagesByProposal({})
      return
    }
    Promise.all(
      proposalIds.map((id) =>
        request<ProposalMessageItem[]>(`/v1/proposals/${id}/messages`, {
          headers: { Authorization: `Bearer ${token}` },
          baseUrl: DISPATCH_BASE_URL,
        })
          .then((items): [string, ProposalMessageItem[]] | null => [id, items])
          // Best-effort per proposal, not merely per batch: a transient
          // failure for one card's own thread must never overwrite its
          // last-known messages with an empty flash -- omitted here, then
          // filtered out below, so [current] (the merge target) simply
          // keeps whatever it already had for that one id, exactly
          // [loadOrderDetails]'s own whole-fetch best-effort tolerance,
          // applied per-id instead of per-batch.
          .catch((): [string, ProposalMessageItem[]] | null => null)
      )
    ).then((entries) => {
      if (!active) {
        return
      }
      const succeeded = entries.filter((entry): entry is [string, ProposalMessageItem[]] => entry !== null)
      setMessagesByProposal((current) => ({ ...current, ...Object.fromEntries(succeeded) }))
    })
  }

  /** H6 ("Driver Growth Snapshot"): best-effort, same tolerance as [loadOrderDetails] -- a failure here only hides today's client count, nothing actionable on this screen depends on it. ADR-055: this endpoint now requires this driver's own session token. */
  function loadConnections(active: boolean, forDriverId: string, token: string) {
    request<ConnectionListItem[]>(`/v1/connections?driverId=${forDriverId}`, {
      headers: { Authorization: `Bearer ${token}` },
      baseUrl: PASSENGER_EXPERIENCE_BASE_URL,
    })
      .then((result) => {
        if (!active) {
          return
        }
        setConnections(result)
      })
      .catch(() => {
        // Best-effort: see this function's own KDoc.
      })
  }

  /** Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2): best-effort, same tolerance as [loadConnections] -- a failure here only hides the growth card's ride count/streak, nothing actionable on this screen depends on it. */
  function loadMilestones(active: boolean, forDriverId: string, token: string) {
    request<DriverMilestonesInfo>(`/v1/drivers/${forDriverId}/milestones`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((result) => {
        if (!active) {
          return
        }
        setMilestones(result)
      })
      .catch(() => {
        // Best-effort: see this function's own KDoc.
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

  /**
   * ADR-060 Decision 4: `?driverId=` now requires this driver's own Bearer
   * token -- unlike [loadConnections]'s own best-effort tolerance, a
   * failure here is not cosmetic (this is the core of the driver's
   * screen), so it still surfaces [proposalsStatus] `'error'` exactly as
   * before this ADR. [loadOrderDetails] is now called from inside this
   * function's own `.then`, not independently by the polling effect --
   * `GET /v1/orders?ids=` (ADR-060 Mode 2) needs the order ids this
   * response carries, which the previous, independent call never had.
   */
  function loadProposals(
    active: boolean,
    forDriverId: string,
    token: string,
    opts: { silent: boolean } = { silent: false }
  ) {
    if (!opts.silent) {
      setProposalsStatus('loading')
    }
    request<ProposalListItem[]>(`/v1/proposals?driverId=${forDriverId}`, {
      headers: { Authorization: `Bearer ${token}` },
      baseUrl: DISPATCH_BASE_URL,
    })
      .then((result) => {
        if (!active) {
          return
        }
        setProposals(result)
        setProposalsStatus('ready')
        loadAssignments(active, result)
        loadOrderDetails(active, Array.from(new Set(result.map((p) => p.orderId))), token)
        // Minimal In-Ride Messaging (Product Cycle): scoped per proposal,
        // not per order -- see [loadMessages]'s own KDoc.
        loadMessages(active, result.map((p) => p.proposalId), token)
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
   * ACCEPTED proposal in [currentProposals], in one batched request via
   * `GET /v1/assignments?orderIds=<id>,<id>,...` -- Dispatch's own
   * `AssignmentController.listAssignments` (Client CRM depth / N+1 fix
   * task) now accepts this alongside its original single-`orderId` mode,
   * the same comma-separated-ids convention `GET /v1/orders?ids=` already
   * established (ADR-060 Mode 2). Previously issued one request per
   * accepted order, every 3-second poll -- a real N+1 this fixes without
   * changing the poll interval or anything this function's own callers
   * observe. Reads the freshly-fetched proposals passed in, not the
   * `proposals` state, since this always runs from inside the same
   * `.then` that just resolved them — reading state here would see the
   * previous poll's value.
   */
  function loadAssignments(active: boolean, currentProposals: ProposalListItem[]) {
    const acceptedOrderIds = Array.from(
      new Set(currentProposals.filter((p) => p.status === 'ACCEPTED').map((p) => p.orderId))
    )
    if (acceptedOrderIds.length === 0) {
      return
    }
    request<AssignmentInfo[]>(`/v1/assignments?orderIds=${acceptedOrderIds.join(',')}`, {
      baseUrl: DISPATCH_BASE_URL,
    })
      .then((results) => {
        if (!active) {
          return
        }
        setAssignments((current) => {
          const updated = { ...current }
          results.forEach((assignment) => {
            updated[assignment.orderId] = assignment
          })
          return updated
        })
      })
      .catch(() => {
        // Best-effort, same tolerance as before this fix: a failed batch
        // leaves the last-known assignments on screen rather than clearing
        // them, exactly like [loadOrderDetails]'s own convention.
      })
  }

  // Task 23 (Assignment API Security Remediation): arrive/start/complete
  // now require an Authorization header naming this driver's own
  // session -- Dispatch verifies it names the exact driver this
  // Assignment belongs to before permitting any of the three (see
  // docs/PIOS_TAXI_TASK_22_ASSIGNMENT_SECURITY_AUDIT.md and
  // docs/PIOS_TAXI_TASK_23_ASSIGNMENT_SECURITY_REMEDIATION_REPORT.md).
  // identity is guaranteed non-null here for the same reason Task 21
  // already established for respondToProposal, moments away in this same
  // file: this whole screen returns early, before any JSX, when identity
  // is null.
  async function respondToAssignment(assignmentId: string, action: 'arrive' | 'start' | 'complete') {
    if (assignmentActions[assignmentId] === 'submitting' || !identity) {
      return
    }
    setAssignmentActions((current) => ({ ...current, [assignmentId]: 'submitting' }))
    try {
      const updated = await request<AssignmentInfo>(`/v1/assignments/${assignmentId}/${action}`, {
        method: 'POST',
        baseUrl: DISPATCH_BASE_URL,
        headers: { Authorization: `Bearer ${identity.token}` },
      })
      setAssignments((current) => ({ ...current, [updated.orderId]: updated }))
      setAssignmentActions((current) => ({ ...current, [assignmentId]: 'idle' }))
      // UX audit Section 5/9 ("COMPLETE"): the underlying filter that
      // removes a COMPLETED ride from `visibleProposals` is unchanged
      // (ADR-040's own "экран освобождается") -- this only adds a brief,
      // honest acknowledgment before that happens, since a driver
      // otherwise saw the card vanish with no confirmation at all.
      if (action === 'complete') {
        showCompletionFeedback('Поездка завершена')
      }
    } catch {
      setAssignmentActions((current) => ({ ...current, [assignmentId]: 'error' }))
    }
  }

  function handleWelcomeContinue() {
    setAuthStep('auth')
  }

  function handleAuthFieldChange(setter: (value: string) => void) {
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

  async function handleRegisterSubmit() {
    const trimmedPhone = normalizePhone(phone.trim())
    if (!trimmedPhone) {
      setAuthError('Пожалуйста, укажите номер телефона.')
      return
    }
    // E-001 fix (docs/PIOS_PRODUCT_EVIDENCE.md): reject an invalid phone
    // format here, before ever calling the backend -- the backend's own
    // `Phone` value class already enforces this exact shape, silently, with
    // no way for this screen to distinguish that from a real network
    // failure once the request is sent (see this file's own catch block
    // below, and `phoneFormat.ts`'s own KDoc for the full incident).
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
      setIdentity(created)
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

  /** Section 6 ("Final Pre-Pilot Sprint"): the account itself is untouched — only this device forgets its own session. */
  function handleLogout() {
    identityProvider.logout()
    setIdentity(null)
    setAuthStep('intro')
    setDriver(null)
    setStatus('loading')
    setProposals([])
    setProposalsStatus('loading')
    setConnections([])
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
          body: JSON.stringify({
            driverId: newDriverId,
            displayName: trimmed,
            // ADR-073 Part 3: present only when this screen was reached
            // through a driver's own `/d/:inviterDriverCode` link -- every
            // other registration path (including the passenger-facing
            // `/i/:driverCode`) omits it, unchanged.
            ...(inviterDriverCode ? { invitedByDriverId: inviterDriverCode } : {}),
          }),
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

  // Product Owner instruction, 2026-09-05: a driver must state a price
  // before a ride proceeds -- `statedPrice` is now mandatory, unlike this
  // request's own pre-existing, still-supported optional shape on the
  // legacy `/accept` endpoint (ADR-042 Decision Revised R2/R5). The
  // caller (`respondToProposal`) already refuses to call this at all when
  // the input is blank -- see [RequestCard]'s own `acceptDisabled` wiring
  // below -- so `statedPrice` here is trusted to already be non-blank.
  function proposePriceRequestInit(proposalId: string): RequestInit {
    const statedPrice = (priceInputs[proposalId] ?? '').trim()
    const statedEtaMinutes = etaInputs[proposalId] ?? null
    return {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        statedPrice,
        ...(statedEtaMinutes !== null ? { statedEtaMinutes } : {}),
      }),
    }
  }

  // Task 21 (Proposal API Security Remediation): accept/decline now
  // require an Authorization header naming this driver's own session --
  // Dispatch verifies it names the exact driver a proposal was made to
  // before permitting either action (see
  // docs/PIOS_TAXI_TASK_20_PROPOSAL_SECURITY_AUDIT.md). identity is
  // guaranteed non-null here: this whole screen already returns early,
  // before any JSX (and so before this function could ever be invoked by
  // a click), when identity is null -- same guarantee `identity.token`
  // already relies on elsewhere in this component (e.g. loadProposals's
  // own call sites).
  //
  // Product Owner instruction, 2026-09-05: 'accept' now means "propose a
  // price" (`POST .../propose-price`), not the old direct `/accept` --
  // the ride is not confirmed until the passenger separately agrees to
  // that price (see the PRICE_PROPOSED render branch below).
  async function respondToProposal(proposalId: string, action: 'accept' | 'decline') {
    if (proposalActions[proposalId] === 'submitting' || !identity) {
      return
    }
    if (action === 'accept' && !(priceInputs[proposalId] ?? '').trim()) {
      return
    }
    setProposalActions((current) => ({ ...current, [proposalId]: 'submitting' }))
    try {
      const endpoint = action === 'accept' ? 'propose-price' : 'decline'
      const extra = action === 'accept' ? proposePriceRequestInit(proposalId) : {}
      const updated = await request<ProposalListItem>(`/v1/proposals/${proposalId}/${endpoint}`, {
        method: 'POST',
        baseUrl: DISPATCH_BASE_URL,
        ...extra,
        headers: { ...(extra.headers ?? {}), Authorization: `Bearer ${identity.token}` },
      })
      setProposals((current) => current.map((proposal) => (proposal.proposalId === proposalId ? updated : proposal)))
      setProposalActions((current) => ({ ...current, [proposalId]: 'idle' }))
    } catch (error) {
      setProposalActions((current) => ({ ...current, [proposalId]: 'error' }))
      // A 404/409 here means another actor already resolved this proposal (or it no
      // longer exists) -- reload the list so this screen reflects its real state.
      if (error instanceof ApiError && (error.status === 404 || error.status === 409) && driverId && identity) {
        loadProposals(true, driverId, identity.token)
      }
    }
  }

  /**
   * Minimal In-Ride Messaging (Product Cycle): the driver's own act of
   * sending a short reply on one specific proposal's own thread. Guarded
   * against double-submit like every other mutating action on this screen
   * ([respondToProposal]'s own precedent). Appends the server's own
   * returned message optimistically -- Dispatch's response already
   * carries the real, server-assigned `id`/`sentAt`, not a locally-
   * fabricated placeholder -- rather than waiting for the next poll tick.
   */
  async function handleSendMessage(proposalId: string) {
    const draft = (messageDrafts[proposalId] ?? '').trim()
    if (!draft || !identity || messageActions[proposalId] === 'submitting') {
      return
    }
    setMessageActions((current) => ({ ...current, [proposalId]: 'submitting' }))
    try {
      const created = await request<ProposalMessageItem>(`/v1/proposals/${proposalId}/messages`, {
        method: 'POST',
        baseUrl: DISPATCH_BASE_URL,
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${identity.token}` },
        body: JSON.stringify({ body: draft }),
      })
      setMessagesByProposal((current) => ({ ...current, [proposalId]: [...(current[proposalId] ?? []), created] }))
      setMessageDrafts((current) => ({ ...current, [proposalId]: '' }))
      setMessageActions((current) => ({ ...current, [proposalId]: 'idle' }))
    } catch {
      setMessageActions((current) => ({ ...current, [proposalId]: 'error' }))
    }
  }

  /**
   * Flips this driver's own state via the existing, already-tested
   * `POST /v1/drivers/:id/availability` (`DriverController.declareAvailability`).
   * Only `availability` from the response is merged into [driver] (see
   * [AvailabilityResponse]'s own KDoc for why the response is not used
   * wholesale).
   *
   * Task 25 (Orders Cancellation & Driver Availability Security
   * Remediation): now sends this driver's own Bearer token --
   * `DriverController` verifies it names the exact driver in the URL
   * before permitting the change (see
   * docs/PIOS_TAXI_TASK_24_REMAINING_MUTATION_API_SECURITY_AUDIT.md and
   * docs/PIOS_TAXI_TASK_25_SECURITY_REMEDIATION_REPORT.md). identity is
   * guaranteed non-null here for the same reason already established for
   * `respondToProposal`/`respondToAssignment` (Task 21/23): this whole
   * screen returns early, before any JSX, when identity is null.
   */
  async function toggleAvailability() {
    if (!driver || availabilityAction === 'submitting' || !identity) {
      return
    }
    const nextAvailability = driver.availability === 'AVAILABLE' ? 'UNAVAILABLE' : 'AVAILABLE'
    setAvailabilityAction('submitting')
    try {
      const response = await request<AvailabilityResponse>(`/v1/drivers/${driver.id}/availability`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${identity.token}` },
        body: JSON.stringify({ availability: nextAvailability }),
      })
      setDriver((current) => (current ? { ...current, availability: response.availability } : current))
      setAvailabilityAction('idle')
    } catch {
      setAvailabilityAction('error')
    }
  }

  /**
   * A driver's own declaration of their vehicle's details, via
   * `POST /v1/drivers/:id/vehicle` (`DriverController.updateVehicle`, PIOS
   * Group and Long-Distance Rides Roadmap Stage 1) -- mirrors
   * [toggleAvailability] exactly, including its own `identity` non-null
   * reasoning. A blank seat-count input is sent as `null` (not provided);
   * a non-blank one is parsed as an integer client-side so an obviously
   * invalid value ("abc") never reaches the server as a confusing 400 --
   * the server's own positive-only rule (`Driver.updateVehicle`) still
   * applies to whatever integer *is* sent.
   */
  async function handleUpdateVehicle() {
    if (!driver || vehicleAction === 'submitting' || !identity) {
      return
    }
    const trimmedSeats = vehicleSeatsInput.trim()
    const seatCount = trimmedSeats ? Number.parseInt(trimmedSeats, 10) : null
    if (trimmedSeats && (Number.isNaN(seatCount) || !Number.isFinite(seatCount))) {
      setVehicleAction('error')
      return
    }
    setVehicleAction('submitting')
    try {
      const response = await request<VehicleResponse>(`/v1/drivers/${driver.id}/vehicle`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${identity.token}` },
        body: JSON.stringify({
          make: vehicleMakeInput.trim() || null,
          model: vehicleModelInput.trim() || null,
          color: vehicleColorInput.trim() || null,
          plateNumber: vehiclePlateInput.trim() || null,
          seatCount,
        }),
      })
      setDriver((current) =>
        current
          ? {
              ...current,
              vehicleMake: response.vehicleMake,
              vehicleModel: response.vehicleModel,
              vehicleColor: response.vehicleColor,
              vehiclePlateNumber: response.vehiclePlateNumber,
              vehicleSeatCount: response.vehicleSeatCount,
            }
          : current
      )
      setVehicleAction('idle')
    } catch {
      setVehicleAction('error')
    }
  }

  /**
   * A driver's own declaration of willingness to take long-distance trips,
   * via `POST /v1/drivers/:id/long-distance-preference`
   * (`DriverController.updateLongDistancePreference`, PIOS Group and
   * Long-Distance Rides Roadmap Stage 3) -- mirrors [toggleAvailability]
   * exactly: saves immediately on toggle, no separate "Save" button, same
   * `identity` non-null reasoning.
   */
  async function toggleLongDistancePreference() {
    if (!driver || longDistanceAction === 'submitting' || !identity) {
      return
    }
    const next = !longDistanceInput
    setLongDistanceAction('submitting')
    try {
      const response = await request<DriverInfo>(`/v1/drivers/${driver.id}/long-distance-preference`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${identity.token}` },
        body: JSON.stringify({ accepts: next }),
      })
      setLongDistanceInput(response.acceptsLongDistanceTrips ?? next)
      setDriver((current) =>
        current ? { ...current, acceptsLongDistanceTrips: response.acceptsLongDistanceTrips ?? next } : current
      )
      setLongDistanceAction('idle')
    } catch {
      setLongDistanceAction('error')
    }
  }

  function showFeedback(message: string) {
    setFeedback(message)
    clearTimeout(feedbackTimeout.current)
    feedbackTimeout.current = setTimeout(() => setFeedback(null), FEEDBACK_DURATION_MS)
  }

  /** UX audit Section 5/9 ("COMPLETE") — see [completionFeedback]'s own KDoc. */
  function showCompletionFeedback(message: string) {
    setCompletionFeedback(message)
    clearTimeout(completionFeedbackTimeout.current)
    completionFeedbackTimeout.current = setTimeout(() => setCompletionFeedback(null), RIDE_COMPLETED_FEEDBACK_DURATION_MS)
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

  // ADR-071 (In-App, Poll-Derived Notification Surface): reshapes this
  // screen's own already-polled state ([proposals]/[assignments]/
  // [orderDetails]/[messagesByProposal]) into `deriveNotificationFacts`'s
  // own snapshot shape -- no new request. `useMemo`d on each source
  // state's own identity so the diff effect inside [useNotificationFacts]
  // only actually re-runs once per poll, not on every unrelated
  // re-render (e.g. typing in a price input). Placed here, before every
  // conditional early return below (isRestoringIdentity/!identity/
  // !identity.driverId) -- Rules of Hooks requires every hook this
  // component calls, including [useNotificationFacts]'s own internal
  // ones, to run in the same order on every render.
  const notificationProposals = useMemo<NotificationProposalSnapshot[]>(
    () =>
      proposals.map((p) => ({
        proposalId: p.proposalId,
        orderId: p.orderId,
        status: p.status,
        statedPrice: p.statedPrice,
      })),
    [proposals]
  )
  const notificationAssignments = useMemo<NotificationAssignmentSnapshot[]>(
    () =>
      Object.values(assignments).map((a) => ({
        orderId: a.orderId,
        status: a.status,
        statusChangedAt: a.statusChangedAt,
      })),
    [assignments]
  )
  const notificationOrders = useMemo<NotificationOrderSnapshot[]>(
    () => Object.values(orderDetails).map((o) => ({ id: o.id, status: o.status })),
    [orderDetails]
  )
  const notificationMessages = useMemo<NotificationMessageSnapshot[]>(() => {
    const items: NotificationMessageSnapshot[] = []
    for (const [proposalId, proposalMessages] of Object.entries(messagesByProposal)) {
      const orderId = proposals.find((p) => p.proposalId === proposalId)?.orderId
      if (!orderId) {
        continue
      }
      for (const message of proposalMessages) {
        items.push({ id: message.id, proposalId, orderId, senderRole: message.senderRole, sentAt: message.sentAt })
      }
    }
    return items
  }, [messagesByProposal, proposals])
  const {
    facts: notificationFacts,
    unseenCount: notificationUnseenCount,
    markAllSeen: markAllNotificationsSeen,
  } = useNotificationFacts('driver', notificationProposals, notificationAssignments, notificationOrders, notificationMessages)

  // Sprint 2 (Identity MVP): covers the restoreIdentity() round trip on
  // mount — shown before Phase 1's own check, so a returning driver never
  // sees a flash of the welcome screen while their real identity is still
  // being confirmed against the backend.
  if (isRestoringIdentity) {
    return (
      <div className={styles.screen}>
        <Header />
        <main className={styles.content}>
          <Spinner label="Загрузка…" />
        </main>
      </div>
    )
  }

  // Phase 1: no session on this device yet — welcome and explain, then
  // register or log in for real (ADR-055). No internal id is ever shown here.
  if (!identity) {
    return (
      <div className={styles.screen}>
        <Header />
        <main className={styles.content}>
          {authStep === 'intro' && (
            <>
              <Heading level={1} visual="display">
                Добро пожаловать!
              </Heading>
              <p className={styles.welcomeText}>PIOS помогает вам строить собственную клиентскую сеть.</p>

              <section className={styles.welcomeCard}>
                <p className={styles.welcomeCardTitle}>Ваши постоянные клиенты смогут:</p>
                <p className={styles.welcomeCardItem}>• быстро находить вас;</p>
                <p className={styles.welcomeCardItem}>• заказывать поездки через вашу ссылку;</p>
                <p className={styles.welcomeCardItem}>• оставаться вашими клиентами.</p>
              </section>

              <section className={styles.welcomeCard}>
                <p className={styles.welcomeCardTitle}>Что нужно сделать</p>
                <p className={styles.welcomeCardItem}>1. Создайте свой аккаунт PIOS.</p>
                <p className={styles.welcomeCardItem}>2. Получите свою ссылку.</p>
                <p className={styles.welcomeCardItem}>3. Отправьте её своим постоянным клиентам.</p>
                <p className={styles.welcomeCardItem}>4. Принимайте новые заказы.</p>
              </section>

              <div className={styles.actionRow}>
                <Button label="Начать" variant="primary" onClick={handleWelcomeContinue} />
              </div>
            </>
          )}

          {authStep === 'auth' && (
            <>
              <Heading level={1} visual="display">
                {authMode === 'register' ? 'Создайте свой аккаунт PIOS' : 'Войти в PIOS'}
              </Heading>
              <FormField label="Номер телефона" htmlFor="driver-phone">
                <input
                  id="driver-phone"
                  className={styles.driverCodeInput}
                  type="tel"
                  value={phone}
                  onChange={(event) => handleAuthFieldChange(setPhone)(event.target.value)}
                />
              </FormField>
              <FormField label="Пароль" htmlFor="driver-password">
                <PasswordInput
                  id="driver-password"
                  className={styles.driverCodeInput}
                  value={password}
                  onChange={handleAuthFieldChange(setPassword)}
                  autoComplete={authMode === 'register' ? 'new-password' : 'current-password'}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') {
                      void (authMode === 'register' ? handleRegisterSubmit() : handleLoginSubmit())
                    }
                  }}
                />
              </FormField>
              {authError && (
                <p className={styles.error} role="alert">
                  {authError}
                </p>
              )}
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
          <Heading level={1} visual="display">
            Как вас зовут?
          </Heading>
          <p className={styles.welcomeText}>Это имя увидят ваши клиенты, когда вы их пригласите.</p>
          <FormField label="Ваше имя" htmlFor="driver-name">
            <input
              id="driver-name"
              className={styles.driverCodeInput}
              type="text"
              value={nameInput}
              maxLength={MAX_NAME_LENGTH}
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
          </FormField>
          {nameError && (
            <p className={styles.error} role="alert">
              {nameError}
            </p>
          )}
          <div className={styles.actionRow}>
            <Button
              label="Создать профиль"
              variant="primary"
              loading={isCreatingDriver}
              onClick={() => void handleNameSubmit()}
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
  /**
   * History (MVP completion, §1): the exact inverse of [visibleProposals]
   * above -- reuses the same already-loaded [proposals]/[assignments]
   * state (`loadProposals`/`loadAssignments`, no new request, no new
   * model), rather than the ride simply vanishing with nowhere to go
   * (ADR-040's own "экран освобождается", previously a genuine dead end
   * here). Newest first, matching every other list on this screen's own
   * convention (most-recent-first is never re-derived per list). A
   * proposal never reaches `ACCEPTED` without a `statedPrice` (ADR-042,
   * mandatory) and [assignments] only ever holds a real fetched
   * Assignment, so every entry here has both a real price and a real
   * Assignment to read a completion time from.
   */
  const completedRides = proposals
    .filter((p) => p.status === 'ACCEPTED' && assignments[p.orderId]?.status === 'COMPLETED')
    .sort(
      (a, b) =>
        (assignments[b.orderId]?.statusChangedAt ?? '').localeCompare(assignments[a.orderId]?.statusChangedAt ?? '')
    )
  // Client CRM depth ("Мой бизнес -> Клиенты"): per-passenger ride
  // count/last-ride/repeat-flag, built from [completedRides] immediately
  // above -- see [clientRideStatsByReference]'s own KDoc.
  const clientRideStats = clientRideStatsByReference(completedRides, orderDetails, assignments)
  // Product owner request, 2026-09-07 (business tabs): a badge on the
  // "Маршруты" tab so a driver on "Обзор"/"Клиенты" still notices a ride
  // waiting on them -- OPEN (needs Accept/Decline) or ACCEPTED (needs
  // Прибыл/Начать/Завершить). Mirrors this same set exactly, just counted
  // rather than rendered.
  const routesNeedingAttentionCount = visibleProposals.filter(
    (p) => p.status === 'OPEN' || p.status === 'ACCEPTED',
  ).length

  return (
    <div className={styles.screen}>
      <Header />
      {/* ADR-071: a floating overlay, deliberately not nested inside
          `<Header />` itself (a shared component every other screen also
          renders, unmodified by this ADR) -- see
          `.notificationBellSlot`'s own CSS comment. */}
      <div className={styles.notificationBellSlot}>
        <NotificationBell
          facts={notificationFacts}
          unseenCount={notificationUnseenCount}
          onOpen={markAllNotificationsSeen}
        />
      </div>
      <main className={styles.content}>
        {status === 'loading' && <Spinner label="Загружаем ваш профиль…" />}

        {status === 'error' && (
          <div className={styles.errorBlock}>
            <p className={styles.error} role="alert">
              Не удалось загрузить профиль водителя. Проверьте связь с интернетом.
            </p>
            <Button
              label="Попробовать снова"
              variant="secondary"
              onClick={() => loadDriver(true, identity.driverId!)}
            />
          </div>
        )}

        {/* Product owner request, 2026-09-07: bottom-tab app shell
            (Главное/Работа/Бизнес/Профиль), matching the concept mockup's
            own bottom navigation -- see BottomNav below. Each of the four
            top-level sections below is gated on `activeMainTab` in
            addition to `status === 'ready' && driver`; nothing about any
            individual section's own internal logic changed, only which
            `activeMainTab` value makes it visible. "Работа" (Ваши заказы)
            keeps the exact reasoning docs/PIOS_DRIVER_HOME_UX_AUDIT.md
            Sections 4/9/11 already established for why it must never be
            silently buried -- BottomNav's own badge on this tab is that
            reasoning's equivalent for a tabbed shell: a driver on another
            tab still sees that something needs a response. */}
        {status === 'ready' && driver && activeMainTab === 'home' && (
          <>
            <h1 className={styles.pageTitle}>Главное</h1>
            <AvailabilityStatus
              availability={driver.availability}
              onToggle={() => void toggleAvailability()}
              loading={availabilityAction === 'submitting'}
              error={availabilityAction === 'error'}
            />
          </>
        )}

        {activeMainTab === 'work' && (
          <>
            <h1 className={styles.pageTitle}>Ваши заказы</h1>
            {/* Final UX walkthrough (2026-09-12): this used to tell a driver
                to press "Принять" -- no such button exists on an OPEN
                request (Product Owner instruction, 2026-09-05, changed
                accept into "propose a price"; RequestCard's own primary
                action has read "Предложить цену" ever since). A driver
                reading this instruction and then scanning the card below
                it for a button that matches was told something false at
                exactly the moment they need to know what to do next. */}
            <p className={styles.hint}>
              Здесь появляются заявки от ваших клиентов. Проверьте, откуда забрать пассажира и куда его отвезти,
              укажите цену и время подачи, и нажмите «Предложить цену».
            </p>

            {/* UX audit Section 5/9 ("COMPLETE"): a ride used to simply vanish
                from `visibleProposals` (still does, unchanged -- see that
                filter's own KDoc, ADR-040's "экран освобождается") with no
                acknowledgment at all. Minimal fix: the same transient-
                feedback pattern this file already uses for copy/share
                (`showFeedback`/`feedbackTimeout`), a dedicated state so it
                can never be overwritten by an unrelated copy/share toast,
                rendered with the already-existing `StatusMessage` (no new
                component, no modal). Clears itself; does not delay or alter
                the existing filter in any way. */}
            {completionFeedback && <StatusMessage tone="success">{completionFeedback}</StatusMessage>}

            {proposalsStatus === 'loading' && <Spinner label="Загружаем заказы…" />}
            {proposalsStatus === 'error' && (
              <div className={styles.errorBlock}>
                <p className={styles.error} role="alert">
                  Не удалось загрузить заказы. Проверьте связь с интернетом.
                </p>
                <Button
                  label="Попробовать снова"
                  variant="secondary"
                  onClick={() => loadProposals(true, identity.driverId!, identity.token)}
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
            const orderCode = shortOrderCode(proposal.orderId)
            const details = (
              <ProposalDetails
                proposal={proposal}
                order={order}
                time={time}
                messages={messagesByProposal[proposal.proposalId] ?? []}
                isMessagingOpen={proposal.status !== 'DECLINED' && proposal.status !== 'LAPSED' && proposal.status !== 'WITHDRAWN'}
                messageDraft={messageDrafts[proposal.proposalId] ?? ''}
                messageStatus={messageActions[proposal.proposalId] ?? 'idle'}
                onMessageDraftChange={(value) =>
                  setMessageDrafts((current) => ({ ...current, [proposal.proposalId]: value }))
                }
                onSendMessage={() => void handleSendMessage(proposal.proposalId)}
              />
            )

            // OPEN: this is the one state that needs the driver to decide
            // something right now -- RequestCard's own elevated, accent-
            // ruled treatment is the audit's own fix for a proposal that
            // used to render identically to every other status
            // (docs/PIOS_DESIGN_CONTINUATION_AUDIT.md Section 4).
            if (proposal.status === 'OPEN') {
              return (
                <RequestCard
                  key={proposal.proposalId}
                  orderCode={orderCode}
                  status="OPEN"
                  statusLabel={PROPOSAL_STATUS_LABEL.OPEN}
                  details={details}
                  onAccept={() => respondToProposal(proposal.proposalId, 'accept')}
                  onDecline={() => respondToProposal(proposal.proposalId, 'decline')}
                  submitting={proposalActions[proposal.proposalId] === 'submitting'}
                  error={proposalActions[proposal.proposalId] === 'error'}
                  // Product Owner instruction, 2026-09-05: a driver must
                  // name a price before a ride proceeds -- disabled, not
                  // hidden, so the requirement itself is visible rather
                  // than a click that silently does nothing.
                  acceptDisabled={!(priceInputs[proposal.proposalId] ?? '').trim()}
                >
                  {/* UI/UX redesign, Stage 3 (2026-09-12): the price is the
                      one real decision this whole card exists for --
                      "Цена + действие должны быть очевиднее второстепенных
                      элементов" -- so it gets a persistent, visible label
                      (`FormField`, matching this exact fix already applied
                      to every other form field in the app) instead of a
                      placeholder that disappears the moment a driver
                      starts typing the one number that matters most. */}
                  <FormField label="Ваша цена" htmlFor={`price-${proposal.proposalId}`}>
                    <Input
                      id={`price-${proposal.proposalId}`}
                      type="text"
                      value={priceInputs[proposal.proposalId] ?? ''}
                      placeholder="Например, 300 ₽"
                      onChange={(event) =>
                        setPriceInputs((current) => ({ ...current, [proposal.proposalId]: event.target.value }))
                      }
                    />
                  </FormField>
                  <FormField label="Когда сможете приехать?" htmlFor={`eta-${proposal.proposalId}`}>
                    <Select
                      id={`eta-${proposal.proposalId}`}
                      value={etaInputs[proposal.proposalId] ?? ''}
                      onChange={(event) =>
                        setEtaInputs((current) => ({
                          ...current,
                          [proposal.proposalId]: event.target.value ? Number(event.target.value) : null,
                        }))
                      }
                    >
                      <option value="">Не указано</option>
                      {ETA_OPTIONS_MINUTES.map((minutes) => (
                        <option key={minutes} value={minutes}>
                          {minutes} мин
                        </option>
                      ))}
                    </Select>
                  </FormField>
                </RequestCard>
              )
            }

            // PRICE_PROPOSED (Product Owner instruction, 2026-09-05): the
            // driver has named a price; nothing is settled yet -- no
            // Assignment exists -- until the passenger separately confirms
            // or declines it. A plain Card, like the resolved statuses
            // below: no action remains for the driver here, only waiting.
            if (proposal.status === 'PRICE_PROPOSED') {
              return (
                <Card key={proposal.proposalId}>
                  <Text role="label" tone="secondary">
                    Заказ №{orderCode}
                  </Text>
                  <RideStatus status="PRICE_PROPOSED" label={PROPOSAL_STATUS_LABEL.PRICE_PROPOSED} />
                  {details}
                </Card>
              )
            }

            // ACCEPTED: this driver's own current ride -- ActiveRidePanel's
            // own separate visual priority (Section 4 of the same audit)
            // is exactly what distinguished it from a still-pending
            // decision. Preserves the pre-existing `assignment && (...)`
            // gating exactly: no primary action at all until the
            // Assignment itself has loaded (see ActiveRidePanel's own
            // `primaryAction?` KDoc).
            if (proposal.status === 'ACCEPTED') {
              const primaryAction =
                assignment && (assignment.status === 'CREATED' || assignment.status === 'ACCEPTED')
                  ? { label: 'Прибыл', onClick: () => respondToAssignment(assignment.assignmentId, 'arrive') }
                  : assignment?.status === 'ARRIVED'
                    ? { label: 'Начать поездку', onClick: () => respondToAssignment(assignment.assignmentId, 'start') }
                    : assignment?.status === 'IN_PROGRESS'
                      ? {
                          label: 'Завершить поездку',
                          onClick: () => respondToAssignment(assignment.assignmentId, 'complete'),
                        }
                      : undefined
              return (
                <ActiveRidePanel
                  key={proposal.proposalId}
                  orderCode={orderCode}
                  status={(assignment?.status as RideLifecycleStatus | undefined) ?? 'ACCEPTED'}
                  statusLabel={PROPOSAL_STATUS_LABEL.ACCEPTED}
                  details={details}
                  primaryAction={primaryAction}
                  submitting={Boolean(assignment && assignmentActions[assignment.assignmentId] === 'submitting')}
                  error={Boolean(assignment && assignmentActions[assignment.assignmentId] === 'error')}
                />
              )
            }

            // DECLINED / LAPSED / WITHDRAWN: resolved, no action remains --
            // a plain Card, same detail fields, no elevated/accent
            // treatment (nothing here needs the driver's attention).
            return (
              <Card key={proposal.proposalId}>
                <Text role="label" tone="secondary">
                  Заказ №{orderCode}
                </Text>
                <RideStatus status={proposal.status} label={PROPOSAL_STATUS_LABEL[proposal.status]} />
                {details}
              </Card>
            )
          })}
          </>
        )}

        {status === 'ready' && driver && activeMainTab === 'business' && (
          <>
            <h1 className={styles.pageTitle}>Мой бизнес</h1>
            {/* Product owner request, 2026-09-07: "Мой бизнес" as three
                separate screens (Обзор/Клиенты/Маршруты), not one long
                scroll, matching the tab set from the concept mockup shown
                earlier. This whole section is now itself one of four
                BottomNav tabs (Главное/Работа/Бизнес/Профиль) -- "Ваши
                заказы" lives under "Работа", not here; BottomNav's own
                badge on that tab is what now carries the safety property
                docs/PIOS_DRIVER_HOME_UX_AUDIT.md Section 4/9/11
                established (a driver must never lose sight of something
                needing a response), since the two are siblings rather
                than one being folded into the other. */}
            <div className={styles.tabBar} role="tablist" aria-label="Мой бизнес">
              <button
                type="button"
                role="tab"
                aria-selected={activeBusinessTab === 'overview'}
                className={`${styles.tabButton} ${activeBusinessTab === 'overview' ? styles.tabButtonActive : ''}`}
                onClick={() => setActiveBusinessTab('overview')}
              >
                Обзор
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={activeBusinessTab === 'clients'}
                className={`${styles.tabButton} ${activeBusinessTab === 'clients' ? styles.tabButtonActive : ''}`}
                onClick={() => setActiveBusinessTab('clients')}
              >
                Клиенты
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={activeBusinessTab === 'routes'}
                className={`${styles.tabButton} ${activeBusinessTab === 'routes' ? styles.tabButtonActive : ''}`}
                onClick={() => setActiveBusinessTab('routes')}
              >
                Маршруты
                {routesNeedingAttentionCount > 0 && (
                  <span className={styles.tabBadge}>{routesNeedingAttentionCount}</span>
                )}
              </button>
            </div>

            {activeBusinessTab === 'overview' && (
            /* H6 ("Driver Growth Snapshot"): one honest number per tile,
                deliberately -- see PIOS_PRODUCT_HYPOTHESES.md's own note on
                what this Sprint does not do (no congratulatory wording, no
                hiding a zero). Stat-tile grid, colored by the same semantic
                tokens PIOS_DESIGN_SYSTEM.md already defines (trust for
                "Постоянных клиентов" — its own token comment names this
                exact case: "recognized relationship"). "Новых клиентов"
                (fixed genitive plural), not a declined phrase that changes
                with the count -- same convention TodayCard.tsx already uses
                ("Заказов создано"), which avoids Russian's noun declension
                by number entirely rather than getting it subtly wrong. */
            <section className={styles.growthCard} role="tabpanel">
              <p className={styles.growthTitle}>Сегодня</p>
              <div className={styles.growthGrid}>
                {/* Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md
                    Section 2): the same "one honest number, no hiding a
                    zero" convention as before -- shown even at 0/0 for a
                    brand-new driver, not hidden until it looks impressive. */}
                <div className={`${styles.growthTile} ${styles.growthTileAccent}`}>
                  <span className={styles.growthIcon} aria-hidden="true">
                    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6">
                      <circle cx="10" cy="10" r="7.25" />
                      <path d="M6.8 10.2l2 2 4-4.4" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                  </span>
                  <span className={styles.growthCount}>{milestones?.completedRidesCount ?? 0}</span>
                  <span className={styles.growthLabel}>Завершено поездок</span>
                </div>
                <div className={`${styles.growthTile} ${styles.growthTileSuccess}`}>
                  <span className={styles.growthIcon} aria-hidden="true">
                    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round">
                      <path d="M10 2.5c.7 2 .2 3-.7 4-1.3 1.5-2.5 2.7-2.5 4.8a3.2 3.2 0 0 0 6.4 0c0-1-.35-1.7-.85-2.4.9.5 1.65 1.5 1.65 3a4.5 4.5 0 0 1-9 0c0-3.2 2.1-4.7 3.4-6.4.6-.8 1.1-1.8.9-3z" />
                    </svg>
                  </span>
                  <span className={styles.growthCount}>{milestones?.currentStreakWeeks ?? 0}</span>
                  <span className={styles.growthLabel}>Недель подряд с поездками</span>
                </div>
                {/* Growth Loops TZ v1, Phase 2 extension (docs/PIOS_GROWTH_LOOPS_TZ_V1.md
                    Section 2.1): a passenger's second completed ride with
                    this driver, correlated locally against Order
                    Management's own OrderSubmitted -- see that section for
                    what this deliberately does not (and, for an order with
                    no known passenger yet, cannot) count. Trust-colored:
                    tokens.css's own comment defines that hue for exactly
                    this case, "this is someone you know". */}
                <div className={`${styles.growthTile} ${styles.growthTileTrust}`}>
                  <span className={styles.growthIcon} aria-hidden="true">
                    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round">
                      <path d="M10 16.8s-6-3.7-6-8.1a3.7 3.7 0 0 1 6-2.9 3.7 3.7 0 0 1 6 2.9c0 4.4-6 8.1-6 8.1z" />
                    </svg>
                  </span>
                  <span className={styles.growthCount}>{milestones?.repeatClientsCount ?? 0}</span>
                  <span className={styles.growthLabel}>Постоянных клиентов</span>
                </div>
                <div className={styles.growthTile}>
                  <span className={styles.growthIcon} aria-hidden="true">
                    <svg viewBox="0 0 20 20" fill="currentColor">
                      <path d="M10 2.5l1.4 4.1 4.1 1.4-4.1 1.4-1.4 4.1-1.4-4.1-4.1-1.4 4.1-1.4z" />
                    </svg>
                  </span>
                  <span className={styles.growthCount}>{todaysNewClientCount(connections)}</span>
                  <span className={styles.growthLabel}>Новых клиентов</span>
                </div>
                {/* ADR-064 (Referral Visibility): the one honest signal the
                    existing data actually supports -- how many passengers
                    have ever connected through this driver's own personal
                    link. `connections` is already fetched in full for the
                    tile above; this is its lifetime length, no backend
                    change. Wide summary strip, not a square tile -- it's a
                    lifetime total, not one more "today" fact. */}
                <div className={`${styles.growthTile} ${styles.growthTileWide}`}>
                  <span className={styles.growthIcon} aria-hidden="true">
                    <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round">
                      <path d="M8.2 11.8L11.8 8.2" />
                      <path d="M7.4 12.6l-1.6 1.6a2.6 2.6 0 0 1-3.7-3.7l2.4-2.4a2.6 2.6 0 0 1 3.7 0" />
                      <path d="M12.6 7.4l1.6-1.6a2.6 2.6 0 0 0-3.7-3.7l-2.4 2.4a2.6 2.6 0 0 0 0 3.7" />
                    </svg>
                  </span>
                  <span className={styles.growthLabel}>Всего пришло по вашей ссылке</span>
                  <span className={styles.growthCount}>{connections.length}</span>
                </div>
                {/* ADR-065 (Driver Earnings from Self-Stated Prices):
                    renders only once the backend actually returns a number
                    -- undefined (today, before that ships) means "not
                    shown", never a fabricated ₽0. `unpricedRidesCount` is
                    surfaced alongside so the figure never silently
                    under-reports which rides it does not cover. */}
                {typeof milestones?.totalStatedEarnings === 'number' && (
                  <div className={`${styles.growthTile} ${styles.growthTileWide}`}>
                    <span className={styles.growthIcon} aria-hidden="true">
                      <svg
                        viewBox="0 0 20 20"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="1.6"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      >
                        <path d="M6.5 4h4.5a3 3 0 0 1 0 6h-4.5" />
                        <path d="M6.5 4v12" />
                        <path d="M5 9.5h6" />
                        <path d="M5 12.5h6" />
                      </svg>
                    </span>
                    <span className={styles.growthLabel}>
                      Заработано по вашим ценам
                      {(milestones.unpricedRidesCount ?? 0) > 0 &&
                        ` · ещё ${milestones.unpricedRidesCount} поездок без цены`}
                    </span>
                    <span className={styles.growthCount}>{milestones.totalStatedEarnings} ₽</span>
                  </div>
                )}
              </div>
            </section>
            )}

            {activeBusinessTab === 'clients' && (
            <div className={styles.tabPanel} role="tabpanel">
              <QRCard
                invitationLink={invitationProvider.linkFor(driver.id)}
                linkTo={`/i/${driver.id}`}
                onCopy={handleCopy}
                onShare={handleShare}
                feedback={feedback}
              />
              <p className={styles.hint}>Отправьте эту ссылку клиенту — он сможет заказать поездку прямо у вас.</p>

              {/* Sprint "My Business + Circle of Trust", journey item 8 --
                  "Мои пассажиры", not "Пассажиры PIOS" (Section 14 of the
                  brief): these are this driver's own connections
                  (`GET /v1/connections?driverId=...`, already loaded above
                  for the "Сегодня" tab), named where a past order already
                  revealed a name, otherwise honestly labelled as not yet
                  named rather than guessed.

                  Product audit follow-up (2026-09-12): this list used to be
                  read-only text -- a driver with an established client had
                  no in-product action toward them at all, unlike the
                  passenger's own mirrored "Мои водители" list (already
                  fixed) which offers "Заказать поездку". No new invitation
                  system is introduced here: `handleShare`/`handleCopy`
                  already share this exact driver's one personal link
                  (`invitationProvider.linkFor(driver.id)`) from the QR card
                  just above. Reusing it here is deliberate and correct, not
                  a shortcut -- `PassengerLanding.tsx`'s own
                  `checkCircleThenAdvance` already resolves this same link
                  differently per recipient: an already-connected passenger
                  (exactly who appears in this list) is redirected straight
                  to `/request` with no re-registration, while a stranger
                  still sees the normal invite flow. So "share this link
                  with this specific client" is already a correct,
                  existing PIOS path to reach them -- no passenger-specific
                  URL parameter exists or is needed in the current
                  Connection model, and none is added here. */}
              {connections.length > 0 ? (
                <section className={styles.growthCard}>
                  <p className={styles.growthTitle}>Мои пассажиры</p>
                  <div className={styles.passengerList}>
                    {connections.map((connection) => {
                      const name = passengerNamesByReference(orderDetails)[connection.passengerReference]
                      const stats = clientRideStats[connection.passengerReference] ?? ZERO_CLIENT_RIDE_STATS
                      const lastRideText = formatHistoryDateTime(stats.lastRideAt)
                      return (
                        <div key={connection.passengerReference} className={styles.passengerListItem}>
                          <div className={styles.passengerListItemRow}>
                            <span>{name ?? 'Пассажир по вашей ссылке'}</span>
                            <button
                              type="button"
                              className={styles.passengerShareAction}
                              aria-label={`Поделиться ссылкой с ${name ?? 'пассажиром по вашей ссылке'}`}
                              onClick={() => void handleShare()}
                            >
                              Поделиться
                            </button>
                          </div>
                          {/* Client CRM depth ("Мой бизнес -> Клиенты"): ride
                              count (fixed "Поездок: N", same declension-free
                              convention as "Новых клиентов"/"Заказов
                              создано" elsewhere on this screen -- see this
                              file's own note on that above), last-ride date
                              (same [formatHistoryDateTime] the "Маршруты" tab
                              already uses), and a repeat-client flag at the
                              same `rideCount >= 2` threshold the backend's
                              own `driver_client_rides` already uses. Renders
                              a real "Поездок: 0" for a connection with no
                              completed ride yet, never hidden. */}
                          <Text role="caption" tone="secondary">
                            {`Поездок: ${stats.rideCount}`}
                            {lastRideText && ` · Последняя поездка: ${lastRideText}`}
                            {stats.isRepeat && ' · Постоянный клиент'}
                          </Text>
                        </div>
                      )
                    })}
                  </div>
                </section>
              ) : (
                /* UI/UX redesign, Stage 5 (2026-09-12): this section used to
                   render nothing at all for a driver with zero connections
                   -- honest (no fake list), but silent in a way that could
                   read as an unfinished screen rather than a real, expected
                   "nothing yet" state, unlike every other empty state in
                   this app (e.g. "Пока нет заказов…" just above, on
                   "Работа"). Same plain-text convention, no new component. */
                <p className={styles.hint}>
                  Пока никто не подключился по вашей ссылке. Поделитесь ей, чтобы увидеть здесь своего первого
                  клиента.
                </p>
              )}
            </div>
            )}

            {activeBusinessTab === 'routes' && (
            /**
             * History (MVP completion, §1): real completed rides, built
             * from [completedRides] above -- the same [proposals]/
             * [assignments]/[orderDetails] state this screen already loads
             * for the active-work list, never a second, parallel fetch or
             * model. Shares [proposalsStatus] for loading/error exactly:
             * [completedRides] is derived from the same request that state
             * already tracks, so a second, independent loading/error state
             * for the identical underlying data would only be able to
             * drift from it, never add real information.
             */
            <section className={styles.growthCard} role="tabpanel">
              <p className={styles.growthTitle}>История поездок</p>
              {proposalsStatus === 'loading' && <Spinner label="Загружаем историю…" />}
              {proposalsStatus === 'error' && (
                <div className={styles.errorBlock}>
                  <p className={styles.error}>Не удалось загрузить историю. Проверьте соединение.</p>
                  <Button
                    label="Повторить"
                    variant="secondary"
                    onClick={() => loadProposals(true, identity.driverId!, identity.token)}
                  />
                </div>
              )}
              {proposalsStatus === 'ready' && completedRides.length === 0 && (
                <p className={styles.hint}>
                  Здесь появятся ваши завершённые поездки. Заказы, которые ждут вашего ответа или уже приняты — в
                  разделе «Ваши заказы» выше.
                </p>
              )}
              {proposalsStatus === 'ready' && completedRides.length > 0 && (
                <div className={styles.historyList}>
                  {completedRides.map((proposal) => {
                    const order = orderDetails[proposal.orderId]
                    const assignment = assignments[proposal.orderId]
                    return (
                      <RideHistoryCard
                        key={proposal.proposalId}
                        dateTime={formatHistoryDateTime(assignment?.statusChangedAt ?? null)}
                        route={formatHistoryRoute(order)}
                        counterpart={order?.passengerName?.trim() || 'Пассажир'}
                        price={proposal.statedPrice}
                      />
                    )
                  })}
                </div>
              )}
            </section>
            )}
          </>
        )}

        {status === 'ready' && driver && activeMainTab === 'profile' && (
          <>
            <h1 className={styles.pageTitle}>Профиль</h1>
            {/* UI/UX redesign, Stage 6 (2026-09-12): moved from the very
                top (where it used to sit above the driver's own identity
                card) down to the bottom, grouped with "Выйти" -- both are
                quiet, low-frequency utility actions, not "who I am" or
                "what I can change" content, and the onboarding this
                replays already shows itself automatically on first load
                (see [hasAutoShownOnboarding]) -- a driver opening Profile
                came to see their own info first, not to be led to a
                tutorial link before it. */}
            <DriverCard
              driverCode={driver.id}
              displayName={driver.displayName}
              availability={driver.availability}
              hideCode
            />
            {/* PIOS Group and Long-Distance Rides Roadmap, Stage 1: a
                minimal, owned-by-driver vehicle record -- same card shape
                as the install card just below, so this reads as one more
                fact about this driver's own profile, not a separate
                widget bolted on. Public once saved (`GET /v1/drivers`
                already shows `displayName` unauthenticated for the same
                invite-preview reason), so a passenger can see which car
                to look for. */}
            <section className={styles.growthCard}>
              <p className={styles.growthTitle}>Моя машина</p>
              <p className={styles.hint}>Пассажир увидит это на странице приглашения — чтобы узнать вашу машину.</p>
              <FormField label="Марка" htmlFor="vehicle-make">
                <input
                  id="vehicle-make"
                  className={styles.driverCodeInput}
                  placeholder="Например, Lada"
                  value={vehicleMakeInput}
                  onChange={(event) => setVehicleMakeInput(event.target.value)}
                />
              </FormField>
              <FormField label="Модель" htmlFor="vehicle-model">
                <input
                  id="vehicle-model"
                  className={styles.driverCodeInput}
                  placeholder="Например, Vesta"
                  value={vehicleModelInput}
                  onChange={(event) => setVehicleModelInput(event.target.value)}
                />
              </FormField>
              <FormField label="Цвет" htmlFor="vehicle-color">
                <input
                  id="vehicle-color"
                  className={styles.driverCodeInput}
                  value={vehicleColorInput}
                  onChange={(event) => setVehicleColorInput(event.target.value)}
                />
              </FormField>
              <FormField label="Гос. номер" htmlFor="vehicle-plate">
                <input
                  id="vehicle-plate"
                  className={styles.driverCodeInput}
                  value={vehiclePlateInput}
                  onChange={(event) => setVehiclePlateInput(event.target.value)}
                />
              </FormField>
              <FormField label="Количество мест" htmlFor="vehicle-seats">
                <input
                  id="vehicle-seats"
                  className={styles.driverCodeInput}
                  type="number"
                  min={1}
                  value={vehicleSeatsInput}
                  onChange={(event) => setVehicleSeatsInput(event.target.value)}
                />
              </FormField>
              {vehicleAction === 'error' && <p className={styles.error}>Не удалось сохранить. Попробуйте ещё раз.</p>}
              <div className={styles.actionRow}>
                <Button
                  label="Сохранить машину"
                  loading={vehicleAction === 'submitting'}
                  onClick={handleUpdateVehicle}
                />
              </div>
              {/* PIOS Group and Long-Distance Rides Roadmap, Stage 3: a
                  simple flag, not a new algorithm -- PIOS records this
                  driver's own stated willingness and shows it to a
                  passenger; no automatic matching happens anywhere. */}
              <label className={styles.checkboxRow}>
                <input
                  type="checkbox"
                  checked={longDistanceInput}
                  disabled={longDistanceAction === 'submitting'}
                  onChange={toggleLongDistancePreference}
                />
                Беру дальние поездки (вахта, аэропорт, другой город)
              </label>
              {longDistanceAction === 'error' && (
                <p className={styles.error}>Не удалось сохранить. Попробуйте ещё раз.</p>
              )}
            </section>
            {/* PIOS Install v1 (Product Owner exception): a separate,
                additive card -- never replaces "Как это работает" above,
                which stays the onboarding-replay control. Hidden once PIOS
                is already running installed (`isStandalone()`) -- nothing
                to offer a driver who already has it. */}
            {!isStandalone() && (
              <section className={styles.installCard}>
                <p className={styles.growthTitle}>PIOS всегда под рукой</p>
                <p className={styles.installCardText}>Добавьте PIOS на экран телефона.</p>
                <Button label="Установить PIOS" variant="secondary" onClick={() => setShowInstall(true)} />
              </section>
            )}
            <button type="button" className={styles.linkAction} onClick={() => setShowOnboarding(true)}>
              Как это работает
            </button>
            <button type="button" className={styles.linkAction} onClick={handleLogout}>
              Выйти
            </button>
          </>
        )}
      </main>

      {status === 'ready' && driver && (
        <BottomNav
          activeTab={activeMainTab}
          onChange={setActiveMainTab}
          workBadgeCount={routesNeedingAttentionCount}
        />
      )}
      {showOnboarding && (
        <DriverOnboarding onComplete={handleOnboardingDismiss} onSkip={handleOnboardingDismiss} />
      )}
      {showInstall && <InstallPIOS onClose={() => setShowInstall(false)} />}
    </div>
  )
}
