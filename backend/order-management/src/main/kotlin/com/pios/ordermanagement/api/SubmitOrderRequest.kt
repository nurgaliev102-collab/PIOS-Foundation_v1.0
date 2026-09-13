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
 *
 * [requestedPickupAt] (ADR-058, Scheduled Pickup Time) follows the same
 * optional, defaulting-to-`null`, appended-last shape — a passenger's
 * requested pickup instant for a pre-booked ride, as an ISO-8601 string
 * carrying an explicit offset or `Z` (ADR-058 Decision item 4). `null`
 * (or absent) means "as soon as possible", unchanged from today. Parsed
 * to [java.time.Instant] at the application boundary
 * ([com.pios.ordermanagement.application.OrderSubmissionRequestHandler.handle]);
 * a value that does not parse is a 400.
 *
 * [explicitDriverIntent] (Task 15C: First Refusal Contract Completion and
 * Concurrency Safety) follows the same optional, defaulting-to-`false`
 * shape — see [com.pios.ordermanagement.domain.Order.explicitDriverIntent]'s
 * own KDoc. Not sent by any current caller; the field exists so a future
 * caller that already knows it is about to make an explicit driver
 * choice for this order can record that fact atomically with submission.
 *
 * [passengerCount] (PIOS Group and Long-Distance Rides Roadmap, Stage 2)
 * follows the same optional, defaulting-to-`null` shape — see
 * [com.pios.ordermanagement.domain.Order]'s own KDoc. A negative or zero
 * value surfaces as the domain's own [IllegalArgumentException], mapped
 * by [OrderSubmissionController] to the same 400 every other invalid
 * input on this contract already produces.
 *
 * [notes] (Product Cycle: Passenger Ride Requirements) follows the same
 * optional, defaulting-to-`null` shape — the passenger's own free-text
 * statement of anything about this specific ride PIOS has no dedicated
 * field for (a child seat, extra luggage, a pet, help boarding, a
 * meeting-point landmark). See
 * [com.pios.ordermanagement.domain.Order]'s own KDoc for why this is a
 * plain, unvalidated `String?` rather than a typed value, and for its one
 * bound: a value longer than [com.pios.ordermanagement.domain.Order.MAX_NOTES_LENGTH]
 * surfaces as the domain's own [IllegalArgumentException], mapped the
 * same way [passengerCount]'s own invalid value already is.
 */
data class SubmitOrderRequest(
    val passengerReference: String,
    val destination: String? = null,
    val passengerName: String? = null,
    val pickupAddress: String? = null,
    val requestedPickupAt: String? = null,
    val isTest: Boolean = false,
    val explicitDriverIntent: Boolean = false,
    val passengerCount: Int? = null,
    val notes: String? = null
)
