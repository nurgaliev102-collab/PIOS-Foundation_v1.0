package com.pios.dispatch.application

/**
 * The persistence boundary for Dispatch's own idempotency ledger over
 * consumed `OrderCancelled` events (ADR-053, Proposal Resolution on Order
 * Cancellation; ADR-031). No storage technology, broker, or framework
 * detail is named here. Unlike [DriverAvailabilityRepository], this
 * boundary holds no projection of its own — Dispatch does not mirror any
 * Order Management information; it only records which events it has
 * already acted on, per ADR-053 Part 7's own boundary (Dispatch never
 * verifies or stores anything about an Order beyond the reference already
 * carried by [com.pios.dispatch.domain.OrderReference]).
 *
 * Only [OrderCancelledApplicationService] writes through this boundary;
 * no other module implements or depends on it.
 */
interface OrderCancelledRepository {
    /**
     * Records that [eventId] has been processed. Returns `true` the first
     * time a given [eventId] is recorded, `false` if it was already
     * recorded (a redelivery of an event already handled) — mirrors
     * [DriverAvailabilityRepository.markProcessed] exactly.
     */
    fun markProcessed(eventId: String): Boolean
}
