import { useEffect, useRef, useState } from 'react'
import { Header } from '../../components/Header'
import { DriverCard } from '../../components/DriverCard'
import { QRCard } from '../../components/QRCard'
import { ActionButton } from '../../components/ActionButton'
import { ApiError, request } from '../../api/apiClient'
import { CURRENT_DRIVER_ID, invitationLinkFor } from './currentDriver'
import styles from './DriverHome.module.css'

const FEEDBACK_DURATION_MS = 2000

// Dispatch's own local port (INTERFACE_CONTRACTS.md) — same constant as
// `Coordinator.tsx` (Sprint FR-004): this page now also calls Dispatch
// directly, in addition to Driver Management (Sprint IMPLEMENTATION-005,
// Driver Proposal MVP).
const DISPATCH_BASE_URL = import.meta.env.VITE_DISPATCH_BASE_URL ?? 'http://localhost:8084'

type Status = 'loading' | 'error' | 'ready'
type ProposalActionStatus = 'idle' | 'submitting' | 'error'

interface DriverInfo {
  id: string
  availability: 'AVAILABLE' | 'UNAVAILABLE'
}

interface ProposalListItem {
  proposalId: string
  orderId: string
  driverId: string
  status: 'OPEN' | 'ACCEPTED' | 'DECLINED' | 'LAPSED'
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
 * instead of `mockDriver.ts`, which is removed. `CURRENT_DRIVER_ID`
 * (`currentDriver.ts`) is still a fixed local constant, since no driver
 * authentication exists yet — only the driver's own data is now real.
 * Invitation, passenger, and ride-request logic remain entirely mocked,
 * unchanged.
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
 * sections already established. `CURRENT_DRIVER_ID` is reused as-is for
 * "which driver's proposals to list" — the same placeholder already
 * standing in for real driver authentication everywhere else on this
 * page.
 */
export function DriverHome() {
  const [status, setStatus] = useState<Status>('loading')
  const [driver, setDriver] = useState<DriverInfo | null>(null)
  const [feedback, setFeedback] = useState<string | null>(null)
  const feedbackTimeout = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  const [proposalsStatus, setProposalsStatus] = useState<Status>('loading')
  const [proposals, setProposals] = useState<ProposalListItem[]>([])
  const [proposalActions, setProposalActions] = useState<Record<string, ProposalActionStatus>>({})

  useEffect(() => {
    let active = true
    setStatus('loading')
    request<DriverInfo>(`/v1/drivers/${CURRENT_DRIVER_ID}`)
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
    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    let active = true
    loadProposals(active)
    return () => {
      active = false
    }
  }, [])

  function loadProposals(active: boolean) {
    setProposalsStatus('loading')
    request<ProposalListItem[]>(`/v1/proposals?driverId=${CURRENT_DRIVER_ID}`, { baseUrl: DISPATCH_BASE_URL })
      .then((result) => {
        if (!active) {
          return
        }
        setProposals(result)
        setProposalsStatus('ready')
      })
      .catch(() => {
        if (active) {
          setProposalsStatus('error')
        }
      })
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
      if (error instanceof ApiError && (error.status === 404 || error.status === 409)) {
        loadProposals(true)
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
      await navigator.clipboard.writeText(invitationLinkFor(driver.id))
      showFeedback('Copied')
    } catch {
      showFeedback('Could not copy')
    }
  }

  async function handleShare() {
    if (!driver) {
      return
    }
    const invitationLink = invitationLinkFor(driver.id)

    if (navigator.share) {
      try {
        await navigator.share({
          title: 'PIOS',
          text: 'You have been invited to PIOS',
          url: invitationLink,
        })
      } catch (error) {
        // A user-cancelled share (AbortError) is not a failure — no feedback needed.
        if (error instanceof Error && error.name !== 'AbortError') {
          showFeedback('Could not share')
        }
      }
      return
    }

    // Fallback for browsers without the Web Share API: copy instead.
    try {
      await navigator.clipboard.writeText(invitationLink)
      showFeedback('Link copied')
    } catch {
      showFeedback('Could not share')
    }
  }

  return (
    <div className={styles.screen}>
      <Header />
      <main className={styles.content}>
        {status === 'loading' && <p className={styles.status}>Loading driver…</p>}

        {status === 'error' && <p className={styles.status}>Could not load driver information.</p>}

        {status === 'ready' && driver && (
          <>
            <DriverCard driverCode={driver.id} availability={driver.availability} />
            <QRCard
              invitationLink={invitationLinkFor(driver.id)}
              linkTo={`/i/${driver.id}`}
              onCopy={handleCopy}
              onShare={handleShare}
              feedback={feedback}
            />
          </>
        )}

        <h2 className={styles.sectionTitle}>Proposals</h2>

        {proposalsStatus === 'loading' && <p className={styles.status}>Loading proposals…</p>}
        {proposalsStatus === 'error' && <p className={styles.status}>Could not load proposals.</p>}
        {proposalsStatus === 'ready' && proposals.length === 0 && (
          <p className={styles.status}>No proposals right now.</p>
        )}

        {proposalsStatus === 'ready' &&
          proposals.map((proposal) => (
            <section key={proposal.proposalId} className={styles.proposalRow}>
              <div className={styles.proposalDetails}>
                <span className={styles.proposalOrderId}>{proposal.orderId}</span>
                <span
                  className={`${styles.proposalStatus} ${
                    proposal.status === 'OPEN'
                      ? styles.proposalOpen
                      : proposal.status === 'ACCEPTED'
                        ? styles.proposalAccepted
                        : styles.proposalResolved
                  }`}
                >
                  {proposal.status}
                </span>
              </div>

              {proposal.status === 'OPEN' && (
                <div className={styles.proposalActions}>
                  <ActionButton
                    label={proposalActions[proposal.proposalId] === 'submitting' ? 'Accepting…' : 'Accept'}
                    variant="primary"
                    onClick={() => respondToProposal(proposal.proposalId, 'accept')}
                    disabled={proposalActions[proposal.proposalId] === 'submitting'}
                  />
                  <ActionButton
                    label={proposalActions[proposal.proposalId] === 'submitting' ? 'Declining…' : 'Decline'}
                    variant="secondary"
                    onClick={() => respondToProposal(proposal.proposalId, 'decline')}
                    disabled={proposalActions[proposal.proposalId] === 'submitting'}
                  />
                </div>
              )}

              {proposalActions[proposal.proposalId] === 'error' && (
                <p className={styles.error} role="alert">
                  Could not update this proposal. Please try again.
                </p>
              )}
            </section>
          ))}
      </main>
    </div>
  )
}
