package com.pios.dispatch.domain

import java.time.Instant

/**
 * ProposalLapsed. Owned exclusively by Dispatch. Records that no response
 * arrived to a proposal while waiting remained appropriate — distinct
 * from [ProposalDeclined] as a passive, not an actor-driven, outcome
 * (PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Section 9). The
 * mechanism by which lapsing is recognized is not represented here — see
 * [Proposal.lapse]'s own KDoc. No event infrastructure, broker, or schema
 * is implied by this representation, consistent with ADR-003.
 */
data class ProposalLapsed(
    val orderId: OrderReference,
    val driverId: DriverReference,
    val occurredAt: Instant = Instant.now()
)
