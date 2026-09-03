package com.pios.passengerexperience.application

/**
 * The message-broker publishing boundary for Passenger Experience's own
 * outbox relay (mirrors Dispatch's own `EventPublisher` exactly; ADR-029,
 * ADR-031, ADR-032). [publish] sends [payload] to this module's own
 * exchange, at [routingKey], and returns only once the broker has
 * confirmed receipt — or throws if it does not. No storage technology,
 * broker client library, or connection detail is named here.
 *
 * Only this module's own outbox relay ([OutboxRelay]) calls this; no
 * other module implements or depends on it.
 */
interface EventPublisher {
    fun publish(routingKey: String, payload: String)
}
