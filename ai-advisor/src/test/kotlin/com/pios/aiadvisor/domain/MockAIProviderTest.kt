package com.pios.aiadvisor.domain

import com.pios.aiadvisor.api.AssignmentMetrics
import com.pios.aiadvisor.api.DailySnapshot
import com.pios.aiadvisor.api.DriverMetrics
import com.pios.aiadvisor.api.HealthMetrics
import com.pios.aiadvisor.api.OrderMetrics
import com.pios.aiadvisor.api.PilotAnalysisRequest
import com.pios.aiadvisor.api.ProposalMetrics
import com.pios.aiadvisor.api.ReactionTimeMetrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Server-side port of `frontend/src/pages/OwnerControlCenter/aiProvider.test.ts`'s
 * own `MockAIProvider` suite (ADR-056 Rollout Step 2) — same rules, same
 * thresholds, same expected classifications, now proven against the Kotlin
 * implementation that replaces the frontend-local one as the source of truth.
 */
class MockAIProviderTest {

    private fun baseRequest(
        orders: OrderMetrics = OrderMetrics(0, 0, 0, 0),
        proposals: ProposalMetrics = ProposalMetrics(0, 0, 0, 0, 0, 0),
        assignments: AssignmentMetrics = AssignmentMetrics(0, 0, 0),
        drivers: DriverMetrics = DriverMetrics(0, 0, 0),
        reactionTime: ReactionTimeMetrics = ReactionTimeMetrics(null, null, 0),
        health: HealthMetrics = HealthMetrics(5, 5),
        currentDay: DailySnapshot? = null,
        history: List<DailySnapshot>? = null
    ) = PilotAnalysisRequest(
        generatedAt = "2026-08-17T12:00:00.000Z",
        periodLabel = "Весь период наблюдения (все данные, доступные системе сейчас)",
        orders = orders,
        proposals = proposals,
        assignments = assignments,
        drivers = drivers,
        reactionTime = reactionTime,
        health = health,
        currentDay = currentDay,
        history = history
    )

    private val provider = MockAIProvider()

    @Test
    fun `reports unknown and asks to wait for data when there are zero orders`() {
        val outcome = provider.analyze(baseRequest())

        val result = (outcome as AIProviderOutcome.Success).result
        assertEquals("unknown", result.status)
        assertTrue(result.summary.contains("Недостаточно данных"))
        assertTrue(result.keyFindings.isEmpty())
        assertTrue(result.risks.isEmpty())
        assertEquals(PilotAnalysisMetricsAllNull, Triple(result.metrics.acceptanceRate, result.metrics.completionRate, result.metrics.cancellationRate))
    }

    @Test
    fun `reports ok for a clean pilot -- no lapsed or declined proposals, no cancellations, every driver active, all modules up`() {
        val outcome = provider.analyze(
            baseRequest(
                orders = OrderMetrics(total = 5, completed = 5, cancelled = 0, open = 0),
                proposals = ProposalMetrics(total = 5, accepted = 5, declined = 0, lapsed = 0, withdrawn = 0, open = 0),
                drivers = DriverMetrics(total = 2, available = 2, withActivity = 2)
            )
        )

        val result = (outcome as AIProviderOutcome.Success).result
        assertEquals("ok", result.status)
        assertTrue(result.risks.isEmpty())
        assertEquals(1.0, result.metrics.acceptanceRate)
        assertEquals(1.0, result.metrics.completionRate)
        assertEquals(0.0, result.metrics.cancellationRate)
    }

    @Test
    fun `reports critical when a backend module is down, regardless of otherwise-healthy metrics`() {
        val outcome = provider.analyze(
            baseRequest(
                orders = OrderMetrics(total = 3, completed = 3, cancelled = 0, open = 0),
                proposals = ProposalMetrics(total = 3, accepted = 3, declined = 0, lapsed = 0, withdrawn = 0, open = 0),
                health = HealthMetrics(modulesUp = 4, modulesTotal = 5)
            )
        )

        val result = (outcome as AIProviderOutcome.Success).result
        assertEquals("critical", result.status)
        assertTrue(result.risks.any { it.contains("модул") })
    }

    @Test
    fun `reports critical when the cancellation rate is at or above 50 percent`() {
        val outcome = provider.analyze(
            baseRequest(
                orders = OrderMetrics(total = 4, completed = 2, cancelled = 2, open = 0),
                proposals = ProposalMetrics(total = 4, accepted = 2, declined = 0, lapsed = 0, withdrawn = 0, open = 0)
            )
        )

        assertEquals("critical", (outcome as AIProviderOutcome.Success).result.status)
    }

