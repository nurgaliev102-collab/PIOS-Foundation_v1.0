package com.pios.passengerexperience.application

/**
 * A no-op [OutboxRepository]. Used only as a default when no real outbox
 * behavior is wired in — for example, tests exercising Connection
 * lifecycle logic unrelated to the outbox — mirroring exactly the same
 * default already proven for Dispatch's, Order Management's, and Driver
 * Management's own application services. Never used in a running
 * application: Spring always finds and injects the real
 * [com.pios.passengerexperience.persistence.PostgreSQLOutboxRepository]
 * bean there instead.
 */
object NoOpOutboxRepository : OutboxRepository {
    override fun save(record: OutboxRecord): OutboxRecord = record
    override fun findUnpublished(): List<OutboxRecord> = emptyList()
    override fun markPublished(id: Long) = Unit
    override fun countUnpublished(): OutboxBacklog = OutboxBacklog(pending = 0, oldestPendingCreatedAt = null)
}
