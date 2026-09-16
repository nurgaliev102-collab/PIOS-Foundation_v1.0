package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.dispatch.application.DispatchRequestApplicationService
import com.pios.dispatch.application.RouteSubmittedOrderCommand
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import java.time.Instant

/** Validates OrderSubmitted at the transport boundary and persists its routing obligation. */
@Component
class OrderSubmittedFirstRefusalListener(
    private val dispatchRequests: DispatchRequestApplicationService,
    private val objectMapper: ObjectMapper
) {
    @RabbitListener(
        queues = [RabbitMQOrderManagementTopologyConfiguration.ORDER_SUBMITTED_QUEUE_NAME],
        containerFactory = "rabbitListenerContainerFactory"
    )
    fun onMessage(payload: String) {
        val envelope = objectMapper.readTree(payload)
        val eventType = envelope.get("eventType")?.asText()
        require(eventType == SUPPORTED_EVENT_TYPE) {
            "Unsupported event type for ${RabbitMQOrderManagementTopologyConfiguration.ORDER_SUBMITTED_QUEUE_NAME}: '$eventType'"
        }
        val eventVersion = envelope.get("eventVersion")?.asInt()
        require(eventVersion in SUPPORTED_EVENT_VERSIONS) {
            "Unsupported $SUPPORTED_EVENT_TYPE eventVersion: $eventVersion (supported: $SUPPORTED_EVENT_VERSIONS)"
        }
        require(!envelope.get("eventId")?.asText().isNullOrBlank()) { "Missing eventId" }
        val data = requireNotNull(envelope.get("payload")) { "Missing payload" }
        val orderId = data.get("orderId")?.asText()
        require(!orderId.isNullOrBlank()) { "Missing payload.orderId" }
        val passengerReference = data.get("passengerReference")?.asText()
        require(!passengerReference.isNullOrBlank()) { "Missing payload.passengerReference" }
        val explicitDriverIntent = data.get("explicitDriverIntent")?.asBoolean() ?: false
        val requestedDriverId = data.get("requestedDriverId")?.takeUnless { it.isNull }?.asText()?.ifBlank { null }
        require(requestedDriverId == null || explicitDriverIntent) {
            "payload.requestedDriverId requires explicitDriverIntent"
        }
        dispatchRequests.routeSubmitted(
            RouteSubmittedOrderCommand(
                orderId = orderId,
                passengerReference = passengerReference,
                isTest = data.get("isTest")?.asBoolean() ?: false,
                explicitDriverIntent = explicitDriverIntent,
                requestedDriverId = requestedDriverId,
                requestedPickupAt = data.get("requestedPickupAt")?.takeUnless { it.isNull }?.asText()?.let(Instant::parse),
                submittedAt = envelope.get("occurredAt")?.asText()?.let(Instant::parse)
                    ?: throw IllegalArgumentException("Missing occurredAt")
            )
        )
    }

    companion object {
        const val SUPPORTED_EVENT_TYPE = "OrderSubmitted"
        val SUPPORTED_EVENT_VERSIONS = setOf(1, 2, 3)
    }
}
