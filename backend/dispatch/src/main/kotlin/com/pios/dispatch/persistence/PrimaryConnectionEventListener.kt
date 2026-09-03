package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.PrimaryDriverClearedCommand
import com.pios.dispatch.application.PrimaryDriverDesignatedCommand
import com.pios.dispatch.application.PrimaryDriverProjectionApplicationService
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * The transport boundary for Dispatch's consumption of
 * `PrimaryConnectionDesignated`/`PrimaryConnectionCleared` (ADR-062; Task
 * 14, First Refusal Foundation). The only place in this class a RabbitMQ/
 * Spring AMQP type ([org.springframework.amqp.rabbit.annotation.RabbitListener])
 * appears; [PrimaryDriverProjectionApplicationService] and everything it
 * depends on remain completely unaware of RabbitMQ. Mirrors
 * [DriverAvailabilityChangedListener]'s own exact shape and discipline.
 *
 * Validates the envelope's `eventType` and `eventVersion` before
 * extracting anything: only `"PrimaryConnectionDesignated"` or
 * `"PrimaryConnectionCleared"`, each at [SUPPORTED_EVENT_VERSION], is
 * accepted. Any other event type, an unsupported version, or a
 * structurally malformed envelope throws — never silently ignored and
 * never misinterpreted — so [RabbitMQListenerContainerConfiguration]'s
 * bounded retry policy applies uniformly, and an exhausted retry routes
 * the message to Dispatch's own dead-letter queue
 * ([RabbitMQPassengerExperienceTopologyConfiguration.DEAD_LETTER_QUEUE_NAME])
 * rather than discarding or misinterpreting it.
 *
 * Extracts only `payload.passengerReference` and (for the designated
 * case) `payload.driverId` — the fields the Passenger Experience →
 * Dispatch contract (ADR-062) actually justifies — and translates them,
 * together with the envelope's own `eventId`, into a primitive-typed
 * command before calling the application service. Never imports any
 * Passenger Experience type.
 *
 * Not catching any exception here is deliberate — see
 * [DriverAvailabilityChangedListener]'s own KDoc for why letting it
 * propagate is what makes the container's retry-then-recoverer advice
 * work correctly.
 */
@Component
class PrimaryConnectionEventListener(
    private val applicationService: PrimaryDriverProjectionApplicationService,
    private val objectMapper: ObjectMapper
) {

    @RabbitListener(
        queues = [RabbitMQPassengerExperienceTopologyConfiguration.QUEUE_NAME],
        containerFactory = "rabbitListenerContainerFactory"
    )
    fun onMessage(payload: String) {
        val envelope = objectMapper.readTree(payload)

        val eventType = envelope.get("eventType")?.asText()
        require(eventType == DESIGNATED_EVENT_TYPE || eventType == CLEARED_EVENT_TYPE) {
            "Unsupported event type for ${RabbitMQPassengerExperienceTopologyConfiguration.QUEUE_NAME}: '$eventType'"
        }

        val eventVersion = envelope.get("eventVersion")?.asInt()
        require(eventVersion == SUPPORTED_EVENT_VERSION) {
            "Unsupported $eventType eventVersion: $eventVersion (supported: $SUPPORTED_EVENT_VERSION)"
        }

        val eventId = envelope.get("eventId")?.asText()
        require(!eventId.isNullOrBlank()) { "Missing eventId" }

        val data = envelope.get("payload")
        requireNotNull(data) { "Missing payload" }

        val passengerReference = data.get("passengerReference")?.asText()
        require(!passengerReference.isNullOrBlank()) { "Missing payload.passengerReference" }

        when (eventType) {
            DESIGNATED_EVENT_TYPE -> {
                val driverId = data.get("driverId")?.asText()
                require(!driverId.isNullOrBlank()) { "Missing payload.driverId" }
                applicationService.handleDesignated(
                    PrimaryDriverDesignatedCommand(
                        eventId = eventId,
                        passengerReference = passengerReference,
                        driverId = driverId
                    )
                )
            }
            CLEARED_EVENT_TYPE -> {
                applicationService.handleCleared(
                    PrimaryDriverClearedCommand(
                        eventId = eventId,
                        passengerReference = passengerReference
                    )
                )
            }
        }
    }

    companion object {
        const val DESIGNATED_EVENT_TYPE = "PrimaryConnectionDesignated"
        const val CLEARED_EVENT_TYPE = "PrimaryConnectionCleared"
        const val SUPPORTED_EVENT_VERSION = 1
    }
}
