package com.pios.drivermanagement.domain

import java.time.Instant

/**
 * Server-side read model for "Мой бизнес" (driver CRM view of their
 * passengers) -- one row per (driver, passenger) pair this driver has
 * completed at least one ride with, per
 * `docs/PIOS_TAXI_RELATIONSHIP_MODEL_EVALUATION.md` Part 5's "safe to
 * implement autonomously" read-model half of the Relationship stage.
 *
 * A plain data holder, not an aggregate -- [PostgreSQLDriverClientsRepository]
 * owns the read-modify-write of [rideCount]/[lastRideAt] entirely, exactly
 * the same shape [DriverMilestones]'s own KDoc already establishes for this
 * module.
 *
 * [isRepeat] mirrors `driver_client_rides.ride_count >= 2` verbatim -- the
 * same threshold [DriverMilestonesRepository.incrementRepeatClientsCount]'s
 * caller (`AssignmentCompletedApplicationService`) already uses to grow
 * `driver_milestones.repeat_clients_count`, per [REPEAT_CLIENT_THRESHOLD]
 * below. Not a new business rule -- see
 * `V7__driver_milestones_repeat_clients.sql`'s own comment ("ride_count
 * reaching 2 is the moment this pair becomes a 'repeat client'").
 */
data class DriverClientRecord(
    val driverId: DriverId,
    val passengerReference: String,
    val rideCount: Int,
    val lastRideAt: Instant?
) {
    init {
        require(rideCount >= 0) { "rideCount must not be negative" }
    }

    val isRepeat: Boolean get() = rideCount >= REPEAT_CLIENT_THRESHOLD

    companion object {
        const val REPEAT_CLIENT_THRESHOLD = 2
    }
}
