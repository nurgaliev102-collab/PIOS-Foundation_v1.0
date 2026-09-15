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
 *
 * [vehicleMake]/[vehicleModel]/[vehicleColor]/[vehiclePlateNumber]/
 * [vehicleSeatCount] (PIOS Group and Long-Distance Rides Roadmap, Stage 1
 * -- a minimal owned-by-driver vehicle record, per
 * `docs/PIOS_TAXI_V1_TARGET_ARCHITECTURE.md` Section 3/6/19's own analysis:
 * optional at first, plain fields rather than a new Value Object, mirroring
 * [displayName]'s own precedent exactly -- there is no cross-field
 * invariant among them worth a dedicated type). Set together, via
 * [updateVehicle], never individually -- a driver either has a car on file
 * or does not; there is no meaningful state where only some of these fields
 * are known and PIOS asserts anything about which ones. `null` for every
 * driver who has not yet declared a vehicle, including every driver
 * registered before this capability existed.
 *
 * [acceptsLongDistanceTrips] (PIOS Group and Long-Distance Rides Roadmap,
 * Stage 3) is a driver's own declaration of willingness to take long trips
 * (vakhta/airport/another city runs) -- a plain boolean, not a new Value
 * Object, mirroring [isTest]'s own precedent: defaults to `false` so every
 * existing driver, including every one registered before this field
 * existed, is unaffected until they explicitly opt in via
 * [updateLongDistancePreference].
 *
 * [invitedByDriverId] (ADR-073, Driver-to-Driver Referral -- Single-Hop
 * Origin Fact) is the id of the Driver whose own registration link this
 * driver registered through, if any -- a single, immutable, optional edge
 * recorded once at creation, appended last so every existing positional
 * call site keeps compiling, the same treatment [createdAt] received.
 * [com.pios.drivermanagement.application.CreateDriverApplicationService]
 * is the only place this is ever set, and it degrades an unrecognized or
 * self-referencing value to `null` rather than failing registration (see
 * that class's own KDoc). `null` for every driver registered before this
 * field existed and for every driver who registered through the
 * passenger-facing link or with no inviter at all.
 *
 * This is the aggregate's first Driver-to-Driver relationship (ADR-073
 * Consequences) -- intentionally just one nullable string, no new Value
 * Object, no chain: ADR-073 Part 5 binds this codebase to never walk it
 * more than one hop, and to never attach anything of value to it.
 */
class Driver(
    val id: DriverId,
    availability: Availability = Availability.UNAVAILABLE,
    val displayName: String? = null,
    val createdAt: Instant? = null,
    val isTest: Boolean = false,
    vehicleMake: String? = null,
    vehicleModel: String? = null,
    vehicleColor: String? = null,
    vehiclePlateNumber: String? = null,
    vehicleSeatCount: Int? = null,
    acceptsLongDistanceTrips: Boolean = false,
    val invitedByDriverId: String? = null
) {
    var availability: Availability = availability
        private set

    var vehicleMake: String? = vehicleMake
        private set

    var vehicleModel: String? = vehicleModel
        private set

    var vehicleColor: String? = vehicleColor
        private set

    var vehiclePlateNumber: String? = vehiclePlateNumber
        private set

    var vehicleSeatCount: Int? = vehicleSeatCount
        private set

    var acceptsLongDistanceTrips: Boolean = acceptsLongDistanceTrips
        private set

    /**
     * A driver's own declaration of their vehicle's details -- always
     * replaces the whole record, mirroring [declareAvailability]'s own
     * "single current state, replaced never appended" invariant. Blank
     * strings are treated as "not provided" (`null`), the same convention
     * [com.pios.drivermanagement.api.DriverController] already applies
     * nowhere else on this aggregate but that this codebase applies
     * elsewhere (e.g. `Order.pickupAddress`'s own caller-side trimming) --
     * kept here, not at the transport boundary, since this is the one and
     * only place this rule needs to be enforced. [seatCount], if supplied,
     * must be positive: a car with zero or negative seats is not a fact
     * PIOS can record. No event is produced -- unlike [declareAvailability],
     * nothing outside this module reads a driver's vehicle today, so there
     * is no cross-module contract to notify.
     */
    fun updateVehicle(make: String?, model: String?, color: String?, plateNumber: String?, seatCount: Int?) {
        require(seatCount == null || seatCount > 0) { "vehicleSeatCount must be positive" }
        vehicleMake = make?.trim()?.takeIf { it.isNotBlank() }
        vehicleModel = model?.trim()?.takeIf { it.isNotBlank() }
        vehicleColor = color?.trim()?.takeIf { it.isNotBlank() }
        vehiclePlateNumber = plateNumber?.trim()?.takeIf { it.isNotBlank() }
        vehicleSeatCount = seatCount
    }

    /**
     * A driver's own declaration of their willingness to take long-distance
     * trips (PIOS Group and Long-Distance Rides Roadmap, Stage 3) — always
     * replaces the whole flag, mirroring [updateVehicle]'s own "single
     * current state, replaced never appended" invariant. No event is
     * produced — the same reasoning [updateVehicle]'s own KDoc gives:
     * nothing outside this module reads this fact today.
     */
    fun updateLongDistancePreference(accepts: Boolean) {
        acceptsLongDistanceTrips = accepts
    }

    /**
     * A driver's own declaration of their readiness to receive an
     * assignment (DOMAIN_MODEL.md Section 6, "Availability"). Produces a
     * [DriverAvailabilityChanged] event only when the declared state
     * differs from the driver's current state — consistent with that
     * event's own meaning in EVENT_CATALOG.md Section 7, "has changed."
     *
     * Returns null when the declared availability matches the current
     * state, since no change occurred to report.
     *
     * Carries this driver's own [isTest] on the produced event (ADR-069) --
     * this aggregate's own field, sourced by the owning module, never
     * inferred or fetched by any consumer.
     */
    fun declareAvailability(newAvailability: Availability): DriverAvailabilityChanged? {
        if (newAvailability == availability) {
            return null
        }
        availability = newAvailability
        return DriverAvailabilityChanged(
            driverId = id,
            availability = newAvailability,
            isTest = isTest
        )
    }
}
