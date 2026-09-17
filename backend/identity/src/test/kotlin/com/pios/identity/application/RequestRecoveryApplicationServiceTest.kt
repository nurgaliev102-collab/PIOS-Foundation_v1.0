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
 * the controller's own generic response true, not just asserted. D-03.7
 * closure pass: both the per-phone and per-client-key budgets are proven
 * here, independently, including that exhausting either one alone
 * silently blocks -- never a distinguishable outcome.
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
    private val defaultClientKey = "198.51.100.1"

    private fun service(phoneMaxPerWindow: Int = 1000, clientKeyMaxPerWindow: Int = 1000) = RequestRecoveryApplicationService(
        identityRepository, challengeIssuer,
        PhoneOtpRequestRateLimiter(phoneMaxPerWindow, 3_600_000),
        OtpClientKeyRateLimiter(clientKeyMaxPerWindow, 3_600_000)
    )

    @Test
    fun `an unknown phone never throws and sends nothing`() {
        service().handle("+79995550001", defaultClientKey)
        assertTrue(sentCodes.isEmpty())
    }

    @Test
    fun `a guest identity -- which has no phone -- can never match and sends nothing`() {
        // Guests are excluded structurally: findByPhone can never match a
        // null-phone identity, so there is no special-case branch to test
        // beyond confirming a guest row does not interfere with lookup.
        identityRepository.save(Identity(IdentityId("guest-1"), null, null, Instant.EPOCH))
        service().handle("+79995550002", defaultClientKey)
        assertTrue(sentCodes.isEmpty())
    }

    @Test
    fun `a registered but unverified-legacy phone is not eligible and sends nothing`() {
        val phone = Phone("+79995550003")
        identityRepository.save(Identity(IdentityId("legacy-1"), phone, null, Instant.EPOCH))

        service().handle(phone.value, defaultClientKey)

        assertFalse(sentCodes.containsKey(phone.value))
        assertNull(challengeRepository.findLiveForUpdate(IdentityId("legacy-1"), PhoneVerificationPurpose.RECOVERY))
    }

    @Test
    fun `a registered and phone-verified identity is eligible and receives a code`() {
        val phone = Phone("+79995550004")
        val identity = Identity(IdentityId("verified-1"), phone, null, Instant.EPOCH).withPhoneVerified(Instant.EPOCH)
        identityRepository.save(identity)

        service().handle(phone.value, defaultClientKey)

        assertTrue(sentCodes.containsKey(phone.value))
        assertTrue(challengeRepository.findLiveForUpdate(identity.id, PhoneVerificationPurpose.RECOVERY)!!.isLive)
    }

    @Test
    fun `a malformed phone never throws`() {
        service().handle("not-a-phone", defaultClientKey)
        assertTrue(sentCodes.isEmpty())
    }

    @Test
    fun `requests beyond the per-phone window are silently dropped, never thrown`() {
        val phone = Phone("+79995550005")
        val identity = Identity(IdentityId("verified-2"), phone, null, Instant.EPOCH).withPhoneVerified(Instant.EPOCH)
        identityRepository.save(identity)
        val limited = service(phoneMaxPerWindow = 1)

        limited.handle(phone.value, defaultClientKey)
        val firstChallenge = challengeRepository.findLiveForUpdate(identity.id, PhoneVerificationPurpose.RECOVERY)!!

        limited.handle(phone.value, defaultClientKey) // rate-limited: must not supersede the first challenge
        val stillFirst = challengeRepository.findLiveForUpdate(identity.id, PhoneVerificationPurpose.RECOVERY)!!
        assertEquals(firstChallenge.id, stillFirst.id)
    }

    // --- D-03.7 closure pass: client/IP-key rate limiting ---

    @Test
    fun `requests beyond the per-client-key window are silently dropped, even for an eligible phone`() {
        val phone = Phone("+79995550006")
        val identity = Identity(IdentityId("verified-3"), phone, null, Instant.EPOCH).withPhoneVerified(Instant.EPOCH)
        identityRepository.save(identity)
        val limited = service(clientKeyMaxPerWindow = 1)

        limited.handle(phone.value, defaultClientKey)
        assertTrue(sentCodes.containsKey(phone.value))
        sentCodes.clear()

        limited.handle(phone.value, defaultClientKey) // same client key, budget already spent
        assertFalse(sentCodes.containsKey(phone.value), "a second request from the same client key must send nothing")
    }

    @Test
    fun `the client-key budget is independent per key -- a different client can still request for the same phone`() {
        val phone = Phone("+79995550007")
        val identity = Identity(IdentityId("verified-4"), phone, null, Instant.EPOCH).withPhoneVerified(Instant.EPOCH)
        identityRepository.save(identity)
        val limited = service(clientKeyMaxPerWindow = 1)

        limited.handle(phone.value, "198.51.100.10")
        assertTrue(sentCodes.containsKey(phone.value))
        sentCodes.clear()

        // A different client key, well under its own budget -- the earlier
        // client's exhaustion must not leak into this one's own window.
        limited.handle(phone.value, "198.51.100.20")
        assertTrue(sentCodes.containsKey(phone.value))
    }

    @Test
    fun `exhausting the client-key budget alone -- with the phone budget untouched -- still sends nothing`() {
        val phone = Phone("+79995550008")
        val identity = Identity(IdentityId("verified-5"), phone, null, Instant.EPOCH).withPhoneVerified(Instant.EPOCH)
        identityRepository.save(identity)
        // Phone budget generous, client-key budget exhausted after one call.
        val limited = service(phoneMaxPerWindow = 1000, clientKeyMaxPerWindow = 1)

        limited.handle(phone.value, defaultClientKey)
        sentCodes.clear()

        limited.handle(phone.value, defaultClientKey)
        assertFalse(sentCodes.containsKey(phone.value), "the client-key budget alone must be enough to block sending")
    }

    @Test
    fun `exhausting the phone budget alone -- with the client-key budget untouched -- still sends nothing`() {
        val phone = Phone("+79995550009")
        val identity = Identity(IdentityId("verified-6"), phone, null, Instant.EPOCH).withPhoneVerified(Instant.EPOCH)
        identityRepository.save(identity)
        // Phone budget exhausted after one call; client-key budget generous.
        val limited = service(phoneMaxPerWindow = 1, clientKeyMaxPerWindow = 1000)

        limited.handle(phone.value, defaultClientKey)
        sentCodes.clear()

        // A fresh client key too -- proves it is specifically the phone
        // budget, not the client-key one, doing the blocking here.
        limited.handle(phone.value, "203.0.113.99")
        assertFalse(sentCodes.containsKey(phone.value), "the phone budget alone must be enough to block sending")
    }
}
