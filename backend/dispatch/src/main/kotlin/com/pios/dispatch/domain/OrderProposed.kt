package com.pios.dispatch.domain

import java.time.Instant

/**
 * OrderProposed. Owned exclusively by Dispatch. Records that Dispatch has
 * proposed a specific driver for a specific order, prior to that driver's
 * own confirmation (ADR-035 Part 1; Product Decision: Opportunity Before
 * Assignment v1.0). Named after the order, mirroring [OrderAssigned]'s own
 * naming convention for the creation-moment event within this same
 * aggregate family (Implementation Design — Proposal Aggregate, Section
 * 7) — distinguished from Order Management's own `OrderSubmitted` by
 * owning domain and by verb, exactly as the existing Order-subject events
 * already coexist. No event infrastructure, broker, or schema is implied
 * by this representation, consistent with ADR-003.
 */
data class OrderProposed(
    val orderId: OrderReference,
    val driverId: DriverReference,
    val occurredAt: Instant = Instant.now()
)
