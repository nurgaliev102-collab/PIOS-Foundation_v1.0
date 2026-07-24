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
 */
data class OrderResponse(val id: String, val status: String, val origin: String, val destination: String?)
