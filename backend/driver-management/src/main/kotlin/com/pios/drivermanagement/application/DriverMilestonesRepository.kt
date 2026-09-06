package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.DriverId
import com.pios.drivermanagement.domain.DriverMilestones
import java.time.Instant

/**
 * Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2):
 * storage boundary for a driver's own milestone counts. No storage
 * technology is named here, mirroring every other repository interface in
 * this module.
 */
interface DriverMilestonesRepository {
    /**
     * Records one newly-completed ride for [driverId] at [completedAt]:
     * increments the stored completed-ride count by one and recomputes the
     * streak via [com.pios.drivermanagement.domain.RideStreakCalculator]
     * against whatever this driver's own previous streak/[lastCompletedAt]
     * already were (a driver with no existing row is treated as
     * streak `0`, `lastCompletedAt = null` — their first completed ride).
     * Idempotency (never calling this twice for the same event) is
     * [AssignmentCompletedApplicationService]'s own responsibility, not
     * this repository's.
     */
    fun recordCompletedRide(driverId: DriverId, completedAt: Instant)

    /**
     * Growth Loops TZ v1, Phase 2 extension: increments [driverId]'s stored
     * `repeat_clients_count` by one. Only ever called immediately after
     * [recordCompletedRide] for the same [driverId] within the same
     * transaction (`AssignmentCompletedApplicationService`), so a row is
     * always already guaranteed to exist by the time this runs.
     */
    fun incrementRepeatClientsCount(driverId: DriverId)

    /** `null` for a driver with no completed ride recorded yet. */
    fun findByDriverId(driverId: DriverId): DriverMilestones?
}
