package com.pios.identity.domain

import java.time.Instant

/**
 * Documents the future shape of "an in-progress attempt to verify a
 * [Phone]" (ADR-038, Этап 3) — an OTP challenge, a Silent Network
 * Authentication request, or a Telegram verification round-trip would each
 * eventually produce one of these. [method] exists as a discriminator from
 * day one specifically because `docs/PIOS_IDENTITY_ARCHITECTURE_RESEARCH.md`
 * recommends more than one verification path depending on market — this is
 * one of the two points ADR-038's self-critique revises relative to that
 * report's original framing.
 *
 * No code sends an SMS, contacts a carrier, or calls Telegram anywhere in
 * this module. Nothing constructs this type outside of tests that exist
 * solely to document its shape. No database table backs it.
 */
data class VerificationChallenge(
    val id: VerificationChallengeId,
    val identityId: IdentityId,
    val phone: Phone,
    val method: VerificationMethod,
    val createdAt: Instant,
    val expiresAt: Instant
)

@JvmInline
value class VerificationChallengeId(val value: String) {
    init {
        require(value.isNotBlank()) { "VerificationChallengeId must not be blank" }
    }
}

/**
 * Candidate verification paths named in
 * `docs/PIOS_IDENTITY_ARCHITECTURE_RESEARCH.md` Section 2 — listed as a
 * closed set here only so [VerificationChallenge] has a concrete
 * discriminator type; none is implemented, and this list is not itself a
 * commitment to build all three, only a record of what was considered.
 */
enum class VerificationMethod {
    SMS_OTP,
    SILENT_NETWORK_AUTH,
    TELEGRAM
}
