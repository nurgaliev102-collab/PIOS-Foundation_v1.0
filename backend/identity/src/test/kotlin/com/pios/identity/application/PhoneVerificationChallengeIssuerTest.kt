package com.pios.identity.application

import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.Phone
import com.pios.identity.domain.PhoneVerificationPurpose
import com.pios.identity.persistence.InMemoryPhoneVerificationChallengeRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** ADR-082 (D-03.7) — issuance-side security properties, in isolation. */
class PhoneVerificationChallengeIssuerTest {
    private val repository = InMemoryPhoneVerificationChallengeRepository()
    private val otpCipher = testOtpCipher()
    private val outbox = testSmsOutbox(repository)
    private val issuer = PhoneVerificationChallengeIssuer(
        repository, PasswordHasher(defaultIterations = 1000), otpCipher, outbox,
        ttlSeconds = 600, maxAttempts = 5, codeDigits = 6
    )
    private val identityId = IdentityId("issuer-test-identity")
    private val phone = Phone("+79990000001")

    @Test
    fun `issuing encrypts the OTP and creates a pending outbox record without sending`() {
        issuer.issue(identityId, phone, PhoneVerificationPurpose.RECOVERY)

        val stored = repository.findLiveForUpdate(identityId, PhoneVerificationPurpose.RECOVERY)!!
        val generatedCode = otpCipher.decrypt(assertNotNull(stored.otpCiphertext), assertNotNull(stored.otpNonce))
        assertEquals(6, generatedCode.length)
        assertTrue(generatedCode.all { it.isDigit() })
        assertNotEquals(generatedCode, stored.codeHash)
        assertFalse(stored.codeHash.contains(generatedCode))
        val record = assertNotNull(outbox.findByChallengeId(stored.id))
        assertEquals(SmsOutboxStatus.PENDING, record.status)
        assertFalse(record.toString().contains(generatedCode))
        assertFalse(record.toString().contains(phone.value))
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

    @Test
    fun `issuance atomically leaves a bounded live challenge and pending outbox record`() {
        issuer.issue(identityId, phone, PhoneVerificationPurpose.RECOVERY)
        val stored = repository.findLiveForUpdate(identityId, PhoneVerificationPurpose.RECOVERY)!!
        assertTrue(stored.isLive)
        assertEquals(5, stored.maxAttempts)
        assertEquals(600L, stored.expiresAt.epochSecond - stored.createdAt.epochSecond)
        assertEquals(SmsOutboxStatus.PENDING, outbox.findByChallengeId(stored.id)?.status)
    }

    @Test
    fun `superseding a challenge cancels its unclaimed outbox record`() {
        issuer.issue(identityId, phone, PhoneVerificationPurpose.RECOVERY)
        val first = repository.findLiveForUpdate(identityId, PhoneVerificationPurpose.RECOVERY)!!
        issuer.issue(identityId, phone, PhoneVerificationPurpose.RECOVERY)
        val second = repository.findLiveForUpdate(identityId, PhoneVerificationPurpose.RECOVERY)!!

        val supersededRecord = assertNotNull(outbox.findByChallengeId(first.id))
        assertEquals(SmsOutboxStatus.FAILED, supersededRecord.status)
        assertEquals("SUPERSEDED", supersededRecord.failureReason)
        assertEquals(SmsOutboxStatus.PENDING, outbox.findByChallengeId(second.id)?.status)
    }
}
