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
    val orders: OrderMetrics,
    val proposals: ProposalMetrics,
    val assignments: AssignmentMetrics,
    val drivers: DriverMetrics,
    val reactionTime: ReactionTimeMetrics,
    val health: HealthMetrics
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
