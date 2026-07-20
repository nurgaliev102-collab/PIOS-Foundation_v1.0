package com.pios.dispatch.persistence

import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves [RabbitMQEventPublisher] actually delivers a message to
 * Dispatch's own exchange on a real, running RabbitMQ broker (ADR-029,
 * ADR-031; Tranche 1: Dispatch Event Publishing Completion) — not merely
 * that it constructs correctly.
 */
class RabbitMQEventPublisherTest {

    private val connectionFactory = RabbitMQTestConnection.connectionFactory
    private val publisher = RabbitMQEventPublisher(RabbitTemplate(connectionFactory))

    @Test
    fun `a published message actually arrives on Dispatch's own exchange`() {
        val observer = RabbitMQTestObserverQueue(connectionFactory)
        val marker = UUID.randomUUID().toString()
        val payload = """{"orderId":"$marker"}"""

        publisher.publish("order.assigned", payload)

        assertEquals(payload, observer.receiveMessageContaining(marker))
    }
}
