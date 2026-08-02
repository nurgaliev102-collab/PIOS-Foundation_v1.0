package com.pios.ordermanagement.api

import com.pios.ordermanagement.application.RetrieveOrdersHandler
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
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
 * `listOrders` is read-only: delegates to the already-existing
 * [RetrieveOrdersHandler] and adds no business logic of its own,
 * mirroring Driver Management's own `DriverController.listDrivers`
 * convention (Sprint FR-002). Returns every order, unfiltered and
 * unordered — Sprint FR-003 explicitly excludes filtering, search, and
 * pagination.
 */
@RestController
@RequestMapping("/v1/orders")
class OrderQueryController(
    private val retrieveOrdersHandler: RetrieveOrdersHandler
) {

    @GetMapping
    fun listOrders(): ResponseEntity<List<OrderResponse>> =
        ResponseEntity.ok(
            retrieveOrdersHandler.handleAll()
                .map {
                    OrderResponse(
                        it.id.value,
                        it.status.name,
                        it.origin.reference,
                        it.destination,
                        it.passengerName,
                        it.createdAt?.toString(),
                        it.pickupAddress
                    )
                }
        )
}
