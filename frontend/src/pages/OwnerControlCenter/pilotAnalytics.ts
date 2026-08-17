import type { ModuleHealth } from './healthPoll'
import type { OwnerCredential } from './ownerCredential'
import {
  fetchAssignmentsForOrder,
  fetchDrivers,
  fetchOrders,
  fetchProposalsForDriver,
  type AssignmentListItem,
  type DriverListItem,
  type OrderListItem,
  type ProposalListItem,
} from './todayData'

/**
 * AI Analyst's own read of the platform (docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md,
 * "первый технический этап" — 2026-08-17). Deliberately calls the exact
 * same `GET` endpoints `todayData.ts`'s own `loadTodaySnapshot` already
 * calls (`fetchDrivers`/`fetchOrders`/`fetchProposalsForDriver`/
 * `fetchAssignmentsForOrder`, all already exported for `Coordinator.tsx`
 * per ADR-061 Decision 2) — no new backend endpoint, no new contract.
 *
 * This module does **not** import `loadTodaySnapshot` or its internal
 * `mapWithConcurrency`/fan-out constants, and runs its own fan-out on the
 * same bounded-concurrency shape instead (duplicated, not reused) — a
 * deliberate choice, not an oversight: `todayData.ts`'s own fan-out is the
 * exact code path the 2026-08-17 overlapping-poll incident (see
 * `OwnerControlCenter.tsx`'s own KDoc) was found and fixed in. AI Analyst
 * is triggered once, on demand, by an explicit owner click — never by the
 * 15s poll — and keeping its own data collection fully separate means a
 * future change to either path can never reintroduce that incident's
 * failure mode in the other.
 *
 * `docs/ADR/ADR-056-Control-Center-AI-Advisor.md` describes the eventual
 * real AI advisor (a dedicated `ai-advisor` backend, GigaChat) — that ADR
 * is still **Proposed**, not Accepted. This module is not an implementation
 * of it: it makes no network call beyond PIOS's own already-public `GET`
 * endpoints, holds no provider credential, and never leaves the browser
 * except to reach PIOS itself. `PilotAnalyticsInput` below is shaped to be
 * a reasonable *future* prompt-context source (Section 5 of that Product
 * Decision), but nothing here sends it anywhere.
 *
 * PII/secret minimization (this task's own explicit requirement): this
 * input carries counts, rates, and durations only — never a password, a
 * password hash/salt, an API key, a session token, or a passenger/driver
 * name or address. `ProposalListItem.statedPrice` (money) is read from the
 * same backend response every other fetch here already receives, but is
 * never copied into this module's output, mirroring `todayData.ts`'s own
 * `TodayEvent`/`TodayCounters` never doing so (ADR-043 Decision 6; ADR-042
 * R4.3) — the AI Analyst inherits that same boundary, not a new one.
 */

const FAN_OUT_CONCURRENCY_LIMIT = 5

async function mapWithConcurrency<T, R>(items: T[], limit: number, fn: (item: T) => Promise<R>): Promise<R[]> {
  const results: R[] = new Array(items.length)
  let nextIndex = 0
  async function worker() {
    while (nextIndex < items.length) {
      const index = nextIndex++
      results[index] = await fn(items[index])
    }
  }
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker))
  return results
}

export interface PilotOrderMetrics {
  total: number
  completed: number
  cancelled: number
  open: number
}

export interface PilotProposalMetrics {
  total: number
  accepted: number
  declined: number
  lapsed: number
  withdrawn: number
  open: number
}

export interface PilotAssignmentMetrics {
  total: number
  completed: number
  inProgress: number
}

export interface PilotDriverMetrics {
  total: number
  available: number
  withActivity: number
}

/**
 * Driver reaction time — `respondedAt - createdAt` for every proposal that
 * actually resolved (`ACCEPTED`/`DECLINED`; both timestamps are real,
 * backend-set values already carried on `ProposalListItem`, ADR-043). Never
 * estimated or interpolated: a proposal missing either timestamp is simply
 * excluded from the sample, and `sampleSize: 0` means exactly what it
 * says — this task's own explicit "не придумывать значения" rule.
 */
export interface PilotReactionTimeMetrics {
  averageMinutes: number | null
  medianMinutes: number | null
  sampleSize: number
}

export interface PilotHealthMetrics {
  modulesUp: number
  modulesTotal: number
}

export interface PilotAnalyticsInput {
  generatedAt: string
  /** Honest description of what was actually read, not a fabricated date range — see this module's own KDoc. */
  periodLabel: string
  orders: PilotOrderMetrics
  proposals: PilotProposalMetrics
  assignments: PilotAssignmentMetrics
  drivers: PilotDriverMetrics
  reactionTime: PilotReactionTimeMetrics
  health: PilotHealthMetrics
}

function median(values: number[]): number | null {
  if (values.length === 0) {
    return null
  }
  const sorted = [...values].sort((a, b) => a - b)
  const mid = Math.floor(sorted.length / 2)
  return sorted.length % 2 === 0 ? (sorted[mid - 1] + sorted[mid]) / 2 : sorted[mid]
}

function computeOrderMetrics(orders: OrderListItem[]): PilotOrderMetrics {
  return {
    total: orders.length,
    completed: orders.filter((order) => order.status === 'COMPLETED').length,
    cancelled: orders.filter((order) => order.status === 'CANCELLED').length,
    open: orders.filter((order) => order.status === 'SUBMITTED').length,
  }
}

