package com.pios.dispatch.application

import com.pios.dispatch.domain.PassengerReference

/**
 * The persistence boundary for Dispatch's own local primary-driver
 * projection and its idempotency ledger (ADR-062; Task 14, First Refusal
 * Foundation), following [DriverAvailabilityRepository]'s own exact
 * precedent. No storage technology, broker, or framework detail is named
 * here.
 *
 * Only Dispatch's own consumer path
 * ([PrimaryDriverProjectionApplicationService]) writes through this
 * boundary; [FirstRefusalApplicationService] is its one production reader.
 */
interface PrimaryDriverRepository {
    /**
     * Records that [eventId] has been processed. Returns `true` the first
     * time a given [eventId] is recorded, `false` if it was already
     * recorded (a redelivery of an event already handled) -- the building
     * block idempotent consumption is built on, mirroring
     * [DriverAvailabilityRepository.markProcessed] exactly.
     */
    fun markProcessed(eventId: String): Boolean

    /** Upserts Dispatch's local primary-driver record for the passenger [record] concerns. */
    fun upsert(record: PrimaryDriverRecord)

    /** Clears any local primary-driver record for [passengerReference] -- a no-op if none exists. */
    fun clear(passengerReference: PassengerReference)

    /** The current local record for [passengerReference], if a primary driver is currently designated. */
    fun findByPassenger(passengerReference: PassengerReference): PrimaryDriverRecord?
}
