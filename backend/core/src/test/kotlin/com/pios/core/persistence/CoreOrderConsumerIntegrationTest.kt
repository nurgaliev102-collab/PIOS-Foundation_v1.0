package com.pios.core.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.core.application.ParticipantHistoryProjectionApplicationService
import com.pios.core.domain.HistoryEventKind
import com.pios.core.domain.ParticipantReference
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * End-to-end proof of ADR-067's Slice 01 goal for the order events: a
 * real `OrderSubmitted`- / `OrderCompleted`-shaped message, published to
 * the `order-management.events` exchange exactly as order-management's own
 * publisher would, is consumed by Core's real listener and results in the
 * right `pios_core_test` history rows — never calling the service directly.
 *
 * The required integration case (task item M): one event → consumer →
 * History row; the same event again → exactly one History row.
 *
 * Fail-closed: connects only through [PostgreSQLTestDatabase] /
 * [RabbitMQTestConnection], which run [com.pios.core.qa.CoreQaSafetyGate]
 * first (ADR-067 QA / Production Gate item 4). Without a valid QA
 * PostgreSQL configuration (`-Dpios.core.qa.postgres.*`) this test throws
 * [com.pios.core.qa.QaSafetyViolation] before opening any connection.
 * RabbitMQ defaults to the broker-refused `pios-test` / `pios_test`
 * namespace. See `docs/PIOS_CORE_SLICE_01_QA_ENVIRONMENT_PLAN.md`.
 */
class CoreOrderConsumerIntegrationTest {

    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val jdbcTemplate = JdbcTemplate(dataSource)
    private val historyRepository = PostgreSQLParticipantHistoryRepository(jdbcTemplate)
    private val processedRepository = PostgreSQLProcessedEventRepository(jdbcTemplate)
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val projection = ParticipantHistoryProjectionApplicationService(processedRepository, historyRepository, transactionRunner)
    private val listener = OrderEventListener(projection, ObjectMapper())
    private val publisher = OrderEventMessagePublisher(RabbitMQTestConnection.connectionFactory)
    private val harness = CoreOrderEventTestListenerHarness(RabbitMQTestConnection.connectionFactory, listener)

    @AfterTest
    fun stop() = harness.stop()

    @Test
    fun `an OrderSubmitted message produces exactly one ORDER_SUBMITTED history row, redelivery adds none`() {
        val passenger = "identity-${UUID.randomUUID()}"
        val order = "order-${UUID.randomUUID()}"
        val eventId = UUID.randomUUID().toString()

        publisher.publishOrderSubmitted(orderId = order, passengerReference = passenger, eventId = eventId)

        val first = awaitUntilNotNull {
            historyRepository.history(ParticipantReference(passenger)).takeIf { it.isNotEmpty() }
        }
        assertNotNull(first)
        assertEquals(listOf(HistoryEventKind.ORDER_SUBMITTED), first!!.map { it.kind })

        // Redeliver the identical event id — idempotency ledger must
        // suppress a second row.
        publisher.publishOrderSubmitted(orderId = order, passengerReference = passenger, eventId = eventId)
        Thread.sleep(1500)
        assertEquals(1, historyRepository.history(ParticipantReference(passenger)).size)
    }

    @Test
    fun `an OrderCompleted with no prior OrderSubmitted is marked processed and writes no history row`() {
        val order = "order-orphan-${UUID.randomUUID()}"
        val eventId = UUID.randomUUID().toString()

        publisher.publishOrderCompleted(orderId = order, eventId = eventId)

        val processed = awaitUntilNotNull {
            jdbcTemplate.queryForList("SELECT event_id FROM processed_events WHERE event_id = ?", String::class.java, eventId)
                .firstOrNull()
        }
        assertNotNull(processed)
        val rows = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM participant_history_events WHERE source_event_id = ?", Long::class.java, eventId
        )
        assertEquals(0L, rows)
    }
}
