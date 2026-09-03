package com.pios.passengerexperience.persistence

import com.pios.passengerexperience.application.EventPublisher
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component

/**
 * The RabbitMQ-backed adapter for [EventPublisher] (Task 14, First
 * Refusal Foundation; ADR-062). Publishes to Passenger Experience's own
 * topic exchange ([EXCHANGE_NAME], declared by
 * [RabbitMQProducerTopologyConfiguration]) at the given routing key. Uses
 * [RabbitTemplate.invoke] to dedicate a single channel for the publish,
 * then `waitForConfirmsOrDie` to block until the broker actually confirms
 * receipt — throwing if it does not, so
 * [com.pios.passengerexperience.application.OutboxRelay] never marks a
 * record published without a real confirmation. Mirrors Dispatch's own
 * already-proven publisher exactly.
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
        const val EXCHANGE_NAME = "passenger-experience.events"
        private const val CONFIRM_TIMEOUT_MILLIS = 5000L
    }
}
