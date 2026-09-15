package com.pios.dispatch.application

import com.pios.dispatch.domain.Assignment
import com.pios.dispatch.domain.AssignmentId
import com.pios.dispatch.domain.OrderReference

/**
 * The persistence boundary for the Assignment aggregate
 * (PERSISTENCE_ARCHITECTURE.md Section 3, "Dispatch": "Persists the
 * Assignment logical entity"). This is the repository abstraction
 * Domain-Owned Persistence (PERSISTENCE_ARCHITECTURE.md Section 2)
 * requires: it names only what Dispatch's own domain needs — saving and
 * loading an Assignment by its identity — and says nothing about how or
 * where an Assignment is actually stored. No storage technology,
 * framework, or persistence implementation detail is named here or
 * anywhere in the domain layer.
 *
 * Only Dispatch persists or changes Assignment information
 * (PERSISTENCE_ARCHITECTURE.md Section 3); no other module implements or
 * depends on this interface.
 *
 * [findByOrder] added by Sprint FR-004 (Manual Assignment): closes the
 * gap [Assignment.create]'s own KDoc already names — "no repository
 * exists yet ... to check against a persisted store" — so a real
 * `POST /v1/assignments` can supply [DispatchAssignmentApplicationService.handle]
 * with the order's actual existing assignments instead of an empty list,
 * honoring the one-active-assignment-per-order invariant for real rather
 * than only in tests that construct the collection by hand. Purely
 * additive: [save] and [findById] are unchanged.
 *
 * [findByOrders] added alongside `GET /v1/assignments`'s own `orderIds`
 * batch parameter (`AssignmentController.listAssignments`): closes a real
 * N+1 — `DriverHome.tsx`'s 3-second poll previously issued one
 * [findByOrder] request per accepted proposal. Semantics mirror
 * [findByOrder] exactly, just across many orders in one call; an empty
 * [orders] list returns an empty result (never "no filter means
 * everything"). Purely additive: [findByOrder] is unchanged and remains
 * correct for a genuine single-order lookup.
 */
interface AssignmentRepository {
    fun save(assignment: Assignment)
    fun findById(id: AssignmentId): Assignment?
    fun findByOrder(order: OrderReference): List<Assignment>
    fun findByOrders(orders: List<OrderReference>): List<Assignment>
}
