package com.pios.drivermanagement.persistence

import com.pios.drivermanagement.application.DriverMilestonesRepository
import com.pios.drivermanagement.domain.DriverId
import com.pios.drivermanagement.domain.DriverMilestones
import com.pios.drivermanagement.domain.RideStreakCalculator
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2):
 * a fast, dependency-free test double for [DriverMilestonesRepository],
 * mirroring [InMemoryDriverRepository]'s own precedent exactly. Applies
 * the same [RideStreakCalculator] logic
 * [PostgreSQLDriverMilestonesRepository] uses, so a test exercising
 * [com.pios.drivermanagement.application.AssignmentCompletedApplicationService]
 * against this double observes the same streak behavior production would.
 */
class InMemoryDriverMilestonesRepository : DriverMilestonesRepository {
    private val store = ConcurrentHashMap<DriverId, DriverMilestones>()

    override fun recordCompletedRide(driverId: DriverId, completedAt: Instant, statedPriceParsed: Long?) {
        val existing = store[driverId]
        val newStreak = RideStreakCalculator.nextStreak(
            previousStreak = existing?.currentStreakWeeks ?: 0,
            lastCompletedAt = existing?.lastCompletedAt,
            newRideAt = completedAt
        )
        store[driverId] = DriverMilestones(
            driverId = driverId,
            completedRidesCount = (existing?.completedRidesCount ?: 0) + 1,
            currentStreakWeeks = newStreak,
            lastCompletedAt = completedAt,
            repeatClientsCount = existing?.repeatClientsCount ?: 0,
            totalStatedEarnings = (existing?.totalStatedEarnings ?: 0) + (statedPriceParsed ?: 0),
            unpricedRidesCount = (existing?.unpricedRidesCount ?: 0) + (if (statedPriceParsed == null) 1 else 0)
        )
    }

    override fun incrementRepeatClientsCount(driverId: DriverId) {
        val existing = store[driverId]
        store[driverId] = if (existing == null) {
            DriverMilestones(
                driverId = driverId,
                completedRidesCount = 0,
                currentStreakWeeks = 0,
                lastCompletedAt = null,
                repeatClientsCount = 1
            )
        } else {
            existing.copy(repeatClientsCount = existing.repeatClientsCount + 1)
        }
    }

    override fun findByDriverId(driverId: DriverId): DriverMilestones? = store[driverId]
}
