package com.pios.identity.application

import com.pios.identity.domain.Identity
import java.time.Instant

/**
 * The result of [RegisterIdentityApplicationService.handle] (ADR-055
 * Decision 3) — the newly created [identity] plus the session [token]
 * issued immediately, so a freshly registered caller does not need a
 * separate login round-trip.
 */
data class RegisterIdentityOutcome(val identity: Identity, val token: String, val expiresAt: Instant)
