package com.pios.core.application

import com.pios.core.domain.ParticipantHistoryEvent
import com.pios.core.domain.ParticipantReference

/**
 * The persistence boundary for Core's Slice 01 read-model (ADR-067 Data
 * Model Scope). No storage technology is named here.
 *
 * Every write goes through [ParticipantHistoryProjectionApplicationService];
 * every read through [ParticipantHistoryQueryService]. No other module
 * implements or depends on this boundary, and Core writes to no database
 * but its own `pios_core` (ADR-067 Write Boundary).
 */
interface ParticipantHistoryRepository {

    /**
     * Ensures a `participants` row exists for [reference], recording its
     * first-seen time on creation. Idempotent: a no-op if the row already
     * exists. Never stores any personal data — only the reference and a
     * timestamp (ADR-067 Data Model Scope item 1).
     */
    fun ensureParticipant(reference: ParticipantReference)

    /**
     * Appends one immutable [event] to the participant's history. Callers
     * invoke [ensureParticipant] for [ParticipantHistoryEvent.participant]
     * first, in the same transaction.
     */
    fun record(event: ParticipantHistoryEvent)

    /**
     * Correlation lookup for `OrderCompleted` / `OrderCancelled`, whose
     * payload carries only `orderId`: returns the participant a prior
     * `ORDER_SUBMITTED` history row attributed [orderReference] to, or
     * `null` if no such row exists.
     *
     * A `null` result means Core never saw the matching `OrderSubmitted`
     * (deployed after the submit, or the submit was lost). The caller
     * then records the event as processed and writes **no** history row —
     * it does **not** ask order-management (ADR-067 Write Boundary /
     * Input Events "Rule").
     */
    fun findParticipantByOrderReference(orderReference: String): ParticipantReference?

    /**
     * The ordered history of [reference] — every recorded fact, oldest
     * first. Returns an empty list for an unknown participant.
     */
    fun history(reference: ParticipantReference): List<ParticipantHistoryEvent>
}
