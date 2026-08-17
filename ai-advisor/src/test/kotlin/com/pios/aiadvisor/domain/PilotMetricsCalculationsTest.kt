package com.pios.aiadvisor.domain

import com.pios.aiadvisor.api.OrderMetrics
import com.pios.aiadvisor.api.ProposalMetrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Server-side port of the same rate calculations already proven in `frontend/.../pilotAnalytics.test.ts`. */
class PilotMetricsCalculationsTest {

    @Test
    fun `acceptance rate divides accepted by resolved -- accepted + declined + lapsed -- excluding still-open proposals`() {
        val metrics = ProposalMetrics(total = 10, accepted = 3, declined = 2, lapsed = 1, withdrawn = 0, open = 4)
        assertEquals(0.5, calculateAcceptanceRate(metrics))
    }

    @Test
    fun `acceptance rate is null when nothing has resolved yet`() {
        val metrics = ProposalMetrics(total = 5, accepted = 0, declined = 0, lapsed = 0, withdrawn = 0, open = 5)
        assertNull(calculateAcceptanceRate(metrics))
    }

    @Test
    fun `completion rate divides completed by total orders`() {
        val metrics = OrderMetrics(total = 4, completed = 3, cancelled = 1, open = 0)
        assertEquals(0.75, calculateCompletionRate(metrics))
    }

    @Test
    fun `completion rate is null for zero orders, never 0`() {
        val metrics = OrderMetrics(total = 0, completed = 0, cancelled = 0, open = 0)
        assertNull(calculateCompletionRate(metrics))
    }

    @Test
    fun `cancellation rate divides cancelled by total orders`() {
        val metrics = OrderMetrics(total = 10, completed = 5, cancelled = 2, open = 3)
        assertEquals(0.2, calculateCancellationRate(metrics))
    }

    @Test
    fun `cancellation rate is null for zero orders`() {
        val metrics = OrderMetrics(total = 0, completed = 0, cancelled = 0, open = 0)
        assertNull(calculateCancellationRate(metrics))
    }
}
