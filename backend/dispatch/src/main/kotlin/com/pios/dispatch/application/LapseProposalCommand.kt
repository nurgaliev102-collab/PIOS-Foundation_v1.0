package com.pios.dispatch.application

import com.pios.dispatch.domain.ProposalId

/**
 * The Lapse Proposal command. Represents the recognition that no response
 * arrived to a proposal while waiting remained appropriate. The mechanism
 * by which lapsing is recognized (a timeout, a policy decision) is not
 * this command's concern — see [com.pios.dispatch.domain.Proposal.lapse]'s
 * own KDoc; this command only records the fact once it is already
 * established elsewhere. Mirrors [AcceptProposalCommand]'s own shape.
 */
data class LapseProposalCommand(
    val proposalId: ProposalId
)
