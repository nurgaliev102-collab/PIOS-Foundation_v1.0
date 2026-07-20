package com.pios.ordermanagement.application

/**
 * The idempotency boundary for Order Management's own consumption of
 * AssignmentAccepted (Tranche 1: Dispatch Event Publishing Completion;
 * ADR-031, ADR-032), mirroring Dispatch's own already-proven
 * `DriverAvailabilityRepository.markProcessed` exactly. No storage
 * technology, broker, or framework detail is named here.
 *
 * Only Order Management's own consumer path
 * ([AssignmentAcceptedProjectionApplicationService]) writes through this
 * boundary; no other module implements or depends on it.
 */
interface AssignmentAcceptedRepository {
    /**
     * Records that [eventId] has been processed. Returns `true` the first
     * time a given [eventId] is recorded, `false` if it was already
     * recorded (a redelivery of an event already handled) -- the
     * building block idempotent consumption is built on.
     */
    fun markProcessed(eventId: String): Boolean

    /**
     * A side-effect-free check of whether [eventId] has already been
     * recorded as processed. Exists only so tests can observe that
     * asynchronous, broker-driven consumption actually completed, without
     * the observation itself risking a false positive the way calling
     * [markProcessed] as a poll would (it would itself record the event
     * as processed on its first, possibly-premature call).
     */
    fun isProcessed(eventId: String): Boolean
}
