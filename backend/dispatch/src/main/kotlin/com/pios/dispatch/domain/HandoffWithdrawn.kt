package com.pios.dispatch.domain

import java.time.Instant

/**
 * HandoffWithdrawn (D-07). Owned exclusively by Dispatch. Records that
 * the original driver retracted a Handoff before passenger consent — per
 * D-07's own locked invariant, this is only ever reachable before
 * [HandoffStatus.COMMITTED] (enforced by [Handoff.withdraw]'s own
 * `check`), so this event's mere existence is proof the underlying
 * Assignment/Trip was never touched. No event infrastructure, broker, or
 * schema is implied by this representation (ADR-003); not published to
 * the outbox today.
 */
data class HandoffWithdrawn(
    val assignmentId: AssignmentId,
    val orderId: OrderReference,
    val occurredAt: Instant = Instant.now()
)
