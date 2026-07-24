package com.pios.dispatch.api

/**
 * The REST request body for Create Proposal. Carries only the two
 * references [com.pios.dispatch.application.ProposeDriverCommand] already
 * requires — plain strings at the transport boundary, converted to
 * [com.pios.dispatch.domain.OrderReference] /
 * [com.pios.dispatch.domain.DriverReference] by [ProposalController],
 * which is also where a blank value's [IllegalArgumentException] is
 * caught and mapped to HTTP 400. Mirrors [AssignOrderRequest] exactly.
 */
data class ProposeDriverRequest(val orderId: String, val driverId: String)
