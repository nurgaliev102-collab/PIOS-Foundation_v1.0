package com.pios.dispatch.domain

import java.time.Instant

/**
 * AssignmentAccepted (EVENT_CATALOG.md Section 6). Owned exclusively by
 * Dispatch. Records that a proposed assignment has been confirmed —
 * relied upon by Order Management to know the order will proceed toward
 * a ride (EVENT_CATALOG.md Section 9). No event infrastructure, broker,
 * or schema is implied by this representation, consistent with ADR-003.
 */
data class AssignmentAccepted(
    val orderId: OrderReference,
    val driverId: DriverReference,
    val occurredAt: Instant = Instant.now()
)
