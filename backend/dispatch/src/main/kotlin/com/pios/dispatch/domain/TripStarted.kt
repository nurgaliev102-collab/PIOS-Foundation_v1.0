package com.pios.dispatch.domain

import java.time.Instant

/**
 * TripStarted (ADR-063). Owned exclusively by Dispatch. Records that the
 * ride itself has begun — the same real fact `AssignmentStarted` already
 * records today, now also modeled on Trip per ADR-063's own relocation.
 * See [TripArrived]'s own KDoc for the full context this shares.
 */
data class TripStarted(
    val orderId: OrderReference,
    val driverId: DriverReference,
    val occurredAt: Instant = Instant.now()
)
