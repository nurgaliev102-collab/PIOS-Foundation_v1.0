package com.pios.ordermanagement.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.ordermanagement.application.AssignmentCompletedApplicationService
import com.pios.ordermanagement.application.AssignmentCompletedUpdateCommand
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * The transport boundary for Order Management's consumption of
 * AssignmentCompleted (ADR-041, Order Lifecycle Synchronization with
 * Assignment Completion). The only place in this class a RabbitMQ/Spring
 * AMQP type ([org.springframework.amqp.rabbit.annotation.RabbitListener])
 * appears; [AssignmentCompletedApplicationService] and everything it
 * depends on remain completely unaware of RabbitMQ. Mirrors
 * [AssignmentAcceptedListener] exactly, on its own, separate queue
 * ([RabbitMQConsumerTopologyConfiguration.ASSIGNMENT_COMPLETED_QUEUE_NAME])
 * -- ADR-041 Decision item 5 explicitly rejects adding
 * `assignment.completed` as a second binding on the existing
 * `order-management.from-dispatch` queue, since
 * [AssignmentAcceptedListener.onMessage] hard-requires
 * `eventType == "AssignmentAccepted"` and would dead-letter every
 * completion event on a shared queue.
 *
 * Validates the envelope's `eventType` and `eventVersion` before
 * extracting anything: only `"AssignmentCompleted"` at
 * [SUPPORTED_EVENT_VERSION] is accepted. Any other event type, an
 * unsupported version, or a structurally malformed envelope throws --
 * never silently ignored and never interpreted as the supported version
 * -- so [RabbitMQListenerContainerConfiguration]'s bounded retry policy
 * (ADR-031) applies uniformly to every kind of processing failure this
 * listener can encounter, and an exhausted retry routes the message to
 * this queue's own dead-letter queue rather than discarding or
 * misinterpreting it.
 *
 * Extracts only `payload.orderId` -- the field ADR-041's trigger actually
 * needs -- and translates it, together with the envelope's own `eventId`,
 * into a primitive-typed [AssignmentCompletedUpdateCommand] before calling
 * the application service. Never imports any Dispatch type.
 *
 * Not catching any exception here is deliberate, mirroring
 * [AssignmentAcceptedListener]'s own reasoning exactly.
 */
@Component
class AssignmentCompletedListener(
    private val applicationService: AssignmentCompletedApplicationService,
    private val objectMapper: ObjectMapper
) {

    @RabbitListener(
        queues = [RabbitMQConsumerTopologyConfiguration.ASSIGNMENT_COMPLETED_QUEUE_NAME],
        containerFactory = "rabbitListenerContainerFactory"
    )
    fun onMessage(payload: String) {
        val envelope = objectMapper.readTree(payload)

        val eventType = envelope.get("eventType")?.asText()
        require(eventType == SUPPORTED_EVENT_TYPE) {
            "Unsupported event type for ${RabbitMQConsumerTopologyConfiguration.ASSIGNMENT_COMPLETED_QUEUE_NAME}: '$eventType'"
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

        applicationService.handle(
            AssignmentCompletedUpdateCommand(
                eventId = eventId,
                orderReference = orderReference
            )
        )
    }

    companion object {
        const val SUPPORTED_EVENT_TYPE = "AssignmentCompleted"
        const val SUPPORTED_EVENT_VERSION = 1
    }
}
