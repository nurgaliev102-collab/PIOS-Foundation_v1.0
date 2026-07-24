package com.pios.ordermanagement.application

import com.pios.ordermanagement.domain.Order
import com.pios.ordermanagement.domain.OrderId

/**
 * The persistence boundary for the Order aggregate
 * (PERSISTENCE_ARCHITECTURE.md Section 3, "Order Management": "Persists
 * the Order logical entity across its lifecycle"). This is the
 * repository abstraction Domain-Owned Persistence
 * (PERSISTENCE_ARCHITECTURE.md Section 2) requires: it names only what
 * Order Management's own domain needs — saving and loading an Order by
 * its identity — and says nothing about how or where an Order is
 * actually stored. No storage technology, framework, or persistence
 * implementation detail is named here or anywhere in the domain layer.
 *
 * Order Management is the final authority on an order's persisted state
 * (PERSISTENCE_ARCHITECTURE.md Section 3); no other module implements or
 * depends on this interface.
 *
 * [findAll] added by Sprint FR-003 (Order Query) for the coordinator's own
 * list-of-orders view — the same broadening [DriverRepository.findAll]
 * already added to Driver Management in Sprint FR-002, for the same
 * reason. No filtering, sorting, or pagination.
 */
interface OrderRepository {
    fun save(order: Order)
    fun findById(id: OrderId): Order?
    fun findAll(): List<Order>
}
