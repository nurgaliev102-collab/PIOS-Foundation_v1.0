package com.pios.identity.application

import com.pios.identity.domain.Phone
import com.pios.identity.domain.PhoneVerificationPurpose
import org.springframework.stereotype.Service

/**
 * ADR-082 Part 5/§5 (D-03.1, D-03.2, D-03.6, D-03.7) — application-layer
 * coordination for `POST /v1/identities/recovery/request`. Always returns
 * normally (`Unit`) — never throws for an ordinary ineligibility reason
 * (unknown phone, guest, unregistered, unverified-legacy) — so
 * [com.pios.identity.api.IdentityController] can return the exact same
 * generic response every time (D-03.7's own "generic response,
 * independently of whether Identity exists" requirement), regardless of
 * what actually happened inside this method.
 *
 * Recovery eligibility, per ADR-082's ratified state machine: registered
 * (phone != null) AND phone_verified_at != null. A guest (`phone == null`)
 * is excluded by [com.pios.identity.application.IdentityRepository.findByPhone]
 * itself never matching one — no special-case branch is needed or added.
 * An unverified-legacy identity is excluded explicitly (D-03.2: knowing a
 * phone number is never sufficient by itself).
 *
 * D-03.7 closure pass: rate-limited by **both** phone and client/IP key,
 * the same two limiter classes [RequestPhoneVerificationApplicationService]
 * also uses for the other OTP purpose — never a third system. Both checks
 * fail the exact same way (a silent `return`, no exception, no status
 * distinguishable from any other outcome): this endpoint is anonymous, so
 * a rate-limit failure must never become an enumeration oracle any more
 * than "unknown phone" or "unverified legacy" already are.
 */
@Service
class RequestRecoveryApplicationService(
    private val identityRepository: IdentityRepository,
    private val challengeIssuer: PhoneVerificationChallengeIssuer,
    private val phoneOtpRequestRateLimiter: PhoneOtpRequestRateLimiter,
    private val otpClientKeyRateLimiter: OtpClientKeyRateLimiter
) {
    fun handle(rawPhone: String, clientKey: String): RecoveryRequestTimingScope {
        // Rate-limited by phone regardless of whether it resolves to
        // anything -- an attacker probing many phone numbers is bounded the
        // same way a real recovering user would be. Checked before the
        // client-key budget so a single-phone-targeted burst never spends
        // the client's own general budget it needs for other phones.
        if (!phoneOtpRequestRateLimiter.tryAcquire(rawPhone)) {
            return RecoveryRequestTimingScope.EXCLUDED
        }
        if (!otpClientKeyRateLimiter.tryAcquire(clientKey)) {
            return RecoveryRequestTimingScope.EXCLUDED
        }
        val phone = phoneOrNull(rawPhone) ?: return RecoveryRequestTimingScope.EXCLUDED
        val identity = identityRepository.findByPhone(phone) ?: return RecoveryRequestTimingScope.PAD_TO_FLOOR
        if (identity.phoneVerifiedAt == null) {
            return RecoveryRequestTimingScope.PAD_TO_FLOOR
        }
        challengeIssuer.issue(identity.id, phone, PhoneVerificationPurpose.RECOVERY)
        return RecoveryRequestTimingScope.PAD_TO_FLOOR
    }

    private fun phoneOrNull(raw: String): Phone? =
        try {
            Phone(raw)
        } catch (ex: IllegalArgumentException) {
            null
        }
}

/** Internal-only result; every caller still receives the same generic HTTP response. */
enum class RecoveryRequestTimingScope { PAD_TO_FLOOR, EXCLUDED }
