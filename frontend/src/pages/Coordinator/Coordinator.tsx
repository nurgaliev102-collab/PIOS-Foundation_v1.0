import { useEffect, useState } from 'react'
import { Header } from '../../components/Header'
import { DriverCard } from '../../components/DriverCard'
import { ActionButton } from '../../components/ActionButton'
import { ApiError, request } from '../../api/apiClient'
import styles from './Coordinator.module.css'

// Order Management's own local port (INTERFACE_CONTRACTS.md) — same
// constant as `RideRequest.tsx` (Sprint FR-001), since this page now also
// calls that module directly, in addition to Driver Management.
const ORDER_MANAGEMENT_BASE_URL = import.meta.env.VITE_ORDER_MANAGEMENT_BASE_URL ?? 'http://localhost:8083'

// Dispatch's own local port (INTERFACE_CONTRACTS.md) — Sprint FR-004
// (Manual Assignment): this page now also calls Dispatch directly, a
// third backend module alongside Driver Management and Order Management.
const DISPATCH_BASE_URL = import.meta.env.VITE_DISPATCH_BASE_URL ?? 'http://localhost:8084'

type Status = 'loading' | 'error' | 'ready'
type AssignStatus = 'idle' | 'submitting' | 'error'
type CheckStatus = 'idle' | 'checking' | 'error'

interface DriverListItem {
  id: string
  availability: 'AVAILABLE' | 'UNAVAILABLE'
}

interface OrderListItem {
  id: string
  status: string
  origin: string
}

interface ProposalResponse {
  proposalId: string
  orderId: string
  driverId: string
  status: string
}

/**
 * Coordinator — Sprint FR-002: Driver Availability; Sprint FR-003: Order
 * Query; Sprint FR-004: Manual Assignment; Sprint IMPLEMENTATION-005:
 * Driver Proposal MVP.
 *
 * The coordinator role already exists operationally
 * (`docs/NETWORK_PILOT_LAUNCH_KIT_V1.md`, Part 3) — this is its first
 * screen backed by real software: a plain list of every driver and their
 * current, self-declared availability (`GET /v1/drivers`), reusing
 * `DriverCard` exactly as `DriverHome` already does for a single driver.
 *
 * Sprint FR-003 (Order Query) added a second, independent section listing
 * every existing order (`GET /v1/orders`, Order Management) — id, status,
 * and origin, the only fields `OrderQueryController`'s own response
 * carries. The two sections load independently (separate `status` state
 * each) — a failure loading orders does not block the driver list, or
 * vice versa.
 *
 * Sprint FR-004 (Manual Assignment) made both sections selectable: the
 * coordinator clicks one order row and one `DriverCard`, then an action
 * button. That originally called Dispatch's own `POST /v1/assignments`
 * directly.
 *
 * Sprint IMPLEMENTATION-005 (Driver Proposal MVP) replaces that direct
 * call with `POST /v1/proposals` (`ProposalController`, Dispatch): the
 * coordinator no longer assigns a driver directly — it proposes one, and
 * the driver's own acceptance (now possible through `DriverHome`'s own
 * new Proposals section) is what creates the Assignment, automatically,
 * through the already-existing Proposal → Assignment orchestration
 * (Sprint IMPLEMENTATION-004). `POST /v1/assignments` still exists
 * (`AssignmentController`, deprecated but not removed) for manual
 * override; this page simply no longer calls it. "Check status" lets the
 * coordinator re-fetch the last proposal (`GET /v1/proposals/:id`) to see
 * whether the driver has responded yet — this page has no live push or
 * polling, so seeing the eventual Assignment happen still requires this
 * one manual action, deliberately kept this simple for the first pilot.
 * No selection criteria are applied by this page — every order and every
 * driver is selectable regardless of status or availability, exactly as
 * Dispatch's own `ProposalController` performs no such check either.
 *
 * Deliberately minimal, per every sprint's own scope so far: no
 * filtering, no search, no sorting, no auto-refresh/WebSocket, and no
 * authentication — this page is reachable by anyone who navigates to it,
 * same as every other page in this project today. It still does not let
 * the coordinator change any driver's availability or act on any order
 * beyond proposing it (no cancel, no complete).
 */
