package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference

/**
 * Dispatch's own local, derived record of a driver's current availability
 * (Dispatch Consumer Foundation v1.0). This is not a copy of Driver
 * Management's `Driver` aggregate, and Dispatch is not its source of truth
 * -- Driver Management alone owns what "available" means and when it
 * changes (EVENT_CATALOG.md Section 10). This record exists only to
 * answer "which drivers are currently available" locally, from the
 * DriverAvailabilityChanged events Dispatch consumes; it carries nothing
 * beyond [driverReference] and [available] -- no profile, standing,
 * participation, ranking, or matching-criteria data, none of which any
 * ratified document attributes to Dispatch (ADR-002).
 */
data class DriverAvailabilityRecord(
    val driverReference: DriverReference,
    val available: Boolean
)
