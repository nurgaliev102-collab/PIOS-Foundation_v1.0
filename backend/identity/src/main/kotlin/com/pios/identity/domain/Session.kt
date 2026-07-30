package com.pios.identity.domain

import java.time.Instant

/**
 * Documents the future shape of "an active, logged-in session for an
 * [Identity] on a [Device]" (ADR-038, Этап 3). Deliberately not decided
 * here whether a real implementation would persist sessions in a table
 * like this one, or issue stateless signed tokens instead (a JWT-style
 * credential needs no server-side row at all) — that choice belongs to
 * whichever future ADR actually implements login, not to this one, which
 * only records that the question exists. Unreferenced by any application
 * service; no database table backs it.
 */
data class Session(
    val id: SessionId,
    val identityId: IdentityId,
    val deviceId: DeviceId,
    val issuedAt: Instant,
    val expiresAt: Instant
)

@JvmInline
value class SessionId(val value: String) {
    init {
        require(value.isNotBlank()) { "SessionId must not be blank" }
    }
}
