package com.pios.drivermanagement.api

/**
 * The shape ADR-043 Decision 2 and `PIOS_OWNER_CONTROL_CENTER_MVP_DESIGN.md`
 * Section 7.2 fix for `GET /v1/health` — this module's own self-report and
 * nothing else. [outbox] is present here because `driver-management` is one
 * of the three pilot modules with an outbox; `passenger-experience` and
 * `identity` never populate it, since neither owns one.
 */
data class HealthResponse(
    val module: String,
    val status: String,
    val database: String,
    val outbox: OutboxHealthResponse? = null,
    val checkedAt: String
)

/**
 * [pending] is this module's own unpublished outbox row count;
 * [oldestPendingAgeSeconds] is how long the oldest of those rows has been
 * waiting, or `null` when [pending] is zero — both obtained from
 * [com.pios.drivermanagement.application.OutboxRepository.countUnpublished],
 * never from [com.pios.drivermanagement.application.OutboxRepository.findUnpublished]
 * (ADR-043 Decision 2).
 */
data class OutboxHealthResponse(
    val pending: Long,
    val oldestPendingAgeSeconds: Long?
)
