package com.pios.drivermanagement.domain

import java.time.Instant

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
 *
 * [displayName] (Sprint 7B: Personal Network Flow MVP) is the plain-text
 * name shown to a passenger who was invited through this driver's own
 * link ("Вас пригласил Артур") — optional and immutable once set, the
 * same "plain nullable field, no new Value Object" precedent
 * `Order.destination` already established (Sprint 3B).
 *
 * [createdAt] (ADR-043, Owner Control Center — Observation Boundary) is
 * the moment this driver was registered, surfaced as `registeredAt` on
 * `GET /v1/drivers` — optional and immutable once set, appended last so
 * every existing positional call site keeps compiling. `null` for every
 * driver created before this field existed (`V4__add_driver_created_at.sql`'s
 * own disclosed limitation) and for any caller that still omits it.
 *
 * [isTest] (Owner Control Center test/production data separation,
 * 2026-08-17) marks a driver deliberately created by an automated or
 * manual technical verification rather than a real pilot participant.
 * Defaults to `false` so every existing caller (every real driver's own
 * registration) is unaffected; only a caller that explicitly opts in
 * produces `true`. Set once, at creation, and never changes afterward —
 * the same immutability every other optional field on this class already
 * has.
 */
class Driver(
    val id: DriverId,
    availability: Availability = Availability.UNAVAILABLE,
    val displayName: String? = null,
    val createdAt: Instant? = null,
    val isTest: Boolean = false
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
