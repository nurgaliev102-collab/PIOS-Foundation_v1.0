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
 */
data class AssignmentResponse(
    val assignmentId: String,
    val orderId: String,
    val driverId: String,
    val status: String,
    val statusChangedAt: String?
)
