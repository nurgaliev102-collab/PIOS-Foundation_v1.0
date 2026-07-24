import { useEffect, useState } from 'react'
import { Navigate, useParams } from 'react-router-dom'
import { Header } from '../../components/Header'
import { ActionButton } from '../../components/ActionButton'
import { getInvitationByDriverCode } from '../PassengerLanding/invitationSource'
import { getPassengerIdentity } from '../../persistence/localPassengerIdentity'
import { request } from '../../api/apiClient'
import styles from './RideRequest.module.css'

// Order Management's own local port (INTERFACE_CONTRACTS.md) — distinct
// from apiClientConfig's default (Driver Management's port), since this
// is the second backend module this frontend now genuinely calls.
const ORDER_MANAGEMENT_BASE_URL = import.meta.env.VITE_ORDER_MANAGEMENT_BASE_URL ?? 'http://localhost:8083'

type Step = 'loading' | 'not-found' | 'form' | 'confirmed'

interface SubmitOrderResponse {
  orderId: string
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
 * Driver Home's own direct-module-call precedent (Sprint 5). The real
 * backend contract accepts only `{ passengerReference }` (see
 * `backend/order-management/.../api/SubmitOrderRequest.kt`) — it has no
 * field for `destination` or `notes` (Sprint FND-006 added and then
 * reverted a `destination` field specifically to avoid breaking this
 * contract; see that sprint's own report). Those two inputs are still
 * collected by this form but are not sent anywhere — see
 * `frontend/README.md` for why this is disclosed as a known limitation,
 * not silently hidden.
 *
 * Submission is guarded against double-clicks
 * (`isSubmitting`): Order Management's own docs flag Submit Order as not
 * idempotent, so a duplicate request could create a duplicate order.
 */
export function RideRequest() {
  const { driverCode } = useParams<{ driverCode: string }>()
  const [identity] = useState(() => getPassengerIdentity())
  const [step, setStep] = useState<Step>('loading')
  const [destination, setDestination] = useState('')
  const [notes, setNotes] = useState('')
  const [destinationError, setDestinationError] = useState<string | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [orderId, setOrderId] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    setStep('loading')
    getInvitationByDriverCode(driverCode ?? '').then((result) => {
      if (!active) {
        return
      }
      if (!result) {
        setStep('not-found')
        return
      }
      setStep('form')
    })
    return () => {
      active = false
    }
  }, [driverCode])

  if (!identity) {
    return <Navigate to={`/i/${driverCode ?? ''}`} replace />
  }

  const passengerId = identity.id

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
    const trimmedDestination = destination.trim()
    if (!trimmedDestination) {
      setDestinationError('Destination is required.')
      return
    }

    setSubmitError(null)
    setIsSubmitting(true)
    try {
      const response = await request<SubmitOrderResponse>('/v1/orders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ passengerReference: passengerId }),
        baseUrl: ORDER_MANAGEMENT_BASE_URL,
      })
      setOrderId(response.orderId)
      setStep('confirmed')
    } catch {
      setSubmitError('Could not submit your ride request. Please try again.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className={styles.screen}>
      <Header />
      <main className={styles.content}>
        {step === 'loading' && <p className={styles.status}>Loading…</p>}

        {step === 'not-found' && <p className={styles.status}>This invitation could not be found.</p>}

        {step === 'form' && (
          <>
            <h1 className={styles.title}>Request a Ride</h1>

            <label className={styles.label} htmlFor="destination">
              Destination
            </label>
            <input
              id="destination"
              className={styles.input}
              type="text"
              value={destination}
              placeholder="Where are you going?"
              onChange={(event) => handleDestinationChange(event.target.value)}
            />
            {destinationError && (
              <p className={styles.error} role="alert">
                {destinationError}
              </p>
            )}

            <label className={styles.label} htmlFor="notes">
              Notes (optional)
            </label>
            <textarea
              id="notes"
              className={styles.textarea}
              value={notes}
              placeholder="Anything your driver should know?"
              onChange={(event) => setNotes(event.target.value)}
            />

            {submitError && (
              <p className={styles.error} role="alert">
                {submitError}
              </p>
            )}

            <div className={styles.actionRow}>
              <ActionButton
                label={isSubmitting ? 'Submitting…' : 'Request a Ride'}
                variant="primary"
                onClick={handleSubmit}
              />
            </div>
          </>
        )}

        {step === 'confirmed' && orderId && (
          <>
            <p className={styles.confirmed}>Ride request submitted.</p>
            <p className={styles.invitedBy}>Order ID:</p>
            <p className={styles.driverName}>{orderId}</p>
          </>
        )}
      </main>
    </div>
  )
}
