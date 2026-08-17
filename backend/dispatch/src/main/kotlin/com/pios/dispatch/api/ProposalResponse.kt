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
 *
 * [createdAt]/[respondedAt] (ADR-043, Owner Control Center — Observation
 * Boundary) surface [com.pios.dispatch.domain.Proposal]'s own same-named
 * properties — optional, appended last, `null` for every proposal created
 * before `V7__proposal_timestamps.sql`, and [respondedAt] specifically
 * `null` for any proposal still [com.pios.dispatch.domain.ProposalStatus.OPEN].
 * Any consumer of this response (the Owner Control Center included) must
 * discard [statedPrice] on receipt rather than render, sum, or report it
 * (ADR-043 Decision 6; ADR-042 R4.3) — a constraint on the reader, not
 * something this shape itself can enforce.
 */
data class ProposalResponse(
    val proposalId: String,
    val orderId: String,
    val driverId: String,
    val status: String,
    val statedPrice: String? = null,
    val createdAt: String? = null,
    val respondedAt: String? = null,
    val statedEtaMinutes: Int? = null,
    val isTest: Boolean = false
)
