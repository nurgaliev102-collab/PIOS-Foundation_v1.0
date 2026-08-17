package com.pios.aiadvisor.api

/**
 * `POST /v1/advisor/analyze`'s own response body (ADR-056 Decision 6) — an
 * explicit [outcome], never an implicit one. `result` is present only when
 * `outcome == "ok"`; `message` is present for every other outcome, and is
 * always the honest, non-technical text `AIAnalystCard.tsx` shows directly
 * (`docs/PRODUCT_DECISION_CONTROL_CENTER_AI.md` Section 12) — never a raw
 * provider status code or exception message.
 *
 * `outcome` values: `"ok"`, `"provider_unavailable"`, `"budget_exceeded"`,
 * `"invalid_input"`. This is a plain `String`, not a Kotlin enum, so the
 * JSON shape is stable and self-describing on the frontend side without a
 * generated type — mirrors how every other DTO in this codebase already
 * serializes its own status/enum fields as plain strings (e.g.
 * `AssignmentResponse.status`).
 */
data class PilotAnalysisResponse(
    val outcome: String,
    val result: PilotAnalysisResultDto?,
    val message: String?
)

data class PilotAnalysisResultDto(
    val status: String,
    val summary: String,
    val keyFindings: List<String>,
    val risks: List<String>,
    val recommendations: List<String>,
    val metrics: PilotAnalysisMetricsDto,
    val generatedAt: String,
    val providerName: String
)

data class PilotAnalysisMetricsDto(
    val acceptanceRate: Double?,
    val completionRate: Double?,
    val cancellationRate: Double?
)
