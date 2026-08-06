package com.pios.ordermanagement.api

/**
 * The REST response body for Cancel Order (P0-2 Tier 1;
 * `docs/SPRINT_PILOT_BLOCKERS.md`). Mirrors [SubmitOrderResponse]'s own
 * minimal shape: built directly from
 * [com.pios.ordermanagement.domain.OrderCancelled]'s own fields, never
 * from a fresh repository read — the caller already knows [orderId] (it
 * supplied it), and [status] confirms the transition without exposing
 * anything [OrderResponse]'s own fuller, list-query shape carries.
 */
data class CancelOrderResponse(val orderId: String, val status: String)
