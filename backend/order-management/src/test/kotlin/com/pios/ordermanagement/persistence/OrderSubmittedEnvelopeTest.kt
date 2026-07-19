package com.pios.ordermanagement.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.ordermanagement.application.EventPublisher
import com.pios.ordermanagement.application.OrderLifecycleApplicationService
import com.pios.ordermanagement.application.OutboxRelay
import com.pios.ordermanagement.application.SubmitOrderCommand
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Proves the transport envelope Hardening Review v1.0 required (an event
 * identity distinct from outbox persistence bookkeeping, and stability of
 * that identity across a retried publish) — gaps the original RabbitMQ
 * Event Publishing Foundation v1.0 payload (just `orderId`/`occurredAt`)
 * left unaddressed.
 */
class OrderSubmittedEnvelopeTest {

    private val objectMapper = ObjectMapper()
    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val orderRepository = PostgreSQLOrderRepository(JdbcTemplate(dataSource))
    private val outboxRepository = PostgreSQLOutboxRepository(JdbcTemplate(dataSource))
    private val transactionRunner = SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val service = OrderLifecycleApplicationService(orderRepository, outboxRepository, transactionRunner, objectMapper)

    @Test
    fun `the persisted envelope carries a stable eventId, an explicit eventVersion, and a business occurredAt distinct from outbox bookkeeping`() {
        val submitted = service.submitOrder(SubmitOrderCommand())

        val record = outboxRepository.findUnpublished().single { it.aggregateId == submitted.order.id.value }
        val envelope = objectMapper.readTree(record.payload)

        assertTrue(envelope.get("eventId").asText().isNotBlank())
        assertEquals("OrderSubmitted", envelope.get("eventType").asText())
        assertEquals(1, envelope.get("eventVersion").asInt())
        assertEquals(submitted.event.occurredAt.toString(), envelope.get("occurredAt").asText())
        assertEquals(submitted.order.id.value, envelope.get("payload").get("orderId").asText())

        // The envelope's own business occurredAt must be a distinct field
        // from the outbox row's persistence timestamp -- never the same
        // value read twice under two names.
        assertNotNull(record.createdAt)
        assertNotEquals(record.createdAt.toString(), envelope.get("occurredAt").asText())
    }

    @Test
    fun `two distinct submissions never share an eventId`() {
        val first = service.submitOrder(SubmitOrderCommand())
        val second = service.submitOrder(SubmitOrderCommand())

        val firstEventId = objectMapper.readTree(
            outboxRepository.findUnpublished().single { it.aggregateId == first.order.id.value }.payload
        ).get("eventId").asText()
        val secondEventId = objectMapper.readTree(
            outboxRepository.findUnpublished().single { it.aggregateId == second.order.id.value }.payload
        ).get("eventId").asText()

        assertNotEquals(firstEventId, secondEventId)
    }

    @Test
    fun `a failed publish leaves the record pending and a retried publish carries the identical eventId`() {
        val submitted = service.submitOrder(SubmitOrderCommand())
        val marker = submitted.order.id.value
        val originalPayload = outboxRepository.findUnpublished()
            .single { it.aggregateId == marker }.payload

        // findUnpublished() returns every pending record in the shared
        // test database, including unrelated ones other test classes may
        // have left behind -- so the simulated failure must target only
        // this test's own record by its unique marker, not "whichever
        // record the relay happens to process first."
        val capturedPayloads = mutableListOf<String>()
        var failedOnce = false
        val flakyPublisher = object : EventPublisher {
            override fun publish(routingKey: String, payload: String) {
                if (!payload.contains(marker)) return
                capturedPayloads.add(payload)
                if (!failedOnce) {
                    failedOnce = true
                    throw RuntimeException("simulated broker confirm failure")
                }
            }
        }
        val relay = OutboxRelay(outboxRepository, flakyPublisher)

        // First attempt: publisher throws (broker did not confirm) -- the
        // record must remain pending, never marked published.
        relay.relay()
        assertTrue(outboxRepository.findUnpublished().any { it.aggregateId == submitted.order.id.value })

        // Second attempt (the next poll): publisher succeeds -- the record
        // is now marked published, and the payload it actually sent both
        // times is byte-identical, proving the outbox never regenerates a
        // new eventId for a retried publish of the same record.
        relay.relay()
        assertTrue(outboxRepository.findUnpublished().none { it.aggregateId == submitted.order.id.value })

        assertEquals(2, capturedPayloads.size)
        assertEquals(originalPayload, capturedPayloads[0])
        assertEquals(originalPayload, capturedPayloads[1])
    }
}
