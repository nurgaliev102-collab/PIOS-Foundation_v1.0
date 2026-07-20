package com.pios.dispatch.application

/**
 * An [EventPublisher] that does nothing. Passed explicitly to
 * [OutboxRelay] only by callers not concerned with actual publication
 * (for example, tests exercising outbox read behavior unrelated to
 * publication). [OutboxRelay] has no default for its [EventPublisher]
 * argument specifically so this can never become an accidental, silent
 * production fallback (the same Hardening Review v1.0 rule already
 * applied to Order Management's own relay) -- a Spring context missing a
 * real [com.pios.dispatch.persistence.RabbitMQEventPublisher] bean must
 * fail to start, not silently discard every event.
 */
object NoOpEventPublisher : EventPublisher {
    override fun publish(routingKey: String, payload: String) = Unit
}
