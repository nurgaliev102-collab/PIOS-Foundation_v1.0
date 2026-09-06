package com.pios.drivermanagement.application

/**
 * Growth Loops TZ v1, Phase 2 extension (docs/PIOS_GROWTH_LOOPS_TZ_V1.md
 * Section 2.1's own disclosed gap, now closed): the primitive-typed
 * command Driver Management's own transport boundary
 * (`com.pios.drivermanagement.persistence.OrderSubmittedListener`)
 * translates a received `OrderSubmitted` message into. Carries only
 * [eventId] (idempotent processing), [orderId], and [passengerReference] —
 * nothing from Order Management's own domain types, and nothing about
 * `explicitDriverIntent`/matching, which this module has no reason to
 * know about (unlike Dispatch's own consumer of this same event).
 */
data class OrderSubmittedUpdateCommand(
    val eventId: String,
    val orderId: String,
    val passengerReference: String
)
