package com.pios.drivermanagement.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Growth Loops TZ v1, Phase 2 (docs/PIOS_GROWTH_LOOPS_TZ_V1.md Section 2.3):
 * "no rides this week resets, a ride every week for N weeks streaks to N, a
 * gap week resets from the gap forward" -- the three cases the TZ commits
 * to covering, plus the same-week no-op case the calculator's own KDoc
 * documents.
 */
class RideStreakCalculatorTest {

    // Monday 2026-08-03 00:00 UTC -- an arbitrary, known ISO week start.
    private val week1Monday = Instant.parse("2026-08-03T09:00:00Z")
    private val week1Friday = Instant.parse("2026-08-07T18:00:00Z")
    private val week2Monday = Instant.parse("2026-08-10T09:00:00Z")
    private val week3Monday = Instant.parse("2026-08-17T09:00:00Z")
    private val week5Monday = Instant.parse("2026-08-31T09:00:00Z")

    @Test
    fun `a driver's first-ever completed ride starts the streak at 1`() {
        val streak = RideStreakCalculator.nextStreak(previousStreak = 0, lastCompletedAt = null, newRideAt = week1Monday)

        assertEquals(1, streak)
    }

    @Test
    fun `a second ride in the same ISO week does not increment the streak`() {
        val streak = RideStreakCalculator.nextStreak(previousStreak = 1, lastCompletedAt = week1Monday, newRideAt = week1Friday)

        assertEquals(1, streak)
    }

    @Test
    fun `a ride every week for N consecutive weeks streaks to N`() {
        var streak = RideStreakCalculator.nextStreak(previousStreak = 0, lastCompletedAt = null, newRideAt = week1Monday)
        streak = RideStreakCalculator.nextStreak(previousStreak = streak, lastCompletedAt = week1Monday, newRideAt = week2Monday)
        streak = RideStreakCalculator.nextStreak(previousStreak = streak, lastCompletedAt = week2Monday, newRideAt = week3Monday)

        assertEquals(3, streak)
    }

    @Test
    fun `a gap week resets the streak to 1 from the gap forward`() {
        val streakBeforeGap = 3

        val streakAfterGap = RideStreakCalculator.nextStreak(
            previousStreak = streakBeforeGap,
            lastCompletedAt = week3Monday,
            newRideAt = week5Monday
        )

        assertEquals(1, streakAfterGap)
    }

    @Test
    fun `a ride landing in a week before the last counted ride also resets rather than decrementing`() {
        val streak = RideStreakCalculator.nextStreak(previousStreak = 4, lastCompletedAt = week3Monday, newRideAt = week1Monday)

        assertEquals(1, streak)
    }
}
