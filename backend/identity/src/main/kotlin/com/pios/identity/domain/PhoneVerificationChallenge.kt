package com.pios.identity.domain

import java.time.Instant

/**
 * ADR-082 (D-03) — what [VerificationChallenge] was always meant to become
 * once a real verification mechanism existed, but shaped by the actual
 * security requirements D-03.7 ratified rather than by that earlier,
 * unpersisted sketch: a durable, hashed, single-use, expiring, attempt-bounded
 * OTP challenge. Deliberately a new type rather than retrofitting
 * [VerificationChallenge] itself — that class's own shape (bare `method`
 * discriminator, no hash, no attempt count, no supersession) does not carry
 * what this ADR requires, and `ADR-038` never authorized more than the
 * shape it already has. [VerificationChallenge]/[VerificationMethod] remain
 * exactly as unpersisted as `ADR-038` left them; this ADR does not touch them.
 *
 * Shared by both [PhoneVerificationPurpose] values — recovery and legacy
 * enrolment get identical security properties from one mechanism, not two.
 * A plain data holder, not an aggregate: [PostgreSQLPhoneVerificationChallengeRepository]
 * owns the read-modify-write of [attemptCount]/[consumedAt]/[supersededAt]
 * entirely, the same "plain data holder" convention
 * [com.pios.drivermanagement.domain.DriverMilestones]'s own KDoc already
 * established for this codebase.
 */
data class PhoneVerificationChallenge(
    val id: String,
    val identityId: IdentityId,
    val phone: Phone,
    val purpose: PhoneVerificationPurpose,
    val codeHash: String,
    val codeSalt: String,
    val iterations: Int,
    val attemptCount: Int,
    val maxAttempts: Int,
    val createdAt: Instant,
    val expiresAt: Instant,
    val consumedAt: Instant?,
    val supersededAt: Instant?
) {
    init {
        require(id.isNotBlank()) { "PhoneVerificationChallenge id must not be blank" }
        require(attemptCount >= 0) { "attemptCount must not be negative" }
        require(maxAttempts > 0) { "maxAttempts must be positive" }
    }

    /** Neither consumed nor superseded — the one row a verify attempt may act on. */
    val isLive: Boolean get() = consumedAt == null && supersededAt == null

    fun isExpired(now: Instant): Boolean = !now.isBefore(expiresAt)

    val attemptsExhausted: Boolean get() = attemptCount >= maxAttempts
}

/**
 * D-03.5: only [RECOVERY] is implemented this ADR. [LEGACY_ENROLLMENT] is
 * the D-03.2 enrolment act — proving an already-on-file phone from an
 * already-authenticated session, never a recovery event itself.
 */
enum class PhoneVerificationPurpose {
    RECOVERY,
    LEGACY_ENROLLMENT
}
