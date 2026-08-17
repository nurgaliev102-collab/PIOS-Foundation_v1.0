package com.pios.aiadvisor.api

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicReference

/**
 * ADR-056 Decision 8 — cost/abuse controls, checked **before** any provider
 * call is attempted (so a guarded-out request costs nothing, not even a
 * wasted call once a real provider exists). Same shape as
 * `OwnerCredentialGate`'s own rate limiter: in-memory, per-process, resets
 * on restart — acceptable at pilot scale (a handful of owner-initiated
 * clicks per day), disclosed rather than hidden, not solved with a database
 * `ai-advisor` deliberately does not have (ADR-056 Decision 11).
 *
 * Three independent gates, checked in order — minimum interval first (the
 * cheapest check, catches accidental double-clicks), then daily budget,
 * then monthly budget:
 * - [minIntervalMillis] between successive calls, regardless of budget.
 * - [maxCallsPerDay] successful calls per calendar day.
 * - [maxCallsPerMonth] successful calls per calendar month.
 *
 * "Successful" means a call that reached [recordSuccess] — a request
 * rejected by this guard itself, or one that failed before a provider
 * answered, is never counted against budget.
 */
@Component
class BudgetGuard(
    @Value("\${pios.ai-advisor.rate-limit.min-interval-ms:10000}") private val minIntervalMillis: Long,
    @Value("\${pios.ai-advisor.budget.max-calls-per-day:200}") private val maxCallsPerDay: Int,
    @Value("\${pios.ai-advisor.budget.max-calls-per-month:2000}") private val maxCallsPerMonth: Int
) {
    sealed interface GuardResult {
        data object Allowed : GuardResult
        data class RateLimited(val retryAfterMillis: Long) : GuardResult
        data object DailyBudgetExceeded : GuardResult
        data object MonthlyBudgetExceeded : GuardResult
    }

    private data class DayWindow(val date: LocalDate, val count: Int)
    private data class MonthWindow(val yearMonth: YearMonth, val count: Int)

    private val lastCallAt = AtomicReference<Instant?>(null)
    private val dayWindow = AtomicReference(DayWindow(LocalDate.now(), 0))
    private val monthWindow = AtomicReference(MonthWindow(YearMonth.now(), 0))

    /**
     * Checked before [AIProvider][com.pios.aiadvisor.domain.AIProvider] is
     * ever invoked. Does **not** itself record anything — [recordSuccess]
     * does that, separately, only once the provider has actually answered
     * (see this class's own KDoc on what "successful" means).
     */
    fun check(): GuardResult {
        val last = lastCallAt.get()
        if (last != null) {
            val sinceLast = Duration.between(last, Instant.now()).toMillis()
            if (sinceLast < minIntervalMillis) {
                return GuardResult.RateLimited(minIntervalMillis - sinceLast)
            }
        }
        val today = LocalDate.now()
        val currentDay = dayWindow.updateAndGet { if (it.date == today) it else DayWindow(today, 0) }
        if (currentDay.count >= maxCallsPerDay) {
            return GuardResult.DailyBudgetExceeded
        }
        val thisMonth = YearMonth.now()
        val currentMonth = monthWindow.updateAndGet { if (it.yearMonth == thisMonth) it else MonthWindow(thisMonth, 0) }
        if (currentMonth.count >= maxCallsPerMonth) {
            return GuardResult.MonthlyBudgetExceeded
        }
        return GuardResult.Allowed
    }

    /** Records one successful analysis against the rate limiter and both budget windows. */
    fun recordSuccess() {
        lastCallAt.set(Instant.now())
        val today = LocalDate.now()
        dayWindow.updateAndGet { if (it.date == today) DayWindow(it.date, it.count + 1) else DayWindow(today, 1) }
        val thisMonth = YearMonth.now()
        monthWindow.updateAndGet { if (it.yearMonth == thisMonth) MonthWindow(it.yearMonth, it.count + 1) else MonthWindow(thisMonth, 1) }
    }
}
