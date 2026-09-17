package com.pios.identity.application

import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.Phone
import com.pios.identity.domain.PhoneVerificationPurpose
import com.pios.identity.persistence.InMemoryPhoneVerificationChallengeRepository
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private class RecordingOutboundSmsPort : OutboundSmsPort {
    val sentCodes = ConcurrentHashMap<String, String>()
    override fun sendVerificationCode(phone: Phone, code: String) {
        sentCodes[phone.value] = code
    }
}

/** ADR-082 (D-03.7) — issuance-side security properties, in isolation. */
class PhoneVerificationChallengeIssuerTest {
    private val repository = InMemoryPhoneVerificationChallengeRepository()
    private val smsPort = RecordingOutboundSmsPort()
    private val issuer = PhoneVerificationChallengeIssuer(
        repository, PasswordHasher(defaultIterations = 1000), smsPort,
        ttlSeconds = 600, maxAttempts = 5, codeDigits = 6
    )
    private val identityId = IdentityId("issuer-test-identity")
    private val phone = Phone("+79990000001")

    @Test
    fun `issuing sends a plaintext code but persists only a hash that does not equal it`() {
        issuer.issue(identityId, phone, PhoneVerificationPurpose.RECOVERY)

        val sentCode = smsPort.sentCodes.getValue(phone.value)
        assertEquals(6, sentCode.length)
        assertTrue(sentCode.all { it.isDigit() })

        val stored = repository.findLiveForUpdate(identityId, PhoneVerificationPurpose.RECOVERY)!!
        assertNotEquals(sentCode, stored.codeHash)
        assertFalse(stored.codeHash.contains(sentCode))
    }

    @Test
    fun `issuing twice supersedes the first challenge, leaving exactly one live`() {
        issuer.issue(identityId, phone, PhoneVerificationPurpose.RECOVERY)
        val first = repository.findLiveForUpdate(identityId, PhoneVerificationPurpose.RECOVERY)!!

        issuer.issue(identityId, phone, PhoneVerificationPurpose.RECOVERY)
        val second = repository.findLiveForUpdate(identityId, PhoneVerificationPurpose.RECOVERY)!!

        assertNotEquals(first.id, second.id)
        assertTrue(second.isLive)
    }

    @Test
    fun `different purposes for the same identity do not supersede each other`() {
        issuer.issue(identityId, phone, PhoneVerificationPurpose.RECOVERY)
        issuer.issue(identityId, phone, PhoneVerificationPurpose.LEGACY_ENROLLMENT)

        assertTrue(repository.findLiveForUpdate(identityId, PhoneVerificationPurpose.RECOVERY)!!.isLive)
        assertTrue(repository.findLiveForUpdate(identityId, PhoneVerificationPurpose.LEGACY_ENROLLMENT)!!.isLive)
    }

    @Test
    fun `expiry is set ttlSeconds after issuance`() {
        issuer.issue(identityId, phone, PhoneVerificationPurpose.RECOVERY)
        val stored = repository.findLiveForUpdate(identityId, PhoneVerificationPurpose.RECOVERY)!!
        val actualTtl = stored.expiresAt.epochSecond - stored.createdAt.epochSecond
        assertEquals(600L, actualTtl)
    }
}
