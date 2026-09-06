package com.pios.drivermanagement.application

/**
 * Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2):
 * the idempotency boundary for Driver Management's own consumption of
 * `AssignmentCompleted`, mirroring Order Management's own
 * `AssignmentCompletedRepository` exactly (same shape, same reasoning) —
 * see that interface's own KDoc for why a dedicated, event-type-scoped
 * ledger interface is the established pattern here, not a generic one
 * shared across event types. No storage technology, broker, or framework
 * detail is named here.
 */
interface AssignmentCompletedRepository {
    /**
     * Records that [eventId] has been processed. Returns `true` the first
     * time a given [eventId] is recorded, `false` if it was already
     * recorded (a redelivery of an event already handled).
     */
    fun markProcessed(eventId: String): Boolean

    /** Side-effect-free check, for tests observing asynchronous consumption. */
    fun isProcessed(eventId: String): Boolean
}
