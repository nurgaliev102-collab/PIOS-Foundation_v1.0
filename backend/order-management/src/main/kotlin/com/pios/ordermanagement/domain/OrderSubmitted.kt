package com.pios.ordermanagement.domain

import java.time.Instant

/**
 * OrderSubmitted (EVENT_CATALOG.md Section 5). Owned exclusively by Order
 * Management. Marks the beginning of an order's lifecycle: an order has
 * been received. No event infrastructure, broker, or schema is implied
 * by this representation, consistent with ADR-003 and EVENT_CATALOG.md's
 * own scope.
 *
 * ## Task 15C (First Refusal Contract Completion and Concurrency Safety)
 *
 * [origin] and [explicitDriverIntent] are added so a future Dispatch-side
 * consumer of this event can determine whether the passenger's own
 * primary driver (ADR-062) may be offered First Refusal for this order,
 * without Dispatch ever querying Order Management or Passenger Experience
 * synchronously. Both are additive: every field this event already
 * carried remains unchanged in name, type, and meaning.
 *
 * [origin] — Order Management's own reference to the order's own
 * passenger (see [OrderOrigin]'s own KDoc; the same value [Order.origin]
 * already carries) — serialized on the wire as the opaque
 * `payload.passengerReference` string
 * ([com.pios.ordermanagement.application.OrderLifecycleApplicationService]'s
 * own envelope builder), never as a richer representation. No passenger
 * profile, contact, or other data is added — exactly the identifier a
 * Dispatch-side `PrimaryDriverRecord` lookup needs and nothing more.
 *
 * [explicitDriverIntent] mirrors [Order.explicitDriverIntent] exactly —
 * see that property's own KDoc for the atomicity argument. Defaults to
 * `false` so a caller of [com.pios.ordermanagement.domain.Order.submit]
 * unaware of this concept produces the same event shape as before this
 * field's own addition, with the same (correct, conservative) meaning.
 *
 * ## Task 16 (First Refusal Runtime Integration)
 *
 * [isTest] mirrors [Order.isTest] exactly. Found missing during this
 * task's own Phase 0 verification of Task 15C's own work: without it, a
 * First-Refusal Proposal automatically created for a *test* order would
 * be indistinguishable from real production activity in Owner Control
 * Center's own test/production data separation (`Proposal.isTest`,
 * `Assignment.isTest`) — every other Dispatch-side aggregate this order
 * eventually produces already carries this flag; the event that
 * originates the whole chain must too. Defaults to `false`, matching
 * [Order.isTest]'s own default and every existing caller's own current,
 * unaffected behavior.
 */
data class OrderSubmitted(
    val orderId: OrderId,
    val origin: OrderOrigin,
    val explicitDriverIntent: Boolean = false,
    val isTest: Boolean = false,
    val requestedDriverId: String? = null,
    val requestedPickupAt: Instant? = null,
    val occurredAt: Instant = Instant.now()
)
