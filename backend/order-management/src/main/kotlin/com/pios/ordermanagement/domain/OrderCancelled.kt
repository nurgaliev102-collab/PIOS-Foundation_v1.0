package com.pios.ordermanagement.domain

import java.time.Instant

/**
 * OrderCancelled (EVENT_CATALOG.md Section 5). Owned exclusively by Order
 * Management. Records that an order has been terminated before completion
 * (DOMAIN_MODEL.md Section 11).
 */
data class OrderCancelled(
    val orderId: OrderId,
    val occurredAt: Instant = Instant.now()
)
