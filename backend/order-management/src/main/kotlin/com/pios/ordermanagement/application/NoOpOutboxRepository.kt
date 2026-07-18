package com.pios.ordermanagement.application

/**
 * A no-op [OutboxRepository]. Used only as [OrderLifecycleApplicationService]'s
 * default when no real outbox behavior is wired in -- for example, tests
 * exercising order lifecycle logic unrelated to the outbox. Never used in
 * a running application: Spring always finds and injects the real
 * [com.pios.ordermanagement.persistence.PostgreSQLOutboxRepository] bean
 * there instead, since it is the only actual [OutboxRepository]
 * implementation Spring's component scan registers as a bean (ADR-032).
 */
object NoOpOutboxRepository : OutboxRepository {
    override fun save(record: OutboxRecord): OutboxRecord = record
    override fun findUnpublished(): List<OutboxRecord> = emptyList()
    override fun markPublished(id: Long) = Unit
}
