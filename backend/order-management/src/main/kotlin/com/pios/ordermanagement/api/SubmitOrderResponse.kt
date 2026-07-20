package com.pios.ordermanagement.api

/**
 * The REST response body for Submit Order (Tranche 2: Passenger Experience
 * REST Transport; ADR-004, ADR-028). Mirrors
 * [com.pios.ordermanagement.application.OrderSubmissionRequestHandler.handle]'s
 * own existing return value exactly — the new order's id, nothing else.
 */
data class SubmitOrderResponse(val orderId: String)
