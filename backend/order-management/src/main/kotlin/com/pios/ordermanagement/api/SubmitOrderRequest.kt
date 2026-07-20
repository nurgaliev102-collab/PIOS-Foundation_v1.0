package com.pios.ordermanagement.api

/**
 * The REST request body for Submit Order (Tranche 2: Passenger Experience
 * REST Transport; ADR-004, ADR-028). Carries exactly the primitive shape
 * ADR-027 already fixed for this contract — [passengerReference] alone,
 * nothing else — so this transport-layer addition introduces no request
 * field beyond what [com.pios.ordermanagement.application.OrderSubmissionRequestHandler]
 * already accepts.
 */
data class SubmitOrderRequest(val passengerReference: String)
