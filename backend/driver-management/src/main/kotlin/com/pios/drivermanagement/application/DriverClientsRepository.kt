package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.DriverId

/**
 * Growth Loops TZ v1, Phase 2 extension (docs/PIOS_GROWTH_LOOPS_TZ_V1.md
 * Section 2.1's own disclosed gap, now closed): tracks how many completed
 * rides each (driver, passenger) pair has shared, so
 * [AssignmentCompletedApplicationService] can tell the one moment that
 * matters — a passenger's *second* completed ride with a given driver,
 * when they become a "repeat client" — without recomputing it by scanning
 * every ride this driver has ever had.
 */
interface DriverClientsRepository {
    /**
     * Records one more completed ride for this (driverId, passengerReference)
     * pair. Returns `true` exactly once per pair — the call where the
     * stored ride count transitions from 1 to 2 — `false` on the first
     * ride (not yet a repeat) and on every ride after the second (already
     * counted as a repeat client, nothing new to add).
     */
    fun recordRideForClient(driverId: DriverId, passengerReference: String): Boolean
}
