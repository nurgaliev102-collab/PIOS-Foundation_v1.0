package com.pios.drivermanagement.domain

import java.time.Instant

/**
 * Conceptual representation of the DriverAvailabilityChanged domain event
 * (EVENT_CATALOG.md Section 7). Owned exclusively by Driver Management
 * (EVENT_CATALOG.md Section 10, Event Ownership Rules); no other module may
 * create this event. No event infrastructure, broker, or schema is implied
 * by this representation, consistent with ADR-003 and EVENT_CATALOG.md's
 * own scope.
 *
 * [isTest] (ADR-069, Test/Real Segregation in Fallback Driver Selection) is
 * this driver's own [Driver.isTest] classification, carried on the event so
 * Dispatch can project it and partition real orders from test orders at
 * selection time. Additive: `eventVersion` stays `1` (ADR-030 lines 33-36,
 * 58 -- adding a field is not a version change), and every field this event
 * already carried is unchanged.
 */
data class DriverAvailabilityChanged(
    val driverId: DriverId,
    val availability: Availability,
    val isTest: Boolean,
    val occurredAt: Instant = Instant.now()
)
