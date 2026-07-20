package com.pios.ordermanagement.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.time.Instant

/**
 * Test-only simulator of Dispatch's own AssignmentAccepted publisher,
 * used to prove Order Management's consumer path against a real broker
 * without any dependency -- test-scoped or otherwise -- on Dispatch's
 * code (a hard constraint mirrored from Dispatch Consumer Foundation
 * v1.0, applied here for Tranche 1: Dispatch Event Publishing
 * Completion). Builds exactly the envelope shape Dispatch's own
 * `DispatchAssignmentApplicationService.envelopeFor` produces (`eventId`,
 * `eventType`, `eventVersion`, `occurredAt`, `payload.orderId`,
 * `payload.driverId`) and publishes it to the real, shared
 * `dispatch.events` exchange at the real, already-ratified
 * `assignment.accepted` routing key -- exactly as the real producer
 * would, letting Order Management's real consumer topology and listener
 * handle it exactly as they would a genuine message.
 */
internal class AssignmentAcceptedMessagePublisher(connectionFactory: ConnectionFactory) {
    private val rabbitTemplate = RabbitTemplate(connectionFactory)
    private val objectMapper = ObjectMapper()

    fun publishAssignmentAccepted(
        orderReference: String,
        driverReference: String,
        eventId: String = java.util.UUID.randomUUID().toString(),
        eventVersion: Int = 1,
        eventType: String = "AssignmentAccepted",
        routingKey: String = RabbitMQConsumerTopologyConfiguration.ASSIGNMENT_ACCEPTED_ROUTING_KEY
    ) {
        val envelope = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to eventId,
                "eventType" to eventType,
                "eventVersion" to eventVersion,
                "occurredAt" to Instant.now().toString(),
                "payload" to mapOf(
                    "orderId" to orderReference,
                    "driverId" to driverReference
                )
            )
        )
        publishRaw(envelope, routingKey)
    }

    fun publishRaw(payload: String, routingKey: String = RabbitMQConsumerTopologyConfiguration.ASSIGNMENT_ACCEPTED_ROUTING_KEY) {
        rabbitTemplate.invoke { operations ->
            operations.convertAndSend(RabbitMQConsumerTopologyConfiguration.PRODUCER_EXCHANGE_NAME, routingKey, payload)
            operations.waitForConfirmsOrDie(5000L)
        }
    }
}
