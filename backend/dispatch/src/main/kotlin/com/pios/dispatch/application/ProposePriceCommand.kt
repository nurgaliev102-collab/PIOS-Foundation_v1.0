package com.pios.dispatch.application

import com.pios.dispatch.domain.ProposalId

/**
 * The Propose Price command. Represents a driver naming a price for this
 * ride, before it is confirmed (Product Owner instruction, 2026-09-05: a
 * driver must state a price before a ride is confirmed, and the passenger
 * must separately agree to it). Mirrors [AcceptProposalCommand]'s own
 * shape, except [statedPrice] is mandatory here, not optional — this
 * command exists specifically because a price is now required, not merely
 * offered.
 */
data class ProposePriceCommand(
    val proposalId: ProposalId,
    val statedPrice: String,
    val statedEtaMinutes: Int? = null
) {
    init {
        require(statedPrice.isNotBlank()) { "statedPrice must not be blank" }
        require(statedEtaMinutes == null || statedEtaMinutes in 1..240) {
            "statedEtaMinutes must be between 1 and 240 when present"
        }
    }
}
