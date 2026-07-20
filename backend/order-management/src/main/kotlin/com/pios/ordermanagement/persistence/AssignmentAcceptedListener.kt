package com.pios.ordermanagement.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.ordermanagement.application.AssignmentAcceptedProjectionApplicationService
import com.pios.ordermanagement.application.AssignmentAcceptedUpdateCommand
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * The transport boundary for Order Management's consumption of
 * AssignmentAccepted (Tranche 1: Dispatch Event Publishing Completion).
 * The only place in this module a RabbitMQ/Spring AMQP type
 * ([org.springframework.amqp.rabbit.annotation.RabbitListener]) appears;
 * [AssignmentAcceptedProjectionApplicationService] and everything it
 * depends on remain completely unaware of RabbitMQ. Mirrors Dispatch's
 * own already-proven `DriverAvailabilityChangedListener` exactly.
 *
 * Validates the envelope's `eventType` and `eventVersion` before
 * extracting anything: only `"AssignmentAccepted"` at
 * [SUPPORTED_EVENT_VERSION] is accepted. Any other event type, an
 * unsupported version, or a structurally malformed envelope throws --
 * never silently ignored and never interpreted as the supported version
 * -- so [RabbitMQListenerContainerConfiguration]'s bounded retry policy
 * (ADR-031) applies uniformly to every kind of processing failure this
 * listener can encounter, and an exhausted retry routes the message to
 * Order Management's own dead-letter queue rather than discarding or
 * misinterpreting it.
 *
 * Extracts only `payload.orderId` and `payload.driverId` -- the fields
 * the Dispatch -> Order Management contract (INTERFACE_CONTRACTS.md
 * Section 5) actually justifies -- and translates them, together with the
 * envelope's own `eventId`, into a primitive-typed
 * [AssignmentAcceptedUpdateCommand] before calling the application
 * service. Never imports any Dispatch type.
 *
 * Not catching any exception here is deliberate: letting it propagate is
 * what lets the container's retry-then-recoverer advice
 * ([RabbitMQListenerContainerConfiguration]) decide the message's fate,
 * and what guarantees a message is never acknowledged as processed if the
 * application service's own transaction failed.
 */
@Component
class AssignmentAcceptedListener(
    private val applicationService: AssignmentAcceptedProjectionApplicationService,
    private val objectMapper: ObjectMapper
) {

    @RabbitListener(
        queues = [RabbitMQConsumerTopologyConfiguration.QUEUE_NAME],
        containerFactory = "rabbitListenerContainerFactory"
    )
    fun onMessage(payload: String) {
        val envelope = objectMapper.readTree(payload)

        val eventType = envelope.get("eventType")?.asText()
        require(eventType == SUPPORTED_EVENT_TYPE) {
            "Unsupported event type for ${RabbitMQConsumerTopologyConfiguration.QUEUE_NAME}: '$eventType'"
        }

        val eventVersion = envelope.get("eventVersion")?.asInt()
        require(eventVersion == SUPPORTED_EVENT_VERSION) {
            "Unsupported $SUPPORTED_EVENT_TYPE eventVersion: $eventVersion (supported: $SUPPORTED_EVENT_VERSION)"
        }

        val eventId = envelope.get("eventId")?.asText()
        require(!eventId.isNullOrBlank()) { "Missing eventId" }

        val data = envelope.get("payload")
        requireNotNull(data) { "Missing payload" }

        val orderReference = data.get("orderId")?.asText()
        require(!orderReference.isNullOrBlank()) { "Missing payload.orderId" }

        val driverReference = data.get("driverId")?.asText()
        require(!driverReference.isNullOrBlank()) { "Missing payload.driverId" }

        applicationService.handle(
            AssignmentAcceptedUpdateCommand(
                eventId = eventId,
                orderReference = orderReference,
                driverReference = driverReference
            )
        )
    }

    companion object {
        const val SUPPORTED_EVENT_TYPE = "AssignmentAccepted"
        const val SUPPORTED_EVENT_VERSION = 1
    }
}
