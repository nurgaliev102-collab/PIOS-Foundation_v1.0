package com.pios.core.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.time.Instant
import java.util.UUID

/**
 * Test-only simulator of order-management's own event publisher, used to
 * prove Core's consumer path against a real broker (isolated `pios-test`
 * vhost) without any dependency — test-scoped or otherwise — on
 * order-management's code. Builds exactly the envelope shape
 * `OrderLifecycleApplicationService` produces (verified against its actual
 * source) and publishes it to the `order-management.events` exchange at
 * the real, already-ratified routing keys. Mirrors dispatch's own
 * `OrderCancelledMessagePublisher`.
 *
 * This is a TEST publisher. Core's own production code publishes nothing
 * (ADR-067 Event Boundary) — see [com.pios.core.CoreBoundaryStaticTest].
 *
 * Publishes only to the QA RabbitMQ namespace supplied by
 * [RabbitMQTestConnection] → the fail-closed
 * [com.pios.core.qa.CoreQaSafetyGate] (ADR-067 QA / Production Gate item 4).
 */
internal class OrderEventMessagePublisher(connectionFactory: ConnectionFactory) {
    private val rabbitTemplate = RabbitTemplate(connectionFactory)
    private val objectMapper = ObjectMapper()

    fun publishOrderSubmitted(
        orderId: String,
        passengerReference: String,
        eventId: String = UUID.randomUUID().toString(),
        occurredAt: Instant = Instant.now()
    ) = publishRaw(
        routingKey = RabbitMQOrderManagementTopologyConfiguration.ORDER_SUBMITTED_ROUTING_KEY,
        envelope = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to eventId,
                "eventType" to "OrderSubmitted",
                "eventVersion" to 1,
                "occurredAt" to occurredAt.toString(),
                "payload" to mapOf(
                    "orderId" to orderId,
                    "passengerReference" to passengerReference,
                    "explicitDriverIntent" to null,
                    "isTest" to true
                )
            )
        )
    )

    fun publishOrderCompleted(
        orderId: String,
        eventId: String = UUID.randomUUID().toString(),
        occurredAt: Instant = Instant.now()
    ) = publishRaw(
        routingKey = RabbitMQOrderManagementTopologyConfiguration.ORDER_COMPLETED_ROUTING_KEY,
        envelope = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to eventId,
                "eventType" to "OrderCompleted",
                "eventVersion" to 1,
                "occurredAt" to occurredAt.toString(),
                "payload" to mapOf("orderId" to orderId)
            )
        )
    )

    fun publishRaw(envelope: String, routingKey: String) {
        rabbitTemplate.invoke { operations ->
            operations.convertAndSend(RabbitMQOrderManagementTopologyConfiguration.PRODUCER_EXCHANGE_NAME, routingKey, envelope)
            operations.waitForConfirmsOrDie(5000L)
        }
    }
}
