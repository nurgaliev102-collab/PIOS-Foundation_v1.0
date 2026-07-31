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
 *
 * [statedPrice] is the optional amount the driver stated when accepting
 * (ADR-042, Decision Revised R2/R5) — `null` for "no amount stated,"
 * which is also the value for every caller that predates this field. If
 * present, it must be non-blank; this mirrors the same
 * `require(value.isNotBlank())` shape this module's own reference value
 * types ([ProposalId], [com.pios.dispatch.domain.OrderReference],
 * [com.pios.dispatch.domain.DriverReference]) already use for their own
 * (mandatory) string content, applied here only when a value is actually
 * supplied (ADR-042 R4.2: no check beyond basic shape is authorized).
 */
data class AcceptProposalCommand(
    val proposalId: ProposalId,
    val statedPrice: String? = null
) {
    init {
        require(statedPrice == null || statedPrice.isNotBlank()) {
            "statedPrice must not be blank when present"
        }
    }
}
