package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.PassengerReference

/**
 * Dispatch's own local, derived projection of one member of a passenger's
 * trusted circle (ADR-068, Relationship-Ordered Fallback Dispatch —
 * Trusted → Network → Open Marketplace, Part 1), following
 * [PrimaryDriverRecord]'s own exact precedent. This is not a copy of
 * Passenger Experience's own `Connection` table -- Passenger Experience
 * alone owns what a passenger's trusted circle is and when it changes
 * (ADR-068 Part 1, "Passenger Experience remains sole owner"). This record
 * exists only so Dispatch's own Fallback Dispatch tier-1 selection has a
 * locally available answer to "is this driver a member of this
 * passenger's trusted circle" without querying Passenger Experience
 * directly (a synchronous cross-module call ADR-068 explicitly rejects,
 * for the identical reasons ADR-062 already rejected it).
 *
 * Unlike [PrimaryDriverRecord] (a single designation per passenger), a
 * passenger's trusted circle is a set -- so [TrustedDriverRecord] is one
 * row per `(passengerReference, driverId)` pair, not one row per
 * passenger. Carries nothing beyond the two opaque identifiers ADR-068
 * Part 1 authorizes -- no profile, rating, reputation, or any other
 * Connection metadata.
 */
data class TrustedDriverRecord(
    val passengerReference: PassengerReference,
    val driverId: DriverReference
)
