package com.pios.dispatch.domain

import java.time.Instant

/**
 * ProposalPriceProposed. Owned exclusively by Dispatch. Records that the
 * driver has stated a price for this ride, moving the proposal from
 * [ProposalStatus.OPEN] to [ProposalStatus.PRICE_PROPOSED] — the
 * passenger's own confirmation ([Proposal.confirmPrice]) or refusal
 * ([Proposal.declinePriceProposal]) of it settles the fact this event only
 * opens. Mirrors [ProposalAccepted]/[ProposalDeclined]'s own shape; not
 * wired into the outbox, for the same reason those two are not
 * ([ProposalApplicationService]'s own KDoc, "Outbox integration
 * deliberately not wired in") — no other module needs to react to it, and
 * introducing that would create an unratified external contract.
 */
data class ProposalPriceProposed(
    val orderId: OrderReference,
    val driverId: DriverReference,
    val occurredAt: Instant = Instant.now()
)
