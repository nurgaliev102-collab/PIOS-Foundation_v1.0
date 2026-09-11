package com.pios.core.application

/**
 * The persistence boundary for Core's idempotency ledger over every event
 * it consumes (ADR-067 Idempotency; ADR-031). No storage technology,
 * broker, or framework detail is named here.
 *
 * Only [ParticipantHistoryProjectionApplicationService] writes through
 * this boundary; no other module implements or depends on it. Mirrors
 * dispatch's own `OrderCancelledRepository.markProcessed` contract.
 */
interface ProcessedEventRepository {
    /**
     * Records that [eventId] has been processed. Returns `true` the first
     * time a given [eventId] is recorded, `false` if it was already
     * recorded (a redelivery of an event already handled — RabbitMQ being
     * an at-least-once broker, ADR-029 / ADR-031).
     *
     * The implementation relies on PostgreSQL-enforced uniqueness
     * (`processed_events.event_id` primary key + `ON CONFLICT DO
     * NOTHING`), not an application-level check.
     */
    fun markProcessed(eventId: String): Boolean
}
