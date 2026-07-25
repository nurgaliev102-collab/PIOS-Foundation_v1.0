package com.pios.passengerexperience.domain

/**
 * A reference to the driver who invited a passenger (Sprint 7B: Personal
 * Network Flow MVP). Passenger Experience does not own driver data --
 * this value carries no availability, standing, or other Driver
 * Management-owned information, only the plain id needed to record and
 * look up a [Connection]. Local to Passenger Experience, mirroring the
 * same module-isolation pattern [PassengerReference]'s own KDoc already
 * describes for Dispatch's `OrderReference`/`DriverReference`.
 */
@JvmInline
value class DriverReference(val driverId: String) {
    init {
        require(driverId.isNotBlank()) { "Driver reference must not be blank" }
    }
}
