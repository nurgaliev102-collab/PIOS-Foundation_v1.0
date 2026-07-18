package com.pios.ordermanagement.persistence

import com.pios.ordermanagement.application.OrderRepository
import com.pios.ordermanagement.domain.Order
import com.pios.ordermanagement.domain.OrderId
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [OrderRepository]. Proves the save/load
 * lifecycle a repository abstraction requires without selecting or
 * depending on any external database, ORM, or shared storage — the
 * specific storage technology remains intentionally undecided until a
 * future Database Design (PERSISTENCE_ARCHITECTURE.md Section 8). Not a
 * production storage mechanism: state is held only in this process's
 * memory and is lost when it ends.
 */
@Repository
class InMemoryOrderRepository : OrderRepository {
    private val store = ConcurrentHashMap<OrderId, Order>()

    override fun save(order: Order) {
        store[order.id] = order
    }

    override fun findById(id: OrderId): Order? = store[id]
}
