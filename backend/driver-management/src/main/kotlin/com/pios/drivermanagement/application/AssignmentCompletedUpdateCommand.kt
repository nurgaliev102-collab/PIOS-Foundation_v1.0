package com.pios.drivermanagement.application

import java.time.Instant

/**
 * Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2):
 * the primitive-typed command Driver Management's own transport boundary
 * (`com.pios.drivermanagement.persistence.AssignmentCompletedListener`)
 * translates a received `AssignmentCompleted` message into. Carries
 * [eventId] (idempotent processing), [driverId] — Dispatch's own
 * `AssignmentCompleted` payload already names the driver directly, so
 * unlike Order Management's own consumer of this same event, no
 * cross-module lookup is needed to know which driver a completed ride
 * belongs to — and [occurredAt], the event's own timestamp of when the ride
 * actually completed. [occurredAt] is carried explicitly, rather than
 * defaulting to the moment this message happens to be processed, because
 * [com.pios.drivermanagement.domain.RideStreakCalculator]'s week-boundary
 * math must reflect when the ride happened, not when a retried/delayed
 * delivery was eventually consumed. Never a Dispatch type (ADR-005/ADR-009).
 *
 * [orderId] (Phase 2 extension, docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section
 * 2.1's own disclosed gap, now closed) is the same order reference
 * [OrderSubmittedUpdateCommand.orderId] already recorded a passenger
 * against — [AssignmentCompletedApplicationService] uses it purely to look
 * that passenger back up locally, never to reach into Order Management's
 * own domain.
 *
 * [statedPrice] (ADR-065, Driver Earnings from Self-Stated Prices) is
 * Dispatch's own `payload.statedPrice` — the order's `ACCEPTED` Proposal's
 * stated price, forwarded verbatim and unparsed. `null` for an event
 * published before this ADR shipped, a blank value, or the no-Proposal
 * path (Dispatch's own `POST /v1/assignments`) — never a validation
 * failure at the transport boundary
 * ([com.pios.drivermanagement.persistence.AssignmentCompletedListener]'s
 * own KDoc). [AssignmentCompletedApplicationService] is the one that
 * interprets it, via [com.pios.drivermanagement.domain.PriceParser].
 */
data class AssignmentCompletedUpdateCommand(
    val eventId: String,
    val driverId: String,
    val orderId: String,
    val occurredAt: Instant,
    val statedPrice: String? = null
)
