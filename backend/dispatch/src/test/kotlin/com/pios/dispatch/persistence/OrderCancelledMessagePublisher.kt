package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.time.Instant

/**
 * Test-only simulator of Order Management's own OrderCancelled publisher,
 * used to prove Dispatch's consumer path (ADR-053, Proposal Resolution on
 * Order Cancellation — Accepted) against a real broker without any
 * dependency -- test-scoped or otherwise -- on Order Management's code.
 * Builds exactly the envelope shape
 * `OrderLifecycleApplicationService.envelopeFor` produces (verified
 * against its actual source: `eventId`, `eventType`, `eventVersion`,
 * `occurredAt`, `payload.orderId`) and publishes it to the real, shared
 * `order-management.events` exchange at the real, already-ratified
 * `order.cancelled` routing key -- exactly as the real producer would,
 * letting Dispatch's real consumer topology and listener handle it
 * exactly as they would a genuine message. Mirrors
 * [DriverAvailabilityChangedMessagePublisher] exactly.
 */
internal class OrderCancelledMessagePublisher(connectionFactory: ConnectionFactory) {
    private val rabbitTemplate = RabbitTemplate(connectionFactory)
    private val objectMapper = ObjectMapper()

    fun publishOrderCancelled(
        orderReference: String,
        eventId: String = java.util.UUID.randomUUID().toString(),
        eventVersion: Int = 1,
        eventType: String = "OrderCancelled",
        routingKey: String = RabbitMQOrderManagementTopologyConfiguration.ORDER_CANCELLED_ROUTING_KEY
    ) {
        val envelope = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to eventId,
                "eventType" to eventType,
                "eventVersion" to eventVersion,
                "occurredAt" to Instant.now().toString(),
                "payload" to mapOf("orderId" to orderReference)
            )
        )
        publishRaw(envelope, routingKey)
    }

    fun publishRaw(payload: String, routingKey: String = RabbitMQOrderManagementTopologyConfiguration.ORDER_CANCELLED_ROUTING_KEY) {
        rabbitTemplate.invoke { operations ->
            operations.convertAndSend(RabbitMQOrderManagementTopologyConfiguration.PRODUCER_EXCHANGE_NAME, routingKey, payload)
            operations.waitForConfirmsOrDie(5000L)
        }
    }
}
