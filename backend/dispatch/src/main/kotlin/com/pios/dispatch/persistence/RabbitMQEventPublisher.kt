package com.pios.dispatch.persistence

import com.pios.dispatch.application.EventPublisher
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component

/**
 * The RabbitMQ-backed adapter for [EventPublisher] (ADR-029, ADR-031;
 * Tranche 1: Dispatch Event Publishing Completion). Publishes to
 * Dispatch's own topic exchange ([EXCHANGE_NAME], declared by
 * [RabbitMQProducerTopologyConfiguration]) at the given routing key.
 * Uses [RabbitTemplate.invoke] to dedicate a single channel for the
 * publish, then [org.springframework.amqp.rabbit.core.RabbitOperations.waitForConfirmsOrDie]
 * to block until the broker actually confirms receipt (ADR-031's
 * publisher-confirm requirement) — throwing if it does not, so
 * [com.pios.dispatch.application.OutboxRelay] never marks a record
 * published without a real confirmation. Mirrors Order Management's and
 * Driver Management's own already-proven publisher exactly.
 */
@Component
class RabbitMQEventPublisher(
    private val rabbitTemplate: RabbitTemplate
) : EventPublisher {

    override fun publish(routingKey: String, payload: String) {
        rabbitTemplate.invoke { operations ->
            operations.convertAndSend(EXCHANGE_NAME, routingKey, payload)
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MILLIS)
        }
    }

    companion object {
        const val EXCHANGE_NAME = "dispatch.events"
        private const val CONFIRM_TIMEOUT_MILLIS = 5000L
    }
}
