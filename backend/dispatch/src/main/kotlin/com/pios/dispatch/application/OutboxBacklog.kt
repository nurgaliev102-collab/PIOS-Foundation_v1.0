package com.pios.dispatch.application

import java.time.Instant

/**
 * A cheap, aggregate-only measurement of this module's own unpublished
 * outbox backlog (ADR-043 Decision 2; `GET /v1/health`) — [pending] is the
 * count of rows with `published_at IS NULL`, [oldestPendingCreatedAt] the
 * `created_at` of the oldest one, or `null` when [pending] is zero.
 * Deliberately carries no payload and no row identity: the one thing this
 * type exists to avoid is loading what [OutboxRepository.findUnpublished]
 * loads.
 */
data class OutboxBacklog(
    val pending: Long,
    val oldestPendingCreatedAt: Instant?
)
