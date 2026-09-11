package com.pios.core.application

import java.time.Instant

/**
 * The primitive-typed command for a received `OrderCompleted` message.
 * The `order.completed` envelope's payload carries **only** `orderId`
 * (verified against `OrderLifecycleApplicationService.envelopeFor`), so
 * this command carries only [eventId], [orderReference], and [occurredAt].
 * The participant is resolved by correlating [orderReference] against a
 * prior `ORDER_SUBMITTED` history row — never fetched from order-management
 * (ADR-067 Write Boundary).
 */
data class OrderCompletedRecordCommand(
    val eventId: String,
    val orderReference: String,
    val occurredAt: Instant
)
