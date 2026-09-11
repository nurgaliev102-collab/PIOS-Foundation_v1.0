package com.pios.core.api

/**
 * `GET /v1/health/core`'s own self-report and nothing else (ADR-043
 * Decision 2 shape; ADR-067 Failure Isolation). No `outbox` field —
 * unlike dispatch / order-management / passenger-experience, Core has no
 * outbox (consumer-only, ADR-067 Event Boundary).
 */
data class HealthResponse(
    val module: String,
    val status: String,
    val database: String,
    val checkedAt: String
)
