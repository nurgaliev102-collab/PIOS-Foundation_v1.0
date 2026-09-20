package com.pios.identity.application

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.Phone
import com.pios.identity.domain.PhoneVerificationPurpose
import com.pios.identity.persistence.InMemoryPhoneVerificationChallengeRepository
import org.slf4j.LoggerFactory
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

    @Test
    fun `provider failure is logged by classification only and leaves committed challenge bounded`() {
        var codeSeenByProvider = ""
        val failingPort = OutboundSmsPort { _, code ->
            codeSeenByProvider = code
            throw OutboundSmsDeliveryException(
                SmsSubmissionState.UNKNOWN, SmsSubmissionFailure.TRANSPORT_TIMEOUT
            )
        }
        val failingIssuer = PhoneVerificationChallengeIssuer(
            repository, PasswordHasher(defaultIterations = 1000), failingPort,
            ttlSeconds = 600, maxAttempts = 5, codeDigits = 6
        )
        val logger = LoggerFactory.getLogger(PhoneVerificationChallengeIssuer::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            failingIssuer.issue(identityId, phone, PhoneVerificationPurpose.RECOVERY)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }

        val stored = repository.findLiveForUpdate(identityId, PhoneVerificationPurpose.RECOVERY)!!
        assertTrue(stored.isLive)
        assertEquals(5, stored.maxAttempts)
        assertEquals(600L, stored.expiresAt.epochSecond - stored.createdAt.epochSecond)
        assertNotEquals(codeSeenByProvider, stored.codeHash)
        val logText = appender.list.joinToString("\n") { it.formattedMessage }
        assertTrue(logText.contains("UNKNOWN"))
        assertFalse(logText.contains(codeSeenByProvider))
        assertFalse(logText.contains(phone.value))
    }

    @Test
    fun `unexpected transport exception cannot put credentials or Authorization in logs`() {
        val fakeKey = "fixture-only-key"
        val fakeAuthorization = "Basic Zml4dHVyZQ=="
        var codeSeenByProvider = ""
        val failingPort = OutboundSmsPort { recipient, code ->
            codeSeenByProvider = code
            throw IllegalStateException("$fakeKey $fakeAuthorization ${recipient.value} $code")
        }
        val failingIssuer = PhoneVerificationChallengeIssuer(
            repository, PasswordHasher(defaultIterations = 1000), failingPort,
            ttlSeconds = 600, maxAttempts = 5, codeDigits = 6
        )
        val logger = LoggerFactory.getLogger(PhoneVerificationChallengeIssuer::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            failingIssuer.issue(identityId, phone, PhoneVerificationPurpose.RECOVERY)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }

        val logText = appender.list.joinToString("\n") { it.formattedMessage }
        assertTrue(logText.contains("UNKNOWN reason=UNEXPECTED"))
        assertFalse(logText.contains(fakeKey))
        assertFalse(logText.contains(fakeAuthorization))
        assertFalse(logText.contains(phone.value))
        assertFalse(logText.contains(codeSeenByProvider))
    }
}
