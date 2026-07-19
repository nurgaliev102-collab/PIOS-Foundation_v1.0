package com.pios.drivermanagement.application

import com.pios.drivermanagement.persistence.PostgreSQLOutboxRepository
import com.pios.drivermanagement.persistence.PostgreSQLTestDatabase
import com.pios.drivermanagement.persistence.RabbitMQEventPublisher
import com.pios.drivermanagement.persistence.RabbitMQTestConnection
import com.pios.drivermanagement.persistence.RabbitMQTestObserverQueue
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves the outbox relay (Driver Management Event Publishing v1.0;
 * ADR-031, ADR-032) actually publishes pending records to the real broker
 * and marks each one published only once the broker confirms, and that a
 * failed publish leaves the record pending without silently discarding it
 * -- applying the pattern already hardened for Order Management (RabbitMQ
 * Event Publishing Foundation v1.0 Hardening Review).
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
                eventType = "DriverAvailabilityChanged",
                routingKey = "driver.availability.changed",
                payload = """{"driverId":"$marker"}"""
            )
        )

        val relayed = relay.relay()

        assertTrue(relayed.any { it.id == saved.id })
        assertTrue(outboxRepository.findUnpublished().none { it.id == saved.id })
        assertEquals("""{"driverId":"$marker"}""", observer.receiveMessageContaining(marker))
    }

    @Test
    fun `a failed publish leaves the record pending and does not silently discard it`() {
        val marker = UUID.randomUUID().toString()
        val saved = outboxRepository.save(
            OutboxRecord(
                aggregateId = marker,
                eventType = "DriverAvailabilityChanged",
                routingKey = "driver.availability.changed",
                payload = """{"driverId":"$marker"}"""
            )
        )
        val failingPublisher = object : EventPublisher {
            override fun publish(routingKey: String, payload: String) {
                if (payload.contains(marker)) throw RuntimeException("simulated broker confirm failure")
            }
        }
        val failingRelay = OutboxRelay(outboxRepository, failingPublisher)

        val relayed = failingRelay.relay()

        assertTrue(relayed.none { it.id == saved.id })
        assertTrue(outboxRepository.findUnpublished().any { it.id == saved.id })
    }
}
