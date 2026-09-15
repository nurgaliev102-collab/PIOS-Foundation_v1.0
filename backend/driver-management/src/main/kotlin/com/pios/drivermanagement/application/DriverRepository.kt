package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.Driver
import com.pios.drivermanagement.domain.DriverId

/**
 * The persistence boundary for the Driver aggregate
 * (PERSISTENCE_ARCHITECTURE.md Section 3, "Driver Management": "Persists
 * the Driver logical entity, including its availability information").
 * This is the repository abstraction Domain-Owned Persistence
 * (PERSISTENCE_ARCHITECTURE.md Section 2) requires: it names only what
 * Driver Management's own domain needs — saving and loading a Driver by
 * its identity — and says nothing about how or where a Driver is
 * actually stored. No storage technology, framework, or persistence
 * implementation detail is named here or anywhere in the domain layer.
 *
 * Only Driver Management persists a driver's availability state
 * (PERSISTENCE_ARCHITECTURE.md Section 3); no other module implements or
 * depends on this interface.
 *
 * [findAll] added by Sprint FR-002 (Driver Availability), for the
 * coordinator's own list-of-drivers view — no filtering or ordering is
 * named here, consistent with that sprint's own scope.
 *
 * [countInvitedBy] (ADR-073, Driver-to-Driver Referral -- Single-Hop
 * Origin Fact, Part 4) is a direct count over this same storage --
 * "derived, not denormalized" -- rather than a value persisted anywhere,
 * so it can never drift from the [Driver.invitedByDriverId] facts it
 * counts.
 *
 * Deliberately does NOT filter on `isTest` (correction, 2026-09-15,
 * found via live production E2E): ADR-073 Consequences offered the
 * `isTest = FALSE` filter only as "a recommendation to the developer,
 * not a business rule," and named the exact condition for escalating it
 * instead -- "if this is judged a behaviour change rather than hygiene,
 * it needs the Product Owner." The first implementation applied it
 * unconditionally, which made the feature permanently unverifiable
 * through this project's own sanctioned E2E method (every live test in
 * this codebase, without exception, uses `isTest = true` entities --
 * never real production accounts). A count a driver can never see
 * exercised in production because production testing is itself
 * excluded from it is a behaviour change, not hygiene. Reverted to no
 * filter, matching the identical, already-ratified precedent this
 * ADR's own Consequences section names directly: `driver_milestones`
 * "does not segregate today" either. `isTest` drivers are already kept
 * out of every real user's visible surface by the mechanisms that
 * actually matter (ADR-069's fallback selection, Circle of Trust) --
 * a private count on a driver's own gated dashboard needs no second,
 * inconsistent gate on top of that.
 */
interface DriverRepository {
    fun save(driver: Driver)
    fun findById(id: DriverId): Driver?
    fun findAll(): List<Driver>
    fun countInvitedBy(id: DriverId): Long
}
