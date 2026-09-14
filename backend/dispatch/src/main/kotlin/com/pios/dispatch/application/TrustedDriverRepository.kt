package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference
import com.pios.dispatch.domain.PassengerReference

/**
 * The persistence boundary for Dispatch's own local trusted-circle
 * projection and its idempotency ledger (ADR-068, Relationship-Ordered
 * Fallback Dispatch — Trusted → Network → Open Marketplace, Part 1),
 * following [PrimaryDriverRepository]'s own exact structure. No storage
 * technology, broker, or framework detail is named here.
 *
 * Only Dispatch's own consumer path
 * ([TrustedDriverProjectionApplicationService]) writes through this
 * boundary; [FallbackDispatchApplicationService] is its one production
 * reader.
 */
interface TrustedDriverRepository {
    /**
     * Records that [eventId] has been processed. Returns `true` the first
     * time a given [eventId] is recorded, `false` if it was already
     * recorded (a redelivery of an event already handled) -- mirrors
     * [PrimaryDriverRepository.markProcessed] exactly. A ledger of its
     * own, separate from [PrimaryDriverRepository]'s, per ADR-068 Part 1
     * ("its own per-consumer DLQ and its own `markProcessed(eventId)`
     * idempotency ledger").
     */
    fun markProcessed(eventId: String): Boolean

    /** Adds [record] to Dispatch's local trusted-circle projection -- a no-op if already present. */
    fun add(record: TrustedDriverRecord)

    /** Removes the `(passengerReference, driverId)` pair from Dispatch's local trusted-circle projection -- a no-op if absent. */
    fun remove(passengerReference: PassengerReference, driverId: DriverReference)

    /**
     * The candidate for Fallback Dispatch Tier 1 (ADR-068 Part 2, `T1
     * TRUSTED`): among [passengerReference]'s trusted drivers, excluding
     * [excluding], the one whose local `driver_availability` record has
     * held `available = true` the longest -- the exact same tie-break
     * [DriverAvailabilityRepository.findLongestIdleAvailable] already
     * uses, applied within the narrower trusted-and-available candidate
     * set instead of across the whole platform. `null` if the passenger
     * has no trusted driver who is both a member of this projection and
     * currently available.
     */
    fun findLongestIdleTrustedAvailable(
        passengerReference: PassengerReference,
        excluding: Set<DriverReference> = emptySet()
    ): DriverReference?
}
