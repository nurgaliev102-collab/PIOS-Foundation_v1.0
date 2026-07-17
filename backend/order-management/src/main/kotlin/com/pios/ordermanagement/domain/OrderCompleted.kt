package com.pios.ordermanagement.domain

import java.time.Instant

/**
 * OrderCompleted (EVENT_CATALOG.md Section 5). Owned exclusively by Order
 * Management. Marks an order's terminal, completed state, after which it
 * cannot return to an active state (DOMAIN_MODEL.md Section 11).
 */
data class OrderCompleted(
    val orderId: OrderId,
    val occurredAt: Instant = Instant.now()
)
