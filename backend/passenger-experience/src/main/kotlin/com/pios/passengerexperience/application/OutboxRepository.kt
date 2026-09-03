package com.pios.passengerexperience.application

/**
 * The persistence boundary for Passenger Experience's own transactional
 * outbox (Task 14, First Refusal Foundation; mirrors Dispatch's own
 * `OutboxRepository` exactly, per ADR-029, ADR-031, ADR-032). [save] must
 * be invoked within the same transaction as the domain state change the
 * record describes — this interface does not itself enforce that; the
 * calling application service owns the transactional boundary. No storage
 * technology, broker, or framework detail is named here.
 *
 * Only this module's own relay reads from this boundary; no other module
 * implements or depends on it.
 */
interface OutboxRepository {
    /** Persists [record], returning it with its assigned [OutboxRecord.id]. */
    fun save(record: OutboxRecord): OutboxRecord

    /** Every record not yet marked published, in commit order. */
    fun findUnpublished(): List<OutboxRecord>

    /** Marks the record identified by [id] as published. */
    fun markPublished(id: Long)

    /**
     * A count-only measurement of the unpublished backlog, obtained by one
     * aggregate query — never by loading the rows [findUnpublished] loads.
     */
    fun countUnpublished(): OutboxBacklog
}
