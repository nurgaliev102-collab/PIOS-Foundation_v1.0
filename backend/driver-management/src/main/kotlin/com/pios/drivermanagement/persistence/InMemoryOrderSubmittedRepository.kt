package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.OrderSubmittedRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * Growth Loops TZ v1, Phase 2 extension (docs/PIOS_GROWTH_LOOPS_TZ_V1.md
 * Section 2.1's own disclosed gap, now closed): a fast, dependency-free
 * test double for [OrderSubmittedRepository], mirroring
 * [InMemoryAssignmentCompletedRepository]'s own precedent exactly.
 */
class InMemoryOrderSubmittedRepository : OrderSubmittedRepository {
    private val processedEventIds = ConcurrentHashMap.newKeySet<String>()

    override fun markProcessed(eventId: String): Boolean = processedEventIds.add(eventId)

    override fun isProcessed(eventId: String): Boolean = processedEventIds.contains(eventId)
}
