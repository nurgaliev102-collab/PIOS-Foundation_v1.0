package com.pios.drivermanagement.application

/**
 * Growth Loops TZ v1, Phase 2 extension (docs/PIOS_GROWTH_LOOPS_TZ_V1.md
 * Section 2.1's own disclosed gap, now closed): the idempotency boundary
 * for Driver Management's own consumption of `OrderSubmitted`. A distinct
 * interface from [AssignmentCompletedRepository], even though both are
 * backed by the same physical `driver_management_processed_events` ledger
 * — mirroring Order Management's own established convention of one
 * ledger-interface per consumer path sharing one physical table (see that
 * module's own `AssignmentCompletedRepository`/`AssignmentAcceptedRepository`
 * split and its own KDoc for why: each interface's contract is scoped to
 * its own consumer, never a generic "any event" ledger).
 */
interface OrderSubmittedRepository {
    /**
     * Records that [eventId] has been processed. Returns `true` the first
     * time a given [eventId] is recorded, `false` if it was already
     * recorded (a redelivery of an event already handled).
     */
    fun markProcessed(eventId: String): Boolean

    /** Side-effect-free check, for tests observing asynchronous consumption. */
    fun isProcessed(eventId: String): Boolean
}
