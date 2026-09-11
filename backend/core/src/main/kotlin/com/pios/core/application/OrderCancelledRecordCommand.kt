package com.pios.core.application

import java.time.Instant

/**
 * The primitive-typed command for a received `OrderCancelled` message.
 * Like [OrderCompletedRecordCommand], the `order.cancelled` envelope's
 * payload carries only `orderId`, so the participant is resolved by
 * correlating [orderReference] against a prior `ORDER_SUBMITTED` history
 * row — never fetched from order-management (ADR-067 Write Boundary).
 */
data class OrderCancelledRecordCommand(
    val eventId: String,
    val orderReference: String,
    val occurredAt: Instant
)
