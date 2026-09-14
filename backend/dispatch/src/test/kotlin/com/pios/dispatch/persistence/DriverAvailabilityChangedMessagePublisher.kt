package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.time.Instant

/**
 * Test-only simulator of Driver Management's own DriverAvailabilityChanged
 * publisher, used to prove Dispatch's consumer path against a real broker
 * without any dependency -- test-scoped or otherwise -- on Driver
 * Management's code (a hard constraint of Dispatch Consumer Foundation
 * v1.0). Builds exactly the envelope shape Driver Management's own
 * `envelopeFor` produces (verified against its actual source at commit
 * 9d7cea9: `eventId`, `eventType`, `eventVersion`, `occurredAt`,
 * `payload.driverId`, `payload.availability`) and publishes it to the
 * real, shared `driver-management.events` exchange at the real,
 * already-ratified `driver.availability.changed` routing key -- exactly
 * as the real producer would, letting Dispatch's real consumer topology
 * and listener handle it exactly as they would a genuine message.
 */
internal class DriverAvailabilityChangedMessagePublisher(connectionFactory: ConnectionFactory) {
    private val rabbitTemplate = RabbitTemplate(connectionFactory)
    private val objectMapper = ObjectMapper()

    /**
     * [isTest] (ADR-069) defaults to `null` -- meaning "omit `payload.isTest`
     * entirely," the exact shape a not-yet-upgraded Driver Management
     * publisher would send, and the case
     * [com.pios.dispatch.persistence.DriverAvailabilityChangedListener]'s
     * own tolerant-parse must turn into "unknown," not "false." Passing
     * `true`/`false` explicitly includes the field with that value instead.
     */
    fun publishDriverAvailabilityChanged(
        driverReference: String,
        available: Boolean,
        isTest: Boolean? = null,
        eventId: String = java.util.UUID.randomUUID().toString(),
        eventVersion: Int = 1,
        eventType: String = "DriverAvailabilityChanged",
        routingKey: String = RabbitMQTopologyConfiguration.DRIVER_AVAILABILITY_CHANGED_ROUTING_KEY
    ) {
        val payload = mutableMapOf<String, Any>(
            "driverId" to driverReference,
            "availability" to if (available) "AVAILABLE" else "UNAVAILABLE"
        )
        if (isTest != null) {
            payload["isTest"] = isTest
        }
        val envelope = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to eventId,
                "eventType" to eventType,
                "eventVersion" to eventVersion,
                "occurredAt" to Instant.now().toString(),
                "payload" to payload
            )
        )
        publishRaw(envelope, routingKey)
    }

    fun publishRaw(payload: String, routingKey: String = RabbitMQTopologyConfiguration.DRIVER_AVAILABILITY_CHANGED_ROUTING_KEY) {
        rabbitTemplate.invoke { operations ->
            operations.convertAndSend(RabbitMQTopologyConfiguration.PRODUCER_EXCHANGE_NAME, routingKey, payload)
            operations.waitForConfirmsOrDie(5000L)
        }
    }
}
