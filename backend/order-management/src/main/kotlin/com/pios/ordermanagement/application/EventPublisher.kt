package com.pios.ordermanagement.application

/**
 * The message-broker publishing boundary for Order Management's own
 * outbox relay (ADR-029, ADR-031, ADR-032). [publish] sends [payload] to
 * Order Management's own exchange, at [routingKey], and returns only
 * once the broker has confirmed receipt — the publisher-confirm
 * mechanism ADR-031 requires — or throws if it does not. No storage
 * technology, broker client library, or connection detail is named here.
 *
 * Only Order Management's own outbox relay ([OutboxRelay]) calls this;
 * no other module implements or depends on it.
 */
interface EventPublisher {
    fun publish(routingKey: String, payload: String)
}
