package com.pios.dispatch.application

import com.pios.dispatch.domain.ProposalId

/**
 * The Decline Proposal command. Represents a driver's active refusal of a
 * proposed pairing, before any obligation existed
 * (PRODUCT_DECISION_OPPORTUNITY_ACCEPTANCE_COMMITMENT.md Section 3).
 * Mirrors [AcceptProposalCommand]'s own shape; it does not itself decide
 * whether the decline is valid — that remains the Proposal aggregate's
 * own decision.
 */
data class DeclineProposalCommand(
    val proposalId: ProposalId
)
