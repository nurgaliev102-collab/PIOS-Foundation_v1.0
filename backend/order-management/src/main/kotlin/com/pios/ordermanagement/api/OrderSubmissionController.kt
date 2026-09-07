package com.pios.ordermanagement.api

import com.pios.ordermanagement.application.OrderSubmissionRequestHandler
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
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
 *
 * ## Order Provenance / Authentication Remediation (P0, `docs/PIOS_DATA_FLOW_CODE_AUDIT.md` Section 5)
 *
 * `submitOrder` now requires a `Bearer` session token whose own `sub`
 * equals `request.passengerReference` -- closing the gap that audit found:
 * this endpoint previously accepted any caller-supplied
 * `passengerReference` with no authentication at all, so `Order.origin`
 * (ADR-060 Decision 2: "the authenticated passenger's own identityId") was,
 * in practice, whatever string the caller happened to send. Uses no new
 * mechanism -- [sessionTokenVerifier] is the same class
 * [OrderCancellationController]'s own `cancelOrder` and
 * [OrderQueryController]'s own `listOrders` already use, and the check
 * mirrors [OrderCancellationController.cancelOrder]'s own shape exactly:
 * 401 with no valid token, checked before the request body is otherwise
 * interpreted; 403 when the token's own `sub` names a different passenger
 * than [SubmitOrderRequest.passengerReference]. No owner/`Basic` credential
 * branch is added -- unlike [OrderQueryController]'s own read side, no
 * legitimate reason exists for anyone but the passenger themselves to
 * submit an order in their own name. `frontend/src/pages/RideRequest/RideRequest.tsx`'s
 * own `handleSubmit` already holds a valid session token before this
 * screen is ever reachable (its own early `if (!identity) return` guard),
 * so this closes the gap without requiring a new login flow anywhere.
 */
@RestController
@RequestMapping("/v1/orders")
class OrderSubmissionController(
    private val orderSubmissionRequestHandler: OrderSubmissionRequestHandler,
    private val sessionTokenVerifier: SessionTokenVerifier
) {

    @PostMapping
    fun submitOrder(
        @RequestBody request: SubmitOrderRequest,
        @RequestHeader("Authorization", required = false) authorization: String? = null
    ): ResponseEntity<SubmitOrderResponse> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (request.passengerReference != verified.sub) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        return try {
            val orderId = orderSubmissionRequestHandler.handle(
                request.passengerReference,
                request.destination,
                request.passengerName,
                request.pickupAddress,
                request.requestedPickupAt,
                request.isTest,
                request.explicitDriverIntent,
                request.passengerCount
            )
            ResponseEntity.status(HttpStatus.CREATED).body(SubmitOrderResponse(orderId))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }
}
