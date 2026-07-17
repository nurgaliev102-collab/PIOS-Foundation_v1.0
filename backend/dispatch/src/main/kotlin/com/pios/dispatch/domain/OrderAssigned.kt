package com.pios.dispatch.domain

import java.time.Instant

/**
 * OrderAssigned (EVENT_CATALOG.md Section 6). Owned exclusively by
 * Dispatch. Records that Dispatch has connected an order to a driver —
 * the outcome of the assignment decision that only Dispatch may make
 * (ADR-002). No event infrastructure, broker, or schema is implied by
 * this representation, consistent with ADR-003.
 */
data class OrderAssigned(
    val orderId: OrderReference,
    val driverId: DriverReference,
    val occurredAt: Instant = Instant.now()
)
