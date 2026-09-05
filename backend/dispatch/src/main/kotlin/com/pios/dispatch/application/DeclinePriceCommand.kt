package com.pios.dispatch.application

import com.pios.dispatch.domain.ProposalId

/**
 * The Decline Price command. Represents the passenger's own refusal of
 * the price the driver already stated ([ProposePriceCommand]). Per
 * Product Owner instruction (2026-09-05), this simply closes the request
 * -- no renegotiation, no automatic reroute to another driver.
 */
data class DeclinePriceCommand(
    val proposalId: ProposalId
)
