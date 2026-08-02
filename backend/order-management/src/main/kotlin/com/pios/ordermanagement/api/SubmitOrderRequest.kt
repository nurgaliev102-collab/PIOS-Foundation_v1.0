package com.pios.ordermanagement.api

/**
 * The REST request body for Submit Order (Tranche 2: Passenger Experience
 * REST Transport; ADR-004, ADR-028). Carries exactly the primitive shape
 * [com.pios.ordermanagement.application.OrderSubmissionRequestHandler]
 * already accepts — no field is introduced here that the handler does
 * not itself require.
 *
 * A `destination` field was added by Sprint FND-006 (Minimal Order Model)
 * and then reverted within the same sprint: it made an existing,
 * already-deployed contract's request body *require* a field its one
 * real caller (`passenger-experience`'s `RestClientOrderSubmissionClient`)
 * never sent — a breaking change to a live contract, given ADR-026's
 * independent-deployability guarantee.
 *
 * Sprint 3B (MVR Pilot Enablement — Optional Destination) reintroduces
 * [destination] the way that revert said it should be reintroduced:
 * **optional**, defaulting to `null`, so a request body carrying only
 * `passengerReference` — exactly what `RestClientOrderSubmissionClient`
 * still sends — remains valid and unaffected. See
 * [com.pios.ordermanagement.domain.Order]'s own KDoc for why
 * [destination] is a plain, unvalidated `String?`.
 *
 * [passengerName] (first-pilot feedback) follows the same optional,
 * defaulting-to-`null` shape for the same backward-compatibility reason.
 *
 * [pickupAddress] (Sprint H5: Entrepreneur Working Cycle Integrity) follows
 * the same optional, defaulting-to-`null` shape, appended last, for the
 * same backward-compatibility reason — it lets the passenger state where
 * to be picked up, closing the gap where this contract previously only
 * carried where they were going.
 */
data class SubmitOrderRequest(
    val passengerReference: String,
    val destination: String? = null,
    val passengerName: String? = null,
    val pickupAddress: String? = null
)
