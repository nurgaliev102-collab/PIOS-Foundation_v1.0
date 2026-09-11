package com.pios.core.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.core.application.OrderCancelledRecordCommand
import com.pios.core.application.OrderCompletedRecordCommand
import com.pios.core.application.OrderSubmittedRecordCommand
import com.pios.core.application.ParticipantHistoryProjectionApplicationService
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * The transport boundary for Core's consumption of order-management's
 * `OrderSubmitted` / `OrderCompleted` / `OrderCancelled` (ADR-067 Current
 * Authorized Inputs). The only place in this class a RabbitMQ/Spring AMQP
 * type appears; [ParticipantHistoryProjectionApplicationService] and
 * everything it depends on remain unaware of RabbitMQ.
 *
 * One listener, one queue ([RabbitMQOrderManagementTopologyConfiguration.QUEUE_NAME]),
 * branching on `eventType` — the exact pattern dispatch's own
 * `PrimaryConnectionEventListener` establishes for two related event
 * kinds on one queue.
 *
 * Validates the envelope's `eventType` and `eventVersion` before
 * extracting anything: only `OrderSubmitted` / `OrderCompleted` /
 * `OrderCancelled` at [SUPPORTED_EVENT_VERSION] is accepted. Any other
 * event type (including every Reserved Future Input, and
 * `DriverAvailabilityChanged`), an unsupported version, or a structurally
 * malformed envelope **throws** — never silently ignored, never
 * misinterpreted — so [RabbitMQListenerContainerConfiguration]'s bounded
 * retry policy applies uniformly and an exhausted retry routes the
 * message to this queue's own dead-letter queue.
 *
 * Extracts only the fields each event's real payload carries and Slice 01
 * needs (verified against `OrderLifecycleApplicationService`): from
 * `OrderSubmitted`, `payload.passengerReference` and `payload.orderId`;
 * from `OrderCompleted` / `OrderCancelled`, `payload.orderId` alone (that
 * payload carries nothing else). The envelope's own `occurredAt` is
 * carried verbatim. Never imports any order-management type; never
 * fetches a missing value (ADR-067 Write Boundary).
 *
 * Not catching any exception here is deliberate — letting it propagate is
 * what lets the container's retry-then-recoverer advice decide the
 * message's fate, and guarantees a message is never acknowledged if the
 * application service's own transaction failed.
 */
@Component
class OrderEventListener(
    private val applicationService: ParticipantHistoryProjectionApplicationService,
    private val objectMapper: ObjectMapper
) {

    @RabbitListener(
        queues = [RabbitMQOrderManagementTopologyConfiguration.QUEUE_NAME],
        containerFactory = "rabbitListenerContainerFactory"
    )
    fun onMessage(payload: String) {
        val envelope = objectMapper.readTree(payload)

        val eventType = envelope.get("eventType")?.asText()
        require(
            eventType == ORDER_SUBMITTED || eventType == ORDER_COMPLETED || eventType == ORDER_CANCELLED
        ) {
            "Unsupported event type for ${RabbitMQOrderManagementTopologyConfiguration.QUEUE_NAME}: '$eventType'"
        }

        val eventVersion = envelope.get("eventVersion")?.asInt()
        require(eventVersion == SUPPORTED_EVENT_VERSION) {
            "Unsupported $eventType eventVersion: $eventVersion (supported: $SUPPORTED_EVENT_VERSION)"
        }

        val eventId = envelope.get("eventId")?.asText()
        require(!eventId.isNullOrBlank()) { "Missing eventId" }

        val occurredAtText = envelope.get("occurredAt")?.asText()
        require(!occurredAtText.isNullOrBlank()) { "Missing occurredAt" }
        val occurredAt = parseOccurredAt(occurredAtText)

        val data = envelope.get("payload")
        requireNotNull(data) { "Missing payload" }

        val orderReference = data.get("orderId")?.asText()
        require(!orderReference.isNullOrBlank()) { "Missing payload.orderId" }

        when (eventType) {
            ORDER_SUBMITTED -> {
                val passengerReference = data.get("passengerReference")?.asText()
                require(!passengerReference.isNullOrBlank()) { "Missing payload.passengerReference" }
                applicationService.handleOrderSubmitted(
                    OrderSubmittedRecordCommand(
                        eventId = eventId,
                        passengerReference = passengerReference,
                        orderReference = orderReference,
                        occurredAt = occurredAt
                    )
                )
            }
            ORDER_COMPLETED -> applicationService.handleOrderCompleted(
                OrderCompletedRecordCommand(
                    eventId = eventId,
                    orderReference = orderReference,
                    occurredAt = occurredAt
                )
            )
            ORDER_CANCELLED -> applicationService.handleOrderCancelled(
                OrderCancelledRecordCommand(
                    eventId = eventId,
                    orderReference = orderReference,
                    occurredAt = occurredAt
                )
            )
        }
    }

    private fun parseOccurredAt(text: String): Instant =
        try {
            Instant.parse(text)
        } catch (ex: Exception) {
            throw IllegalArgumentException("Unparseable occurredAt: '$text'", ex)
        }

    companion object {
        const val ORDER_SUBMITTED = "OrderSubmitted"
        const val ORDER_COMPLETED = "OrderCompleted"
        const val ORDER_CANCELLED = "OrderCancelled"
        const val SUPPORTED_EVENT_VERSION = 1
    }
}
