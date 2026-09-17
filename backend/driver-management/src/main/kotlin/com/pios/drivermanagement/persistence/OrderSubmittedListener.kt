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
 * `isTest`, and -- as of `OrderSubmitted` v2/v3 -- `requestedPickupAt`/
 * `requestedDriverId`) is relevant to this module's own, unrelated
 * purpose.
 *
 * **`SUPPORTED_EVENT_VERSIONS` (fixed 2026-09-17, live production defect):**
 * this listener required `eventVersion == 1` from Sprint 3A until this fix,
 * while Order Management has published `OrderSubmitted` v3 since `8206ff3`
 * (2026-09-16, ADR-076's `requestedDriverId` widening). Both widenings were
 * purely additive per `ADR-030` and needed no consumer change on their own
 * merits -- but this listener's rigid `==` check rejected every v2/v3
 * event regardless, and every one was dead-lettered
 * (`driver-management.from-order-management.order-submitted.dlq`, 8
 * messages found 2026-09-17, none replayed by this fix -- see the fix's own
 * commit for the live evidence). The correct, already-precedented pattern
 * is [com.pios.dispatch.persistence.OrderSubmittedFirstRefusalListener]'s
 * own `eventVersion in SUPPORTED_EVENT_VERSIONS` (`setOf(1, 2, 3)`), copied
 * here rather than a range check, so a future non-additive version bump
 * still fails closed exactly as `ADR-030` requires.
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
        require(eventVersion in SUPPORTED_EVENT_VERSIONS) {
            "Unsupported $SUPPORTED_EVENT_TYPE eventVersion: $eventVersion (supported: $SUPPORTED_EVENT_VERSIONS)"
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
        val SUPPORTED_EVENT_VERSIONS = setOf(1, 2, 3)
    }
}
