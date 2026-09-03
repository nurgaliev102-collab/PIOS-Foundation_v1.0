package com.pios.passengerexperience.application

import java.time.Instant

/**
 * A pending or already-relayed record in Passenger Experience's own
 * transactional outbox (Task 14, First Refusal Foundation; mirrors
 * Dispatch's, Order Management's, and Driver Management's own
 * already-proven shape exactly, per ADR-029, ADR-031, ADR-032). This is
 * this module's *first* event-publishing capability of any kind — Sprint
 * 7B / ADR-054 (Circle of Trust) deliberately built none ("no new event,
 * no outbox record" was that ADR's own explicit non-goal at the time);
 * ADR-062 now authorizes exactly the narrow addition this record exists
 * for. [eventType] and [routingKey] describe where a record belongs in
 * this module's own new exchange topology
 * ([com.pios.passengerexperience.persistence.RabbitMQProducerTopologyConfiguration]);
 * [payload] is its serialized event data (ADR-030's schema policy). [id]
 * is `null` until the record has actually been persisted and assigned an
 * identity by the database; once assigned, it also doubles as the
 * commit-order position [OutboxRepository] requires publication to
 * preserve. [publishedAt] is `null` until the record has actually been
 * confirmed published by a broker.
 */
data class OutboxRecord(
    val id: Long? = null,
    val aggregateId: String,
    val eventType: String,
    val routingKey: String,
    val payload: String,
    val createdAt: Instant? = null,
    val publishedAt: Instant? = null
)
