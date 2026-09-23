package com.pios.identity.application

import com.pios.identity.domain.Identity
import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.Phone
import com.pios.identity.domain.PhoneVerificationPurpose
import com.pios.identity.persistence.InMemoryIdentityRepository
import com.pios.identity.persistence.InMemoryPhoneVerificationChallengeRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RequestPhoneVerificationApplicationServiceTest {
    private val identityRepository = InMemoryIdentityRepository()
    private val challengeRepository = InMemoryPhoneVerificationChallengeRepository()
    private val challengeIssuer = PhoneVerificationChallengeIssuer(
        challengeRepository, PasswordHasher(defaultIterations = 1000), testOtpCipher(), testSmsOutbox(challengeRepository),
        ttlSeconds = 600, maxAttempts = 5, codeDigits = 6
    )

    private fun service(maxPerWindow: Int = 1000) = RequestPhoneVerificationApplicationService(
        identityRepository, challengeIssuer, PhoneOtpRequestRateLimiter(maxPerWindow, 3_600_000)
    )

    @Test
    fun `an unknown identity throws IdentityNotFoundException`() {
        assertFailsWith<IdentityNotFoundException> {
            service().handle(IdentityId("never-registered"))
        }
    }

    @Test
    fun `an already-verified identity throws PhoneAlreadyVerifiedException and issues nothing new`() {
        val id = IdentityId("already-verified")
        identityRepository.save(Identity(id, Phone("+79996660001"), null, Instant.EPOCH).withPhoneVerified(Instant.EPOCH))

        assertFailsWith<PhoneAlreadyVerifiedException> { service().handle(id) }
    }

    @Test
    fun `an unverified legacy identity with a phone on file becomes eligible to issue a LEGACY_ENROLLMENT challenge`() {
        val id = IdentityId("legacy-to-enroll")
        identityRepository.save(Identity(id, Phone("+79996660002"), null, Instant.EPOCH))

        service().handle(id)

        val live = challengeRepository.findLiveForUpdate(id, PhoneVerificationPurpose.LEGACY_ENROLLMENT)!!
        assertTrue(live.isLive)
    }

    @Test
    fun `issuing a LEGACY_ENROLLMENT challenge never creates a RECOVERY one`() {
        val id = IdentityId("legacy-to-enroll-2")
        identityRepository.save(Identity(id, Phone("+79996660003"), null, Instant.EPOCH))

        service().handle(id)

        assertTrue(challengeRepository.findLiveForUpdate(id, PhoneVerificationPurpose.RECOVERY) == null)
    }

    // --- D-03.7 closure pass: phone rate limiting ---

    @Test
    fun `exceeding the per-phone budget throws TooManyOtpRequestsException and sends no further OTP`() {
        val id = IdentityId("legacy-rate-limited")
        identityRepository.save(Identity(id, Phone("+79996660004"), null, Instant.EPOCH))
        val limited = service(maxPerWindow = 1)

        limited.handle(id) // consumes the one-request budget
        val firstChallenge = challengeRepository.findLiveForUpdate(id, PhoneVerificationPurpose.LEGACY_ENROLLMENT)!!

        assertFailsWith<TooManyOtpRequestsException> { limited.handle(id) }

        // No second challenge was issued -- the budget's own rejection
        // happened before the issuer was ever called.
        val stillFirst = challengeRepository.findLiveForUpdate(id, PhoneVerificationPurpose.LEGACY_ENROLLMENT)!!
        assertTrue(stillFirst.id == firstChallenge.id)
    }

    @Test
    fun `the phone budget is keyed by the identity's own on-file phone, not the identity id`() {
        val sharedPhoneLimiter = PhoneOtpRequestRateLimiter(maxPerWindow = 1, windowMillis = 3_600_000)
        val serviceWithSharedLimiter =
            RequestPhoneVerificationApplicationService(identityRepository, challengeIssuer, sharedPhoneLimiter)

        val firstId = IdentityId("legacy-shared-phone-1")
        val secondId = IdentityId("legacy-shared-phone-2")
        val phone = Phone("+79996660005")
        identityRepository.save(Identity(firstId, phone, null, Instant.EPOCH))
        // Two different identities can never really share one phone in
        // production (ux_identities_phone is unique), but this proves the
        // *keying*, not a realistic account state.
        identityRepository.save(Identity(secondId, phone, null, Instant.EPOCH))

        serviceWithSharedLimiter.handle(firstId)
        assertFailsWith<TooManyOtpRequestsException> { serviceWithSharedLimiter.handle(secondId) }
    }
}
