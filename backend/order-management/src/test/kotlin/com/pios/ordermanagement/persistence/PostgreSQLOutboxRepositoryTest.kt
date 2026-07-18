package com.pios.ordermanagement.persistence

import com.pios.ordermanagement.application.OutboxRecord
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves the save/find/mark-published lifecycle described by
 * [OutboxRepository] against a real PostgreSQL database (Outbox
 * Foundation v1.0; ADR-032).
 */
class PostgreSQLOutboxRepositoryTest {

    private val repository = PostgreSQLOutboxRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))

    @Test
    fun `a saved outbox record is assigned an id and appears in findUnpublished`() {
        val saved = repository.save(
            OutboxRecord(
                aggregateId = "outbox-repo-order-1",
                eventType = "OrderSubmitted",
                routingKey = "order.submitted",
                payload = "{}"
            )
        )

        assertTrue(saved.id != null)
        assertTrue(repository.findUnpublished().any { it.id == saved.id })
    }

    @Test
    fun `marking a record published removes it from findUnpublished`() {
        val saved = repository.save(
            OutboxRecord(
                aggregateId = "outbox-repo-order-2",
                eventType = "OrderCompleted",
                routingKey = "order.completed",
                payload = "{}"
            )
        )

        repository.markPublished(saved.id!!)

        assertTrue(repository.findUnpublished().none { it.id == saved.id })
    }

    @Test
    fun `unpublished records for the same aggregate are returned in commit order`() {
        // A fresh, unique aggregate id per run -- not a fixed literal --
        // since PostgreSQL persists across test executions and a fixed
        // literal could collide with a leftover row from an earlier run.
        val aggregateId = "outbox-repo-order-3-${java.util.UUID.randomUUID()}"
        val first = repository.save(
            OutboxRecord(
                aggregateId = aggregateId,
                eventType = "OrderSubmitted",
                routingKey = "order.submitted",
                payload = "{}"
            )
        )
        val second = repository.save(
            OutboxRecord(
                aggregateId = aggregateId,
                eventType = "OrderCompleted",
                routingKey = "order.completed",
                payload = "{}"
            )
        )

        val unpublished = repository.findUnpublished().filter { it.aggregateId == aggregateId }

        assertEquals(listOf(first.id, second.id), unpublished.map { it.id })
    }
}
