package com.pios.dispatch.persistence

import com.pios.dispatch.application.AssignmentRepository
import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.OrderReference
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [AssignmentRepository]. Originally
 * proved the save/load lifecycle a repository abstraction requires ahead
 * of any database technology decision (PERSISTENCE_ARCHITECTURE.md
 * Section 8); superseded as Dispatch's production adapter by
 * [PostgreSQLAssignmentRepository] (PostgreSQL Persistence Dispatch
 * v1.0, per ADR-025). Retained, deliberately not `@Repository`-annotated,
 * as a fast, dependency-free test double — several existing unit tests
 * across this module and its consumers construct it directly and would
 * otherwise require a live PostgreSQL connection for basic checks. Not a
 * production storage mechanism: state is held only in this process's
 * memory and is lost when it ends.
 */
class InMemoryAssignmentRepository : AssignmentRepository {
    private val store = ConcurrentHashMap<AssignmentId, Assignment>()

    override fun save(assignment: Assignment) {
        store[assignment.id] = assignment
    }

    override fun findById(id: AssignmentId): Assignment? = store[id]

    override fun findByOrder(order: OrderReference): List<Assignment> = store.values.filter { it.order == order }

    override fun findByOrders(orders: List<OrderReference>): List<Assignment> {
        if (orders.isEmpty()) return emptyList()
        val orderSet = orders.toSet()
        return store.values.filter { it.order in orderSet }
    }
}
