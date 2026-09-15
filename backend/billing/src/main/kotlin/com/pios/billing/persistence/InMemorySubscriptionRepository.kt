package com.pios.billing.persistence

import com.pios.billing.application.SubscriptionRepository
import com.pios.billing.domain.DriverReference
import com.pios.billing.domain.Subscription
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [SubscriptionRepository]. Not a
 * production storage mechanism -- state is held only in this process's
 * memory and is lost when it ends. Deliberately not `@Repository`-
 * annotated, mirroring `driver-management`'s own
 * `InMemoryDriverRepository`: a fast, dependency-free test double for
 * application-layer tests that do not need a live PostgreSQL connection.
 */
class InMemorySubscriptionRepository : SubscriptionRepository {
    private val store = ConcurrentHashMap<DriverReference, Subscription>()

    override fun findByDriverId(driverId: DriverReference): Subscription? = store[driverId]

    override fun save(subscription: Subscription) {
        store[subscription.driverId] = subscription
    }
}
