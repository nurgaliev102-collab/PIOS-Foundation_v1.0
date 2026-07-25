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

// Dispatch's own local port (INTERFACE_CONTRACTS.md) — Sprint 7B (Personal
// Network Flow MVP): once the order exists, this page proposes it directly
// to the driver whose link the passenger arrived through, reusing
// Dispatch's already-existing `POST /v1/proposals` exactly as
// `Coordinator.tsx` already does for the general queue -- no Coordinator
// step for this, invited-passenger path.
const DISPATCH_BASE_URL = import.meta.env.VITE_DISPATCH_BASE_URL ?? 'http://localhost:8084'

type Step = 'loading' | 'not-found' | 'form' | 'confirmed'
type ProposalStatus = 'proposing' | 'proposed' | 'error'

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
 * Driver Home's own direct-module-call precedent (Sprint 5).
 *
 * Sprint 3B (MVR Pilot Enablement — Optional Destination): the real
 * backend contract now also accepts an optional `destination` (see
 * `backend/order-management/.../api/SubmitOrderRequest.kt`), sent here as
 * plain text — no geocoding, no coordinates. `notes` is still collected
 * by this form but still not sent anywhere; that remains a known,
 * disclosed limitation (`frontend/README.md`), not silently hidden.
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
  const [proposalStatus, setProposalStatus] = useState<ProposalStatus | null>(null)

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
        body: JSON.stringify({ passengerReference: passengerId, destination: trimmedDestination }),
        baseUrl: ORDER_MANAGEMENT_BASE_URL,
      })
      setOrderId(response.orderId)
      setStep('confirmed')
      void attemptProposal(response.orderId)
    } catch {
      setSubmitError('Could not submit your ride request. Please try again.')
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

            {proposalStatus === 'proposing' && <p className={styles.status}>Notifying your driver…</p>}
            {proposalStatus === 'proposed' && <p className={styles.status}>Your driver has been notified.</p>}
            {proposalStatus === 'error' && (
              <>
                <p className={styles.error} role="alert">
                  Could not notify your driver. Your ride request is still saved.
                </p>
                <div className={styles.actionRow}>
                  <ActionButton label="Retry" variant="secondary" onClick={handleRetryProposal} />
                </div>
              </>
            )}
          </>
        )}
      </main>
    </div>
  )
}
