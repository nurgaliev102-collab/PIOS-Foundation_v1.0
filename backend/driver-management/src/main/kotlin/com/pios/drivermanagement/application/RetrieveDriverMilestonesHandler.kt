package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.DriverId
import com.pios.drivermanagement.domain.DriverMilestones
import org.springframework.stereotype.Service

/**
 * Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2):
 * application-layer coordination for the Retrieve Driver Milestones query
 * -- a read-only lookup, mirroring [RetrieveDriverAvailabilityHandler]'s
 * own shape. A driver with no completed ride recorded yet is not an error:
 * [handle] returns a zeroed [DriverMilestones] rather than throwing, since
 * "no milestones yet" is this query's own normal answer for a brand-new
 * driver, not a not-found condition the way an unknown [driverId] would be
 * (this handler does not check driver existence at all — Driver
 * Management's own `GET /v1/drivers/{id}` already owns that check).
 */
@Service
class RetrieveDriverMilestonesHandler(
    private val driverMilestonesRepository: DriverMilestonesRepository
) {
    fun handle(driverId: DriverId): DriverMilestones =
        driverMilestonesRepository.findByDriverId(driverId)
            ?: DriverMilestones(driverId, completedRidesCount = 0, currentStreakWeeks = 0, lastCompletedAt = null)
}
