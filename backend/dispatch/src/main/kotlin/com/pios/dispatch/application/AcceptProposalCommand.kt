package com.pios.dispatch.application

import com.pios.dispatch.domain.ProposalId

/**
 * The Accept Proposal command. Represents a driver's confirmation of a
 * proposed pairing. Mirrors [AcceptAssignmentCommand] exactly: it does
 * not itself decide whether that confirmation is valid — that remains the
 * Proposal aggregate's own decision. [proposalId] identifies which
 * proposal the command applies to; the proposal instance itself may be
 * supplied separately by the caller, or looked up by
 * [ProposalApplicationService] itself.
 */
data class AcceptProposalCommand(
    val proposalId: ProposalId
)
