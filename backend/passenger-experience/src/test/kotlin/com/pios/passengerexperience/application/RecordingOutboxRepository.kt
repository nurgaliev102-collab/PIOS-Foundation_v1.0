package com.pios.passengerexperience.application

/**
 * A minimal [OutboxRepository] fake that simply records what was saved,
 * in order — for assertions only. Mirrors the identical pattern already
 * established in Dispatch's own test suite
 * (`com.pios.dispatch.application.DispatchAssignmentApplicationServiceRideProgressConvergenceTest.RecordingOutboxRepository`,
 * Task 12).
 */
class RecordingOutboxRepository : OutboxRepository {
    val records = mutableListOf<OutboxRecord>()

    override fun save(record: OutboxRecord): OutboxRecord {
        records.add(record)
        return record.copy(id = records.size.toLong())
    }

    override fun findUnpublished(): List<OutboxRecord> = records

    override fun markPublished(id: Long) = Unit

    override fun countUnpublished(): OutboxBacklog =
        OutboxBacklog(pending = records.size.toLong(), oldestPendingCreatedAt = null)
}
