package com.pios.ordermanagement.domain

import java.util.UUID

/**
 * The Order aggregate (DOMAIN_MODEL.md Section 4), within Order
 * Management's exclusive ownership (ADR-005, ADR-019).
 *
 * This implementation covers submission, completion, and cancellation
 * only. The assigned, accepted, and ride states an order passes through
 * once Dispatch allocates a driver (DOMAIN_MODEL.md Section 12) depend on
 * Dispatch's own capability and are outside this task's scope; they are
 * not modeled here. Order Origin (DOMAIN_MODEL.md Section 6) is likewise
 * not represented here, for the same reason — neither is named in this
 * task's explicit scope.
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
    status: OrderStatus
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
         * to it.
         */
        fun submit(): SubmittedOrder {
            val order = Order(id = OrderId(UUID.randomUUID().toString()), status = OrderStatus.SUBMITTED)
            return SubmittedOrder(order = order, event = OrderSubmitted(orderId = order.id))
        }
    }
}

/**
 * The result of submitting an order: the new [Order] and the
 * [OrderSubmitted] event recording its creation.
 */
data class SubmittedOrder(val order: Order, val event: OrderSubmitted)
