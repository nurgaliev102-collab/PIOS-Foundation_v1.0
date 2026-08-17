package com.pios.aiadvisor.domain

import com.pios.aiadvisor.api.PilotAnalysisRequest
import com.pios.aiadvisor.api.PilotAnalysisResultDto

/**
 * ADR-056 Decision 7: the provider abstraction. `AdvisorController` (and
 * every future caller) depends on this interface only — never on
 * `MockAIProvider`, and never, once built, on `DeepSeekProvider` or
 * `ClaudeProvider`'s own wire shape. Swapping providers is implementing
 * this interface plus a Spring configuration change; no caller-side code
 * changes.
 *
 * "Never throws, returns outcomes as values" — the same pattern
 * `healthPoll.ts`'s `fetchModuleHealth` already established on the frontend
 * (ADR-056 Decision 6), applied here on the backend side.
 */
interface AIProvider {
    val name: String
    fun analyze(input: PilotAnalysisRequest): AIProviderOutcome
}

sealed interface AIProviderOutcome {
    data class Success(val result: PilotAnalysisResultDto) : AIProviderOutcome
    data class Failure(val reason: FailureReason, val providerStatusCode: Int?) : AIProviderOutcome
}

/** Every real failure mode a future `DeepSeekProvider`/`ClaudeProvider` can hit (ADR-056 Decision 10). [MockAIProvider] never produces one. */
enum class FailureReason { UNAVAILABLE, RATE_LIMITED, QUOTA_EXCEEDED, AUTH_FAILED, TIMEOUT, MALFORMED_RESPONSE }
