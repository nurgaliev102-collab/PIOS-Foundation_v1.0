import type { ModuleHealth } from './healthPoll'
import type { OwnerCredential } from './ownerCredential'
import {
  excludeTestData,
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
 * Test/production data separation (2026-08-17): imports `excludeTestData`
 * from `todayData.ts` (not its own copy — a plain filter function carries
 * none of the fan-out-timing risk this module's own KDoc above is about)
 * and applies it right after each fetch, identically to `loadTodaySnapshot`,
 * so the AI Advisor's own metrics stay consistent with what Owner Control
 * Center's counters and EventFeed already show.
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

/**
 * One calendar day's own already-computed metrics — field-for-field the
 * same shape `ai-advisor`'s own `DailySnapshot` (Kotlin) expects
 * (`ai-advisor/src/main/kotlin/com/pios/aiadvisor/api/PilotAnalysisRequest.kt`),
 * so the mapping stays mechanical, matching this file's own existing
 * `PilotAnalyticsInput`/`PilotAnalysisRequest` convention.
 *
 * `activeDrivers` (renamed from `availableDrivers`, 2026-08-17, data-quality
 * fix) is **not** a historical read of `DriverListItem.availability` — that
 * field is live, mutable, current-only state with no timestamp of its own,
 * so treating it as if it had a value "on 2026-08-16" would be inventing
 * data that was never recorded (this task's own "не придумывай отсутствующие
 * исторические данные" rule). It counts distinct drivers who actually had
 * proposal activity that calendar day (`ProposalListItem.createdAt`) — the
 * same real, dated signal `computeDriverMetrics`'s own `withActivity`
 * already uses for the current snapshot, just bucketed per day instead of
 * over the whole period. It must **never** be compared to
 * `PilotDriverMetrics.available` (a live snapshot of "on the line right
 * now") as if they were the same metric — see [computeCurrentDaySnapshot]'s
 * own KDoc and `MockAIProvider.kt`'s own trend-finding logic for how this
 * boundary is enforced downstream.
 */
export interface DailySnapshot {
  date: string
  orders: PilotOrderMetrics
  proposals: PilotProposalMetrics
  assignments: PilotAssignmentMetrics
  activeDrivers: number
}

export interface PilotAnalyticsInput {
  generatedAt: string
  /** Honest description of what was actually read, not a fabricated date range — see this module's own KDoc. */
  periodLabel: string
  /**
   * ALL-PERIOD / cumulative — every field below (`orders`/`proposals`/
   * `assignments`/`drivers`/`reactionTime`) is computed over the *entire*
   * observation period (`periodLabel` above), not "today." It is **not**
   * comparable to a single day of [history] or to [currentDay] without
   * first picking [currentDay] or a [history] entry as the same-scale
   * counterpart (data-quality fix, 2026-08-17 — see [computeCurrentDaySnapshot]).
   */
  orders: PilotOrderMetrics
  proposals: PilotProposalMetrics
  assignments: PilotAssignmentMetrics
  /** `available` here is LIVE — the driver's current `availability` status at the moment this snapshot was collected, not a per-day historical figure. Never the same metric as a [DailySnapshot.activeDrivers] entry. */
  drivers: PilotDriverMetrics
  reactionTime: PilotReactionTimeMetrics
  health: PilotHealthMetrics
  /** CURRENT DAY — today's own real snapshot, or `null` if PIOS has no dated activity for today yet. See [computeCurrentDaySnapshot]. The only field here on the same scale as [history]'s own entries. */
  currentDay: DailySnapshot | null
  /** HISTORICAL DAILY — days strictly before today, most recent first. See [computeDailyHistory]. */
  history: DailySnapshot[]
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

/** PIOS Intelligence Trend Context v1 (2026-08-17): how many calendar days of history {@link computeDailyHistory} returns at most. */
export const HISTORY_MAX_DAYS = 7

function startOfLocalDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

function toDateKey(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

/** `null` for a missing or unparseable timestamp — mirrors `todayData.ts`'s own `isToday()` treating a missing timestamp as "not this day," never a guess. */
function dateKeyFromIso(isoTimestamp: string | null): string | null {
  if (!isoTimestamp) {
    return null
  }
  const parsed = new Date(isoTimestamp)
  return Number.isNaN(parsed.getTime()) ? null : toDateKey(parsed)
}

function groupByDateKey<T>(items: T[], dateOf: (item: T) => string | null): Map<string, T[]> {
  const map = new Map<string, T[]>()
  for (const item of items) {
    const key = dateOf(item)
    if (key === null) {
      continue
    }
    const bucket = map.get(key)
    if (bucket) {
      bucket.push(item)
    } else {
      map.set(key, [item])
    }
  }
  return map
}

/**
 * Groups `orders`/`proposals`/`assignments` this module already loaded for
 * the current snapshot into one [DailySnapshot] per calendar day, for the
 * `days` calendar days immediately before today (today itself is the
 * "current" snapshot already reported separately, never duplicated into
 * history). No new HTTP request: every array here is the same one
 * {@link collectPilotAnalyticsInput} already fetched.
 *
 * Reuses {@link computeOrderMetrics}/{@link computeProposalMetrics}/
 * {@link computeAssignmentMetrics} unchanged, applied to each day's own
 * subset — the same "bucket by `createdAt`, then reuse the existing
 * counters" approach `todayData.ts`'s own `loadTodaySnapshot` already uses
 * for "today". `AssignmentListItem` carries no `createdAt` of its own, so an
 * assignment is bucketed by the first of `arrivedAt`/`startedAt`/`completedAt`
 * it actually has.
 *
 * A day with genuinely nothing in it (no order, proposal, or assignment
 * activity) is simply absent from the result, never a fabricated zero-filled
 * entry — an empty array here means "no data for that day," not "AI should
 * see a day of zero orders."
 */
export function computeDailyHistory(
  orders: OrderListItem[],
  proposals: ProposalListItem[],
  assignments: AssignmentListItem[],
  days: number = HISTORY_MAX_DAYS
): DailySnapshot[] {
  const today = startOfLocalDay(new Date())
  const windowKeys: string[] = []
  for (let offset = 1; offset <= days; offset++) {
    const day = new Date(today)
    day.setDate(day.getDate() - offset)
    windowKeys.push(toDateKey(day))
  }

  const ordersByDate = groupByDateKey(orders, (order) => dateKeyFromIso(order.createdAt))
  const proposalsByDate = groupByDateKey(proposals, (proposal) => dateKeyFromIso(proposal.createdAt))
  const assignmentsByDate = groupByDateKey(
    assignments,
    (assignment) =>
      dateKeyFromIso(assignment.arrivedAt) ?? dateKeyFromIso(assignment.startedAt) ?? dateKeyFromIso(assignment.completedAt)
  )

  const snapshots: DailySnapshot[] = []
  for (const key of windowKeys) {
    const dayOrders = ordersByDate.get(key)
    const dayProposals = proposalsByDate.get(key)
    const dayAssignments = assignmentsByDate.get(key)
    if (!dayOrders && !dayProposals && !dayAssignments) {
      continue
    }
    snapshots.push({
      date: key,
      orders: computeOrderMetrics(dayOrders ?? []),
      proposals: computeProposalMetrics(dayProposals ?? []),
      assignments: computeAssignmentMetrics(dayAssignments ?? []),
      activeDrivers: new Set((dayProposals ?? []).map((p) => p.driverId)).size,
    })
  }

  return snapshots.sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0))
}

/**
 * Today's own real [DailySnapshot], on the same daily scale as
 * {@link computeDailyHistory}'s own entries — data-quality fix (2026-08-17):
 * before this function existed, the only "current" numbers available were
 * [PilotAnalyticsInput]'s own ALL-PERIOD/cumulative fields, and comparing
 * those against one day of history was comparing different scales (e.g. "18
 * orders all-time" vs "12 orders yesterday" is not a real day-over-day
 * trend). This reuses the exact same bucket-by-`createdAt`-then-reuse-
 * `computeOrderMetrics`/etc. approach {@link computeDailyHistory} already
 * uses, just for today's own calendar day instead of the days before it.
 *
 * Returns `null`, never a fabricated all-zero snapshot, when PIOS has no
 * dated order/proposal/assignment activity for today yet (e.g. early in the
 * day, or a quiet day) — the same "absent, not zero" rule
 * {@link computeDailyHistory} already applies to a day with nothing in it.
 */
export function computeCurrentDaySnapshot(
  orders: OrderListItem[],
  proposals: ProposalListItem[],
  assignments: AssignmentListItem[]
): DailySnapshot | null {
  const todayKey = toDateKey(startOfLocalDay(new Date()))

  const dayOrders = orders.filter((order) => dateKeyFromIso(order.createdAt) === todayKey)
  const dayProposals = proposals.filter((proposal) => dateKeyFromIso(proposal.createdAt) === todayKey)
  const dayAssignments = assignments.filter(
    (assignment) =>
      (dateKeyFromIso(assignment.arrivedAt) ?? dateKeyFromIso(assignment.startedAt) ?? dateKeyFromIso(assignment.completedAt)) ===
      todayKey
  )

  if (dayOrders.length === 0 && dayProposals.length === 0 && dayAssignments.length === 0) {
    return null
  }

  return {
    date: todayKey,
    orders: computeOrderMetrics(dayOrders),
    proposals: computeProposalMetrics(dayProposals),
    assignments: computeAssignmentMetrics(dayAssignments),
    activeDrivers: new Set(dayProposals.map((p) => p.driverId)).size,
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
  const [driversRaw, ordersRaw] = await Promise.all([
    fetchDrivers(credential).catch(() => [] as DriverListItem[]),
    fetchOrders(credential).catch(() => [] as OrderListItem[]),
  ])
  const drivers = excludeTestData(driversRaw)
  const orders = excludeTestData(ordersRaw)

  const proposalLists = await mapWithConcurrency(drivers, FAN_OUT_CONCURRENCY_LIMIT, (driver) =>
    fetchProposalsForDriver(driver.id, credential).catch(() => [] as ProposalListItem[])
  )
  const proposals = excludeTestData(proposalLists.flat())

  const ordersWithAcceptedProposal = new Set(
    proposals.filter((proposal) => proposal.status === 'ACCEPTED').map((proposal) => proposal.orderId)
  )
  const assignmentLists = await mapWithConcurrency(
    [...ordersWithAcceptedProposal],
    FAN_OUT_CONCURRENCY_LIMIT,
    (orderId) => fetchAssignmentsForOrder(orderId, credential).catch(() => [] as AssignmentListItem[])
  )
  const assignments = excludeTestData(assignmentLists.flat())

  return {
    generatedAt: new Date().toISOString(),
    periodLabel: 'Весь период наблюдения (все данные, доступные системе сейчас)',
    orders: computeOrderMetrics(orders),
    proposals: computeProposalMetrics(proposals),
    assignments: computeAssignmentMetrics(assignments),
    drivers: computeDriverMetrics(drivers, proposals),
    reactionTime: computeReactionTime(proposals),
    health: computeHealthMetrics(healths),
    currentDay: computeCurrentDaySnapshot(orders, proposals, assignments),
    history: computeDailyHistory(orders, proposals, assignments),
  }
}
