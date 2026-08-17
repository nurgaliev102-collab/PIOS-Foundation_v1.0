package com.pios.aiadvisor.api

import com.pios.aiadvisor.domain.AIProvider
import com.pios.aiadvisor.domain.AIProviderOutcome
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * `POST /v1/advisor/analyze` (ADR-056 Decision 5–6). Read → Analyze →
 * Report only: this controller has no other endpoint, calls no domain
 * module (Decision 1), and its only outbound call is
 * [aiProvider]'s own [AIProvider.analyze] — never a write of any kind to
 * anything.
 *
 * Gated by [ownerCredentialGate] exactly as every domain module's own
 * `GET /v1/health` is (ADR-044 Decision 3, replicated per ADR-056 Decision
 * 4) — a missing or incorrect credential produces a plain `401`, checked
 * before [budgetGuard] and before the request body is otherwise
 * interpreted, so an unauthenticated caller never consumes any budget.
 */
@RestController
@RequestMapping("/v1/advisor")
class AdvisorController(
    private val ownerCredentialGate: OwnerCredentialGate,
    private val budgetGuard: BudgetGuard,
    private val aiProvider: AIProvider
) {
    private val logger = LoggerFactory.getLogger(AdvisorController::class.java)

    @PostMapping("/analyze")
    fun analyze(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: PilotAnalysisRequest
    ): ResponseEntity<PilotAnalysisResponse> {
        if (!ownerCredentialGate.verify(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        // Observability (this task's own explicit requirement): request
        // metadata and outcome only -- never the Authorization header's own
        // value, never any field of `request` beyond aggregate counts it
        // already carries no PII in (ADR-056 Decision 5).
        logger.info("advisor.request.accepted provider={}", aiProvider.name)

        when (val guard = budgetGuard.check()) {
            is BudgetGuard.GuardResult.RateLimited -> {
                logger.info("advisor.request.rejected reason=rate_limited retryAfterMillis={}", guard.retryAfterMillis)
                return ResponseEntity.ok(
                    PilotAnalysisResponse(
                        outcome = "budget_exceeded",
                        result = null,
                        message = "Слишком частые запросы. Попробуйте через несколько секунд."
                    )
                )
            }
            BudgetGuard.GuardResult.DailyBudgetExceeded -> {
                logger.info("advisor.request.rejected reason=daily_budget_exceeded")
                return ResponseEntity.ok(
                    PilotAnalysisResponse(
                        outcome = "budget_exceeded",
                        result = null,
                        message = "Достигнут дневной лимит запросов к AI-анализу. Попробуйте завтра."
                    )
                )
            }
            BudgetGuard.GuardResult.MonthlyBudgetExceeded -> {
                logger.info("advisor.request.rejected reason=monthly_budget_exceeded")
                return ResponseEntity.ok(
                    PilotAnalysisResponse(
                        outcome = "budget_exceeded",
                        result = null,
                        message = "Достигнут месячный лимит запросов к AI-анализу."
                    )
                )
            }
            BudgetGuard.GuardResult.Allowed -> Unit
        }

        val startedAt = System.nanoTime()
        logger.info("advisor.analysis.started")
        val outcome = aiProvider.analyze(request)
        val durationMs = (System.nanoTime() - startedAt) / 1_000_000

        return when (outcome) {
            is AIProviderOutcome.Success -> {
                budgetGuard.recordSuccess()
                logger.info(
                    "advisor.analysis.completed provider={} durationMs={} status={}",
                    aiProvider.name,
                    durationMs,
                    outcome.result.status
                )
                ResponseEntity.ok(PilotAnalysisResponse(outcome = "ok", result = outcome.result, message = null))
            }
            is AIProviderOutcome.Failure -> {
                logger.warn(
                    "advisor.analysis.failed provider={} durationMs={} reason={} providerStatusCode={}",
                    aiProvider.name,
                    durationMs,
                    outcome.reason,
                    outcome.providerStatusCode
                )
                ResponseEntity.ok(
                    PilotAnalysisResponse(
                        outcome = "provider_unavailable",
                        result = null,
                        message = "AI-помощник сейчас недоступен. Попробуйте позже."
                    )
                )
            }
        }
    }
}
