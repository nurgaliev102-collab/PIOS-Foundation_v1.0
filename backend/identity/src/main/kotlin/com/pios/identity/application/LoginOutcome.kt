package com.pios.identity.application

import com.pios.identity.domain.Identity
import java.time.Instant

/**
 * The result of [LoginApplicationService.handle] (ADR-055 Decision 3).
 * [Failure] carries no reason — an unknown phone, a malformed phone, a
 * phone with no credential yet, and a correct phone with the wrong
 * password are all the exact same [Failure], by design (see
 * [LoginApplicationService]'s own KDoc).
 */
sealed interface LoginOutcome {
    data class Success(val identity: Identity, val token: String, val expiresAt: Instant) : LoginOutcome
    data object Failure : LoginOutcome
}
