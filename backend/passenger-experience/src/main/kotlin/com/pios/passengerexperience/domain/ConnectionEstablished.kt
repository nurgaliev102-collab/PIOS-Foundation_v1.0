package com.pios.passengerexperience.domain

import java.time.Instant

/**
 * ConnectionEstablished (ADR-068, Relationship-Ordered Fallback Dispatch —
 * Trusted → Network → Open Marketplace, Part 1). Owned exclusively by
 * Passenger Experience. Records that [passengerReference] now has
 * [driverId] as a member of their trusted circle — fired by
 * [com.pios.passengerexperience.application.CreateConnectionApplicationService]
 * only when a new `Connection` row is actually created, never on the
 * idempotent "already exists" path (mirrors
 * [PrimaryConnectionDesignated]'s own "fired only on an actual write"
 * discipline).
 *
 * Carries only the two opaque identifiers ADR-068 Part 1 authorizes for
 * this event pair ("exactly two opaque identifiers — `passengerReference`,
 * `driverId` — plus `occurredAt`. No name, phone, rating, `createdAt` of
 * the underlying row, or any other `Connection` metadata") — the same
 * minimum-information standard [PrimaryConnectionDesignated] already
 * establishes for the single-primary case. This is the entire, minimum
 * information Dispatch's own `TrustedDriverRecord` projection needs.
 */
data class ConnectionEstablished(
    val passengerReference: PassengerReference,
    val driverId: DriverReference,
    val occurredAt: Instant = Instant.now()
)
