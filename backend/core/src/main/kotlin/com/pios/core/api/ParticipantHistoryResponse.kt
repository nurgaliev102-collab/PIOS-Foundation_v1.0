package com.pios.core.api

/**
 * The read shape of `GET /v1/core/participants/{participantReference}/history`
 * (ADR-067 Boundary: "exposes one read endpoint"). Reflects only Core's
 * own `pios_core` projection — no field is a copy of a Taxi aggregate
 * (ADR-067 Data Model Scope): `orderReference` / `driverReference` are the
 * same bare correlation strings the history row stores, nothing more.
 */
data class ParticipantHistoryResponse(
    val participantReference: String,
    val events: List<ParticipantHistoryEventResponse>
)

data class ParticipantHistoryEventResponse(
    /** A value from Core's own closed `HistoryEventKind` vocabulary. */
    val kind: String,
    /** ISO-8601, taken verbatim from the source event envelope's `occurredAt`. */
    val occurredAt: String,
    val orderReference: String?,
    val driverReference: String?
)
