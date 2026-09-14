package com.pios.passengerexperience.domain

import java.time.Instant

/**
 * ConnectionRemoved (ADR-068, Relationship-Ordered Fallback Dispatch —
 * Trusted → Network → Open Marketplace, Part 1). Owned exclusively by
 * Passenger Experience. Records that [driverId] is no longer a member of
 * [passengerReference]'s trusted circle — fired by
 * [com.pios.passengerexperience.application.RemoveConnectionApplicationService]
 * whenever an existing `Connection` row is actually deleted, regardless of
 * whether it was the passenger's designated primary (that separate fact is
 * [PrimaryConnectionCleared]'s own, narrower concern, fired only when the
 * removed connection was primary; this event fires on every real removal).
 * Removing an unknown/already-gone [com.pios.passengerexperience.domain.ConnectionId]
 * produces no event, mirroring the idempotent-DELETE contract
 * [RemoveConnectionApplicationService] already preserves for
 * [PrimaryConnectionCleared].
 *
 * Carries [driverId] as well as [passengerReference] -- unlike
 * [PrimaryConnectionCleared] (which represents "this passenger no longer
 * has *a* primary" and needs no driver identity), a passenger's trusted
 * circle is a set, so removal must identify *which* member left it. Same
 * two-opaque-identifiers-plus-occurredAt minimum ADR-068 Part 1 requires
 * of [ConnectionEstablished].
 */
data class ConnectionRemoved(
    val passengerReference: PassengerReference,
    val driverId: DriverReference,
    val occurredAt: Instant = Instant.now()
)
