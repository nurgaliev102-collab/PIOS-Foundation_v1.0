package com.pios.core.domain

import java.time.Instant

/**
 * One immutable fact in a participant's history (ADR-067 Data Model Scope
 * item 2) — the logical shape of a `participant_history_events` row before
 * it is persisted.
 *
 * - [participant] — the participant this fact concerns.
 * - [kind] — a value from Core's own closed [HistoryEventKind] vocabulary.
 * - [occurredAt] — the business time, taken verbatim from the source
 *   event envelope's `occurredAt`. Never `Instant.now()` at record time.
 * - [orderReference] / [driverReference] — bare correlation references
 *   (opaque strings), present only when the source event carried them.
 *   Never a copy of the referenced Order / Driver aggregate. `null` when
 *   the source event did not carry the value — Core does not fetch it
 *   (ADR-067 Write Boundary).
 * - [sourceEventId] — the envelope `eventId` this fact was derived from,
 *   for the audit trail. Not the idempotency key (that is the
 *   `processed_events` ledger).
 *
 * A read-model record only: Core is the authority for nothing (ADR-067
 * Boundary), and this projection is disposable and rebuildable from the
 * event stream (Consequences).
 */
data class ParticipantHistoryEvent(
    val participant: ParticipantReference,
    val kind: HistoryEventKind,
    val occurredAt: Instant,
    val orderReference: String? = null,
    val driverReference: String? = null,
    val sourceEventId: String
)
