package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference

/**
 * The persistence boundary for Dispatch's own local driver-availability
 * projection and its idempotency ledger (Dispatch Consumer Foundation
 * v1.0; ADR-031, ADR-032). No storage technology, broker, or framework
 * detail is named here.
 *
 * Only Dispatch's own consumer path
 * ([DriverAvailabilityProjectionApplicationService]) writes through this
 * boundary; no other module implements or depends on it.
 *
 * Read from production code for the first time by
 * [ProposalApplicationService.handle] (pilot-readiness fix) — this
 * projection existed and was fully tested before that change but was not
 * wired into any production read path.
 */
interface DriverAvailabilityRepository {
    /**
     * Records that [eventId] has been processed. Returns `true` the first
     * time a given [eventId] is recorded, `false` if it was already
     * recorded (a redelivery of an event already handled) -- the
     * building block idempotent consumption is built on.
     */
    fun markProcessed(eventId: String): Boolean

    /** Upserts Dispatch's local availability record for the driver [record] concerns. */
    fun upsert(record: DriverAvailabilityRecord)

    /** The current local record for [driverReference], if one has ever been recorded. */
    fun findByDriverReference(driverReference: DriverReference): DriverAvailabilityRecord?
}
