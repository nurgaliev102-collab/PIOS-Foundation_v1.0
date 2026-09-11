package com.pios.core.persistence

import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * PostgreSQL-integration tests for [PostgreSQLProcessedEventRepository]
 * against the QA `pios_core_test` database. Fail-closed via
 * [com.pios.core.qa.CoreQaSafetyGate] (ADR-067 QA / Production Gate item
 * 4) — throws before connecting without `-Dpios.core.qa.postgres.*`.
 *
 * Proves the PostgreSQL-enforced idempotency guarantee: `markProcessed`
 * returns `true` exactly once per event id, `false` on every redelivery —
 * on the database's own `ON CONFLICT DO NOTHING`, not an application check.
 */
class PostgreSQLProcessedEventRepositoryTest {

    private val repository = PostgreSQLProcessedEventRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))

    @Test
    fun `markProcessed returns true first, false on every redelivery`() {
        val eventId = "evt-${UUID.randomUUID()}"
        assertTrue(repository.markProcessed(eventId))
        assertFalse(repository.markProcessed(eventId))
        assertFalse(repository.markProcessed(eventId))
    }

    @Test
    fun `different event ids are independent`() {
        assertTrue(repository.markProcessed("evt-${UUID.randomUUID()}"))
        assertTrue(repository.markProcessed("evt-${UUID.randomUUID()}"))
    }
}
