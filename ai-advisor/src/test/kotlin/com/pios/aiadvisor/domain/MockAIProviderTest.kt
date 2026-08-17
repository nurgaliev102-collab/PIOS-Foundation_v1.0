package com.pios.aiadvisor.domain

import com.pios.aiadvisor.api.AssignmentMetrics
import com.pios.aiadvisor.api.DriverMetrics
import com.pios.aiadvisor.api.HealthMetrics
import com.pios.aiadvisor.api.OrderMetrics
import com.pios.aiadvisor.api.PilotAnalysisRequest
import com.pios.aiadvisor.api.ProposalMetrics
import com.pios.aiadvisor.api.ReactionTimeMetrics
import kotlin.test.Test
import kotlin.test.assertEquals
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
        health: HealthMetrics = HealthMetrics(5, 5)
    ) = PilotAnalysisRequest(
        generatedAt = "2026-08-17T12:00:00.000Z",
        periodLabel = "Весь период наблюдения (все данные, доступные системе сейчас)",
        orders = orders,
        proposals = proposals,
        assignments = assignments,
        drivers = drivers,
        reactionTime = reactionTime,
        health = health
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
