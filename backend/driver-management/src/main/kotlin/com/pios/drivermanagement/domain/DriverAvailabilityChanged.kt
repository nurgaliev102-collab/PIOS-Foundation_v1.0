package com.pios.drivermanagement.domain

import java.time.Instant

/**
 * Conceptual representation of the DriverAvailabilityChanged domain event
 * (EVENT_CATALOG.md Section 7). Owned exclusively by Driver Management
 * (EVENT_CATALOG.md Section 10, Event Ownership Rules); no other module may
 * create this event. No event infrastructure, broker, or schema is implied
 * by this representation, consistent with ADR-003 and EVENT_CATALOG.md's
 * own scope.
 */
data class DriverAvailabilityChanged(
    val driverId: DriverId,
    val availability: Availability,
    val occurredAt: Instant = Instant.now()
)
