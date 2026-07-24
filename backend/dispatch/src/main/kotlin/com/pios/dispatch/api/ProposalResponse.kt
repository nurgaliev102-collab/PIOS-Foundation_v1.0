package com.pios.dispatch.api

/**
 * The REST response body for every Proposal endpoint. Mirrors
 * [com.pios.dispatch.domain.Proposal]'s own identity, references, and
 * status exactly — nothing else, the same minimal projection
 * [AssignOrderResponse] already establishes for Assignment.
 */
data class ProposalResponse(
    val proposalId: String,
    val orderId: String,
    val driverId: String,
    val status: String
)
