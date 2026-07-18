package com.pios.passengerexperience.domain

import java.time.Instant

/**
 * OrderSubmissionRequested: Passenger Experience's own record that a
 * passenger or corporate customer has requested an order be submitted.
 *
 * This is distinct from EVENT_CATALOG.md's OrderSubmitted, which Order
 * Management alone owns and produces once it actually creates the Order
 * aggregate (INTERFACE_CONTRACTS.md Section 3, "Explicit Ownership";
 * EVENT_CATALOG.md Section 6). This representation is Passenger
 * Experience's side of the Passenger Experience -> Order Management
 * contract (INTERFACE_CONTRACTS.md Section 5): the originating-passenger
 * context Order Management relies on when an order is submitted, not a
 * copy or duplicate of Order Management's own event or aggregate.
 */
data class OrderSubmissionRequested(
    val passenger: PassengerReference,
    val occurredAt: Instant = Instant.now()
)
