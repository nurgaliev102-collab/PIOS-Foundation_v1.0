package com.pios.dispatch.domain

import java.time.Instant

/**
 * ProposalAccepted. Owned exclusively by Dispatch. Records that a
 * proposed driver has confirmed — the precondition for Assignment's own
 * creation (ADR-035; PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md
 * Section 5). No event infrastructure, broker, or schema is implied by
 * this representation, consistent with ADR-003.
 */
data class ProposalAccepted(
    val orderId: OrderReference,
    val driverId: DriverReference,
    val occurredAt: Instant = Instant.now()
)
