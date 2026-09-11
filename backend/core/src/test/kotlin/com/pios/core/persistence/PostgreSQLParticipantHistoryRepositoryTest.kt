package com.pios.core.persistence

import com.pios.core.domain.HistoryEventKind
import com.pios.core.domain.ParticipantHistoryEvent
import com.pios.core.domain.ParticipantReference
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * PostgreSQL-integration tests for [PostgreSQLParticipantHistoryRepository]
 * against `pios_core_test` (never `pios_core`, never any Taxi database).
 *
 * Fail-closed: uses [PostgreSQLTestDatabase] → [com.pios.core.qa.CoreQaEndpoints]
 * → [com.pios.core.qa.CoreQaSafetyGate] (ADR-067 QA / Production Gate item
 * 4). Without `-Dpios.core.qa.postgres.*` this throws
 * [com.pios.core.qa.QaSafetyViolation] before connecting.
 */
class PostgreSQLParticipantHistoryRepositoryTest {

    private val jdbcTemplate = JdbcTemplate(PostgreSQLTestDatabase.dataSource)
    private val repository = PostgreSQLParticipantHistoryRepository(jdbcTemplate)

    @Test
    fun `ensureParticipant is idempotent`() {
        val ref = ParticipantReference("p-${UUID.randomUUID()}")
        repository.ensureParticipant(ref)
        repository.ensureParticipant(ref)
        val count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM participants WHERE participant_reference = ?", Long::class.java, ref.value
        )
        assertEquals(1L, count)
    }

    @Test
    fun `record then history round-trips a fact with occurredAt preserved`() {
        val ref = ParticipantReference("p-${UUID.randomUUID()}")
        val occurredAt = Instant.parse("2026-09-10T10:00:00Z")
        repository.ensureParticipant(ref)
        repository.record(
            ParticipantHistoryEvent(ref, HistoryEventKind.ORDER_SUBMITTED, occurredAt, orderReference = "o-1", sourceEventId = "e-1")
        )
        val history = repository.history(ref)
        assertEquals(1, history.size)
        assertEquals(HistoryEventKind.ORDER_SUBMITTED, history[0].kind)
        assertEquals(occurredAt, history[0].occurredAt)
        assertEquals("o-1", history[0].orderReference)
    }

    @Test
    fun `findParticipantByOrderReference returns the ORDER_SUBMITTED participant, or null`() {
        val ref = ParticipantReference("p-${UUID.randomUUID()}")
        val order = "o-${UUID.randomUUID()}"
        repository.ensureParticipant(ref)
        repository.record(ParticipantHistoryEvent(ref, HistoryEventKind.ORDER_SUBMITTED, Instant.now(), orderReference = order, sourceEventId = "e-1"))

        assertEquals(ref, repository.findParticipantByOrderReference(order))
        assertNull(repository.findParticipantByOrderReference("o-never-seen-${UUID.randomUUID()}"))
    }

    @Test
    fun `history is ordered oldest-first`() {
        val ref = ParticipantReference("p-${UUID.randomUUID()}")
        repository.ensureParticipant(ref)
        repository.record(ParticipantHistoryEvent(ref, HistoryEventKind.ORDER_SUBMITTED, Instant.parse("2026-09-10T12:00:00Z"), orderReference = "late", sourceEventId = "e2"))
        repository.record(ParticipantHistoryEvent(ref, HistoryEventKind.ORDER_SUBMITTED, Instant.parse("2026-09-10T09:00:00Z"), orderReference = "early", sourceEventId = "e1"))
        assertEquals(listOf("early", "late"), repository.history(ref).map { it.orderReference })
    }
}
