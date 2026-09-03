package com.pios.dispatch.domain

import java.time.Instant

/**
 * TripCompleted (ADR-063). Owned exclusively by Dispatch. Records that
 * the ride connected to a Trip has finished — the same real fact
 * `AssignmentCompleted` already records today, now also modeled on Trip
 * per ADR-063's own relocation. `ADR-063`'s own Consequences section
 * names this event as the eventual replacement for `AssignmentCompleted`
 * in Order Management's own consumption contract, once implemented via a
 * dual-publish migration window — not performed by this task. See
 * [TripArrived]'s own KDoc for the full context this shares.
 */
data class TripCompleted(
    val orderId: OrderReference,
    val driverId: DriverReference,
    val occurredAt: Instant = Instant.now()
)
