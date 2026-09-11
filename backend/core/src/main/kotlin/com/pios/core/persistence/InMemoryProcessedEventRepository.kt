package com.pios.core.persistence

import com.pios.core.application.ProcessedEventRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * An in-memory [ProcessedEventRepository] for constructor-based unit tests
 * (this project's convention of constructing collaborators directly
 * rather than through a Spring container). Same `markProcessed` semantics
 * as [PostgreSQLProcessedEventRepository]: `true` the first time an
 * [eventId] is seen, `false` on every redelivery. Not a Spring bean —
 * only [PostgreSQLProcessedEventRepository] is `@Repository`.
 */
class InMemoryProcessedEventRepository : ProcessedEventRepository {
    private val seen = ConcurrentHashMap.newKeySet<String>()

    override fun markProcessed(eventId: String): Boolean = seen.add(eventId)
}
