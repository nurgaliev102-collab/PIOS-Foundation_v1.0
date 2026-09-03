package com.pios.passengerexperience.domain

import java.time.Instant

/**
 * PrimaryConnectionCleared (ADR-062, Primary Driver / First Refusal —
 * Passenger Experience → Dispatch Contract; Task 14, First Refusal
 * Foundation). Owned exclusively by Passenger Experience. Records that
 * [passengerReference] no longer has a designated primary driver — fired
 * by [com.pios.passengerexperience.application.RemoveConnectionApplicationService]
 * only when the specific `Connection` being removed was, at the moment of
 * removal, the passenger's own primary (`primary_connections`'s own `ON
 * DELETE CASCADE`, ADR-054 Part 2, clears the designation at the storage
 * level as a side effect of the delete; this event reports that same
 * fact at the domain level so Dispatch's projection can follow). Removing
 * a non-primary connection produces no event of this kind — the
 * passenger's own primary is unaffected, and Dispatch's projection needs
 * no update.
 *
 * Carries only [passengerReference] — clearing means "this passenger no
 * longer has a primary driver," not "this specific driver was removed";
 * no [DriverReference] is needed to represent that fact, keeping this
 * event to the same minimum-information standard ADR-062 requires of
 * [PrimaryConnectionDesignated].
 */
data class PrimaryConnectionCleared(
    val passengerReference: PassengerReference,
    val occurredAt: Instant = Instant.now()
)
