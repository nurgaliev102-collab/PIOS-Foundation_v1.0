package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.DriverAvailabilityProjectionApplicationService
import com.pios.dispatch.application.DriverAvailabilityUpdateCommand
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * The transport boundary for Dispatch's consumption of
 * DriverAvailabilityChanged (Dispatch Consumer Foundation v1.0). The only
 * place in this module a RabbitMQ/Spring AMQP type
 * ([org.springframework.amqp.rabbit.annotation.RabbitListener]) appears;
 * [DriverAvailabilityProjectionApplicationService] and everything it
 * depends on remain completely unaware of RabbitMQ.
 *
 * Validates the envelope's `eventType` and `eventVersion` before
 * extracting anything: only `"DriverAvailabilityChanged"` at
 * [SUPPORTED_EVENT_VERSION] is accepted. Any other event type, an
 * unsupported version, or a structurally malformed envelope throws --
 * never silently ignored and never interpreted as the supported version
 * -- so [RabbitMQListenerContainerConfiguration]'s bounded retry policy
 * (ADR-031) applies uniformly to every kind of processing failure this
 * listener can encounter, and an exhausted retry routes the message to
 * Dispatch's own dead-letter queue rather than discarding or
 * misinterpreting it.
 *
 * Extracts only `payload.driverId` and `payload.availability` -- the
 * fields the Driver Management -> Dispatch contract
 * (INTERFACE_CONTRACTS.md Section 5) actually justifies -- and translates
 * them, together with the envelope's own `eventId`, into a primitive-typed
 * [DriverAvailabilityUpdateCommand] before calling the application
 * service. Never imports any Driver Management type.
 *
 * Not catching any exception here is deliberate: letting it propagate is
 * what lets the container's retry-then-recoverer advice
 * ([RabbitMQListenerContainerConfiguration]) decide the message's fate,
 * and what guarantees a message is never acknowledged as processed if the
 * application service's own transaction failed.
 */
@Component
class DriverAvailabilityChangedListener(
    private val applicationService: DriverAvailabilityProjectionApplicationService,
    private val objectMapper: ObjectMapper
) {

    @RabbitListener(
        queues = [RabbitMQTopologyConfiguration.QUEUE_NAME],
        containerFactory = "rabbitListenerContainerFactory"
    )
    fun onMessage(payload: String) {
        val envelope = objectMapper.readTree(payload)

        val eventType = envelope.get("eventType")?.asText()
        require(eventType == SUPPORTED_EVENT_TYPE) {
            "Unsupported event type for ${RabbitMQTopologyConfiguration.QUEUE_NAME}: '$eventType'"
        }

        val eventVersion = envelope.get("eventVersion")?.asInt()
        require(eventVersion == SUPPORTED_EVENT_VERSION) {
            "Unsupported $SUPPORTED_EVENT_TYPE eventVersion: $eventVersion (supported: $SUPPORTED_EVENT_VERSION)"
        }

        val eventId = envelope.get("eventId")?.asText()
        require(!eventId.isNullOrBlank()) { "Missing eventId" }

        val data = envelope.get("payload")
        requireNotNull(data) { "Missing payload" }

        val driverReference = data.get("driverId")?.asText()
        require(!driverReference.isNullOrBlank()) { "Missing payload.driverId" }

        val availability = data.get("availability")?.asText()
        val available = when (availability) {
            "AVAILABLE" -> true
            "UNAVAILABLE" -> false
            else -> throw IllegalArgumentException("Unsupported payload.availability value: '$availability'")
        }

        applicationService.handle(
            DriverAvailabilityUpdateCommand(
                eventId = eventId,
                driverReference = driverReference,
                available = available
            )
        )
    }

    companion object {
        const val SUPPORTED_EVENT_TYPE = "DriverAvailabilityChanged"
        const val SUPPORTED_EVENT_VERSION = 1
    }
}
