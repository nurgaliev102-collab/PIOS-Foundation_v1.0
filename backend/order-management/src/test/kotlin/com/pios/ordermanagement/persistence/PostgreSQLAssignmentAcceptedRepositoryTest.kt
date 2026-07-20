package com.pios.ordermanagement.persistence

import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Proves the basic lifecycle [AssignmentAcceptedRepository] describes,
 * against a real PostgreSQL database (Tranche 1: Dispatch Event
 * Publishing Completion), mirroring Dispatch's own equivalent test for
 * `markProcessed` exactly.
 */
class PostgreSQLAssignmentAcceptedRepositoryTest {

    private val repository = PostgreSQLAssignmentAcceptedRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))

    @Test
    fun `markProcessed returns true only the first time a given eventId is recorded`() {
        val eventId = "repo-event-${UUID.randomUUID()}"

        assertTrue(repository.markProcessed(eventId))
        assertFalse(repository.markProcessed(eventId))
    }

    @Test
    fun `isProcessed reflects markProcessed without itself recording anything`() {
        val eventId = "repo-event-${UUID.randomUUID()}"

        assertFalse(repository.isProcessed(eventId))

        repository.markProcessed(eventId)

        assertTrue(repository.isProcessed(eventId))
    }
}
