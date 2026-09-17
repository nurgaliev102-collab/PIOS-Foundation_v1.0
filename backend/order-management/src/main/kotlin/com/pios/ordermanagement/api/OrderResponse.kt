package com.pios.ordermanagement.api

/**
 * The REST response body for a single order in the Retrieve Orders list
 * (Sprint FR-003, Order Query). Distinct from [SubmitOrderResponse]: that
 * type carries only the id Submit Order's own caller already knows it
 * just created, while a coordinator viewing the list has not seen any of
 * these orders before and needs enough to tell them apart —
 * [status] (DOMAIN_MODEL.md Section 6) and [origin] (the fact
 * [com.pios.ordermanagement.domain.OrderOrigin] already carries on the
 * aggregate).
 *
 * [destination] (Sprint 3B: MVR Pilot Enablement — Optional Destination)
 * carries [com.pios.ordermanagement.domain.Order.destination] unchanged
 * — nullable, since the order it came from may never have had one. This
 * is what lets a driver, reading Order Management's own order list by
 * [id], learn a pending trip's destination without any change to
 * Dispatch's `Proposal`/`Assignment`.
 *
 * [passengerName] and [createdAt] (first-pilot feedback) carry
 * [com.pios.ordermanagement.domain.Order.passengerName]/[com.pios.ordermanagement.domain.Order.createdAt]
 * unchanged — both nullable for the same reason [destination] is: an
 * order submitted before this pilot fix, or by a passenger with no local
 * display name set, has neither. [createdAt] is rendered as its own
 * `Instant.toString()` (ISO-8601), the same explicit-`.toString()`
 * convention [com.pios.ordermanagement.application.OrderLifecycleApplicationService.envelopeFor]
 * already uses for a domain event's own `occurredAt`, rather than
 * depending on Jackson's automatic `Instant` (de)serialization.
 *
 * [pickupAddress] (Sprint H5: Entrepreneur Working Cycle Integrity) carries
 * [com.pios.ordermanagement.domain.Order.pickupAddress] unchanged — nullable
 * for the same reason [destination] is. This is what lets a driver, reading
 * this same list, see where to pick the passenger up, not only where they
 * are going.
 *
 * [passengerCount] (PIOS Group and Long-Distance Rides Roadmap, Stage 2)
 * carries [com.pios.ordermanagement.domain.Order.passengerCount] unchanged
 * — nullable for the same reason [destination] is. This is what lets a
 * driver or Coordinator see, next to a driver's own declared vehicle seat
 * count (`DriverResponse.vehicleSeatCount`), whether a car fits a group —
 * a fact this response merely carries, never a comparison this module
 * performs (see [com.pios.ordermanagement.domain.Order]'s own KDoc).
 *
 * [notes] (Product Cycle: Passenger Ride Requirements) carries
 * [com.pios.ordermanagement.domain.Order.notes] unchanged — nullable for
 * the same reason [destination] is. This is what lets a driver, reading
 * this same list before deciding on a price, see whatever the passenger
 * stated about this specific ride (a child seat, extra luggage, a pet,
 * help boarding, a meeting-point landmark) — a fact this response merely
 * carries, exactly like [pickupAddress]/[destination].
 */
data class OrderResponse(
    val id: String,
    val status: String,
    val origin: String,
    val destination: String?,
    val passengerName: String?,
    val createdAt: String?,
    val pickupAddress: String?,
    val requestedPickupAt: String? = null,
    val isTest: Boolean = false,
    val passengerCount: Int? = null,
    val notes: String? = null,
    val commitmentTerminated: Boolean = false
)
