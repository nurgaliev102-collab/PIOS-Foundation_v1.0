package com.pios.dispatch.application

/**
 * The message-broker publishing boundary for Dispatch's own outbox relay
 * (ADR-029, ADR-031, ADR-032; Tranche 1: Dispatch Event Publishing
 * Completion). [publish] sends [payload] to Dispatch's own exchange, at
 * [routingKey], and returns only once the broker has confirmed receipt --
 * the publisher-confirm mechanism ADR-031 requires -- or throws if it
 * does not. No storage technology, broker client library, or connection
 * detail is named here.
 *
 * Only Dispatch's own outbox relay ([OutboxRelay]) calls this; no other
 * module implements or depends on it.
 */
interface EventPublisher {
    fun publish(routingKey: String, payload: String)
}
