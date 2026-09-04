import { useEffect, useState } from 'react'
import { Header } from '../../components/Header'
import { DriverCard } from '../../components/DriverCard'
import { ActionButton } from '../../components/ActionButton'
import { Spinner } from '../../components/Spinner'
import { ApiError, request } from '../../api/apiClient'
import { DISPATCH_BASE_URL } from '../OwnerControlCenter/moduleBaseUrls'
import { LoginScreen } from '../OwnerControlCenter/LoginScreen'
import {
  clearOwnerCredential,
  getStoredOwnerCredential,
  toBasicAuthorizationHeader,
  type OwnerCredential,
} from '../OwnerControlCenter/ownerCredential'
import {
  driverLabel,
  fetchDrivers,
  fetchOrders,
  fetchProposalsForOrder,
  type DriverListItem,
  type OrderListItem,
  type ProposalListItem,
} from '../OwnerControlCenter/todayData'
import styles from './Coordinator.module.css'

type Status = 'loading' | 'error' | 'ready'
type AssignStatus = 'idle' | 'submitting' | 'error'
type CheckStatus = 'idle' | 'checking' | 'error'

interface ProposalResponse {
  proposalId: string
  orderId: string
  driverId: string
  status: string
}

/**
 * UX audit (pilot readiness): a coordinator should never have to read a
 * raw UUID -- mirrors `DriverHome.tsx`'s own `shortOrderCode` exactly,
 * replicated here rather than shared, per this codebase's own convention
 * for small per-screen presentation helpers (no shared code between
 * screens, `MODULE_STRUCTURE.md` Section 4's reasoning applied to
 * sibling frontend pages).
 */
function shortOrderCode(orderId: string): string {
  return orderId.replace(/-/g, '').slice(0, 8).toUpperCase()
}

/** Mirrors `DriverHome.tsx`'s own `formatRequestedPickupAt` (ADR-058) -- this device's own local time, no timezone concept in the contract. */
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
 * The most recently created proposal for an order, i.e. the one the
 * coordinator would act on next -- an order may carry more than one
 * `Proposal` over its lifetime (declined, then re-proposed to another
 * driver), and this screen shows the current one, not a history
 * (ADR-061 Decision 3).
 */
function currentProposal(proposals: ProposalListItem[] | undefined): ProposalListItem | null {
  if (!proposals || proposals.length === 0) {
    return null
  }
  return [...proposals].sort((a, b) => {
    const at = a.createdAt ? new Date(a.createdAt).getTime() : 0
    const bt = b.createdAt ? new Date(b.createdAt).getTime() : 0
    return bt - at
  })[0]
}

const ORDER_STATUS_LABEL: Record<string, string> = {
  SUBMITTED: 'Новый',
  COMPLETED: 'Завершён',
  CANCELLED: 'Отменён',
}

// Mirrors `DriverHome.tsx`'s own `PROPOSAL_STATUS_LABEL` wording, kept
// consistent across both screens rather than inventing a second vocabulary
// for the same underlying status. `WITHDRAWN` (P0-2, order cancellation)
// was missing here before ADR-061 since this screen never rendered a
// proposal's status inline.
const PROPOSAL_STATUS_LABEL: Record<string, string> = {
  OPEN: 'Ждёт ответа водителя',
  ACCEPTED: 'Принята',
  DECLINED: 'Отклонена',
  LAPSED: 'Не отвечено вовремя',
  WITHDRAWN: 'Отменено пассажиром',
}

