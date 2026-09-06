package com.pios.drivermanagement.api

/**
 * Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2)
 * and its Phase 2 extension (Section 2.1's own disclosed gap, now closed):
 * the REST response body for Retrieve Driver Milestones.
 */
data class DriverMilestonesResponse(
    val driverId: String,
    val completedRidesCount: Long,
    val currentStreakWeeks: Int,
    val repeatClientsCount: Int
)
