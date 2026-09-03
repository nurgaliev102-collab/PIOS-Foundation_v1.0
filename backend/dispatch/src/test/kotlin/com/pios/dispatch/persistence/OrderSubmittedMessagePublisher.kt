package com.pios.dispatch.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.time.Instant
import java.util.UUID

/**
 * Test-only simulator of Order Management's own `OrderSubmitted` publisher
 * (Task 16, First Refusal Runtime Integration), used to prove Dispatch's
 * consumer path against a real broker without any dependency -- test-scoped
 * or otherwise -- on Order Management's own code, mirroring
 * [DriverAvailabilityChangedMessagePublisher]/[PrimaryConnectionMessagePublisher]'s
 * own exact precedent. Builds exactly the envelope shape
 * `OrderLifecycleApplicationService.outboxRecordFor(OrderSubmitted)`
 * produces (`eventId`, `eventType`, `eventVersion`, `occurredAt`,
 * `payload.orderId`/`payload.passengerReference`/`payload.explicitDriverIntent`/`payload.isTest`)
 * and publishes it to the real, shared `order-management.events` exchange
 * at the real, already-ratified `order.submitted` routing key.
 */
internal class OrderSubmittedMessagePublisher(connectionFactory: ConnectionFactory) {
    private val rabbitTemplate = RabbitTemplate(connectionFactory)
    private val objectMapper = ObjectMapper()

    fun publish(
        orderId: String,
        passengerReference: String,
        explicitDriverIntent: Boolean = false,
        isTest: Boolean = false,
        eventId: String = UUID.randomUUID().toString(),
        eventVersion: Int = 1
    ) {
        val envelope = objectMapper.writeValueAsString(
            mapOf(
                "eventId" to eventId,
                "eventType" to "OrderSubmitted",
                "eventVersion" to eventVersion,
                "occurredAt" to Instant.now().toString(),
                "payload" to mapOf(
                    "orderId" to orderId,
                    "passengerReference" to passengerReference,
                    "explicitDriverIntent" to explicitDriverIntent,
                    "isTest" to isTest
                )
            )
        )
        publishRaw(envelope)
    }

    fun publishRaw(payload: String) {
        rabbitTemplate.invoke { operations ->
            operations.convertAndSend(
                RabbitMQOrderManagementTopologyConfiguration.PRODUCER_EXCHANGE_NAME,
                RabbitMQOrderManagementTopologyConfiguration.ORDER_SUBMITTED_ROUTING_KEY,
                payload
            )
            operations.waitForConfirmsOrDie(5000L)
        }
    }
}
