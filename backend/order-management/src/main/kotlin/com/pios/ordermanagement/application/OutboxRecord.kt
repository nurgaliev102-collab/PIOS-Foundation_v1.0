package com.pios.ordermanagement.application

import java.time.Instant

/**
 * A pending or already-relayed record in Order Management's own
 * transactional outbox (ADR-032). Represents the intent to publish an
 * event: [eventType] and [routingKey] describe where it belongs in
 * Order Management's own exchange topology (ADR-031); [payload] is its
 * serialized event data (ADR-030's schema policy, in a minimal, provisional
 * form pending a concrete serialization-format decision). [id] is null
 * until the record has actually been persisted and assigned an identity by
 * the database (see [OutboxRepository.save]); once assigned, it also
 * doubles as the commit-order position ADR-032 requires publication to
 * preserve. [publishedAt] is null until the record has actually been
 * confirmed published by a broker -- untouched by this task's relay
 * skeleton, which does not publish anything yet.
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
