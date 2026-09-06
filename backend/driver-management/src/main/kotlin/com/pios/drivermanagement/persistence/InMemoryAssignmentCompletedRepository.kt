package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.AssignmentCompletedRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2):
 * a fast, dependency-free test double for [AssignmentCompletedRepository],
 * mirroring [InMemoryDriverRepository]'s own precedent exactly -- not
 * `@Repository`-annotated, not a production storage mechanism, state lost
 * when the process ends.
 */
class InMemoryAssignmentCompletedRepository : AssignmentCompletedRepository {
    private val processedEventIds = ConcurrentHashMap.newKeySet<String>()

    override fun markProcessed(eventId: String): Boolean = processedEventIds.add(eventId)

    override fun isProcessed(eventId: String): Boolean = processedEventIds.contains(eventId)
}
