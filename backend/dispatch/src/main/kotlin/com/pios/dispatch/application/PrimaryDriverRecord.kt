package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.PassengerReference

/**
 * Dispatch's own local, derived record of a passenger's currently
 * designated primary driver (ADR-062; Task 14, First Refusal Foundation),
 * following [DriverAvailabilityRecord]'s own exact precedent. This is not
 * a copy of Passenger Experience's own `Connection`/`primary_connections`
 * -- Passenger Experience alone owns what "primary" means and when it
 * changes (ADR-062 Decision, "Ownership"). This record exists only to
 * answer "does this passenger currently have a primary driver, and who"
 * locally, from the `PrimaryConnectionDesignated`/`PrimaryConnectionCleared`
 * events Dispatch consumes; it carries nothing beyond [passengerReference]
 * and [primaryDriverId] -- no profile, rating, reputation, or any other
 * Personal Client Relationship data (ADR-062 Constraints: "No ranking,
 * scoring, or weighting is introduced").
 */
data class PrimaryDriverRecord(
    val passengerReference: PassengerReference,
    val primaryDriverId: DriverReference
)
