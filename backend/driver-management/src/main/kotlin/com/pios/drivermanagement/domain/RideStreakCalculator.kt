package com.pios.drivermanagement.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2):
 * pure computation of a driver's "consecutive weeks with at least one
 * completed ride" streak. Kept free of any persistence/transport
 * dependency so it is unit-testable on its own, mirroring this codebase's
 * general preference for isolating business rules from infrastructure.
 *
 * A "week" here is the ISO week (Monday 00:00 UTC through the following
 * Sunday) — a fixed, unambiguous boundary, not the driver's own timezone
 * or a rolling 7-day window, so two drivers completing rides on the same
 * calendar day always land in the same week bucket.
 */
object RideStreakCalculator {

    /**
     * Returns the new streak value after a completed ride at [newRideAt],
     * given the driver's [previousStreak] and the [lastCompletedAt] of
     * their most recently counted ride (`null` for a driver's first-ever
     * completed ride).
     *
     * - No prior ride: streak becomes 1.
     * - Same ISO week as the last counted ride: streak is unchanged (a
     *   second ride in one week does not double-count).
     * - Exactly the next consecutive ISO week: streak increments by 1.
     * - Any earlier or later gap (including [newRideAt] landing in a week
     *   before [lastCompletedAt]'s own week — an out-of-order redelivery
     *   idempotency alone does not already filter out): streak resets to 1,
     *   since a real gap week (or an unresolvable ordering ambiguity) means
     *   "consecutive" can no longer be claimed.
     */
    fun nextStreak(previousStreak: Int, lastCompletedAt: Instant?, newRideAt: Instant): Int {
        if (lastCompletedAt == null) {
            return 1
        }
        val weeksBetween = weeksBetweenIsoWeekStarts(lastCompletedAt, newRideAt)
        return when (weeksBetween) {
            0L -> previousStreak
            1L -> previousStreak + 1
            else -> 1
        }
    }

    private fun weeksBetweenIsoWeekStarts(from: Instant, to: Instant): Long {
        val fromWeekStart = isoWeekStart(from)
        val toWeekStart = isoWeekStart(to)
        return ChronoUnit.WEEKS.between(fromWeekStart, toWeekStart)
    }

    private fun isoWeekStart(instant: Instant) =
        instant.atZone(ZoneOffset.UTC).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
