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
 *
 * [agreedAmount] (D-06, Settlement as Evidence) surfaces the connected
 * [com.pios.dispatch.domain.Trip]'s own same-named property — the amount
 * agreed for this specific Trip, captured once at Trip creation, never
 * recomputed. `null` when the connected Trip has none (no Proposal
 * preceded it, or it predates this field — no backfill). This is the
 * minimal read-model change D-06 Phase 4 calls for: both the driver and
 * the passenger already read this exact endpoint (`AssignmentController.listAssignmentsHttp`,
 * gated so only the Assignment's own driver or the order's own passenger
 * may see a given row), so no new endpoint or authorization rule was
 * introduced for this field.
 *
 * [executingDriverId] (D-07, Handoff Protocol) surfaces the connected
 * Trip's own [com.pios.dispatch.domain.Trip.executingDriver] — the
 * driver currently expected to (or who did) actually drive, separate
 * from [driverId] (the *original committing* driver, unchanged by a
 * Handoff, D-07 invariant #5/#35). Equal to [driverId] for every ride
 * with no Handoff, which is every ride today. `null` only when no Trip
 * is connected yet (never happens on a live path).
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
    val terminationNote: String? = null,
    val agreedAmount: String? = null,
    val executingDriverId: String? = null
)
