package com.pios.drivermanagement.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.drivermanagement.application.AssignmentCompletedApplicationService
import com.pios.drivermanagement.application.AssignmentCompletedUpdateCommand
import java.time.Instant
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2):
 * the transport boundary for Driver Management's own, independent
 * consumption of `AssignmentCompleted` -- mirrors Order Management's own
 * `AssignmentCompletedListener` exactly: validates `eventType`/`eventVersion`
 * before extracting anything, throws (rather than silently ignoring) on
 * anything unexpected so the bounded retry-then-dead-letter policy applies
 * uniformly, and never imports any Dispatch type.
 *
 * Extracts `payload.driverId`, `payload.orderId`, and the envelope's own
 * `occurredAt`. `orderId` (Phase 2 extension, docs/PIOS_GROWTH_LOOPS_TZ_V1.md
 * Section 2.1's own disclosed gap, now closed) is carried through purely so
 * [AssignmentCompletedApplicationService] can look up the passenger
 * already recorded against it by [OrderSubmittedListener] — never to reach
 * into Order Management's own domain.
 */
@Component
class AssignmentCompletedListener(
    private val applicationService: AssignmentCompletedApplicationService,
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

        val driverId = data.get("driverId")?.asText()
        require(!driverId.isNullOrBlank()) { "Missing payload.driverId" }

        val orderId = data.get("orderId")?.asText()
        require(!orderId.isNullOrBlank()) { "Missing payload.orderId" }

        // `occurredAt` is the envelope's own field (sibling of `payload`),
        // not nested inside `payload` -- see DispatchAssignmentApplicationService's
        // own `envelopeFor` helper.
        val occurredAtText = envelope.get("occurredAt")?.asText()
        require(!occurredAtText.isNullOrBlank()) { "Missing occurredAt" }

        applicationService.handle(
            AssignmentCompletedUpdateCommand(
                eventId = eventId,
                driverId = driverId,
                orderId = orderId,
                occurredAt = Instant.parse(occurredAtText)
            )
        )
    }

    companion object {
        const val SUPPORTED_EVENT_TYPE = "AssignmentCompleted"
        const val SUPPORTED_EVENT_VERSION = 1
    }
}
