package com.pios.dispatch.application

import com.pios.dispatch.persistence.PostgreSQLOutboxRepository
import com.pios.dispatch.persistence.PostgreSQLTestDatabase
import com.pios.dispatch.persistence.RabbitMQEventPublisher
import com.pios.dispatch.persistence.RabbitMQTestConnection
import com.pios.dispatch.persistence.RabbitMQTestObserverQueue
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves Dispatch's own outbox relay (Tranche 1: Dispatch Event
 * Publishing Completion; ADR-031, ADR-032) actually publishes pending
 * records to the real broker and marks each one published only once the
 * broker confirms, mirroring Order Management's own equivalent test
 * exactly.
 */
class OutboxRelayTest {

    private val outboxRepository = PostgreSQLOutboxRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
    private val eventPublisher = RabbitMQEventPublisher(RabbitTemplate(RabbitMQTestConnection.connectionFactory))
    private val relay = OutboxRelay(outboxRepository, eventPublisher)

    @Test
    fun `relay publishes a pending record and marks it published once the broker confirms`() {
        val observer = RabbitMQTestObserverQueue(RabbitMQTestConnection.connectionFactory)
        val marker = UUID.randomUUID().toString()
        val saved = outboxRepository.save(
            OutboxRecord(
                aggregateId = marker,
                eventType = "OrderAssigned",
                routingKey = "order.assigned",
                payload = """{"orderId":"$marker"}"""
            )
        )

        val relayed = relay.relay()

        assertTrue(relayed.any { it.id == saved.id })
        assertTrue(outboxRepository.findUnpublished().none { it.id == saved.id })
        assertEquals("""{"orderId":"$marker"}""", observer.receiveMessageContaining(marker))
    }
}
