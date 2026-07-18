package com.pios.passengerexperience.domain

/**
 * A reference to the passenger or corporate customer originating an
 * order (DOMAIN_MODEL.md Section 5, "Passenger" / "Corporate Customer").
 * Passenger Experience represents this participant; this value carries
 * no profile, standing, or Personal Client Relationship information —
 * all of which are out of this capability's scope. Local to Passenger
 * Experience, consistent with the module-isolation pattern already used
 * by Dispatch's OrderReference/DriverReference.
 */
@JvmInline
value class PassengerReference(val passengerId: String) {
    init {
        require(passengerId.isNotBlank()) { "Passenger reference must not be blank" }
    }
}
