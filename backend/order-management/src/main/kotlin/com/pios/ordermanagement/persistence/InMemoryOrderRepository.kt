package com.pios.ordermanagement.persistence

import com.pios.ordermanagement.application.OrderRepository
import com.pios.ordermanagement.domain.Order
import com.pios.ordermanagement.domain.OrderId
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [OrderRepository]. Originally proved
 * the save/load lifecycle a repository abstraction requires ahead of any
 * database technology decision (PERSISTENCE_ARCHITECTURE.md Section 8);
 * superseded as Order Management's production adapter by
 * [PostgreSQLOrderRepository] (PostgreSQL Persistence Order Management
 * v1.0, per ADR-025). Retained, deliberately not `@Repository`-annotated,
 * as a fast, dependency-free test double — many existing unit tests
 * across this module and its consumers construct it directly and would
 * otherwise require a live PostgreSQL connection for basic checks. Not a
 * production storage mechanism: state is held only in this process's
 * memory and is lost when it ends.
 */
class InMemoryOrderRepository : OrderRepository {
    private val store = ConcurrentHashMap<OrderId, Order>()

    override fun save(order: Order) {
        store[order.id] = order
    }

    override fun findById(id: OrderId): Order? = store[id]
}
