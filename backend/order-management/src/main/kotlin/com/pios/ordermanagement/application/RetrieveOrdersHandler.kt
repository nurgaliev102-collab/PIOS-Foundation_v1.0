package com.pios.ordermanagement.application

import com.pios.ordermanagement.domain.Order
import org.springframework.stereotype.Service

/**
 * Application-layer coordination for the Retrieve Orders query
 * (Sprint FR-003, Order Query) — the coordinator's own list-of-orders
 * view, ahead of Sprint FR-004 (Assignment). This is a read-only lookup:
 * it changes nothing, publishes no event, and touches no outbox, unlike
 * [OrderLifecycleApplicationService]'s command handling.
 *
 * Mirrors [com.pios.drivermanagement.application.RetrieveDriverAvailabilityHandler.handleAll]'s
 * own shape exactly. No filtering, sorting, or pagination: this sprint's
 * own scope is a plain list, nothing more.
 */
@Service
class RetrieveOrdersHandler(
    private val orderRepository: OrderRepository
) {

    fun handleAll(): List<Order> = orderRepository.findAll()
}
