package com.pios.ordermanagement.api

/**
 * The REST request body for Submit Order (Tranche 2: Passenger Experience
 * REST Transport; ADR-004, ADR-028). Carries exactly the primitive shape
 * [com.pios.ordermanagement.application.OrderSubmissionRequestHandler]
 * already accepts — no field is introduced here that the handler does
 * not itself require.
 *
 * This is the existing, public v1 shape of this contract. A `destination`
 * field was added by Sprint FND-006 (Minimal Order Model) and then
 * reverted within the same sprint: it made an existing, already-deployed
 * contract's request body require a field its one real caller
 * (`passenger-experience`'s `RestClientOrderSubmissionClient`) never
 * sent — a breaking change to a live contract, given ADR-026's
 * independent-deployability guarantee. `destination` will be
 * (re)introduced through a separate, versioned endpoint in a future
 * sprint, per ADR-010's Versioning Strategy and ADR-015's Evolution
 * Strategy — never by silently changing what this existing version
 * requires.
 */
data class SubmitOrderRequest(val passengerReference: String)
