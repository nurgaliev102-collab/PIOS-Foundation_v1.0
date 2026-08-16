package com.pios.ordermanagement.domain

import java.time.Instant
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
 *
 * Pilot readiness (first-pilot feedback: a driver's order card showed no
 * passenger name and no submission time). [passengerName] follows
 * [destination]'s own precedent exactly — optional, unvalidated passenger-
 * provided context, not a typed domain value, since nothing in ratified
 * documentation says anything about its shape or presence either. A
 * passenger who has not set a local display name (or an older client that
 * never sent one) simply produces `null`, same graceful-degradation
 * already established for [destination]. [createdAt] is set once, by
 * [submit], from the server's own clock — never passenger-supplied, so it
 * needs no validation. Both are set once, at submission, and never change
 * for the rest of the order's lifecycle, mirroring [origin]/[destination]'s
 * own immutability.
 *
 * Sprint H5 (Entrepreneur Working Cycle Integrity) adds [pickupAddress] —
 * where the passenger is, not [origin] (that value object is the
 * passenger's own participant reference, unrelated to any location). Follows
 * [destination]'s own precedent exactly: a plain, unvalidated `String?`,
 * appended as this class's own last constructor parameter (not inserted
 * before [createdAt]) so every existing positional call site — this class's
 * own [submit] factory and any test constructing an [Order] positionally —
 * continues to compile unchanged. Set once, at submission, and never
 * changes for the rest of the order's lifecycle, mirroring
 * [destination]/[passengerName]'s own immutability: neither [complete] nor
 * [cancel] touches it.
 *
 * ADR-058 (Scheduled Pickup Time) adds [requestedPickupAt] — the passenger's
 * own requested pickup instant for a pre-booked ride, `null` meaning "as
 * soon as possible" (the behaviour every existing row and every existing
 * caller already has, unchanged). Follows [pickupAddress]'s own precedent
 * exactly: optional, appended as this class's own last constructor
 * parameter, set once at submission, never touched by [complete] or
 * [cancel]. Unlike every other optional field on this aggregate, it is the
 * first **caller-supplied** time — deliberately not modeled as a new
 * [OrderStatus] value (ADR-058 Decision item 2): a scheduled order is an
 * ordinary [OrderStatus.SUBMITTED] order that happens to carry a requested
 * time, so every existing invariant, guard, and lifecycle path applies to
 * it unchanged.
 */
class Order private constructor(
    val id: OrderId,
    status: OrderStatus,
    val origin: OrderOrigin,
    val destination: String?,
    val passengerName: String?,
    val createdAt: Instant?,
    val pickupAddress: String?,
    val requestedPickupAt: Instant?
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
         * to it. [origin] is required at this point; [destination],
         * [passengerName], and [pickupAddress] default to `null` so every
         * existing call site (this class's own tests,
         * [com.pios.ordermanagement.application.OrderLifecycleApplicationService])
         * continues to compile and behave unchanged. [createdAt] is not a
         * parameter at all — always the server's own clock at the moment
         * of submission, never caller-supplied. [pickupAddress] (Sprint H5)
         * is appended last, after [passengerName], for the same positional-
         * compatibility reason [passengerName] itself was appended after
         * [destination].
         */
        fun submit(
            origin: OrderOrigin,
            destination: String? = null,
            passengerName: String? = null,
            pickupAddress: String? = null,
            requestedPickupAt: Instant? = null
        ): SubmittedOrder {
            val order = Order(
                id = OrderId(UUID.randomUUID().toString()),
                status = OrderStatus.SUBMITTED,
                origin = origin,
                destination = destination,
                passengerName = passengerName,
                createdAt = Instant.now(),
                pickupAddress = pickupAddress,
                requestedPickupAt = requestedPickupAt
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
