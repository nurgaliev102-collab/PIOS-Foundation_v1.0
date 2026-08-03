package com.pios.dispatch.application

/**
 * A no-op [OutboxRepository]. Used only as
 * [DispatchAssignmentApplicationService]'s default when no real outbox
 * behavior is wired in -- for example, tests exercising Assignment
 * lifecycle logic unrelated to the outbox, mirroring exactly the same
 * default already proven for Order Management's own
 * `OrderLifecycleApplicationService` and Driver Management's own
 * `DriverAvailabilityApplicationService`. Never used in a running
 * application: Spring always finds and injects the real
 * [com.pios.dispatch.persistence.PostgreSQLOutboxRepository] bean there
 * instead, since it is the only actual [OutboxRepository] implementation
 * Spring's component scan registers as a bean (ADR-032).
 */
object NoOpOutboxRepository : OutboxRepository {
    override fun save(record: OutboxRecord): OutboxRecord = record
    override fun findUnpublished(): List<OutboxRecord> = emptyList()
    override fun markPublished(id: Long) = Unit
    override fun countUnpublished(): OutboxBacklog = OutboxBacklog(pending = 0, oldestPendingCreatedAt = null)
}
