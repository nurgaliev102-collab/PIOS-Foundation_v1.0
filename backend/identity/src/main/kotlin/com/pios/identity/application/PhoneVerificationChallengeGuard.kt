package com.pios.identity.application

import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.PhoneVerificationPurpose

/**
 * ADR-082/ADR-080 precedent — a per-`(identity, purpose)` PostgreSQL row
 * lock, taken first, before [PhoneVerificationChallengeRepository.supersedeLive]
 * and [PhoneVerificationChallengeRepository.save]. Without it, two
 * concurrent first-ever requests for the same `(identity, purpose)` can
 * both find nothing to supersede and both attempt to insert a new live
 * row, violating `ux_phone_verification_challenges_live` — a real defect
 * this class exists to close structurally, the same reasoning `ADR-080`'s
 * `OrderGuard` already established for Dispatch's own order-scoped races.
 */
interface PhoneVerificationChallengeGuard {
    /** Must be called inside the caller's transaction, before any other write to `phone_verification_challenges`. */
    fun lock(identityId: IdentityId, purpose: PhoneVerificationPurpose)
}

object NoOpPhoneVerificationChallengeGuard : PhoneVerificationChallengeGuard {
    override fun lock(identityId: IdentityId, purpose: PhoneVerificationPurpose) = Unit
}
