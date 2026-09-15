package com.pios.billing.domain

/**
 * A reference to the driver a subscription belongs to (ADR-074 Part 2).
 * Billing does not own driver data -- this value carries no availability,
 * standing, vehicle, or any other Driver Management-owned information,
 * only the plain id needed to record and look up a [Subscription]. Never a
 * foreign key into `driver-management`'s own database, never a copy of
 * that module's data, never resolved through a synchronous call on a write
 * path (ADR-005/ADR-019, the rule first established for
 * `network-management` in ADR-037). Local to Billing, mirroring the same
 * module-isolation pattern already established by
 * `com.pios.passengerexperience.domain.DriverReference` and
 * `com.pios.dispatch.domain.DriverReference`.
 */
@JvmInline
value class DriverReference(val driverId: String) {
    init {
        require(driverId.isNotBlank()) { "Driver reference must not be blank" }
    }
}
