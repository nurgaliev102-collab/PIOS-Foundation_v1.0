package com.pios.passengerexperience.application

import java.time.Instant

/**
 * A cheap, aggregate-only measurement of this module's own unpublished
 * outbox backlog — [pending] is the count of rows with `published_at IS
 * NULL`, [oldestPendingCreatedAt] the `created_at` of the oldest one, or
 * `null` when [pending] is zero. Mirrors Dispatch's own
 * `OutboxBacklog` exactly. Deliberately carries no payload and no row
 * identity: the one thing this type exists to avoid is loading what
 * [OutboxRepository.findUnpublished] loads. Not wired into any health
 * endpoint by this task (out of Task 14's own scope) — present only for
 * interface parity with the established convention, and available for a
 * future task to use exactly as Dispatch's `GET /v1/health` already does.
 */
data class OutboxBacklog(
    val pending: Long,
    val oldestPendingCreatedAt: Instant?
)
