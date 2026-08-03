package com.pios.identity.api

/**
 * The shape ADR-043 Decision 2 and `PIOS_OWNER_CONTROL_CENTER_MVP_DESIGN.md`
 * Section 7.2 fix for `GET /v1/health` — this module's own self-report and
 * nothing else. Carries no `outbox` field at all, unlike `driver-management`,
 * `order-management`, and `dispatch`'s own `HealthResponse`: `identity`
 * owns no outbox table (ADR-043 Decision 2 names it, alongside
 * `passenger-experience`, as one of the two pilot modules without one).
 */
data class HealthResponse(
    val module: String,
    val status: String,
    val database: String,
    val checkedAt: String
)
