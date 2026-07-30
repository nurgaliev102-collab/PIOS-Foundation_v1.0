package com.pios.dispatch.domain

import java.time.Instant

/**
 * AssignmentCompleted (ADR-040, Assignment Ride Lifecycle). Owned
 * exclusively by Dispatch. Records that the ride for this assignment has
 * finished. Distinct from `order-management`'s own `OrderCompleted` — this
 * event says nothing about the connected Order's own status, which ADR-040
 * deliberately leaves untouched (see that ADR's own "Decision" item 5). No
 * event infrastructure, broker, or schema is implied by this
 * representation, consistent with ADR-003.
 */
data class AssignmentCompleted(
    val orderId: OrderReference,
    val driverId: DriverReference,
    val occurredAt: Instant = Instant.now()
)
