package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.TrustedDriverEstablishedCommand
import com.pios.dispatch.application.TrustedDriverProjectionApplicationService
import com.pios.dispatch.application.TrustedDriverRemovedCommand
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * The transport boundary for Dispatch's consumption of
 * `ConnectionEstablished`/`ConnectionRemoved` (ADR-068,
 * Relationship-Ordered Fallback Dispatch — Trusted → Network → Open
 * Marketplace, Part 1). The only place in this class a RabbitMQ/Spring
 * AMQP type ([org.springframework.amqp.rabbit.annotation.RabbitListener])
 * appears; [TrustedDriverProjectionApplicationService] and everything it
 * depends on remain completely unaware of RabbitMQ. Mirrors
 * [PrimaryConnectionEventListener]'s own exact shape and discipline.
 *
 * Validates the envelope's `eventType` and `eventVersion` before
 * extracting anything: only `"ConnectionEstablished"` or
 * `"ConnectionRemoved"`, each at [SUPPORTED_EVENT_VERSION], is accepted.
 * Any other event type, an unsupported version, or a structurally
 * malformed envelope throws — never silently ignored and never
 * misinterpreted — so [RabbitMQListenerContainerConfiguration]'s bounded
 * retry policy applies uniformly, and an exhausted retry routes the
 * message to this listener's own dead-letter queue
 * ([RabbitMQPassengerExperienceTrustedTopologyConfiguration.DEAD_LETTER_QUEUE_NAME])
 * rather than discarding or misinterpreting it.
 *
 * Extracts only `payload.passengerReference` and `payload.driverId` — the
 * fields the Passenger Experience → Dispatch contract (ADR-068 Part 1)
 * actually justifies — and translates them, together with the envelope's
 * own `eventId`, into a primitive-typed command before calling the
 * application service. Never imports any Passenger Experience type.
 */
@Component
class TrustedDriverEventListener(
    private val applicationService: TrustedDriverProjectionApplicationService,
    private val objectMapper: ObjectMapper
) {

    @RabbitListener(
        queues = [RabbitMQPassengerExperienceTrustedTopologyConfiguration.QUEUE_NAME],
        containerFactory = "rabbitListenerContainerFactory"
    )
    fun onMessage(payload: String) {
        val envelope = objectMapper.readTree(payload)

        val eventType = envelope.get("eventType")?.asText()
        require(eventType == ESTABLISHED_EVENT_TYPE || eventType == REMOVED_EVENT_TYPE) {
            "Unsupported event type for ${RabbitMQPassengerExperienceTrustedTopologyConfiguration.QUEUE_NAME}: '$eventType'"
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

        val driverId = data.get("driverId")?.asText()
        require(!driverId.isNullOrBlank()) { "Missing payload.driverId" }

        when (eventType) {
            ESTABLISHED_EVENT_TYPE -> {
                applicationService.handleEstablished(
                    TrustedDriverEstablishedCommand(
                        eventId = eventId,
                        passengerReference = passengerReference,
                        driverId = driverId
                    )
                )
            }
            REMOVED_EVENT_TYPE -> {
                applicationService.handleRemoved(
                    TrustedDriverRemovedCommand(
                        eventId = eventId,
                        passengerReference = passengerReference,
                        driverId = driverId
                    )
                )
            }
        }
    }

    companion object {
        const val ESTABLISHED_EVENT_TYPE = "ConnectionEstablished"
        const val REMOVED_EVENT_TYPE = "ConnectionRemoved"
        const val SUPPORTED_EVENT_VERSION = 1
    }
}
