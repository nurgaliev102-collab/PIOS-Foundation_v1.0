package com.pios.ordermanagement.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.time.Instant

/**
 * Test-only simulator of Dispatch's own AssignmentCompleted publisher
 * (`DispatchAssignmentApplicationService.outboxRecordFor(AssignmentId, AssignmentCompleted)`),
 * used to prove Order Management's own ADR-041 consumer path against a
 * real broker without any dependency -- test-scoped or otherwise -- on
 * Dispatch's code, mirroring [AssignmentAcceptedMessagePublisher] exactly.
 * Builds the same envelope shape Dispatch's own publisher produces
 * (`eventId`, `eventType`, `eventVersion`, `occurredAt`, `payload.orderId`,
 * `payload.driverId`) and publishes it to the real, shared
 * `dispatch.events` exchange at the real, ratified `assignment.completed`
 * routing key.
 */
internal class AssignmentCompletedMessagePublisher(connectionFactory: ConnectionFactory) {
    private val rabbitTemplate = RabbitTemplate(connectionFactory)
    private val objectMapper = ObjectMapper()

    fun publishAssignmentCompleted(
        orderReference: String,
        driverReference: String = "assignment-completed-driver",
        eventId: String = java.util.UUID.randomUUID().toString(),
        eventVersion: Int = 1,
        eventType: String = "AssignmentCompleted",
        routingKey: String = RabbitMQConsumerTopologyConfiguration.ASSIGNMENT_COMPLETED_ROUTING_KEY
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

    fun publishRaw(payload: String, routingKey: String = RabbitMQConsumerTopologyConfiguration.ASSIGNMENT_COMPLETED_ROUTING_KEY) {
        rabbitTemplate.invoke { operations ->
            operations.convertAndSend(RabbitMQConsumerTopologyConfiguration.PRODUCER_EXCHANGE_NAME, routingKey, payload)
            operations.waitForConfirmsOrDie(5000L)
        }
    }
}
