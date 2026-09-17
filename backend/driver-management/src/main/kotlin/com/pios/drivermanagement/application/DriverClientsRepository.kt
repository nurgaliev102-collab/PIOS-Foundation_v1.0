package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.DriverClientRecord
import com.pios.drivermanagement.domain.DriverId
import java.time.Instant

/**
 * Growth Loops TZ v1, Phase 2 extension (docs/PIOS_GROWTH_LOOPS_TZ_V1.md
 * Section 2.1's own disclosed gap, now closed): tracks how many completed
 * rides each (driver, passenger) pair has shared, so
 * [AssignmentCompletedApplicationService] can tell the one moment that
 * matters — a passenger's *second* completed ride with a given driver,
 * when they become a "repeat client" — without recomputing it by scanning
 * every ride this driver has ever had.
 *
 * [findAllForDriver] (server-side "Мой бизнес" read model,
 * `docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 5's read-model-only
 * authorization) is the query side of the same table this interface's
 * [recordRideForClient] already writes to -- see [DriverClientRecord]'s own
 * KDoc.
 */
interface DriverClientsRepository {
    /**
     * Records one more completed ride for this (driverId, passengerReference)
     * pair, at [occurredAt] (the ride's own completion instant -- mirrors
     * [DriverMilestonesRepository.recordCompletedRide]'s own [occurredAt]
     * parameter). Returns `true` exactly once per pair — the call where the
     * stored ride count transitions from 1 to 2 — `false` on the first
     * ride (not yet a repeat) and on every ride after the second (already
     * counted as a repeat client, nothing new to add).
     */
    fun recordRideForClient(driverId: DriverId, passengerReference: String, occurredAt: Instant): Boolean

    /**
     * Every (driver, passenger) pair this driver has completed at least one
     * ride with, most-recent-last-ride first -- the same "newest first"
     * convention `DriverHome.tsx`'s own `completedRides` already
     * established (its own KDoc: "matching every other list on this
     * screen's own convention"). A driver with no completed rides at all
     * returns an empty list, not an error.
     */
    fun findAllForDriver(driverId: DriverId): List<DriverClientRecord>
}
