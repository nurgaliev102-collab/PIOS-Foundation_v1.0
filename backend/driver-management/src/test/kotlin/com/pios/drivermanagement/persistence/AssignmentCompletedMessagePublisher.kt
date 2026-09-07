package com.pios.drivermanagement.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate

/**
 * Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2):
 * test-only simulator of Dispatch's own AssignmentCompleted publisher,
 * mirroring Order Management's own `AssignmentCompletedMessagePublisher`
 * exactly. Builds the same envelope shape Dispatch's own publisher
 * produces (`eventId`, `eventType`, `eventVersion`, `occurredAt`,
 * `payload.orderId`, `payload.driverId`) and publishes it to the
 * **test-vhost** `dispatch.events` exchange ([RabbitMQTestConnection]) --
 * never the real, shared production exchange.
 */
internal class AssignmentCompletedMessagePublisher(connectionFactory: ConnectionFactory) {
    private val rabbitTemplate = RabbitTemplate(connectionFactory)
    private val objectMapper = ObjectMapper()

    fun publishAssignmentCompleted(
        driverId: String,
        orderReference: String = "assignment-completed-order",
        eventId: String = UUID.randomUUID().toString(),
        occurredAt: Instant = Instant.now(),
        eventVersion: Int = 1,
        eventType: String = "AssignmentCompleted",
        routingKey: String = RabbitMQConsumerTopologyConfiguration.ASSIGNMENT_COMPLETED_ROUTING_KEY,
        statedPrice: String? = null
    ) {
        val envelope = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to eventId,
                "eventType" to eventType,
                "eventVersion" to eventVersion,
                "occurredAt" to occurredAt.toString(),
                "payload" to mapOf(
                    "orderId" to orderReference,
                    "driverId" to driverId,
                    "statedPrice" to statedPrice
                )
            )
        )
        rabbitTemplate.invoke { operations ->
            operations.convertAndSend(RabbitMQConsumerTopologyConfiguration.PRODUCER_EXCHANGE_NAME, routingKey, envelope)
            operations.waitForConfirmsOrDie(5000L)
        }
    }
}
