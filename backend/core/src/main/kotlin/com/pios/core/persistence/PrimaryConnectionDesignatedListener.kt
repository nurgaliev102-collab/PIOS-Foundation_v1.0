package com.pios.core.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.core.application.ParticipantHistoryProjectionApplicationService
import com.pios.core.application.PrimaryConnectionDesignatedRecordCommand
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * The transport boundary for Core's consumption of passenger-experience's
 * `PrimaryConnectionDesignated` (ADR-067 Current Authorized Inputs).
 * Mirrors dispatch's own `PrimaryConnectionEventListener` shape — but
 * **only the designated case**: `PrimaryConnectionCleared` is a Reserved
 * Future Input in ADR-067, so this listener accepts only
 * `PrimaryConnectionDesignated` and throws on
 * `PrimaryConnectionCleared` (and everything else).
 *
 * Extracts `payload.passengerReference` and `payload.driverId` — both
 * participant references the one event concerns (ADR-067 Input Events).
 * Never imports any passenger-experience type; never fetches a missing
 * value (ADR-067 Write Boundary).
 */
@Component
class PrimaryConnectionDesignatedListener(
    private val applicationService: ParticipantHistoryProjectionApplicationService,
    private val objectMapper: ObjectMapper
) {

    @RabbitListener(
        queues = [RabbitMQPassengerExperienceTopologyConfiguration.QUEUE_NAME],
        containerFactory = "rabbitListenerContainerFactory"
    )
    fun onMessage(payload: String) {
        val envelope = objectMapper.readTree(payload)

        val eventType = envelope.get("eventType")?.asText()
        require(eventType == SUPPORTED_EVENT_TYPE) {
            "Unsupported event type for ${RabbitMQPassengerExperienceTopologyConfiguration.QUEUE_NAME}: '$eventType'"
        }

        val eventVersion = envelope.get("eventVersion")?.asInt()
        require(eventVersion == SUPPORTED_EVENT_VERSION) {
            "Unsupported $SUPPORTED_EVENT_TYPE eventVersion: $eventVersion (supported: $SUPPORTED_EVENT_VERSION)"
        }

        val eventId = envelope.get("eventId")?.asText()
        require(!eventId.isNullOrBlank()) { "Missing eventId" }

        val occurredAtText = envelope.get("occurredAt")?.asText()
        require(!occurredAtText.isNullOrBlank()) { "Missing occurredAt" }
        val occurredAt = parseOccurredAt(occurredAtText)

        val data = envelope.get("payload")
        requireNotNull(data) { "Missing payload" }

        val passengerReference = data.get("passengerReference")?.asText()
        require(!passengerReference.isNullOrBlank()) { "Missing payload.passengerReference" }

        val driverReference = data.get("driverId")?.asText()
        require(!driverReference.isNullOrBlank()) { "Missing payload.driverId" }

        applicationService.handlePrimaryConnectionDesignated(
            PrimaryConnectionDesignatedRecordCommand(
                eventId = eventId,
                passengerReference = passengerReference,
                driverReference = driverReference,
                occurredAt = occurredAt
            )
        )
    }

    private fun parseOccurredAt(text: String): Instant =
        try {
            Instant.parse(text)
        } catch (ex: Exception) {
            throw IllegalArgumentException("Unparseable occurredAt: '$text'", ex)
        }

    companion object {
        const val SUPPORTED_EVENT_TYPE = "PrimaryConnectionDesignated"
        const val SUPPORTED_EVENT_VERSION = 1
    }
}