/**
 * Coordinator — Sprint FR-002: Driver Availability; Sprint FR-003: Order
 * Query; Sprint FR-004: Manual Assignment; Sprint IMPLEMENTATION-005:
 * Driver Proposal MVP; ADR-060 (Order Query Authorization); ADR-061
 * (Coordinator Owner-Gated Access).
 *
 * ADR-060 Decision 1 made `GET /v1/orders` require a credential this screen
 * never held, breaking its order list (ADR-060 Decision 6, accepted at the
 * time). ADR-061 restores it: the screen is now gated behind the same owner
 * `Authorization: Basic` credential `OwnerControlCenter.tsx` already uses
 * (`ownerCredential.ts`, `LoginScreen` reused unmodified apart from its new
 * `subtitle` prop) -- there is exactly one owner (ADR-044 Decision 6), and
 * the coordinator and the owner are the same person in this pilot, so no
 * second credential is introduced. Because both screens read the same
 * `sessionStorage` key, a credential entered on one screen is already held
 * on the other, in the same tab session.
 *
 * Data loading now reuses `OwnerControlCenter/todayData.ts`'s exported
 * fetch functions (`fetchDrivers`, `fetchOrders`, `fetchProposalsForOrder`,
 * `driverLabel`) rather than re-implementing the `Authorization: Basic`
 * header logic a second time (ADR-061 Decision 2) -- this screen is not
 * folded into Owner Control Center itself, since it alone performs the
 * write action (`POST /v1/proposals`) that screen deliberately never
 * gained (ADR-043 Decision 5: permanently read-only).
 *
 * Order Query (Sprint FR-003) lists every order (`GET /v1/orders`, now
 * Mode 3 of ADR-060: owner credential, no parameter, full list) -- id,
 * status, passenger, route, and (ADR-058) requested pickup time. For each
 * order, ADR-061 additionally fetches its proposal history
 * (`GET /v1/proposals?orderId=`, left unauthenticated by ADR-060 Decision 4
 * as an enumeration sink) to show the proposed driver, its status, and --
 * once accepted -- the stated price and ETA (ADR-042, ADR-057). Showing
 * `statedPrice` here is a narrow, screen-scoped exception to ADR-043
 * Decision 6, made by explicit Product Owner ruling (ADR-061 Decision 3);
 * `buildReport.ts`'s own restriction against ever including it in the
 * owner's copyable report text is untouched.
 *
 * Manual Assignment (Sprint FR-004) and Driver Proposal MVP (Sprint
 * IMPLEMENTATION-005) are unchanged: the coordinator selects one order and
 * one driver, `POST /v1/proposals` proposes the driver, and the driver's
 * own acceptance (through `DriverHome`) is what creates the Assignment.
 * Neither this call nor "Check status" (`GET /v1/proposals/:id`) was ever
 * gated by ADR-060, and neither gains a credential here (ADR-061
 * Decision 4) -- only the page itself is gated, not these two actions.
 *
 * Deliberately minimal, per every sprint's own scope so far: no filtering,
 * no search, no sorting, no auto-refresh/WebSocket. It still does not let
 * the coordinator change any driver's own state or act on any order beyond
 * proposing it (no cancel, no complete).
 */
