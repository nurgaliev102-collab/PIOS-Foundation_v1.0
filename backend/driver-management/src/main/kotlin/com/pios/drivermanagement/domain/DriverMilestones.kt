package com.pios.drivermanagement.domain

import java.time.Instant

/**
 * Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2):
 * a driver's own derived "business milestones" read model — how many rides
 * they have completed in total, how many consecutive weeks (per
 * [RideStreakCalculator]) they have completed at least one, and
 * [repeatClientsCount] — how many distinct passengers have completed at
 * least two rides with this driver.
 *
 * [repeatClientsCount] was originally cut from this phase's scope
 * (`AssignmentCompleted` alone never names the passenger) and is now
 * closed via a local correlation against Order Management's own
 * `OrderSubmitted` (already publishing `payload.passengerReference` for
 * exactly this kind of cross-module correlation — Dispatch's own First
 * Refusal feature already reads it the same way; no new event contract).
 *
 * [totalStatedEarnings] and [unpricedRidesCount] (ADR-065, Driver Earnings
 * from Self-Stated Prices) are this driver's own derived earnings figures:
 * the sum of every completed ride's stated price that parses per
 * [com.pios.drivermanagement.domain.PriceParser]'s digits-only rule
 * (Decision item 4), and how many completed rides did not. Both count only
 * rides completed from this ADR's ship date forward — no historical
 * backfill (Decision item 7).
 *
 * A plain data holder, not an aggregate: nothing here enforces invariants
 * beyond the counts being non-negative, and
 * [PostgreSQLDriverMilestonesRepository] owns the read-modify-write of
 * these fields entirely — this type exists only to give that repository's
 * return value a name.
 */
data class DriverMilestones(
    val driverId: DriverId,
    val completedRidesCount: Long,
    val currentStreakWeeks: Int,
    val lastCompletedAt: Instant?,
    val repeatClientsCount: Int = 0,
    val totalStatedEarnings: Long = 0,
    val unpricedRidesCount: Int = 0
) {
    init {
        require(completedRidesCount >= 0) { "completedRidesCount must not be negative" }
        require(currentStreakWeeks >= 0) { "currentStreakWeeks must not be negative" }
        require(repeatClientsCount >= 0) { "repeatClientsCount must not be negative" }
        require(totalStatedEarnings >= 0) { "totalStatedEarnings must not be negative" }
        require(unpricedRidesCount >= 0) { "unpricedRidesCount must not be negative" }
    }
}
