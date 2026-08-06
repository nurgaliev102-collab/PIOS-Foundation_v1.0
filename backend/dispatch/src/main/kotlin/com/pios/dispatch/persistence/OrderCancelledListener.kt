package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.OrderCancelledApplicationService
import com.pios.dispatch.application.OrderCancelledUpdateCommand
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * The transport boundary for Dispatch's consumption of Order Management's
 * `OrderCancelled` (ADR-053, Proposal Resolution on Order Cancellation —
 * Accepted, Part 1/Part 4). The only place in this class a RabbitMQ/Spring
 * AMQP type ([org.springframework.amqp.rabbit.annotation.RabbitListener])
 * appears; [OrderCancelledApplicationService] and everything it depends on
 * remain completely unaware of RabbitMQ. Mirrors
 * [DriverAvailabilityChangedListener] exactly, on its own, separate queue
 * ([RabbitMQOrderManagementTopologyConfiguration.QUEUE_NAME]).
 *
 * Validates the envelope's `eventType` and `eventVersion` before
 * extracting anything: only `"OrderCancelled"` at [SUPPORTED_EVENT_VERSION]
 * is accepted. Any other event type, an unsupported version, or a
 * structurally malformed envelope throws — never silently ignored and
 * never interpreted as the supported version — so
 * [RabbitMQListenerContainerConfiguration]'s bounded retry policy
 * (ADR-031) applies uniformly to every kind of processing failure this
 * listener can encounter, and an exhausted retry routes the message to
 * this queue's own dead-letter queue rather than discarding or
 * misinterpreting it.
 *
 * Extracts only `payload.orderId` — the field ADR-053's own trigger
 * actually needs — and translates it, together with the envelope's own
 * `eventId`, into a primitive-typed [OrderCancelledUpdateCommand] before
 * calling the application service. Never imports any Order Management
 * type.
 *
 * Not catching any exception here is deliberate, mirroring
 * [DriverAvailabilityChangedListener]'s own reasoning exactly.
 */
@Component
class OrderCancelledListener(
    private val applicationService: OrderCancelledApplicationService,
    private val objectMapper: ObjectMapper
) {

    @RabbitListener(
        queues = [RabbitMQOrderManagementTopologyConfiguration.QUEUE_NAME],
        containerFactory = "rabbitListenerContainerFactory"
    )
    fun onMessage(payload: String) {
        val envelope = objectMapper.readTree(payload)

        val eventType = envelope.get("eventType")?.asText()
        require(eventType == SUPPORTED_EVENT_TYPE) {
            "Unsupported event type for ${RabbitMQOrderManagementTopologyConfiguration.QUEUE_NAME}: '$eventType'"
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
            OrderCancelledUpdateCommand(
                eventId = eventId,
                orderReference = orderReference
            )
        )
    }

    companion object {
        const val SUPPORTED_EVENT_TYPE = "OrderCancelled"
        const val SUPPORTED_EVENT_VERSION = 1
    }
}
