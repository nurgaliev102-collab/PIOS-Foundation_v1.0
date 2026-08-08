package com.pios.passengerexperience.api

/**
 * [connectionId] (ADR-054, Circle of Trust) was added so a caller can
 * immediately act on the just-created (or idempotently-returned)
 * connection -- for example, designating a passenger's very first
 * connection as their primary via `POST /v1/connections/{connectionId}/primary`
 * right after this call succeeds. Purely additive: existing callers that
 * only read [driverId]/[passengerReference] are unaffected, and
 * `POST /v1/connections`'s request shape and 201/200 idempotency
 * semantics are unchanged (ADR-054 Part 4 freezes only those, not this
 * response).
 */
data class CreateConnectionResponse(val driverId: String, val passengerReference: String, val connectionId: String)
