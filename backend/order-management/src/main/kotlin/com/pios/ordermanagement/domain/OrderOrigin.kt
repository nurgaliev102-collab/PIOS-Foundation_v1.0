package com.pios.ordermanagement.domain

/**
 * Order Management's own reference to the participant whose intention
 * originates a given order (DOMAIN_MODEL.md Section 4, Order's own
 * invariant: "originates from exactly one source — platform-aggregated
 * demand, a corporate customer, or a driver's personal client
 * relationship"). Only that this exists is represented here — which of
 * the three sources a given value denotes is not distinguished by this
 * type, since no approved document establishes a discriminator, and
 * Sprint FND-006's own scope is a single, minimal reference, not that
 * distinction.
 *
 * Narrow and local to Order Management, following the same
 * module-isolation reference shape already used elsewhere (Dispatch's
 * `DriverReference`/`OrderReference`; Passenger Experience's own
 * `PassengerReference`) per ADR-005/ADR-009: this type carries only the
 * identity value Order Management's own domain needs, never another
 * module's domain type. A value equal to Passenger Experience's own
 * `PassengerReference.passengerId` may be used to construct one, but the
 * two remain distinct types owned by distinct modules — this is not a
 * copy of Passenger Experience's own representation becoming Order
 * Management's information (INTERFACE_CONTRACTS.md Section 5), it is
 * Order Management's own fact about its own aggregate, expressed in its
 * own type.
 */
@JvmInline
value class OrderOrigin(val reference: String) {
    init {
        require(reference.isNotBlank()) { "OrderOrigin must not be blank" }
    }
}
