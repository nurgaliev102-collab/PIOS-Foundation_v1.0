package com.pios.dispatch.application

import com.pios.dispatch.domain.DriverReference

/**
 * Persistence port for [DriverPushSubscription] (ADR-083, D-10).
 * [PostgreSQLDriverPushSubscriptionRepository][com.pios.dispatch.persistence.PostgreSQLDriverPushSubscriptionRepository]
 * is the real, production-wired implementation;
 * [InMemoryDriverPushSubscriptionRepository][com.pios.dispatch.persistence.InMemoryDriverPushSubscriptionRepository]
 * is the dev/test-only in-memory adapter, mirroring [ProposalRepository]'s
 * own two-adapter convention.
 *
 * No listing-by-nothing / find-all method exists here -- ADR-083 Part 6:
 * "No listing endpoint -- nothing needs to enumerate a driver's devices
 * over HTTP."
 */
interface DriverPushSubscriptionRepository {
    /** Inserts a new row, or replaces the existing row for the same [DriverPushSubscription.endpoint] (its own primary key). */
    fun upsert(subscription: DriverPushSubscription)

    fun findByDriver(driver: DriverReference): List<DriverPushSubscription>

    /** Deletes only a row matching both [driver] and [endpoint] -- never another driver's row for the same or a different endpoint. */
    fun deleteByDriverAndEndpoint(driver: DriverReference, endpoint: String)

    /** Deletes the row for [endpoint] regardless of which driver it currently belongs to -- used only for a stale-subscription (404/410) cleanup after a failed send. */
    fun deleteByEndpoint(endpoint: String)
}
