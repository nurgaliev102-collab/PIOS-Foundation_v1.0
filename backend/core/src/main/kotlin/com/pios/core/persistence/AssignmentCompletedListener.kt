package com.pios.core.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.core.application.AssignmentCompletedRecordCommand
import com.pios.core.application.ParticipantHistoryProjectionApplicationService
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * The transport boundary for Core's consumption of dispatch's
 * `AssignmentCompleted` on the legacy routing key `assignment.completed`
 * (ADR-067 Current Authorized Inputs; binds the legacy key, not `trip.*`).
 * Mirrors dispatch's own `OrderCancelledListener` shape and discipline
 * exactly.
 *
 * Validates the envelope's `eventType` and `eventVersion` before
 * extracting anything: only `AssignmentCompleted` at
 * [SUPPORTED_EVENT_VERSION] is accepted. Any other event type (including
 * `AssignmentArrived` / `AssignmentStarted` / every `Trip*` — all Reserved
 * Future Inputs), an unsupported version, or a malformed envelope throws.
 *
 * Extracts only `payload.driverId` and `payload.orderId` — the fields
 * Slice 01's `RIDE_COMPLETED_AS_DRIVER` fact needs. `payload.statedPrice`
 * from the same payload is deliberately not read: it is a price, not a
 * participant-history fact. Never imports any dispatch type; never
 * fetches a missing value (ADR-067 Write Boundary).
 */
@Component
class AssignmentCompletedListener(
    private val applicationService: ParticipantHistoryProjectionApplicationService,
    private val objectMapper: ObjectMapper
) {

    @RabbitListener(
        queues = [RabbitMQDispatchTopologyConfiguration.QUEUE_NAME],
        containerFactory = "rabbitListenerContainerFactory"
    )
    fun onMessage(payload: String) {
        val envelope = objectMapper.readTree(payload)

        val eventType = envelope.get("eventType")?.asText()
        require(eventType == SUPPORTED_EVENT_TYPE) {
            "Unsupported event type for ${RabbitMQDispatchTopologyConfiguration.QUEUE_NAME}: '$eventType'"
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

        val driverReference = data.get("driverId")?.asText()
        require(!driverReference.isNullOrBlank()) { "Missing payload.driverId" }

        val orderReference = data.get("orderId")?.asText()
        require(!orderReference.isNullOrBlank()) { "Missing payload.orderId" }

        applicationService.handleAssignmentCompleted(
            AssignmentCompletedRecordCommand(
                eventId = eventId,
                driverReference = driverReference,
                orderReference = orderReference,
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
        const val SUPPORTED_EVENT_TYPE = "AssignmentCompleted"
        const val SUPPORTED_EVENT_VERSION = 1
    }
}
