package com.pios.aiadvisor.api

/**
 * `POST /v1/advisor/analyze`'s own request body (ADR-056 Decision 5) —
 * field-for-field identical to
 * `frontend/src/pages/OwnerControlCenter/pilotAnalytics.ts`'s own
 * `PilotAnalyticsInput`, deliberately, so the mapping between them is
 * mechanical, not interpretive.
 *
 * **Carries no credential, token, name, address, or other free text of any
 * kind** — counts, rates, and durations only (ADR-056 Decision 1, Context;
 * this task's own explicit security requirement). There is nothing here for
 * a passenger or driver to have set that could reach an AI prompt as
 * anything other than a number.
 */
data class PilotAnalysisRequest(
    val generatedAt: String,
    val periodLabel: String,
    /**
     * ALL-PERIOD / cumulative — `orders`/`proposals`/`assignments`/`drivers`/
     * `reactionTime` below are computed over the *entire* observation period
     * ([periodLabel]), not "today." **Not comparable** to a single
     * [DailySnapshot] ([currentDay] or one entry of [history]) without first
     * matching scale to scale — comparing this to one day's numbers as if
     * both were daily is a real data-quality bug this task's own Trend
     * Context data-quality fix (2026-08-17) exists to prevent. [currentDay]
     * is the only field here on the same daily scale as [history]'s entries.
     */
    val orders: OrderMetrics,
    val proposals: ProposalMetrics,
    val assignments: AssignmentMetrics,
    /** `available` here is LIVE — the driver's current `availability` status at the moment this request was generated, not a per-day historical figure. Never the same metric as a [DailySnapshot.activeDrivers] entry (see that field's own KDoc). */
    val drivers: DriverMetrics,
    val reactionTime: ReactionTimeMetrics,
    val health: HealthMetrics,
    /**
     * CURRENT DAY — today's own real [DailySnapshot], on the same daily
     * scale as [history]'s own entries, or `null` if PIOS has no dated
     * activity for today yet (never a fabricated all-zero snapshot).
     * Optional and defaulted to `null` so every existing caller/test that
     * builds a [PilotAnalysisRequest] without this field keeps compiling and
     * behaving exactly as before (backward compatibility).
     *
     * This is the field a trend comparison against [history] should actually
     * use — never [orders]/[proposals]/[assignments] above, which are
     * ALL-PERIOD/cumulative and not on the same scale as one day of history.
     */
    val currentDay: DailySnapshot? = null,
    /**
     * HISTORICAL DAILY — day-by-day history strictly before today, most
     * recent day first, mirroring `pilotAnalytics.ts`'s own
     * `computeDailyHistory()`. Optional and defaulted to `null` so every
     * existing caller/test that builds a [PilotAnalysisRequest] without this
     * field keeps compiling and behaving exactly as before (backward
     * compatibility, this task's own explicit requirement) — a `null` or
     * empty list means "no history available," never "zero activity."
     */
    val history: List<DailySnapshot>? = null
)

/**
 * One calendar day's own already-computed metrics — the same shape
 * [PilotAnalysisRequest]'s own ALL-PERIOD fields use, applied to a single
 * day's data instead of the whole observation period. Computed entirely in
 * the frontend (`pilotAnalytics.ts`'s `computeDailyHistory()`/
 * `computeCurrentDaySnapshot()`) from data it has already fetched;
 * `ai-advisor` never fetches, stores, or recomputes this itself (ADR-056
 * Decision 1/11 — still no domain call, no database, no migration,
 * unchanged by this addition).
 */
data class DailySnapshot(
    val date: String,
    val orders: OrderMetrics,
    val proposals: ProposalMetrics,
    val assignments: AssignmentMetrics,
    /**
     * Renamed from `availableDrivers` (2026-08-17, data-quality fix): this is
     * **not** a historical read of live driver availability — that has no
     * timestamp of its own and cannot be reconstructed for a past day
     * without inventing data. It counts distinct drivers who had proposal
     * activity that calendar day — a real, dated signal, but a *different*
     * metric from [DriverMetrics.available] (a live snapshot of "on the line
     * right now"). **Must never be compared to [DriverMetrics.available] as
     * if they were the same metric** — see `MockAIProvider.kt`'s own
     * trend-finding logic for how this boundary is enforced.
     */
    val activeDrivers: Int
)

data class OrderMetrics(
    val total: Int,
    val completed: Int,
    val cancelled: Int,
    val open: Int
)

data class ProposalMetrics(
    val total: Int,
    val accepted: Int,
    val declined: Int,
    val lapsed: Int,
    val withdrawn: Int,
    val open: Int
)

data class AssignmentMetrics(
    val total: Int,
    val completed: Int,
    val inProgress: Int
)

data class DriverMetrics(
    val total: Int,
    val available: Int,
    val withActivity: Int
)

data class ReactionTimeMetrics(
    val averageMinutes: Double?,
    val medianMinutes: Double?,
    val sampleSize: Int
)

data class HealthMetrics(
    val modulesUp: Int,
    val modulesTotal: Int
)
