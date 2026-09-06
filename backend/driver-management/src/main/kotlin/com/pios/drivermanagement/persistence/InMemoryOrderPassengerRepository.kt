package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.OrderPassengerRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * Growth Loops TZ v1, Phase 2 extension (docs/PIOS_GROWTH_LOOPS_TZ_V1.md
 * Section 2.1's own disclosed gap, now closed): a fast, dependency-free
 * test double for [OrderPassengerRepository], mirroring
 * [InMemoryDriverRepository]'s own precedent exactly.
 */
class InMemoryOrderPassengerRepository : OrderPassengerRepository {
    private val store = ConcurrentHashMap<String, String>()

    override fun recordOrderPassenger(orderId: String, passengerReference: String) {
        store.putIfAbsent(orderId, passengerReference)
    }

    override fun findPassengerReference(orderId: String): String? = store[orderId]
}
