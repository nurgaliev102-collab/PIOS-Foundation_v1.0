package com.pios.ordermanagement.domain

import java.util.UUID

/**
 * The Order aggregate (DOMAIN_MODEL.md Section 4), within Order
 * Management's exclusive ownership (ADR-005, ADR-019).
 *
 * This implementation covers submission, completion, and cancellation.
 * The assigned, accepted, and ride states an order passes through once
 * Dispatch allocates a driver (DOMAIN_MODEL.md Section 12) depend on
 * Dispatch's own capability and remain outside this scope; they are not
 * modeled here.
 *
 * Sprint FND-006 (Minimal Order Model) adds [origin] — the fact without
 * which an Order cannot yet be traced back to who it is for. It closes
 * DOMAIN_MODEL.md Section 4's own already-ratified invariant ("An order
 * ... originates from exactly one source"), previously left
 * unimplemented. Set once, at submission, and never changes for the rest
 * of the order's lifecycle — neither [complete] nor [cancel] touches it.
 *
 * Sprint FND-006 originally also added a `destination` field, made
 * mandatory on the existing `POST /v1/orders` contract. That change was
 * reverted in this same sprint's follow-up (backward-compatibility
 * correction): it broke the one real caller of that contract
 * (`passenger-experience`'s `RestClientOrderSubmissionClient`), which
 * never sent one, and — per ADR-026's independent-deployability
 * guarantee — a caller cannot be assumed to upgrade in lockstep with
 * this module. Destination is deliberately not represented here for now;
 * a future sprint's own design proposal (not this file) describes
 * introducing it through a versioned endpoint instead, per ADR-010's
 * Versioning Strategy and ADR-015's Evolution Strategy.
 *
 * Invariant (DOMAIN_MODEL.md Section 11): an order has exactly one current
 * status at any time — enforced by holding a single [status] property
 * that is replaced, never appended to. A completed order cannot return to
 * an active state, and a cancellation may occur only before an order is
 * completed — both enforced by [complete] and [cancel] rejecting any call
 * made outside the SUBMITTED status.
 */
class Order private constructor(
    val id: OrderId,
    status: OrderStatus,
    val origin: OrderOrigin
) {
    var status: OrderStatus = status
        private set

    /**
     * Completes the order, per the invariant that only a submitted order
     * may be completed. Rejects the call if the order is not currently
     * SUBMITTED — including if it is already COMPLETED, since re-completing
     * an order is not a meaningful transition, and if it is CANCELLED,
     * since a cancelled order cannot proceed to completion.
     */
    fun complete(): OrderCompleted {
        check(status == OrderStatus.SUBMITTED) {
            "Order ${id.value} cannot be completed from status $status"
        }
        status = OrderStatus.COMPLETED
        return OrderCompleted(orderId = id)
    }

    /**
     * Cancels the order, per the invariant that cancellation may occur
     * only before an order is completed (DOMAIN_MODEL.md Section 11).
     * Rejects the call if the order is not currently SUBMITTED — including
     * if it is already COMPLETED or already CANCELLED.
     */
    fun cancel(): OrderCancelled {
        check(status == OrderStatus.SUBMITTED) {
            "Order ${id.value} cannot be cancelled from status $status"
        }
        status = OrderStatus.CANCELLED
        return OrderCancelled(orderId = id)
    }

    companion object {
        /**
         * Submits a new order, per the Submit Order command
         * (DOMAIN_MODEL.md Section 9). Submission is the order's creation,
         * not a transition on an existing order, so this is a factory
         * function rather than an instance method. The order's identifier
         * is generated as part of submission, since no order exists prior
         * to it. [origin] is required at this point.
         */
        fun submit(origin: OrderOrigin): SubmittedOrder {
            val order = Order(
                id = OrderId(UUID.randomUUID().toString()),
                status = OrderStatus.SUBMITTED,
                origin = origin
            )
            return SubmittedOrder(order = order, event = OrderSubmitted(orderId = order.id))
        }
    }
}

/**
 * The result of submitting an order: the new [Order] and the
 * [OrderSubmitted] event recording its creation.
 */
data class SubmittedOrder(val order: Order, val event: OrderSubmitted)
