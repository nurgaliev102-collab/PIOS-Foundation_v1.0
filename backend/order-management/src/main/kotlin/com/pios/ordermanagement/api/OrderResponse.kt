package com.pios.ordermanagement.api

/**
 * The REST response body for a single order in the Retrieve Orders list
 * (Sprint FR-003, Order Query). Distinct from [SubmitOrderResponse]: that
 * type carries only the id Submit Order's own caller already knows it
 * just created, while a coordinator viewing the list has not seen any of
 * these orders before and needs enough to tell them apart —
 * [status] (DOMAIN_MODEL.md Section 6) and [origin] (the fact
 * [com.pios.ordermanagement.domain.OrderOrigin] already carries on the
 * aggregate). No other Order field is exposed: Sprint FR-003 excludes
 * filtering, search, and pagination, and nothing this sprint's own scope
 * requires depends on more than these three.
 */
data class OrderResponse(val id: String, val status: String, val origin: String)