export function Coordinator() {
  const [driversStatus, setDriversStatus] = useState<Status>('loading')
  const [drivers, setDrivers] = useState<DriverListItem[]>([])
  const [ordersStatus, setOrdersStatus] = useState<Status>('loading')
  const [orders, setOrders] = useState<OrderListItem[]>([])

  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null)
  const [selectedDriverId, setSelectedDriverId] = useState<string | null>(null)
  const [assignStatus, setAssignStatus] = useState<AssignStatus>('idle')
  const [assignError, setAssignError] = useState<string | null>(null)
  const [lastProposal, setLastProposal] = useState<ProposalResponse | null>(null)
  const [checkStatus, setCheckStatus] = useState<CheckStatus>('idle')

  useEffect(() => {
    let active = true
    setDriversStatus('loading')
    request<DriverListItem[]>('/v1/drivers')
      .then((result) => {
        if (!active) {
          return
        }
        setDrivers(result)
        setDriversStatus('ready')
      })
      .catch(() => {
        if (active) {
          setDriversStatus('error')
        }
      })
    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    let active = true
    setOrdersStatus('loading')
    request<OrderListItem[]>('/v1/orders', { baseUrl: ORDER_MANAGEMENT_BASE_URL })
      .then((result) => {
        if (!active) {
          return
        }
        setOrders(result)
        setOrdersStatus('ready')
      })
      .catch(() => {
        if (active) {
          setOrdersStatus('error')
        }
      })
    return () => {
      active = false
    }
  }, [])

  function selectOrder(orderId: string) {
    setLastProposal(null)
    setAssignError(null)
    setSelectedOrderId((current) => (current === orderId ? null : orderId))
  }

  function selectDriver(driverId: string) {
    setLastProposal(null)
    setAssignError(null)
    setSelectedDriverId((current) => (current === driverId ? null : driverId))
  }

  async function handleAssign() {
    if (assignStatus === 'submitting' || !selectedOrderId || !selectedDriverId) {
      return
    }
    setAssignStatus('submitting')
    setAssignError(null)
    try {
      const response = await request<ProposalResponse>('/v1/proposals', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ orderId: selectedOrderId, driverId: selectedDriverId }),
        baseUrl: DISPATCH_BASE_URL,
      })
      setLastProposal(response)
      setCheckStatus('idle')
      setSelectedOrderId(null)
      setSelectedDriverId(null)
      setAssignStatus('idle')
    } catch (error) {
      setAssignError(
        error instanceof ApiError && error.status === 409
          ? 'This order already has an open proposal.'
          : 'Could not create proposal. Please try again.'
      )
      setAssignStatus('error')
    }
  }

  async function handleCheckStatus() {
    if (!lastProposal || checkStatus === 'checking') {
      return
    }
    setCheckStatus('checking')
    try {
      const response = await request<ProposalResponse>(`/v1/proposals/${lastProposal.proposalId}`, {
        baseUrl: DISPATCH_BASE_URL,
      })
      setLastProposal(response)
      setCheckStatus('idle')
    } catch {
      setCheckStatus('error')
    }
  }

  return (
    <div className={styles.screen}>
      <Header />
      <main className={styles.content}>
        <h1 className={styles.title}>Orders</h1>

        {ordersStatus === 'loading' && <p className={styles.status}>Loading…</p>}
        {ordersStatus === 'error' && <p className={styles.status}>Could not load orders.</p>}
        {ordersStatus === 'ready' && orders.length === 0 && <p className={styles.status}>No orders yet.</p>}
        {ordersStatus === 'ready' &&
          orders.map((order) => (
            <section
              key={order.id}
              className={`${styles.orderRow} ${styles.clickableRow} ${
                selectedOrderId === order.id ? styles.selectedRow : ''
              }`}
              onClick={() => selectOrder(order.id)}
              role="button"
              tabIndex={0}
            >
              <span className={styles.orderId}>{order.id}</span>
              <span className={styles.orderStatus}>{order.status}</span>
              <span className={styles.orderOrigin}>{order.origin}</span>
            </section>
          ))}

        <h1 className={styles.title}>Drivers</h1>

        {driversStatus === 'loading' && <p className={styles.status}>Loading…</p>}
        {driversStatus === 'error' && <p className={styles.status}>Could not load drivers.</p>}
        {driversStatus === 'ready' && drivers.length === 0 && <p className={styles.status}>No drivers yet.</p>}
        {driversStatus === 'ready' &&
          drivers.map((driver) => (
            <DriverCard
              key={driver.id}
              driverCode={driver.id}
              availability={driver.availability}
              onClick={() => selectDriver(driver.id)}
              selected={selectedDriverId === driver.id}
            />
          ))}

        <div className={styles.actionRow}>
          <ActionButton
            label={assignStatus === 'submitting' ? 'Proposing…' : 'Propose'}
            variant="primary"
            onClick={handleAssign}
            disabled={!selectedOrderId || !selectedDriverId || assignStatus === 'submitting'}
          />
        </div>

        {assignError && (
          <p className={styles.error} role="alert">
            {assignError}
          </p>
        )}

        {lastProposal && (
          <div className={styles.proposalResult}>
            <p className={styles.status}>
              Proposal created: {lastProposal.proposalId} ({lastProposal.status})
            </p>
            <ActionButton
              label={checkStatus === 'checking' ? 'Checking…' : 'Check status'}
              variant="secondary"
              onClick={handleCheckStatus}
              disabled={checkStatus === 'checking'}
            />
            {checkStatus === 'error' && (
              <p className={styles.error} role="alert">
                Could not refresh status.
              </p>
            )}
          </div>
        )}
      </main>
    </div>
  )
}