export function Coordinator() {
  const [credential, setCredential] = useState<OwnerCredential | null>(() => getStoredOwnerCredential())

  const [driversStatus, setDriversStatus] = useState<Status>('loading')
  const [drivers, setDrivers] = useState<DriverListItem[]>([])
  const [ordersStatus, setOrdersStatus] = useState<Status>('loading')
  const [orders, setOrders] = useState<OrderListItem[]>([])
  const [proposalsByOrder, setProposalsByOrder] = useState<Record<string, ProposalListItem[]>>({})

  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null)
  const [selectedDriverId, setSelectedDriverId] = useState<string | null>(null)
  const [assignStatus, setAssignStatus] = useState<AssignStatus>('idle')
  const [assignError, setAssignError] = useState<string | null>(null)
  const [lastProposal, setLastProposal] = useState<ProposalResponse | null>(null)
  const [checkStatus, setCheckStatus] = useState<CheckStatus>('idle')

  useEffect(() => {
    if (!credential) {
      return
    }
    let active = true
    setDriversStatus('loading')
    fetchDrivers()
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
  }, [credential])

  useEffect(() => {
    if (!credential) {
      return
    }
    const activeCredential = credential
    let active = true
    setOrdersStatus('loading')
    fetchOrders(activeCredential)
      .then(async (result) => {
        if (!active) {
          return
        }
        setOrders(result)
        setOrdersStatus('ready')

        const proposalLists = await Promise.all(
          result.map((order) => fetchProposalsForOrder(order.id).catch(() => [] as ProposalListItem[]))
        )
        if (!active) {
          return
        }
        const byOrder: Record<string, ProposalListItem[]> = {}
        result.forEach((order, index) => {
          byOrder[order.id] = proposalLists[index]
        })
        setProposalsByOrder(byOrder)
      })
      .catch(() => {
        if (active) {
          setOrdersStatus('error')
        }
      })
    return () => {
      active = false
    }
  }, [credential])

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

  async function refreshProposalsForOrder(orderId: string) {
    const result = await fetchProposalsForOrder(orderId).catch(() => null)
    if (result) {
      setProposalsByOrder((current) => ({ ...current, [orderId]: result }))
    }
  }

  // Task 21 (Proposal API Security Remediation): POST /v1/proposals now
  // requires an Authorization header -- the owner/coordinator credential
  // this screen already holds and already gates itself behind
  // (credential is guaranteed non-null here: the page renders LoginScreen
  // instead of this JSX entirely until one is held, same guarantee
  // fetchOrders(credential)/fetchDrivers already rely on) is exactly what
  // Dispatch's own new check accepts for this endpoint, mirroring
  // OwnerControlCenter/todayData.ts's own toBasicAuthorizationHeader use
  // for this screen's other owner-gated calls.
  async function handleAssign() {
    if (assignStatus === 'submitting' || !selectedOrderId || !selectedDriverId || !credential) {
      return
    }
    setAssignStatus('submitting')
    setAssignError(null)
    try {
      const response = await request<ProposalResponse>('/v1/proposals', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: toBasicAuthorizationHeader(credential) },
        body: JSON.stringify({ orderId: selectedOrderId, driverId: selectedDriverId }),
        baseUrl: DISPATCH_BASE_URL,
      })
      setLastProposal(response)
      setCheckStatus('idle')
      void refreshProposalsForOrder(response.orderId)
      setSelectedOrderId(null)
      setSelectedDriverId(null)
      setAssignStatus('idle')
    } catch (error) {
      setAssignError(
        error instanceof ApiError && error.status === 409
          ? 'Не удалось отправить предложение. Возможно, у заказа уже есть активное предложение, или водитель больше не на линии.'
          : 'Не удалось отправить предложение. Попробуйте ещё раз.'
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
      void refreshProposalsForOrder(response.orderId)
      setCheckStatus('idle')
    } catch {
      setCheckStatus('error')
    }
  }

  function handleLogout() {
    clearOwnerCredential()
    setCredential(null)
    setDrivers([])
    setOrders([])
    setProposalsByOrder({})
    setSelectedOrderId(null)
    setSelectedDriverId(null)
    setLastProposal(null)
  }

  if (!credential) {
    return <LoginScreen subtitle="Координатор" onLoggedIn={() => setCredential(getStoredOwnerCredential())} />
  }

  return (
    <div className={styles.screen}>
      <Header />
      <main className={styles.content}>
        <div className={styles.headerRow}>
          <h1 className={styles.title}>Заказы</h1>
          <ActionButton label="Выйти" variant="secondary" onClick={handleLogout} />
        </div>

        {ordersStatus === 'loading' && <Spinner label="Загрузка…" />}
        {ordersStatus === 'error' && <p className={styles.status}>Не удалось загрузить заказы.</p>}
        {ordersStatus === 'ready' && orders.length === 0 && <p className={styles.status}>Заказов пока нет.</p>}
        {ordersStatus === 'ready' && orders.length > 0 && (
          <p className={styles.hint}>Выберите новый заказ и свободного водителя, затем нажмите «Отправить».</p>
        )}
        {ordersStatus === 'ready' &&
          orders.map((order) => {
            // UX audit (pilot readiness): Dispatch's own `POST /v1/proposals`
            // has no knowledge of Order Management's order status at all (it
            // only checks driver availability and an order's existing
            // proposals) -- it would silently create a proposal for an
            // already-COMPLETED or CANCELLED order if asked to. Only a
            // 'SUBMITTED' order is ever a real choice here; the other two are
            // dimmed and non-clickable, same convention as an UNAVAILABLE
            // driver's own card just below.
            const selectable = order.status === 'SUBMITTED'
            const proposal = currentProposal(proposalsByOrder[order.id])
            const requestedPickupAtLabel = formatRequestedPickupAt(order.requestedPickupAt)
            return (
              <section
                key={order.id}
                className={`${styles.orderRow} ${selectable ? styles.clickableRow : styles.orderRowDisabled} ${
                  selectedOrderId === order.id ? styles.selectedRow : ''
                }`}
                onClick={selectable ? () => selectOrder(order.id) : undefined}
                onKeyDown={
                  selectable
                    ? (event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault()
                          selectOrder(order.id)
                        }
                      }
                    : undefined
                }
                role={selectable ? 'button' : undefined}
                tabIndex={selectable ? 0 : undefined}
              >
                <div className={styles.orderMain}>
                  <span className={styles.orderId}>Заказ №{shortOrderCode(order.id)}</span>
                  <span className={styles.orderStatus}>{ORDER_STATUS_LABEL[order.status] ?? order.status}</span>
                </div>
                {(order.passengerName || order.pickupAddress || order.destination) && (
                  <div className={styles.orderDetails}>
                    {order.passengerName && <span>Пассажир: {order.passengerName}</span>}
                    {order.pickupAddress && <span>Откуда: {order.pickupAddress}</span>}
                    {order.destination && <span>Куда: {order.destination}</span>}
                    {/* ADR-058 (Scheduled Pickup Time), surfaced here for the
                        first time by ADR-061: a coordinator proposing a
                        driver "right now" needs to know this order is a
                        pre-booking for later. */}
                    {requestedPickupAtLabel && <span>📅 Предварительный заказ: {requestedPickupAtLabel}</span>}
                    {/* ADR-061 Decision 3: driver/status/price/ETA for this
                        order's current proposal, never shown on this screen
                        before. */}
                    {proposal && <span>Водитель: {driverLabel(proposal.driverId, drivers)}</span>}
                    {proposal && (
                      <span>Статус предложения: {PROPOSAL_STATUS_LABEL[proposal.status] ?? proposal.status}</span>
                    )}
                    {proposal?.status === 'ACCEPTED' && proposal.statedPrice && (
                      <span>Стоимость: {proposal.statedPrice}</span>
                    )}
                    {proposal?.status === 'ACCEPTED' && typeof proposal.statedEtaMinutes === 'number' && (
                      <span>ETA: {proposal.statedEtaMinutes} мин</span>
                    )}
                  </div>
                )}
              </section>
            )
          })}

        <h1 className={styles.title}>Водители</h1>

        {driversStatus === 'loading' && <Spinner label="Загрузка…" />}
        {driversStatus === 'error' && <p className={styles.status}>Не удалось загрузить водителей.</p>}
        {driversStatus === 'ready' && drivers.length === 0 && <p className={styles.status}>Пока нет водителей.</p>}
        {driversStatus === 'ready' &&
          drivers.map((driver) => (
            <div key={driver.id} className={driver.availability === 'UNAVAILABLE' ? styles.driverUnavailable : undefined}>
              <DriverCard
                driverCode={driver.id}
                displayName={driver.displayName}
                availability={driver.availability}
                onClick={driver.availability === 'AVAILABLE' ? () => selectDriver(driver.id) : undefined}
                selected={selectedDriverId === driver.id}
              />
            </div>
          ))}

        <div className={styles.actionRow}>
          <ActionButton
            label={assignStatus === 'submitting' ? 'Отправляем…' : 'Отправить'}
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
              Предложение отправлено водителю. Статус:{' '}
              {PROPOSAL_STATUS_LABEL[lastProposal.status] ?? lastProposal.status}
            </p>
            <ActionButton
              label={checkStatus === 'checking' ? 'Проверяем…' : 'Проверить'}
              variant="secondary"
              onClick={handleCheckStatus}
              disabled={checkStatus === 'checking'}
            />
            {checkStatus === 'error' && (
              <p className={styles.error} role="alert">
                Не удалось обновить статус.
              </p>
            )}
          </div>
        )}
      </main>
    </div>
  )
}
