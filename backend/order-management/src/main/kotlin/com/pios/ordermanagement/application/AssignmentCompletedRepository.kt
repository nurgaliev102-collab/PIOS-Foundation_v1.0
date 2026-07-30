package com.pios.ordermanagement.application

/**
 * The idempotency boundary for Order Management's own consumption of
 * AssignmentCompleted (ADR-041, Order Lifecycle Synchronization with
 * Assignment Completion), mirroring [AssignmentAcceptedRepository] exactly.
 * No storage technology, broker, or framework detail is named here.
 *
 * Backed by the same `order_management_processed_events` ledger
 * [AssignmentAcceptedRepository] already uses (ADR-041 Decision item 2:
 * "The `order_management_processed_events` ledger continues to hold event
 * ids only; it is an idempotency device, not a second state store.") — a
 * single, event-type-agnostic table keyed on the globally unique `eventId`
 * a RabbitMQ envelope always carries, not a table scoped to one consumer.
 * A separate interface (rather than reusing [AssignmentAcceptedRepository]
 * directly) exists because that interface's own KDoc restricts it to
 * AssignmentAccepted's own consumer path exclusively; this boundary is the
 * same pattern applied to AssignmentCompleted's own, independent consumer
 * path, not a new idempotency mechanism.
 *
 * Only Order Management's own consumer path
 * ([AssignmentCompletedApplicationService]) writes through this boundary;
 * no other module implements or depends on it.
 */
interface AssignmentCompletedRepository {
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
