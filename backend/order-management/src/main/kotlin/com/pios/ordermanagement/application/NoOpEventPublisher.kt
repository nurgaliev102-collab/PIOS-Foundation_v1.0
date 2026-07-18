package com.pios.ordermanagement.application

/**
 * An [EventPublisher] that does nothing. Used only as [OutboxRelay]'s
 * default when no real broker is wired in — for example, tests
 * exercising outbox read behavior unrelated to actual publication. Never
 * used in a running application: Spring always finds and injects the
 * real [com.pios.ordermanagement.persistence.RabbitMQEventPublisher]
 * bean there instead, since it is the only actual [EventPublisher]
 * implementation Spring's component scan registers as a bean.
 */
object NoOpEventPublisher : EventPublisher {
    override fun publish(routingKey: String, payload: String) = Unit
}
