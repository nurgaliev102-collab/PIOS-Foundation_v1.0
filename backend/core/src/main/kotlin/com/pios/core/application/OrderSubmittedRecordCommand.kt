package com.pios.core.application

import java.time.Instant

/**
 * The primitive-typed command Core's transport boundary
 * (`com.pios.core.persistence.OrderEventListener`) translates a received
 * `OrderSubmitted` message into, before calling
 * [ParticipantHistoryProjectionApplicationService]. Carries only what the
 * `order.submitted` envelope actually provides and the projection
 * actually needs — [eventId] for idempotency, [passengerReference] as the
 * participant, [orderReference] for later `OrderCompleted` / `OrderCancelled`
 * correlation, and the envelope's own [occurredAt]. Never any
 * order-management domain type. `explicitDriverIntent` / `isTest` from the
 * same payload are deliberately not carried — Slice 01 does not use them.
 */
data class OrderSubmittedRecordCommand(
    val eventId: String,
    val passengerReference: String,
    val orderReference: String,
    val occurredAt: Instant
)
