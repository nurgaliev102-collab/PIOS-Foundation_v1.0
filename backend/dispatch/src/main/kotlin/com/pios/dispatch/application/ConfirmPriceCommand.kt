package com.pios.dispatch.application

import com.pios.dispatch.domain.ProposalId

/**
 * The Confirm Price command. Represents the passenger's own agreement to
 * the price the driver already stated ([ProposePriceCommand]) — the
 * precondition for Assignment's own creation (ADR-035), taking over
 * [AcceptProposalCommand]'s own role in the real product flow now that a
 * price must be agreed first.
 */
data class ConfirmPriceCommand(
    val proposalId: ProposalId
)
