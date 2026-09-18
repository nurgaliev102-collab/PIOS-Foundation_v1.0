package com.pios.dispatch.domain

import java.time.Instant

/**
 * HandoffSubstituteAccepted (D-07). Owned exclusively by Dispatch.
 * Records that the named substitute has explicitly accepted a specific
 * Handoff — the locked precondition before passenger consent may ever be
 * requested. No event infrastructure, broker, or schema is implied by
 * this representation (ADR-003); not published to the outbox today (see
 * `HandoffApplicationService`'s own KDoc).
 */
data class HandoffSubstituteAccepted(
    val assignmentId: AssignmentId,
    val orderId: OrderReference,
    val driverId: DriverReference,
    val occurredAt: Instant = Instant.now()
)
