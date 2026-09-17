package com.pios.identity.application

import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.PhoneVerificationChallenge
import com.pios.identity.domain.PhoneVerificationPurpose
import java.time.Instant

/**
 * The persistence boundary for [PhoneVerificationChallenge] (ADR-082,
 * D-03), following the same Domain-Owned Persistence discipline as
 * [IdentityRepository]/[CredentialRepository].
 */
interface PhoneVerificationChallengeRepository {
    /** Inserts a brand-new challenge row. Never used to update an existing one — see [update]. */
    fun save(challenge: PhoneVerificationChallenge)

    /**
     * Marks every currently-live challenge for `(identityId, purpose)` as
     * superseded at [at] — D-03.7 "duplicate/replay rejected": requesting a
     * new challenge invalidates any prior one rather than letting two be
     * simultaneously valid. Called inside the same transaction as [save]
     * for the new challenge.
     */
    fun supersedeLive(identityId: IdentityId, purpose: PhoneVerificationPurpose, at: Instant)

    /**
     * Locks and returns the one currently-live challenge for
     * `(identityId, purpose)`, if any — `SELECT ... FOR UPDATE` in the real
     * adapter (ADR-080's own per-row lock discipline, reused here so two
     * concurrent verify attempts against the same challenge can never both
     * succeed). Must be called inside the same transaction as the
     * subsequent [update]/compare, never outside one.
     */
    fun findLiveForUpdate(identityId: IdentityId, purpose: PhoneVerificationPurpose): PhoneVerificationChallenge?

    /** Persists a mutated challenge row (attempt increment, consumption). */
    fun update(challenge: PhoneVerificationChallenge)
}
