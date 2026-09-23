package com.pios.identity.application

import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.Phone
import com.pios.identity.domain.PhoneVerificationPurpose
import com.pios.identity.persistence.InMemoryPhoneVerificationChallengeRepository
import java.time.Clock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmsOutboxRelayTest {
    private val challenges = InMemoryPhoneVerificationChallengeRepository()
    private val outbox = testSmsOutbox(challenges)
    private val cipher = testOtpCipher()
    private val issuer = PhoneVerificationChallengeIssuer(
        challenges, PasswordHasher(defaultIterations = 1_000), cipher, outbox,
        ttlSeconds = 600, maxAttempts = 2, codeDigits = 6
    )

    @Test
    fun `provider acceptance persists its ID destroys ciphertext and does not retry`() {
        val identityId = IdentityId("relay-success")
        issuer.issue(identityId, Phone("+79995551101"), PhoneVerificationPurpose.RECOVERY)
        val challenge = assertNotNull(challenges.findLiveForUpdate(identityId, PhoneVerificationPurpose.RECOVERY))
        val submissions = AtomicInteger()
        val acceptingPort = OutboundSmsPort { _, _ -> submissions.incrementAndGet(); 9876L }
        relay(acceptingPort).relay()
        relay(acceptingPort).relay()

        val row = assertNotNull(outbox.findByChallengeId(challenge.id))
        assertEquals(SmsOutboxStatus.SENT, row.status)
        assertEquals(9876L, row.providerMessageId)
        assertEquals(0, row.retryCount)
        assertEquals(1, submissions.get())
        assertNull(challenges.findById(challenge.id)?.otpCiphertext)
        assertNull(challenges.findById(challenge.id)?.otpNonce)
    }

    @Test
    fun `explicit rejection is terminal and unknown result gets a durable jittered retry`() {
        val rejectedId = IdentityId("relay-rejected")
        issuer.issue(rejectedId, Phone("+79995551102"), PhoneVerificationPurpose.RECOVERY)
        relay(OutboundSmsPort { _, _ ->
            throw OutboundSmsDeliveryException(SmsSubmissionState.FAILED, SmsSubmissionFailure.PROVIDER_REJECTED)
        }).relay()
        val rejected = assertNotNull(challenges.findLiveForUpdate(rejectedId, PhoneVerificationPurpose.RECOVERY))
        assertEquals(SmsOutboxStatus.FAILED, outbox.findByChallengeId(rejected.id)?.status)

        val unknownId = IdentityId("relay-unknown")
        issuer.issue(unknownId, Phone("+79995551103"), PhoneVerificationPurpose.RECOVERY)
        relay(OutboundSmsPort { _, _ ->
            throw OutboundSmsDeliveryException(SmsSubmissionState.UNKNOWN, SmsSubmissionFailure.TRANSPORT_TIMEOUT)
        }).relay()
        val unknown = assertNotNull(challenges.findLiveForUpdate(unknownId, PhoneVerificationPurpose.RECOVERY))
        val retry = assertNotNull(outbox.findByChallengeId(unknown.id))
        assertEquals(SmsOutboxStatus.PENDING, retry.status)
        assertEquals(1, retry.retryCount)
        assertTrue(retry.nextAttemptAt >= retry.updatedAt)
        assertNotNull(challenges.findById(unknown.id)?.otpCiphertext)
    }

    private fun relay(port: OutboundSmsPort) = SmsOutboxRelay(
        outbox, port, cipher, Clock.systemUTC(), SmsRetryJitter { 0L },
        batchSize = 50, leaseSeconds = 60, initialBackoffMs = 30_000,
        maxBackoffMs = 120_000, maxRetryWindowMs = 300_000
    )
}
