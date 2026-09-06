package com.pios.drivermanagement.application

/**
 * Growth Loops TZ v1, Phase 2 extension (docs/PIOS_GROWTH_LOOPS_TZ_V1.md
 * Section 2.1's own disclosed gap, now closed): a local, opaque record of
 * which passenger an order was for — learned from Order Management's own
 * `OrderSubmitted` (its own already-published `payload.passengerReference`,
 * the same field Dispatch's First Refusal feature already reads), so that
 * a later `AssignmentCompleted` for the same order can be attributed to a
 * passenger without a synchronous cross-module call (ADR-027). Not a copy
 * of Order Management's own domain — carries nothing beyond the one
 * opaque string this correlation needs.
 */
interface OrderPassengerRepository {
    /** Idempotent: recording the same [orderId] twice (a redelivery) leaves the stored value unchanged. */
    fun recordOrderPassenger(orderId: String, passengerReference: String)

    /** `null` if this order's own `OrderSubmitted` has not been consumed yet (or predates this feature). */
    fun findPassengerReference(orderId: String): String?
}
