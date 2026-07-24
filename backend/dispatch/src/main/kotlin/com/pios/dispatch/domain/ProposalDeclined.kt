package com.pios.dispatch.domain

import java.time.Instant

/**
 * ProposalDeclined. Owned exclusively by Dispatch. Records that a
 * proposed driver has actively refused, before any obligation existed
 * (PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Sections 3, 9) —
 * the order becomes free for a new proposal (Domain Design — Pre-
 * Commitment Aggregate, Root Invariant). No event infrastructure, broker,
 * or schema is implied by this representation, consistent with ADR-003.
 */
data class ProposalDeclined(
    val orderId: OrderReference,
    val driverId: DriverReference,
    val occurredAt: Instant = Instant.now()
)
