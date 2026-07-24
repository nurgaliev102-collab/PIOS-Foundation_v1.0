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
 * this module.
 *
 * Sprint 3B (MVR Pilot Enablement — Optional Destination) reintroduces
 * [destination] the way Sprint FND-006's own revert said it should be
 * reintroduced: **optional**, not mandatory, so the existing contract's
 * one real caller keeps working unmodified. [destination] is optional
 * passenger-provided trip context for pilot usage — a plain, unvalidated
 * `String?`, deliberately not wrapped in a value object the way [origin]
 * is: [origin] enforces a ratified domain invariant
 * (non-blank, DOMAIN_MODEL.md Section 4), while nothing in ratified
 * documentation says anything about the shape or presence of a
 * destination yet, so this class asserts nothing about it beyond
 * carrying whatever the passenger typed, or nothing at all. Set once, at
 * submission, and never changes for the rest of the order's lifecycle —
 * neither [complete] nor [cancel] touches it, mirroring [origin]'s own
 * immutability exactly.
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
    val origin: OrderOrigin,
    val destination: String?
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
         * to it. [origin] is required at this point; [destination]
         * defaults to `null` so every existing call site (this class's
         * own tests, [com.pios.ordermanagement.application.OrderLifecycleApplicationService])
         * continues to compile and behave unchanged.
         */
        fun submit(origin: OrderOrigin, destination: String? = null): SubmittedOrder {
            val order = Order(
                id = OrderId(UUID.randomUUID().toString()),
                status = OrderStatus.SUBMITTED,
                origin = origin,
                destination = destination
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