    @Test
    fun `reports attention when there are lapsed proposals, without being critical`() {
        val outcome = provider.analyze(
            baseRequest(
                orders = OrderMetrics(total = 5, completed = 4, cancelled = 0, open = 1),
                proposals = ProposalMetrics(total = 5, accepted = 4, declined = 0, lapsed = 1, withdrawn = 0, open = 0),
                drivers = DriverMetrics(total = 1, available = 1, withActivity = 1)
            )
        )

        val result = (outcome as AIProviderOutcome.Success).result
        assertEquals("attention", result.status)
        assertTrue(result.risks.any { it.contains("просрочен", ignoreCase = true) })
        assertTrue(result.recommendations.isNotEmpty())
    }

    @Test
    fun `reports attention when drivers exist but none has received any proposal`() {
        val outcome = provider.analyze(
            baseRequest(
                orders = OrderMetrics(total = 1, completed = 1, cancelled = 0, open = 0),
                proposals = ProposalMetrics(total = 1, accepted = 1, declined = 0, lapsed = 0, withdrawn = 0, open = 0),
                drivers = DriverMetrics(total = 3, available = 3, withActivity = 0)
            )
        )

        val result = (outcome as AIProviderOutcome.Success).result
        assertEquals("attention", result.status)
        assertTrue(result.risks.any { it.contains("Ни один водитель") })
    }

    @Test
    fun `includes the real reaction-time sample in key findings only when a sample exists`() {
        val withSample = provider.analyze(
            baseRequest(
                orders = OrderMetrics(total = 1, completed = 1, cancelled = 0, open = 0),
                proposals = ProposalMetrics(total = 1, accepted = 1, declined = 0, lapsed = 0, withdrawn = 0, open = 0),
                reactionTime = ReactionTimeMetrics(averageMinutes = 3.4, medianMinutes = 3.0, sampleSize = 1)
            )
        )
        assertTrue((withSample as AIProviderOutcome.Success).result.keyFindings.any { it.contains("реакции") })

        val withoutSample = provider.analyze(
            baseRequest(
                orders = OrderMetrics(total = 1, completed = 1, cancelled = 0, open = 0),
                proposals = ProposalMetrics(total = 1, accepted = 1, declined = 0, lapsed = 0, withdrawn = 0, open = 0)
            )
        )
        assertTrue((withoutSample as AIProviderOutcome.Success).result.keyFindings.none { it.contains("реакции") })
    }

    // --- PIOS Intelligence Trend Context -- data-quality fix (2026-08-17) ---

    private val historyDay16 = DailySnapshot(
        date = "2026-08-16",
        orders = OrderMetrics(total = 12, completed = 9, cancelled = 2, open = 0),
        proposals = ProposalMetrics(total = 12, accepted = 9, declined = 1, lapsed = 0, withdrawn = 0, open = 0),
        assignments = AssignmentMetrics(total = 9, completed = 9, inProgress = 0),
        activeDrivers = 4
    )
    private val currentDaySnapshot = DailySnapshot(
        date = "2026-08-17",
        orders = OrderMetrics(total = 6, completed = 4, cancelled = 1, open = 1),
        proposals = ProposalMetrics(total = 6, accepted = 4, declined = 1, lapsed = 0, withdrawn = 0, open = 1),
        assignments = AssignmentMetrics(total = 4, completed = 4, inProgress = 0),
        activeDrivers = 2
    )

    @Test
    fun `both currentDay and history absent -- never adds a trend finding, behaves exactly as before this feature existed`() {
        val outcome = provider.analyze(
            baseRequest(
                orders = OrderMetrics(total = 5, completed = 5, cancelled = 0, open = 0),
                proposals = ProposalMetrics(total = 5, accepted = 5, declined = 0, lapsed = 0, withdrawn = 0, open = 0)
            )
        )

        assertTrue((outcome as AIProviderOutcome.Success).result.keyFindings.none { it.contains("Тренд") || it.contains("сравнени") })
    }

