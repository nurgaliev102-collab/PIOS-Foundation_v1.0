package com.pios.identity.application

import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.PhoneVerificationPurpose
import org.springframework.stereotype.Service

/**
 * ADR-082 Part 2/§5 (D-03.2) — application-layer coordination for
 * `POST /v1/identities/me/phone/verify/request`, the legacy-enrolment
 * request step. Unlike [RequestRecoveryApplicationService], this endpoint
 * is Bearer-gated (the caller already proved who they are), so its
 * failures may be distinguishable — there is no enumeration concern
 * against a target the caller does not already know they are.
 *
 * This path never accepts a new phone number — it only proves the one
 * already on [com.pios.identity.domain.Identity.phone], the D-03.2
 * "existing authenticated session + SMS verification of the existing
 * phone" contract, verbatim.
 *
 * D-03.7 closure pass: also rate-limited by phone — the same
 * [PhoneOtpRequestRateLimiter] [RequestRecoveryApplicationService] uses
 * for the other OTP purpose, never a second, purpose-specific limiter.
 * Keyed by the identity's own on-file phone (the only phone this flow
 * ever concerns), resolved after the identity lookup this method already
 * performs. Unlike recovery, a caller here is already authenticated, so
 * exhaustion may be — and is — reported distinguishably
 * ([TooManyOtpRequestsException], mapped to 429): there is no enumeration
 * concern against an identity the caller has already proven they own.
 */
@Service
class RequestPhoneVerificationApplicationService(
    private val identityRepository: IdentityRepository,
    private val challengeIssuer: PhoneVerificationChallengeIssuer,
    private val phoneOtpRequestRateLimiter: PhoneOtpRequestRateLimiter
) {
    fun handle(identityId: IdentityId) {
        val identity = identityRepository.findById(identityId) ?: throw IdentityNotFoundException(identityId)
        val phone = identity.phone ?: error("a guest identity has no phone to verify -- caller must reject guest tokens before reaching this service")
        if (!phoneOtpRequestRateLimiter.tryAcquire(phone.value)) {
            throw TooManyOtpRequestsException()
        }
        if (identity.phoneVerifiedAt != null) {
            throw PhoneAlreadyVerifiedException(identityId)
        }
        challengeIssuer.issue(identity.id, phone, PhoneVerificationPurpose.LEGACY_ENROLLMENT)
    }
}
