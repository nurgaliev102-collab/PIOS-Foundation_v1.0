package com.pios.aiadvisor.domain

import com.pios.aiadvisor.api.OrderMetrics
import com.pios.aiadvisor.api.PilotAnalysisMetricsDto
import com.pios.aiadvisor.api.PilotAnalysisRequest
import com.pios.aiadvisor.api.PilotAnalysisResultDto
import com.pios.aiadvisor.api.ProposalMetrics
import org.springframework.stereotype.Component
import kotlin.math.roundToInt

/**
 * Server-side rule-based analysis (ADR-056 Rollout Step 2) — the exact same
 * deterministic rules `frontend/src/pages/OwnerControlCenter/aiProvider.ts`'s
 * own `MockAIProvider` already established (commit `af5f9dd`), moved here
 * so the transport changes (frontend now calls `POST /v1/advisor/analyze`)
 * without the analysis logic itself changing or living in two places at
 * once. Never a real language model, and never pretends to be one: every
 * sentence is a template filled with a real number from [PilotAnalysisRequest];
 * a metric this provider cannot compute is reported as "нет данных", never
 * invented ([calculateAcceptanceRate]/[calculateCompletionRate]/
 * [calculateCancellationRate] all return `null` rather than guess).
 *
 * Thresholds below are this MVP's own disclosed heuristic, not a ratified
 * business rule — the same disclosure the frontend original already carried.
 */
@Component
class MockAIProvider : AIProvider {
    override val name = "mock"

    override fun analyze(input: PilotAnalysisRequest): AIProviderOutcome {
        val acceptanceRate = calculateAcceptanceRate(input.proposals)
        val completionRate = calculateCompletionRate(input.orders)
        val cancellationRate = calculateCancellationRate(input.orders)
        val metrics = PilotAnalysisMetricsDto(acceptanceRate, completionRate, cancellationRate)

        if (input.orders.total == 0) {
            return AIProviderOutcome.Success(
                PilotAnalysisResultDto(
                    status = "unknown",
                    summary = "Недостаточно данных для анализа — за наблюдаемый период не зафиксировано ни одного заказа.",
                    keyFindings = emptyList(),
                    risks = emptyList(),
                    recommendations = listOf("Дождитесь первых заказов в системе и запустите анализ ещё раз."),
                    metrics = metrics,
                    generatedAt = input.generatedAt,
                    providerName = name
                )
            )
        }

        val keyFindings = mutableListOf(
            "Заказов всего: ${input.orders.total}, завершено: ${input.orders.completed} " +
                "(completion rate ${formatPercent(completionRate)}).",
            "Решённых предложений: ${input.proposals.accepted + input.proposals.declined + input.proposals.lapsed} " +
                "(принято ${input.proposals.accepted}, отклонено ${input.proposals.declined}, просрочено ${input.proposals.lapsed}); " +
                "acceptance rate ${formatPercent(acceptanceRate)}.",
            "Водителей всего: ${input.drivers.total}, на линии сейчас: ${input.drivers.available}, " +
                "получили хотя бы одно предложение: ${input.drivers.withActivity}."
        )
        if (input.reactionTime.sampleSize > 0) {
            keyFindings.add(
                "Среднее время реакции водителя на предложение: ${formatMinutes(input.reactionTime.averageMinutes)} " +
                    "(медиана: ${formatMinutes(input.reactionTime.medianMinutes)}, на основе ${input.reactionTime.sampleSize} предложений)."
            )
        }

        val risks = mutableListOf<String>()
        if (input.proposals.lapsed > 0) {
            risks.add("Просроченных (lapsed) предложений: ${input.proposals.lapsed} — водители не ответили вовремя.")
        }
        if (input.proposals.declined > 0) {
            risks.add("Отклонённых предложений: ${input.proposals.declined}.")
        }
        if (cancellationRate != null && cancellationRate > 0) {
            risks.add(
                "Доля отменённых заказов: ${formatPercent(cancellationRate)} (${input.orders.cancelled} из ${input.orders.total})."
            )
        }
        if (input.health.modulesTotal > 0 && input.health.modulesUp < input.health.modulesTotal) {
            risks.add("Не все модули PIOS отвечают: ${input.health.modulesUp} из ${input.health.modulesTotal}.")
        }
        if (input.drivers.total > 0 && input.drivers.withActivity == 0) {
            risks.add("Ни один водитель ещё не получил ни одного предложения.")
        }

        val recommendations = mutableListOf<String>()
        if (input.proposals.lapsed > 0) {
            recommendations.add("Проверьте, все ли подключённые водители реально находятся на линии и получают уведомления.")
        }
        if (input.drivers.total > 0 && input.drivers.withActivity == 0) {
            recommendations.add("Убедитесь, что водители делятся своими персональными ссылками с клиентами.")
        }

        val isCritical = (input.health.modulesTotal > 0 && input.health.modulesUp < input.health.modulesTotal) ||
            (cancellationRate != null && cancellationRate >= CRITICAL_CANCELLATION_RATE)
        val isAttention = input.proposals.lapsed > 0 ||
            input.proposals.declined > 0 ||
            (cancellationRate != null && cancellationRate >= ATTENTION_CANCELLATION_RATE) ||
            (input.drivers.total > 0 && input.drivers.withActivity == 0)

        val status = if (isCritical) "critical" else if (isAttention) "attention" else "ok"

        if (recommendations.isEmpty()) {
            recommendations.add(
                if (status == "ok") "Явных проблем не обнаружено — продолжайте наблюдение." else "Проверьте отмеченные риски вручную."
            )
        }

        val summary = when (status) {
            "critical" -> "Пилот требует немедленного внимания владельца."
            "attention" -> "Пилот работает, но есть моменты, на которые стоит обратить внимание."
            else -> "Пилот работает штатно, явных проблем не обнаружено."
        }

        return AIProviderOutcome.Success(
            PilotAnalysisResultDto(
                status = status,
                summary = summary,
                keyFindings = keyFindings,
                risks = risks,
                recommendations = recommendations,
                metrics = metrics,
                generatedAt = input.generatedAt,
                providerName = name
            )
        )
    }

    companion object {
        /** A cancellation rate at or above this is graded `critical` on its own. */
        const val CRITICAL_CANCELLATION_RATE = 0.5
        /** A cancellation rate at or above this (but below critical) contributes to `attention`. */
        const val ATTENTION_CANCELLATION_RATE = 0.2
    }
}

/** Computed only over *resolved* proposals (accepted/declined/lapsed) — a still-`OPEN` proposal has not decided anything yet. `null` when nothing has resolved. */
fun calculateAcceptanceRate(proposals: ProposalMetrics): Double? {
    val resolved = proposals.accepted + proposals.declined + proposals.lapsed
    return if (resolved == 0) null else proposals.accepted.toDouble() / resolved
}

/** `null` when there are no orders at all — not `0`, which would falsely read as "zero completion." */
fun calculateCompletionRate(orders: OrderMetrics): Double? =
    if (orders.total == 0) null else orders.completed.toDouble() / orders.total

/** Same `null`-when-empty rule as [calculateCompletionRate]. */
fun calculateCancellationRate(orders: OrderMetrics): Double? =
    if (orders.total == 0) null else orders.cancelled.toDouble() / orders.total

private fun formatPercent(rate: Double?): String = if (rate == null) "нет данных" else "${(rate * 100).roundToInt()}%"

private fun formatMinutes(value: Double?): String = if (value == null) "—" else "${value.roundToInt()} мин"
