package com.pios.dispatch.domain

import java.time.Instant

/**
 * HandoffRefused (D-07). Owned exclusively by Dispatch. Records that the
 * passenger declined a proposed Handoff — per D-07's own locked
 * invariant, this has no effect on the underlying Assignment/Trip. No
 * event infrastructure, broker, or schema is implied by this
 * representation (ADR-003); not published to the outbox today.
 */
data class HandoffRefused(
    val assignmentId: AssignmentId,
    val orderId: OrderReference,
    val occurredAt: Instant = Instant.now()
)
