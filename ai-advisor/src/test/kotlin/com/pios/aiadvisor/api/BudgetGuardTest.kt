package com.pios.aiadvisor.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Proves ADR-056 Decision 8's three independent gates (rate limit, daily budget, monthly budget), checked in order. */
class BudgetGuardTest {

    private fun guard(minIntervalMillis: Long = 0, maxCallsPerDay: Int = 1000, maxCallsPerMonth: Int = 1000) =
        BudgetGuard(minIntervalMillis, maxCallsPerDay, maxCallsPerMonth)

    @Test
    fun `the first call is always allowed`() {
        assertEquals(BudgetGuard.GuardResult.Allowed, guard().check())
    }

    @Test
    fun `a second call within the minimum interval is rate limited`() {
        val g = guard(minIntervalMillis = 60_000)
        g.check()
        g.recordSuccess()

        assertIs<BudgetGuard.GuardResult.RateLimited>(g.check())
    }

    @Test
    fun `a call after the minimum interval has elapsed is allowed again`() {
        val g = guard(minIntervalMillis = 0)
        g.check()
        g.recordSuccess()

        assertEquals(BudgetGuard.GuardResult.Allowed, g.check())
    }

    @Test
    fun `a rejected (never-succeeded) call does not itself trigger the rate limit`() {
        val g = guard(minIntervalMillis = 60_000)
        // check() alone, with no recordSuccess(), must not arm the interval.
        g.check()

        assertEquals(BudgetGuard.GuardResult.Allowed, g.check())
    }

    @Test
    fun `the daily budget is enforced once max-calls-per-day successes are recorded`() {
        val g = guard(minIntervalMillis = 0, maxCallsPerDay = 2)
        g.check(); g.recordSuccess()
        g.check(); g.recordSuccess()

        assertEquals(BudgetGuard.GuardResult.DailyBudgetExceeded, g.check())
    }

    @Test
    fun `the monthly budget is enforced once max-calls-per-month successes are recorded, even under a higher daily cap`() {
        val g = guard(minIntervalMillis = 0, maxCallsPerDay = 1000, maxCallsPerMonth = 2)
        g.check(); g.recordSuccess()
        g.check(); g.recordSuccess()

        assertEquals(BudgetGuard.GuardResult.MonthlyBudgetExceeded, g.check())
    }

    @Test
    fun `a failed provider call must not be recorded as budget spend -- only recordSuccess counts`() {
        val g = guard(minIntervalMillis = 0, maxCallsPerDay = 1)
        // Simulate a provider failure: check() succeeds, but recordSuccess() is never called.
        g.check()

        assertEquals(BudgetGuard.GuardResult.Allowed, g.check())
    }
}
