package com.pios.identity.application

import com.pios.identity.domain.Identity
import java.time.Instant

/**
 * The result of [ConfirmRecoveryApplicationService.handle] — mirrors
 * [LoginOutcome]'s own uniform-failure shape exactly (ADR-055 Decision 3's
 * "never distinguish" discipline, reused here): [Failure] covers unknown
 * phone, no live challenge, expired challenge, exhausted attempts, and a
 * wrong code alike, so a caller cannot learn which one occurred.
 */
sealed interface ConfirmRecoveryOutcome {
    data class Success(val identity: Identity, val token: String, val expiresAt: Instant) : ConfirmRecoveryOutcome
    object Failure : ConfirmRecoveryOutcome
}
