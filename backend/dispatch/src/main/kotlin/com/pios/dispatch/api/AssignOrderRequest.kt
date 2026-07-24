package com.pios.dispatch.api

/**
 * The REST request body for Assign Order (Sprint FR-004, Manual
 * Assignment). Carries only the two references
 * [com.pios.dispatch.application.AssignOrderCommand] already requires —
 * plain strings at the transport boundary, converted to
 * [com.pios.dispatch.domain.OrderReference] /
 * [com.pios.dispatch.domain.DriverReference] by
 * [AssignmentController], which is also where a blank value's
 * [IllegalArgumentException] is caught and mapped to HTTP 400.
 */
data class AssignOrderRequest(val orderId: String, val driverId: String)
