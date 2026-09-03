package com.pios.ordermanagement.api

import com.pios.ordermanagement.application.OrderSubmissionRequestHandler
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Order Management's Submit Order REST entry point (ADR-004, Resource-
 * Oriented API Style; ADR-028, Production Integration Transport Decision
 * -- direct, request-driven interaction goes through REST). Closes the
 * one remaining MVP flow (Tranche 2: Passenger Experience REST Transport)
 * still exclusively on ADR-027's provisional, test-scoped-only mechanism.
 *
 * Delegates to the already-existing, already-tested
 * [OrderSubmissionRequestHandler] unchanged (INTERFACE_CONTRACTS.md
 * Section 5) -- this controller adds no business logic of its own. The
 * only validation performed here is the transport-level presence check
 * Kotlin's non-nullable [SubmitOrderRequest.passengerReference] already
 * gives for free at deserialization; the sole authority over whether a
 * passenger reference is acceptable remains
 * [com.pios.ordermanagement.domain.OrderOrigin]'s own construction check
 * (APPLICATION_ARCHITECTURE.md Section 2, "No Business Logic
 * Duplication").
 *
 * A blank passenger reference surfaces as the [IllegalArgumentException]
 * the domain value type already throws, mapped here to HTTP 400 -- the
 * first document to fix a concrete status code for this contract
 * (API_SPECIFICATION.md Section 10 leaves the choice to the
 * implementation). The mapping is done directly in this method, not
 * through a `@ControllerAdvice`, so it remains visible and testable
 * without a Spring MVC test context -- consistent with this project's own
 * constructor-based, no-Spring-context testing convention.
 *
 * Sprint FND-006 briefly required a `destination` field on this
 * contract, then reverted it within the same sprint (backward-
 * compatibility correction): see [SubmitOrderRequest]'s own KDoc. Sprint
 * 3B (MVR Pilot Enablement) reintroduces it as optional -- passed through
 * unchanged to [OrderSubmissionRequestHandler.handle] alongside
 * [request]'s passenger reference.
 */
@RestController
@RequestMapping("/v1/orders")
class OrderSubmissionController(
    private val orderSubmissionRequestHandler: OrderSubmissionRequestHandler
) {

    @PostMapping
    fun submitOrder(@RequestBody request: SubmitOrderRequest): ResponseEntity<SubmitOrderResponse> =
        try {
            val orderId = orderSubmissionRequestHandler.handle(
                request.passengerReference,
                request.destination,
                request.passengerName,
                request.pickupAddress,
                request.requestedPickupAt,
                request.isTest,
                request.explicitDriverIntent
            )
            ResponseEntity.status(HttpStatus.CREATED).body(SubmitOrderResponse(orderId))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
}
