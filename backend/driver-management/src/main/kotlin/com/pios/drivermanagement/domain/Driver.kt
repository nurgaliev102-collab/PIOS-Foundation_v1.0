package com.pios.drivermanagement.domain

/**
 * The Driver aggregate (DOMAIN_MODEL.md Section 4), within Driver
 * Management's exclusive ownership (ADR-005, ADR-019).
 *
 * This implementation covers only the availability capability. A driver's
 * standing and participation (DOMAIN_MODEL.md Section 4) are outside this
 * capability's scope and are not represented here.
 *
 * Invariant (DOMAIN_MODEL.md Section 11): a driver has exactly one current
 * availability state at any time — enforced by holding a single
 * [availability] property that is replaced, never appended to.
 */
class Driver(
    val id: DriverId,
    availability: Availability = Availability.UNAVAILABLE
) {
    var availability: Availability = availability
        private set

    /**
     * A driver's own declaration of their readiness to receive an
     * assignment (DOMAIN_MODEL.md Section 6, "Availability"). Produces a
     * [DriverAvailabilityChanged] event only when the declared state
     * differs from the driver's current state — consistent with that
     * event's own meaning in EVENT_CATALOG.md Section 7, "has changed."
     *
     * Returns null when the declared availability matches the current
     * state, since no change occurred to report.
     */
    fun declareAvailability(newAvailability: Availability): DriverAvailabilityChanged? {
        if (newAvailability == availability) {
            return null
        }
        availability = newAvailability
        return DriverAvailabilityChanged(
            driverId = id,
            availability = newAvailability
        )
    }
}
