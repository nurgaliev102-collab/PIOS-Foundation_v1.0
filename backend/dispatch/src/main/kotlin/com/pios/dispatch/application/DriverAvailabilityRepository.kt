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

    /**
     * The currently available driver whose record has held `available = true`
     * for the longest, excluding [excluding] (FR-003A, Fallback Dispatch —
     * the "future routing task" [com.pios.dispatch.application.FirstRefusalApplicationService]'s
     * own KDoc deferred: what happens when First Refusal finds no eligible
     * primary driver). `null` if no eligible driver is currently available
     * at all.
     *
     * [excluding] exists for exactly one reason (FR-003A, decline/lapse
     * retry, [FallbackDispatchApplicationService.attempt]'s own KDoc): a
     * driver whose own Proposal for *this* order just declined or lapsed
     * is not re-selected for the very same order merely because their
     * local availability record (which a decline/lapse never touches —
     * only a real `DriverAvailabilityChanged` event does) still shows
     * `available = true` and happens to be the longest-standing one.
     * Defaults to empty so every other, unrelated caller is unaffected.
     *
     * [orderIsTest] (ADR-069 Part 3) is a second, mandatory eligibility
     * gate applied identically to `excluding`: a candidate is returned
     * only if [DriverAvailabilityRecord.isTest] is **not null** and
     * exactly equals [orderIsTest] -- strict equality in both directions
     * (a real order never matches a test driver and vice versa), and a
     * driver whose classification is still unknown (`null`) is never
     * returned for either kind of order. No default value: every caller
     * must state explicitly which population this order belongs to,
     * mirroring [excluding]'s own load-bearing, never-silently-omitted
     * role in this same method.
     *
     * Defaults to `null` so every existing implementation of this interface
     * — none of which has any interest in fallback selection — continues to
     * compile and behave exactly as before this method's own addition
     * (mirrors [ProposalApplicationService]'s own reasoning for defaulting
     * its `driverAvailabilityRepository` constructor parameter to `null`:
     * forcing every unrelated test double to implement this would be an
     * out-of-scope change to each of them). Only
     * [com.pios.dispatch.persistence.PostgreSQLDriverAvailabilityRepository]
     * overrides it with a real query.
     */
    fun findLongestIdleAvailable(orderIsTest: Boolean, excluding: Set<DriverReference> = emptySet()): DriverReference? = null
}
