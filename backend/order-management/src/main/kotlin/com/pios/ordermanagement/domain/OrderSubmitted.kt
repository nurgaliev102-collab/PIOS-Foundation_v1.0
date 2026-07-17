package com.pios.ordermanagement.domain

import java.time.Instant

/**
 * OrderSubmitted (EVENT_CATALOG.md Section 5). Owned exclusively by Order
 * Management. Marks the beginning of an order's lifecycle: an order has
 * been received. No event infrastructure, broker, or schema is implied
 * by this representation, consistent with ADR-003 and EVENT_CATALOG.md's
 * own scope.
 */
data class OrderSubmitted(
    val orderId: OrderId,
    val occurredAt: Instant = Instant.now()
)
