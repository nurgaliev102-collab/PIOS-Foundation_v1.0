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
 * beyond [driverReference], [available], and [isTest] -- no profile,
 * standing, participation, ranking, or matching-criteria data, none of
 * which any ratified document attributes to Dispatch (ADR-002).
 *
 * [isTest] (ADR-069, Test/Real Segregation in Fallback Driver Selection)
 * is Driver Management's own `Driver.isTest` classification, projected
 * here so Fallback Dispatch's selection predicate can partition real
 * orders from test orders. **Three-valued, not a plain boolean**: `null`
 * means "Dispatch has not yet been told this driver's classification" --
 * an explicit unknown, never treated as `false` (real). Defaults to
 * `null` so every pre-ADR-069 caller/test double that constructs this
 * record with only [driverReference]/[available] continues to compile
 * unchanged (the same "append last, default null" precedent
 * `Driver.createdAt`/`Driver.isTest` themselves already established in
 * Driver Management).
 */
data class DriverAvailabilityRecord(
    val driverReference: DriverReference,
    val available: Boolean,
    val isTest: Boolean? = null
)
