package com.pios.drivermanagement.application

/**
 * The persistence boundary for Driver Management's own transactional
 * outbox (ADR-032). [save] must be invoked within the same transaction as
 * the aggregate state change the record describes; this interface does
 * not itself enforce that -- [DriverAvailabilityApplicationService] owns
 * the transactional boundary (APPLICATION_ARCHITECTURE.md Section 10). No
 * storage technology, broker, or framework detail is named here.
 *
 * Only Driver Management's own relay (ADR-031, ADR-032) reads from this
 * boundary; no other module implements or depends on it.
 */
interface OutboxRepository {
    /** Persists [record], returning it with its assigned [OutboxRecord.id]. */
    fun save(record: OutboxRecord): OutboxRecord

    /** Every record not yet marked published, in commit order (ADR-032). */
    fun findUnpublished(): List<OutboxRecord>

    /** Marks the record identified by [id] as published. */
    fun markPublished(id: Long)

    /**
     * A count-only measurement of the unpublished backlog (ADR-043
     * Decision 2; `GET /v1/health`), obtained by one aggregate query —
     * never by loading the rows [findUnpublished] loads. [findUnpublished]
     * must not be reused for this purpose: it materializes every
     * unpublished row's own payload, which makes a health check built on
     * it slowest and heaviest exactly when the backlog it reports is
     * largest.
     */
    fun countUnpublished(): OutboxBacklog
}