function computeProposalMetrics(proposals: ProposalListItem[]): PilotProposalMetrics {
  return {
    total: proposals.length,
    accepted: proposals.filter((p) => p.status === 'ACCEPTED').length,
    declined: proposals.filter((p) => p.status === 'DECLINED').length,
    lapsed: proposals.filter((p) => p.status === 'LAPSED').length,
    withdrawn: proposals.filter((p) => p.status === 'WITHDRAWN').length,
    open: proposals.filter((p) => p.status === 'OPEN').length,
  }
}

function computeAssignmentMetrics(assignments: AssignmentListItem[]): PilotAssignmentMetrics {
  return {
    total: assignments.length,
    completed: assignments.filter((a) => a.status === 'COMPLETED').length,
    inProgress: assignments.filter((a) => a.status === 'ARRIVED' || a.status === 'IN_PROGRESS').length,
  }
}

function computeDriverMetrics(drivers: DriverListItem[], proposals: ProposalListItem[]): PilotDriverMetrics {
  const driversWithActivity = new Set(proposals.map((p) => p.driverId))
  return {
    total: drivers.length,
    available: drivers.filter((d) => d.availability === 'AVAILABLE').length,
    withActivity: drivers.filter((d) => driversWithActivity.has(d.id)).length,
  }
}

function computeReactionTime(proposals: ProposalListItem[]): PilotReactionTimeMetrics {
  const minutes: number[] = []
  for (const proposal of proposals) {
    if (proposal.status !== 'ACCEPTED' && proposal.status !== 'DECLINED') {
      continue
    }
    if (!proposal.createdAt || !proposal.respondedAt) {
      continue
    }
    const created = new Date(proposal.createdAt).getTime()
    const responded = new Date(proposal.respondedAt).getTime()
    if (Number.isNaN(created) || Number.isNaN(responded) || responded < created) {
      continue
    }
    minutes.push((responded - created) / 60_000)
  }
  if (minutes.length === 0) {
    return { averageMinutes: null, medianMinutes: null, sampleSize: 0 }
  }
  const average = minutes.reduce((sum, value) => sum + value, 0) / minutes.length
  return { averageMinutes: average, medianMinutes: median(minutes), sampleSize: minutes.length }
}

function computeHealthMetrics(healths: ModuleHealth[]): PilotHealthMetrics {
  return {
    modulesUp: healths.filter((h) => h.outcome === 'up').length,
    modulesTotal: healths.length,
  }
}

/** Acceptance rate is computed only over *resolved* proposals (accepted/declined/lapsed) — a still-`OPEN` proposal has not decided anything yet, so including it would understate the rate without cause. `null` when nothing has resolved. */
export function calculateAcceptanceRate(proposals: PilotProposalMetrics): number | null {
  const resolved = proposals.accepted + proposals.declined + proposals.lapsed
  return resolved === 0 ? null : proposals.accepted / resolved
}

/** `null` when there are no orders at all — not `0`, which would falsely read as "zero completion." */
export function calculateCompletionRate(orders: PilotOrderMetrics): number | null {
  return orders.total === 0 ? null : orders.completed / orders.total
}

/** Same `null`-when-empty rule as {@link calculateCompletionRate}. */
export function calculateCancellationRate(orders: PilotOrderMetrics): number | null {
  return orders.total === 0 ? null : orders.cancelled / orders.total
}

/**
 * Collects one `PilotAnalyticsInput` snapshot. Fires only when called —
 * this is an explicit, owner-triggered, one-shot read, never wired into
 * `OwnerControlCenter.tsx`'s own 15s poll (see this module's own KDoc).
 * Never throws: a failed fan-out source degrades that source to its empty
 * value, mirroring `loadTodaySnapshot`'s own "a partial picture beats none"
 * choice, so a partial backend outage still produces a usable (if
 * `unknown`-graded) analysis rather than an opaque error.
 */
export async function collectPilotAnalyticsInput(
  credential: OwnerCredential,
  healths: ModuleHealth[]
): Promise<PilotAnalyticsInput> {
  const [drivers, orders] = await Promise.all([
    fetchDrivers().catch(() => [] as DriverListItem[]),
    fetchOrders(credential).catch(() => [] as OrderListItem[]),
  ])

  const proposalLists = await mapWithConcurrency(drivers, FAN_OUT_CONCURRENCY_LIMIT, (driver) =>
    fetchProposalsForDriver(driver.id, credential).catch(() => [] as ProposalListItem[])
  )
  const proposals = proposalLists.flat()

  const ordersWithAcceptedProposal = new Set(
    proposals.filter((proposal) => proposal.status === 'ACCEPTED').map((proposal) => proposal.orderId)
  )
  const assignmentLists = await mapWithConcurrency(
    [...ordersWithAcceptedProposal],
    FAN_OUT_CONCURRENCY_LIMIT,
    (orderId) => fetchAssignmentsForOrder(orderId).catch(() => [] as AssignmentListItem[])
  )
  const assignments = assignmentLists.flat()

  return {
    generatedAt: new Date().toISOString(),
    periodLabel: 'Весь период наблюдения (все данные, доступные системе сейчас)',
    orders: computeOrderMetrics(orders),
    proposals: computeProposalMetrics(proposals),
    assignments: computeAssignmentMetrics(assignments),
    drivers: computeDriverMetrics(drivers, proposals),
    reactionTime: computeReactionTime(proposals),
    health: computeHealthMetrics(healths),
  }
}
