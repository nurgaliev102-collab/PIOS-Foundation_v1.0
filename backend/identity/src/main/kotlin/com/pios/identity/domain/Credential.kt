package com.pios.identity.domain

import java.time.Instant

/**
 * Documents the shape of "a way to prove control of an [Identity]"
 * (ADR-038, Этап 3). Originally shipped with **no concrete subtype** —
 * that gap is what ADR-055 (Session Authentication and Password
 * Credential) closes, adding [PasswordCredential] as this interface's
 * first real implementation. The historical reasoning for the extension
 * point (a passkey credential, an SMS-verified session credential, a
 * Telegram-verified credential — see
 * `docs/PIOS_IDENTITY_ARCHITECTURE_RESEARCH.md` Section 2 for the
 * candidates this maps to) is preserved rather than rewritten: this
 * interface still leaves room for further subtypes should a later ADR
 * authorize one; ADR-055 selects a starting mechanism, not the only one
 * this interface will ever hold.
 */
sealed interface Credential

/**
 * A phone-and-password credential (ADR-055, Decision 2) — the evidence
 * that proves control of an [Identity], as opposed to [Phone]'s own bare,
 * unverified claim of one. Hashed with
 * [com.pios.identity.application.PasswordHasher]'s own
 * PBKDF2-HMAC-SHA256 parameters, [passwordSalt] generated fresh per
 * credential (never reused across identities, unlike
 * [com.pios.identity.api.OwnerCredentialGate]'s single per-deployment
 * salt). [iterations] is carried on the row itself, not assumed fixed, so
 * a future deployment can raise its default without invalidating
 * passwords already hashed under a lower count. Persisted in
 * `identity_credentials` (`V3__create_identity_credentials.sql`); `phone`
 * itself is not duplicated here — it remains the existing `identities.phone`
 * column, now uniquely indexed, so there is one source of truth.
 */
data class PasswordCredential(
    val identityId: IdentityId,
    val passwordHash: String,
    val passwordSalt: String,
    val iterations: Int,
    val createdAt: Instant
) : Credential