    @Test
    fun `history present but empty, and currentDay present -- no trend finding, nothing to compare against`() {
        val outcome = provider.analyze(
            baseRequest(
                orders = OrderMetrics(total = 5, completed = 5, cancelled = 0, open = 0),
                proposals = ProposalMetrics(total = 5, accepted = 5, declined = 0, lapsed = 0, withdrawn = 0, open = 0),
                currentDay = currentDaySnapshot,
                history = emptyList()
            )
        )

        assertTrue((outcome as AIProviderOutcome.Success).result.keyFindings.none { it.contains("Тренд") })
    }

    @Test
    fun `currentDay and history both present -- compares the two same-scale daily snapshots, never the ALL-PERIOD cumulative total`() {
        val outcome = provider.analyze(
            baseRequest(
                // Deliberately a large ALL-PERIOD total (18) that must NEVER appear in the trend
                // finding's comparison -- only currentDay.orders.total (6) and historyDay16.orders.total (12)
                // are real same-scale daily numbers.
                orders = OrderMetrics(total = 18, completed = 14, cancelled = 3, open = 1),
                proposals = ProposalMetrics(total = 18, accepted = 14, declined = 1, lapsed = 0, withdrawn = 0, open = 3),
                drivers = DriverMetrics(total = 5, available = 9, withActivity = 5),
                currentDay = currentDaySnapshot,
                history = listOf(historyDay16, historyDay16.copy(date = "2026-08-15", activeDrivers = 3))
            )
        )

        val trendFinding = (outcome as AIProviderOutcome.Success).result.keyFindings.single { it.contains("Тренд по дням") }
        assertTrue(trendFinding.contains("2026-08-16"), "compares against the most recent day, not an older one")
        assertTrue(trendFinding.contains("было 12, стало 6"), "compares currentDay.orders.total (6) to the previous day's (12), never the ALL-PERIOD total (18)")
        assertFalse(trendFinding.contains("18"), "the ALL-PERIOD cumulative total must never appear in a daily trend comparison")
        assertTrue(trendFinding.contains("activeDrivers"))
        assertFalse(trendFinding.contains("на линии"), "must never compare historical activeDrivers to the live 'на линии сейчас' driver count")
    }

    @Test
    fun `history present but currentDay absent -- honestly reports the comparison is unavailable, never substitutes the cumulative snapshot`() {
        val outcome = provider.analyze(
            baseRequest(
                orders = OrderMetrics(total = 18, completed = 14, cancelled = 3, open = 1),
                proposals = ProposalMetrics(total = 18, accepted = 14, declined = 1, lapsed = 0, withdrawn = 0, open = 3),
                history = listOf(historyDay16)
            )
        )

        val findings = (outcome as AIProviderOutcome.Success).result.keyFindings
        assertTrue(findings.none { it.contains("Тренд по дням") }, "no fabricated trend finding without a real currentDay")
        val limitation = findings.single { it.contains("недоступно") }
        assertTrue(limitation.contains("сегодня"))
        assertFalse(limitation.contains("18"), "must not silently use the ALL-PERIOD total as if it were today's number")
    }

    @Test
    fun `the general driver-activity finding (live available count) is present and unaffected regardless of trend context`() {
        val outcome = provider.analyze(
            baseRequest(
                orders = OrderMetrics(total = 18, completed = 14, cancelled = 3, open = 1),
                proposals = ProposalMetrics(total = 18, accepted = 14, declined = 1, lapsed = 0, withdrawn = 0, open = 3),
                drivers = DriverMetrics(total = 5, available = 2, withActivity = 5),
                currentDay = currentDaySnapshot,
                history = listOf(historyDay16)
            )
        )

        val findings = (outcome as AIProviderOutcome.Success).result.keyFindings
        assertTrue(findings.any { it.contains("на линии сейчас: 2") }, "the live driver count is still reported, unchanged, as its own finding")
    }

    @Test
    fun `is deterministic -- analyzing the same input twice produces the identical result, never a random one`() {
        val request = baseRequest(
            orders = OrderMetrics(total = 6, completed = 4, cancelled = 1, open = 1),
            proposals = ProposalMetrics(total = 6, accepted = 4, declined = 1, lapsed = 1, withdrawn = 0, open = 0),
            drivers = DriverMetrics(total = 2, available = 1, withActivity = 2)
        )

        val first = provider.analyze(request)
        val second = provider.analyze(request)

        assertEquals(first, second)
    }
}

private val PilotAnalysisMetricsAllNull = Triple<Double?, Double?, Double?>(null, null, null)
