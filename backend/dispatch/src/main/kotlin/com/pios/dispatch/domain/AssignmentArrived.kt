package com.pios.dispatch.domain

import java.time.Instant

/**
 * AssignmentArrived (ADR-040, Assignment Ride Lifecycle). Owned exclusively
 * by Dispatch. Records that the driver has reached the passenger for this
 * assignment. No event infrastructure, broker, or schema is implied by
 * this representation, consistent with ADR-003.
 */
data class AssignmentArrived(
    val orderId: OrderReference,
    val driverId: DriverReference,
    val occurredAt: Instant = Instant.now()
)
