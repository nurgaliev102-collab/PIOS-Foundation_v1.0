package com.pios.ordermanagement.api

import com.pios.ordermanagement.application.RetrieveOrdersHandler
import com.pios.ordermanagement.domain.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Order Management's Retrieve Orders REST entry point (Sprint FR-003,
 * Order Query; ADR-004, Resource-Oriented API Style). A separate
 * controller class from [OrderSubmissionController], despite sharing the
 * same `/v1/orders` base path — that class's own name and KDoc are
 * specific to Submit Order, and this endpoint adds no business logic of
 * its own to reuse there; splitting keeps each controller's name
 * accurate rather than growing "Submission" to also mean "Query". Spring
 * allows multiple `@RestController` classes under the same
 * `@RequestMapping` base as long as their own method mappings do not
 * collide, which `GET` and `POST /v1/orders` do not.
 *
 * ## Authorization (ADR-060, Order Query Authorization)
 *
 * `listOrders` used to return every order, unfiltered, to any caller —
 * a confirmed P0 vulnerability (anonymous retrieval of every passenger's
 * name, pickup address, destination and schedule). It now requires
 * exactly one of three mutually exclusive modes, selected by the
 * `Authorization` header's scheme and the query parameters present (ADR-060
 * Decision 1, "Access matrix — `GET /v1/orders`"):
 *
 * - `Authorization: Basic` (the owner credential [ownerCredentialGate]
 *   already checks for [HealthController]) with **no** query parameter —
 *   every order, unchanged from before this ADR for the one caller who
 *   legitimately needs the full list (ADR-060 Decision 5, amending ADR-044
 *   Decision 5).
 * - `Authorization: Bearer` (a session token [sessionTokenVerifier] checks)
 *   with `?passengerReference=<id>` — only if `<id>` equals the token's own
 *   `sub`, that passenger's own orders (ADR-060 Decision 2: `Order.origin`
 *   is, from this ADR forward, the authenticated passenger's `identityId`).
 * - `Authorization: Bearer` with `?ids=<uuid>,<uuid>,…` — only for a
 *   driver-linked token (`drv != null`), the subset of those ids that
 *   exist. This does **not** verify the driver actually holds a proposal
 *   for each id (ADR-060 Decision 3, a disclosed, Product-Owner-accepted
 *   limitation) — Order Management has no driver concept and does not call
 *   Dispatch to acquire one (ADR-060 Decision 3's own "why not").
 *
 * Every other combination is 401 (auth failure) or 400 (malformed
 * parameters) per the access matrix — never a silent empty list, and never
 * 404 for an unknown id ("simply absent from the response," same reasoning
 * ADR-055 Decision 4 applied to `connectionId`). Auth is resolved before
 * parameter shape is checked, and parameter shape before the subject
 * comparison, so an unauthenticated caller learns nothing about the
 * contract beyond "401."
 *
 * [OrderResponse]'s own shape is unchanged by this ADR (Decision/Answer 3):
 * filtering is row-level only, since every mode already returns only rows
 * the caller is entitled to.
 */
@RestController
@RequestMapping("/v1/orders")
class OrderQueryController(
    private val retrieveOrdersHandler: RetrieveOrdersHandler,
    private val ownerCredentialGate: OwnerCredentialGate,
    private val sessionTokenVerifier: SessionTokenVerifier
) {

    @GetMapping
    fun listOrders(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestParam(required = false) passengerReference: String?,
        @RequestParam(required = false) ids: String?
    ): ResponseEntity<List<OrderResponse>> {
        if (authorization != null && authorization.startsWith("Basic ")) {
            if (!ownerCredentialGate.verify(authorization)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }
            if (passengerReference != null || ids != null) {
                return ResponseEntity.badRequest().build()
            }
            return ResponseEntity.ok(retrieveOrdersHandler.handleAll().map { it.toResponse() })
        }

        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        if (passengerReference != null && ids != null) {
            return ResponseEntity.badRequest().build()
        }

        return when {
            passengerReference != null -> {
                if (passengerReference != verified.sub) {
                    ResponseEntity.status(HttpStatus.FORBIDDEN).build()
                } else {
                    ResponseEntity.ok(
                        retrieveOrdersHandler.handleAll()
                            .filter { it.origin.reference == passengerReference }
                            .map { it.toResponse() }
                    )
                }
            }
            ids != null -> {
                if (verified.drv == null) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
                }
                val idList = ids.split(",")
                if (idList.isEmpty() || idList.any { it.isBlank() } || idList.size > MAX_IDS) {
                    return ResponseEntity.badRequest().build()
                }
                val idSet = idList.toSet()
                ResponseEntity.ok(
                    retrieveOrdersHandler.handleAll()
                        .filter { it.id.value in idSet }
                        .map { it.toResponse() }
                )
            }
            else -> ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    private fun Order.toResponse(): OrderResponse =
        OrderResponse(
            id.value,
            status.name,
            origin.reference,
            destination,
            passengerName,
            createdAt?.toString(),
            pickupAddress,
            requestedPickupAt?.toString(),
            isTest
        )

    companion object {
        // ADR-060: a shape guard against an unbounded query, not a business
        // rule or pagination -- no meaning attaches to the number 100.
        private const val MAX_IDS = 100
    }
}
