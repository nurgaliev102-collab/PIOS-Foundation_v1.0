import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Header } from '../../components/Header'
import { ActionButton } from '../../components/ActionButton'
import { getInvitationByDriverCode } from './invitationSource'
import type { InvitationInfo } from './invitationSource'
import { getPassengerIdentity, savePassengerIdentity } from '../../persistence/localPassengerIdentity'
import type { PassengerIdentity } from '../../persistence/localPassengerIdentity'
import styles from './PassengerLanding.module.css'

const MAX_NAME_LENGTH = 50

type Step = 'loading' | 'not-found' | 'invited' | 'onboarding' | 'greeting'

/**
 * Passenger Landing — Sprint 2: the first passenger-facing screen of
 * PIOS, rendered at `/i/:driverCode`.
 *
 * Sprint 3 — First Real Identity: this page is now a small local state
 * machine (`invited` → `onboarding` → `greeting`) backed entirely by
 * `persistence/localPassengerIdentity.ts` — no `localStorage` call
 * happens in this file directly. If a local identity already exists
 * when the invitation resolves, onboarding is skipped and the passenger
 * goes straight to the greeting screen (session restore). Still no
 * backend, authentication, or invitation persistence exists here.
 *
 * Sprint 4 — Request Your Driver: the greeting screen now offers
 * "Request a Ride", navigating to `/i/:driverCode/request` (RideRequest).
 */
export function PassengerLanding() {
  const { driverCode } = useParams<{ driverCode: string }>()
  const navigate = useNavigate()
  const [step, setStep] = useState<Step>('loading')
  const [invitation, setInvitation] = useState<InvitationInfo | null>(null)
  const [identity, setIdentity] = useState<PassengerIdentity | null>(null)
  const [name, setName] = useState('')
  const [nameError, setNameError] = useState<string | null>(null)

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
      setInvitation(result)
      const existingIdentity = getPassengerIdentity()
      if (existingIdentity) {
        setIdentity(existingIdentity)
        setStep('greeting')
      } else {
        setStep('invited')
      }
    })
    return () => {
      active = false
    }
  }, [driverCode])

  function handleContinue() {
    setStep('onboarding')
  }

  function handleNameChange(value: string) {
    setName(value)
    if (nameError) {
      setNameError(null)
    }
  }

  function handleNameSubmit() {
    const trimmed = name.trim()
    if (!trimmed) {
      setNameError('Please enter a name.')
      return
    }
    if (trimmed.length > MAX_NAME_LENGTH) {
      setNameError(`Name must be ${MAX_NAME_LENGTH} characters or fewer.`)
      return
    }
    const saved = savePassengerIdentity(trimmed)
    setIdentity(saved)
    setStep('greeting')
  }

  function handleRequestRide() {
    navigate(`/i/${driverCode ?? ''}/request`)
  }

  return (
    <div className={styles.screen}>
      <Header />
      <main className={styles.content}>
        {step === 'loading' && <p className={styles.status}>Loading…</p>}

        {step === 'not-found' && <p className={styles.status}>This invitation could not be found.</p>}

        {step === 'invited' && invitation && (
          <>
            <p className={styles.invitedBy}>You were invited by</p>
            <h1 className={styles.driverName}>{invitation.driverName}</h1>
            <p className={styles.driverType}>{invitation.driverType}</p>
            <div className={styles.actionRow}>
              <ActionButton label="Continue" variant="primary" onClick={handleContinue} />
            </div>
          </>
        )}

        {step === 'onboarding' && (
          <>
            <h1 className={styles.question}>How should we address you?</h1>
            <input
              className={styles.input}
              type="text"
              value={name}
              maxLength={MAX_NAME_LENGTH}
              placeholder="Your name"
              aria-label="Your name"
              onChange={(event) => handleNameChange(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  handleNameSubmit()
                }
              }}
            />
            {nameError && (
              <p className={styles.error} role="alert">
                {nameError}
              </p>
            )}
            <div className={styles.actionRow}>
              <ActionButton label="Continue" variant="primary" onClick={handleNameSubmit} />
            </div>
          </>
        )}

        {step === 'greeting' && identity && (
          <>
            <h1 className={styles.greeting}>Hello, {identity.name}</h1>
            <p className={styles.invitedBy}>Joined through:</p>
            <p className={styles.driverName}>{invitation?.driverName}</p>
            <div className={styles.actionRow}>
              <ActionButton label="Request a Ride" variant="primary" onClick={handleRequestRide} />
            </div>
          </>
        )}
      </main>
    </div>
  )
}
