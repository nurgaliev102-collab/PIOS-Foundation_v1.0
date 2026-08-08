package com.pios.identity.domain

import java.time.Instant

/**
 * Documents the future shape of "an active, logged-in session for an
 * [Identity] on a [Device]" (ADR-038, Этап 3). Originally left the choice
 * of persisted-table-vs-stateless-token open for whichever future ADR
 * implemented login. ADR-055 (Session Authentication and Password
 * Credential) is that ADR, and answers: stateless signed tokens, no
 * table — see `com.pios.identity.application.SessionTokenIssuer` and
 * `com.pios.identity.api.SessionTokenVerifier`. This class itself remains
 * unreferenced by any application service and no database table backs
 * it; the resolved answer is implemented elsewhere, not by wiring this
 * type to a real mechanism.
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
