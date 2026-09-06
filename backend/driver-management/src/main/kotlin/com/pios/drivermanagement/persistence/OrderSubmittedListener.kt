package com.pios.drivermanagement.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.drivermanagement.application.OrderSubmittedApplicationService
import com.pios.drivermanagement.application.OrderSubmittedUpdateCommand
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * Growth Loops TZ v1, Phase 2 extension (docs/PIOS_GROWTH_LOOPS_TZ_V1.md
 * Section 2.1's own disclosed gap, now closed): the transport boundary for
 * Driver Management's own consumption of Order Management's
 * `OrderSubmitted` -- mirrors [AssignmentCompletedListener]'s own
 * discipline exactly: validates `eventType`/`eventVersion` before
 * extracting anything, throws on anything unexpected so the bounded
 * retry-then-dead-letter policy applies uniformly, and never imports any
 * Order Management type.
 *
 * Extracts only `payload.orderId` and `payload.passengerReference` --
 * nothing else this event's own payload carries (`explicitDriverIntent`,
 * `isTest`) is relevant to this module's own, unrelated purpose.
 */
@Component
class OrderSubmittedListener(
    private val applicationService: OrderSubmittedApplicationService,
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

        val orderId = data.get("orderId")?.asText()
        require(!orderId.isNullOrBlank()) { "Missing payload.orderId" }

        val passengerReference = data.get("passengerReference")?.asText()
        require(!passengerReference.isNullOrBlank()) { "Missing payload.passengerReference" }

        applicationService.handle(
            OrderSubmittedUpdateCommand(
                eventId = eventId,
                orderId = orderId,
                passengerReference = passengerReference
            )
        )
    }

    companion object {
        const val SUPPORTED_EVENT_TYPE = "OrderSubmitted"
        const val SUPPORTED_EVENT_VERSION = 1
    }
}
