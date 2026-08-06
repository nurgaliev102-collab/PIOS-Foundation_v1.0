package com.pios.dispatch.application

import com.pios.dispatch.domain.ProposalId

/**
 * The Withdraw Proposal command (ADR-053, Proposal Resolution on Order
 * Cancellation). Represents the recognition that the order this proposal
 * was for has been cancelled before any driver acceptance. Mirrors
 * [LapseProposalCommand]'s own shape exactly — this command only records
 * the fact once it is already established elsewhere (Order Management's
 * own `OrderCancelled` event, consumed by
 * [com.pios.dispatch.persistence.OrderCancelledListener]).
 */
data class WithdrawProposalCommand(
    val proposalId: ProposalId
)
