package com.pios.drivermanagement.application

/**
 * An [EventPublisher] that does nothing. Passed explicitly to [OutboxRelay]
 * only by callers not concerned with actual publication (for example,
 * tests exercising outbox read behavior unrelated to publication).
 * [OutboxRelay] has no default for its [EventPublisher] argument
 * specifically so this can never become an accidental, silent production
 * fallback -- consistent with the production-safety lesson already
 * applied to Order Management's own outbox relay (RabbitMQ Event
 * Publishing Foundation v1.0 Hardening Review): a Spring context missing a
 * real [com.pios.drivermanagement.persistence.RabbitMQEventPublisher] bean
 * must fail to start, not silently discard every event.
 */
object NoOpEventPublisher : EventPublisher {
    override fun publish(routingKey: String, payload: String) = Unit
}
