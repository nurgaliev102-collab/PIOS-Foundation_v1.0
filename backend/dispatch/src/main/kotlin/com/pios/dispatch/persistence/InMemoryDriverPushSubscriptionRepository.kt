package com.pios.dispatch.persistence

import com.pios.dispatch.application.DriverPushSubscription
import com.pios.dispatch.application.DriverPushSubscriptionRepository
import com.pios.dispatch.domain.DriverReference
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [DriverPushSubscriptionRepository],
 * mirroring [InMemoryProposalRepository] exactly (ADR-083, D-10). Not a
 * production storage mechanism: state is held only in this process's
 * memory and is lost when it ends. Keyed by `endpoint`, its own primary
 * key in the real schema.
 */
class InMemoryDriverPushSubscriptionRepository : DriverPushSubscriptionRepository {
    private val store = ConcurrentHashMap<String, DriverPushSubscription>()

    override fun upsert(subscription: DriverPushSubscription) {
        store[subscription.endpoint] = subscription
    }

    override fun findByDriver(driver: DriverReference): List<DriverPushSubscription> =
        store.values.filter { it.driverReference == driver }

    override fun deleteByDriverAndEndpoint(driver: DriverReference, endpoint: String) {
        store.computeIfPresent(endpoint) { _, existing -> if (existing.driverReference == driver) null else existing }
    }

    override fun deleteByEndpoint(endpoint: String) {
        store.remove(endpoint)
    }
}
