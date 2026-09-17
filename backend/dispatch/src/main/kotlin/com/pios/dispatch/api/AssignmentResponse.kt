package com.pios.dispatch.api

/**
 * The REST response body for every Assignment ride-lifecycle endpoint
 * (ADR-040, Assignment Ride Lifecycle) and for the new `?orderId=` query.
 * Mirrors [ProposalResponse]'s own shape: identity, references, status —
 * plus [statusChangedAt] (ISO-8601, `Instant.toString()`, the same
 * explicit-`.toString()` convention `OrderResponse`/`OutboxRecord`'s own
 * envelope already use rather than depending on Jackson's automatic
 * `Instant` (de)serialization), `null` for an assignment that has never
 * transitioned (a fresh [com.pios.dispatch.domain.AssignmentStatus.CREATED]).
 *
 * [arrivedAt]/[startedAt]/[completedAt] (ADR-043, Owner Control Center —
 * Observation Boundary) surface [com.pios.dispatch.domain.Assignment]'s
 * own same-named properties — optional, appended last, `null` for every
 * assignment that has not reached that transition yet, and permanently
 * `null` for every assignment created before `V8__assignment_transition_timestamps.sql`.
 * [statusChangedAt] is unaffected and keeps meaning exactly what it always
 * has (ADR-043's own binding constraint: kept, not replaced).
 */
data class AssignmentResponse(
    val assignmentId: String,
    val orderId: String,
    val driverId: String,
    val status: String,
    val statusChangedAt: String?,
    val arrivedAt: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val isTest: Boolean = false,
    val terminationInitiator: String? = null,
    val terminationReasonCode: String? = null,
    val terminatedAt: String? = null,
    val terminationNote: String? = null
)
