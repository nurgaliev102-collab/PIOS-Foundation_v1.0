package com.pios.identity.application

import com.pios.identity.domain.Identity
import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.Phone
import com.pios.identity.domain.PhoneVerificationPurpose
import com.pios.identity.persistence.InMemoryIdentityRepository
import com.pios.identity.persistence.InMemoryPhoneVerificationChallengeRepository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ADR-082 (D-03.7) — `RequestRecoveryApplicationService` never throws for
 * an ordinary ineligibility reason, and only actually issues/sends a
 * challenge when the identity is genuinely eligible -- this is what makes
 * the controller's own generic response true, not just asserted.
 */
class RequestRecoveryApplicationServiceTest {
    private val identityRepository = InMemoryIdentityRepository()
    private val challengeRepository = InMemoryPhoneVerificationChallengeRepository()
    private val sentCodes = ConcurrentHashMap<String, String>()
    private val challengeIssuer = PhoneVerificationChallengeIssuer(
        challengeRepository, PasswordHasher(defaultIterations = 1000),
        OutboundSmsPort { phone, code -> sentCodes[phone.value] = code },
        ttlSeconds = 600, maxAttempts = 5, codeDigits = 6
    )

    private fun service(maxPerWindow: Int = 1000) = RequestRecoveryApplicationService(
        identityRepository, challengeIssuer, PhoneOtpRequestRateLimiter(maxPerWindow, 3_600_000)
    )

    @Test
    fun `an unknown phone never throws and sends nothing`() {
        service().handle("+79995550001")
        assertTrue(sentCodes.isEmpty())
    }

    @Test
    fun `a guest identity -- which has no phone -- can never match and sends nothing`() {
        // Guests are excluded structurally: findByPhone can never match a
        // null-phone identity, so there is no special-case branch to test
        // beyond confirming a guest row does not interfere with lookup.
        identityRepository.save(Identity(IdentityId("guest-1"), null, null, Instant.EPOCH))
        service().handle("+79995550002")
        assertTrue(sentCodes.isEmpty())
    }

    @Test
    fun `a registered but unverified-legacy phone is not eligible and sends nothing`() {
        val phone = Phone("+79995550003")
        identityRepository.save(Identity(IdentityId("legacy-1"), phone, null, Instant.EPOCH))

        service().handle(phone.value)

        assertFalse(sentCodes.containsKey(phone.value))
        assertNull(challengeRepository.findLiveForUpdate(IdentityId("legacy-1"), PhoneVerificationPurpose.RECOVERY))
    }

    @Test
    fun `a registered and phone-verified identity is eligible and receives a code`() {
        val phone = Phone("+79995550004")
        val identity = Identity(IdentityId("verified-1"), phone, null, Instant.EPOCH).withPhoneVerified(Instant.EPOCH)
        identityRepository.save(identity)

        service().handle(phone.value)

        assertTrue(sentCodes.containsKey(phone.value))
        assertTrue(challengeRepository.findLiveForUpdate(identity.id, PhoneVerificationPurpose.RECOVERY)!!.isLive)
    }

    @Test
    fun `a malformed phone never throws`() {
        service().handle("not-a-phone")
        assertTrue(sentCodes.isEmpty())
    }

    @Test
    fun `requests beyond the per-phone window are silently dropped, never thrown`() {
        val phone = Phone("+79995550005")
        val identity = Identity(IdentityId("verified-2"), phone, null, Instant.EPOCH).withPhoneVerified(Instant.EPOCH)
        identityRepository.save(identity)
        val limited = service(maxPerWindow = 1)

        limited.handle(phone.value)
        val firstChallenge = challengeRepository.findLiveForUpdate(identity.id, PhoneVerificationPurpose.RECOVERY)!!

        limited.handle(phone.value) // rate-limited: must not supersede the first challenge
        val stillFirst = challengeRepository.findLiveForUpdate(identity.id, PhoneVerificationPurpose.RECOVERY)!!
        assertEquals(firstChallenge.id, stillFirst.id)
    }
}
