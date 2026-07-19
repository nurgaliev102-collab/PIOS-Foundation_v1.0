package com.pios.drivermanagement.application

import java.time.Instant

/**
 * A pending or already-relayed record in Driver Management's own
 * transactional outbox (ADR-032), mirroring the pattern already proven in
 * Order Management's Outbox Foundation v1.0. Represents the intent to
 * publish an event: [eventType] and [routingKey] describe where it belongs
 * in Driver Management's own exchange topology (ADR-031); [payload] is its
 * serialized event data (ADR-030's schema policy, in a minimal, provisional
 * form pending a concrete serialization-format decision). [id] is null
 * until the record has actually been persisted and assigned an identity by
 * the database (see [OutboxRepository.save]); once assigned, it also
 * doubles as the commit-order position ADR-032 requires publication to
 * preserve. [publishedAt] is null until the record has actually been
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
