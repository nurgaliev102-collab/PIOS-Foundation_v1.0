package com.pios.dispatch.domain

import java.time.Instant

/**
 * HandoffCommitted (D-07). Owned exclusively by Dispatch. Records the
 * one and only constitutive transition D-07 defines: the passenger's own
 * consent to the exact, already-substitute-accepted Handoff. Carries
 * both [originalDriverId] (unchanged, D-07's own "remains historically
 * identifiable" invariant) and [executingDriverId] (the new fact) side
 * by side, since this is the one moment both are simultaneously
 * meaningful to record. No event infrastructure, broker, or schema is
 * implied by this representation (ADR-003); not published to the outbox
 * today (see `HandoffApplicationService`'s own KDoc).
 */
data class HandoffCommitted(
    val assignmentId: AssignmentId,
    val orderId: OrderReference,
    val originalDriverId: DriverReference,
    val executingDriverId: DriverReference,
    val occurredAt: Instant = Instant.now()
)
