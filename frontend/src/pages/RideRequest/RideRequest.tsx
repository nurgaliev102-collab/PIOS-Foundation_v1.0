import { useEffect, useState } from 'react'
import { Navigate, useParams } from 'react-router-dom'
import { Header } from '../../components/Header'
import { ActionButton } from '../../components/ActionButton'
import { Spinner } from '../../components/Spinner'
import { getInvitationByDriverCode } from '../PassengerLanding/invitationSource'
import { getPassengerIdentity } from '../../persistence/localPassengerIdentity'
import { getCurrentOrderId, saveCurrentOrderId } from '../../persistence/localCurrentOrder'
import { request } from '../../api/apiClient'
import styles from './RideRequest.module.css'

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

type Step = 'loading' | 'not-found' | 'error' | 'form' | 'confirmed'
type ProposalStatus = 'proposing' | 'proposed' | 'error'

interface SubmitOrderResponse {
  orderId: string
}

interface ProposalStatusItem {
  status: 'OPEN' | 'ACCEPTED' | 'DECLINED' | 'LAPSED'
}

interface AssignmentStatusItem {
  status: 'CREATED' | 'ACCEPTED' | 'ARRIVED' | 'IN_PROGRESS' | 'COMPLETED'
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
type RideStatus = 'OPEN' | 'DECLINED' | 'LAPSED' | 'ACCEPTED' | 'ARRIVED' | 'IN_PROGRESS' | 'COMPLETED'

const RIDE_STATUS_LABEL: Record<RideStatus, string> = {
  OPEN: '⏳ Ждём ответа водителя. Мы сообщим, как только он подтвердит заказ.',
  DECLINED: '❌ Водитель отклонил ваш заказ.',
  LAPSED: '⌛ Заказ больше не активен — водитель не ответил вовремя.',
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
 */
export function RideRequest() {
  const { driverCode } = useParams<{ driverCode: string }>()
  const [identity] = useState(() => getPassengerIdentity())
  const [step, setStep] = useState<Step>('loading')
  const [pickupAddress, setPickupAddress] = useState('')
  const [pickupAddressError, setPickupAddressError] = useState<string | null>(null)
  const [destination, setDestination] = useState('')
  const [destinationError, setDestinationError] = useState<string | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [orderId, setOrderId] = useState<string | null>(null)
  const [proposalStatus, setProposalStatus] = useState<ProposalStatus | null>(null)
  const [rideStatus, setRideStatus] = useState<RideStatus>('OPEN')

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
      // First-pilot feedback: a passenger who reloads this page must land
      // back on their current order, not a blank form — same driver, same
      // browser, an order already placed through `localCurrentOrder.ts`.
      const existingOrderId = getCurrentOrderId(forDriverCode)
      if (existingOrderId) {
        setOrderId(existingOrderId)
        setStep('confirmed')
        return
      }
      setStep('form')
    })
  }

  useEffect(() => {
    let active = true
    loadInvitation(active, driverCode ?? '')
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
    if (step !== 'confirmed' || !orderId) {
      return
    }
    let active = true
    function poll() {
      request<ProposalStatusItem[]>(`/v1/proposals?orderId=${orderId}`, { baseUrl: DISPATCH_BASE_URL })
        .then((items) => {
          if (!active) {
            return
          }
          if (!items.some((item) => item.status === 'ACCEPTED')) {
            // Sprint H5 (Truthful Status Rendering): reflect whichever real
            // proposal status this order actually has -- OPEN, DECLINED, or
            // LAPSED are three different facts and must not all render as
            // the same "✅ ... он свяжется с вами" success message. Priority
            // (OPEN > DECLINED > LAPSED) only matters if more than one
            // proposal somehow exists for this order; in the normal
            // single-proposal case exactly one of these is true.
            if (items.some((item) => item.status === 'OPEN')) {
              setRideStatus('OPEN')
            } else if (items.some((item) => item.status === 'DECLINED')) {
              setRideStatus('DECLINED')
            } else if (items.some((item) => item.status === 'LAPSED')) {
              setRideStatus('LAPSED')
            } else {
              // No proposal recorded yet (e.g., the propose call is still
              // in flight) -- honestly "waiting", not yet knowable as
              // anything else.
              setRideStatus('OPEN')
            }
            return
          }
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
  }, [step, orderId])

  if (!identity) {
    return <Navigate to={`/i/${driverCode ?? ''}`} replace />
  }

  const passengerId = identity.id
  const passengerName = identity.name

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
              onClick={() => loadInvitation(true, driverCode ?? '')}
            />
          </div>
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
              />
            </div>
          </>
        )}

        {step === 'confirmed' && orderId && (
          <>
            <p className={styles.confirmed}>✅ Заказ оформлен.</p>

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
              <p className={styles.status}>{RIDE_STATUS_LABEL[rideStatus]}</p>
            )}
          </>
        )}
      </main>
    </div>
  )
}
