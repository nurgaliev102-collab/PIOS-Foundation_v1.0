package com.pios.drivermanagement.api

/**
 * Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2)
 * and its Phase 2 extension (Section 2.1's own disclosed gap, now closed):
 * the REST response body for Retrieve Driver Milestones.
 *
 * [totalStatedEarnings] and [unpricedRidesCount] (ADR-065, Driver Earnings
 * from Self-Stated Prices) are this driver's own derived earnings figures
 * -- see [com.pios.drivermanagement.domain.DriverMilestones]'s own KDoc.
 * No currency symbol or formatting is added here: the backend asserts no
 * currency (ADR-042 Open Question 2 stays open; ADR-065 Decision item 4).
 *
 * [invitedDriversCount] (ADR-073, Driver-to-Driver Referral -- Single-Hop
 * Origin Fact, Part 4) is a plain count of drivers who registered through
 * this driver's own `/d/:inviterDriverCode` link, sourced live from
 * [com.pios.drivermanagement.application.DriverRepository.countInvitedBy]
 * -- never persisted here, never a score/rank/trend, and never surfaced
 * anywhere other than this already-`Bearer`-gated endpoint (see
 * [com.pios.drivermanagement.api.DriverController.getMilestones]'s own
 * KDoc for why: this is private business data about this driver, and
 * ADR-073 Part 5 forbids attaching anything of value to this fact).
 */
data class DriverMilestonesResponse(
    val driverId: String,
    val completedRidesCount: Long,
    val currentStreakWeeks: Int,
    val repeatClientsCount: Int,
    val totalStatedEarnings: Long,
    val unpricedRidesCount: Int,
    val invitedDriversCount: Long
)
