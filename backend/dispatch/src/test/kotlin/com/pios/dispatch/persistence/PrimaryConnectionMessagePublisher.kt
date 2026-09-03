package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.time.Instant
import java.util.UUID

/**
 * Test-only simulator of Passenger Experience's own
 * `PrimaryConnectionDesignated`/`PrimaryConnectionCleared` publisher
 * (Task 14, First Refusal Foundation), used to prove Dispatch's consumer
 * path against a real broker without any dependency -- test-scoped or
 * otherwise -- on Passenger Experience's own code, mirroring
 * [DriverAvailabilityChangedMessagePublisher]'s own exact precedent.
 * Builds exactly the envelope shape
 * `SetPrimaryConnectionApplicationService.envelopeFor`/
 * `RemoveConnectionApplicationService.envelopeFor` produce (`eventId`,
 * `eventType`, `eventVersion`, `occurredAt`, `payload.passengerReference`,
 * `payload.driverId` for the designated case) and publishes it to the
 * real, shared `passenger-experience.events` exchange at the real,
 * already-ratified routing keys -- exactly as the real producer would,
 * letting Dispatch's real consumer topology and listener handle it
 * exactly as they would a genuine message.
 */
internal class PrimaryConnectionMessagePublisher(connectionFactory: ConnectionFactory) {
    private val rabbitTemplate = RabbitTemplate(connectionFactory)
    private val objectMapper = ObjectMapper()

    fun publishDesignated(
        passengerReference: String,
        driverId: String,
        eventId: String = UUID.randomUUID().toString(),
        eventVersion: Int = 1
    ) {
        val envelope = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to eventId,
                "eventType" to "PrimaryConnectionDesignated",
                "eventVersion" to eventVersion,
                "occurredAt" to Instant.now().toString(),
                "payload" to mapOf(
                    "passengerReference" to passengerReference,
                    "driverId" to driverId
                )
            )
        )
        publishRaw(envelope, RabbitMQPassengerExperienceTopologyConfiguration.PRIMARY_CONNECTION_DESIGNATED_ROUTING_KEY)
    }

    fun publishCleared(
        passengerReference: String,
        eventId: String = UUID.randomUUID().toString(),
        eventVersion: Int = 1
    ) {
        val envelope = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to eventId,
                "eventType" to "PrimaryConnectionCleared",
                "eventVersion" to eventVersion,
                "occurredAt" to Instant.now().toString(),
                "payload" to mapOf("passengerReference" to passengerReference)
            )
        )
        publishRaw(envelope, RabbitMQPassengerExperienceTopologyConfiguration.PRIMARY_CONNECTION_CLEARED_ROUTING_KEY)
    }

    private fun publishRaw(payload: String, routingKey: String) {
        rabbitTemplate.invoke { operations ->
            operations.convertAndSend(RabbitMQPassengerExperienceTopologyConfiguration.PRODUCER_EXCHANGE_NAME, routingKey, payload)
            operations.waitForConfirmsOrDie(5000L)
        }
    }
}
