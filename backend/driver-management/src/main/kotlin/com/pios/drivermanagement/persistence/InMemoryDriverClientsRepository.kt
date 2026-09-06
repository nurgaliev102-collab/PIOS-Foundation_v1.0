package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.DriverClientsRepository
import com.pios.drivermanagement.domain.DriverId
import java.util.concurrent.ConcurrentHashMap

/**
 * Growth Loops TZ v1, Phase 2 extension (docs/PIOS_GROWTH_LOOPS_TZ_V1.md
 * Section 2.1's own disclosed gap, now closed): a fast, dependency-free
 * test double for [DriverClientsRepository], mirroring
 * [InMemoryDriverRepository]'s own precedent exactly.
 */
class InMemoryDriverClientsRepository : DriverClientsRepository {
    private val rideCounts = ConcurrentHashMap<Pair<DriverId, String>, Int>()

    override fun recordRideForClient(driverId: DriverId, passengerReference: String): Boolean {
        val key = driverId to passengerReference
        val newCount = rideCounts.merge(key, 1, Int::plus)
        return newCount == REPEAT_CLIENT_THRESHOLD
    }

    companion object {
        private const val REPEAT_CLIENT_THRESHOLD = 2
    }
}
