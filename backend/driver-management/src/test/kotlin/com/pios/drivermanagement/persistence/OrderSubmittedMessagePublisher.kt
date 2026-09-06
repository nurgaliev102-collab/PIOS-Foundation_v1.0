package com.pios.drivermanagement.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate

/**
 * Growth Loops TZ v1, Phase 2 extension (docs/PIOS_GROWTH_LOOPS_TZ_V1.md
 * Section 2.1's own disclosed gap, now closed): test-only simulator of
 * Order Management's own OrderSubmitted publisher, mirroring
 * [AssignmentCompletedMessagePublisher] exactly. Publishes to the
 * **test-vhost** `order-management.events` exchange
 * ([RabbitMQTestConnection]) -- never the real, shared production exchange.
 */
internal class OrderSubmittedMessagePublisher(connectionFactory: ConnectionFactory) {
    private val rabbitTemplate = RabbitTemplate(connectionFactory)
    private val objectMapper = ObjectMapper()

    fun publishOrderSubmitted(
        orderId: String,
        passengerReference: String,
        eventId: String = UUID.randomUUID().toString(),
        eventVersion: Int = 1,
        eventType: String = "OrderSubmitted",
        routingKey: String = RabbitMQOrderManagementTopologyConfiguration.ORDER_SUBMITTED_ROUTING_KEY
    ) {
        val envelope = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to eventId,
                "eventType" to eventType,
                "eventVersion" to eventVersion,
                "occurredAt" to Instant.now().toString(),
                "payload" to mapOf(
                    "orderId" to orderId,
                    "passengerReference" to passengerReference
                )
            )
        )
        rabbitTemplate.invoke { operations ->
            operations.convertAndSend(RabbitMQOrderManagementTopologyConfiguration.PRODUCER_EXCHANGE_NAME, routingKey, envelope)
            operations.waitForConfirmsOrDie(5000L)
        }
    }
}
