package com.pios.dispatch.api

/**
 * The REST request body for Create Proposal. Carries only the two
 * references [com.pios.dispatch.application.ProposeDriverCommand] already
 * requires — plain strings at the transport boundary, converted to
 * [com.pios.dispatch.domain.OrderReference] /
 * [com.pios.dispatch.domain.DriverReference] by [ProposalController],
 * which is also where a blank value's [IllegalArgumentException] is
 * caught and mapped to HTTP 400. Mirrors [AssignOrderRequest] exactly.
 *
 * [passengerReference] (ADR-066, Proposal Participant Authorization) is
 * now required by [ProposalController.createProposal] — absent or blank is
 * HTTP 400, mirroring `SubmitOrderRequest`'s own required
 * `passengerReference` precedent (`order-management`). Nullable at the
 * transport type itself only so a caller that omits it entirely gets the
 * same clear 400 as one that sends `""`, rather than a deserialization
 * failure.
 */
data class ProposeDriverRequest(
    val orderId: String,
    val driverId: String,
    val isTest: Boolean = false,
    val passengerReference: String? = null
)
