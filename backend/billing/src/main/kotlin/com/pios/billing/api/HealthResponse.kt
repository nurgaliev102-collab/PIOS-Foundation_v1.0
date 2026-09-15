package com.pios.billing.api

/**
 * The shape ADR-043 Decision 2 and `PIOS_OWNER_CONTROL_CENTER_MVP_DESIGN.md`
 * Section 7.2 fix for `GET /v1/health` — this module's own self-report and
 * nothing else. Carries no `outbox` field at all, mirroring `identity`'s
 * and `passenger-experience`'s own `HealthResponse`: Billing owns no
 * outbox table (ADR-074's own "no RabbitMQ topology, no outbox" constraint).
 */
data class HealthResponse(
    val module: String,
    val status: String,
    val database: String,
    val checkedAt: String
)
