package com.pios.dispatch.api

/**
 * The REST response body for every Proposal endpoint. Mirrors
 * [com.pios.dispatch.domain.Proposal]'s own identity, references, and
 * status — plus, since ADR-042 (Stated Ride Price Minimal Model, Decision
 * Revised R2/R5), the price the driver stated on acceptance, if any.
 *
 * [statedPrice] defaults to `null` so every existing construction of this
 * type (e.g. [ProposalController]'s own `declineProposal`/`lapseProposal`,
 * whose events never carry one, since [com.pios.dispatch.domain.Proposal.decline]
 * and [com.pios.dispatch.domain.Proposal.lapse] never set it) continues to
 * compile unchanged. This same field is delivered on both
 * `GET /v1/proposals?driverId=...` and `GET /v1/proposals?orderId=...`
 * through the one shared [ProposalController.toResponse] mapping — ADR-042
 * R8 records why no per-caller projection is introduced to withhold it
 * from the passenger-facing query, despite no passenger UI ever rendering
 * it.
 */
data class ProposalResponse(
    val proposalId: String,
    val orderId: String,
    val driverId: String,
    val status: String,
    val statedPrice: String? = null
)
