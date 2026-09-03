package com.pios.passengerexperience.domain

import java.time.Instant

/**
 * PrimaryConnectionDesignated (ADR-062, Primary Driver / First Refusal —
 * Passenger Experience → Dispatch Contract; Task 14, First Refusal
 * Foundation). Owned exclusively by Passenger Experience. Records that
 * [passengerReference] has designated [driverId] as their primary driver
 * — fired by [com.pios.passengerexperience.application.SetPrimaryConnectionApplicationService]
 * whenever `primary_connections` is upserted, whether this is the
 * passenger's first-ever designation or a change from a previous one
 * (ADR-054 Part 2's own `INSERT ... ON CONFLICT ... DO UPDATE` — a
 * changed designation is simply a repeat of this same event with a
 * different [driverId], never a separate "changed" event, since the
 * upsert itself carries no other distinguishable case).
 *
 * Carries only the two opaque identifiers ADR-062's own Decision section
 * authorizes ("exactly two opaque identifiers, explicitly forbidding
 * profile/rating/reputation data") — no passenger name, phone number, or
 * other Connection metadata. This is the entire, minimum information
 * Dispatch's own `PrimaryDriverRecord` projection needs.
 */
data class PrimaryConnectionDesignated(
    val passengerReference: PassengerReference,
    val driverId: DriverReference,
    val occurredAt: Instant = Instant.now()
)
