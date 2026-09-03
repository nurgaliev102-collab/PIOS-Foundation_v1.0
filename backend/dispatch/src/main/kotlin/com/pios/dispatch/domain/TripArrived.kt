package com.pios.dispatch.domain

import java.time.Instant

/**
 * TripArrived (ADR-063). Owned exclusively by Dispatch. Records that the
 * driver connected to a Trip has reached the passenger — the same real
 * fact `AssignmentArrived` already records today, now also modeled on
 * Trip per ADR-063's own relocation (Task 11's "Backward Compatibility"
 * scope keeps `AssignmentArrived` itself unrenamed and unremoved
 * alongside this; the two are not yet unified). No event infrastructure,
 * broker, or schema is implied by this representation, consistent with
 * ADR-003. Not yet published to the outbox or consumed by any module —
 * this task establishes the Trip creation foundation only; wiring Trip's
 * own ride-progress transitions into any existing endpoint is explicitly
 * out of this task's scope.
 */
data class TripArrived(
    val orderId: OrderReference,
    val driverId: DriverReference,
    val occurredAt: Instant = Instant.now()
)
