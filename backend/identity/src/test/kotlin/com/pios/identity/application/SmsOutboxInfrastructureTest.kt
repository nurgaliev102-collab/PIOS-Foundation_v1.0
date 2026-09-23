package com.pios.identity.application

import com.pios.identity.IdentityApplication
import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.Phone
import com.pios.identity.domain.PhoneVerificationPurpose
import com.pios.identity.persistence.InMemoryPhoneVerificationChallengeRepository
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmsOutboxInfrastructureTest {
    @Test
    fun `retention deletes sent failed unknown and expired pending rows at their policy boundaries`() {
        val challenges = InMemoryPhoneVerificationChallengeRepository()
        val outbox = testSmsOutbox(challenges)
        val old = Instant.now().minus(Duration.ofDays(91))
        val record = { status: SmsOutboxStatus ->
            SmsOutboxRecord(null, "retention-$status", status, null, null, old, null,
                if (status == SmsOutboxStatus.SENT) old else null, null, 0, old, old)
        }
        outbox.insert(record(SmsOutboxStatus.SENT))
        outbox.insert(record(SmsOutboxStatus.FAILED))
        outbox.insert(record(SmsOutboxStatus.UNKNOWN))

        val identityId = IdentityId("retention-pending")
        val issuer = PhoneVerificationChallengeIssuer(
            challenges, PasswordHasher(defaultIterations = 1_000), testOtpCipher(), outbox,
            ttlSeconds = 600, maxAttempts = 2, codeDigits = 6
        )
        issuer.issue(identityId, Phone("+79995551104"), PhoneVerificationPurpose.RECOVERY)
        val challenge = challenges.findLiveForUpdate(identityId, PhoneVerificationPurpose.RECOVERY)!!
        challenges.update(challenge.copy(expiresAt = old))

        outbox.deleteByRetentionPolicy(Instant.now().minus(Duration.ofDays(30)), Instant.now().minus(Duration.ofDays(90)))
        assertTrue(outbox.allRecords().isEmpty())
    }

    @Test
    fun `relay and retention schedulers are Spring scheduled with locked cadence and UTC cron`() {
        assertTrue(IdentityApplication::class.java.isAnnotationPresent(EnableScheduling::class.java))
        val relay = SmsOutboxRelayScheduler::class.java.getDeclaredMethod("triggerRelay")
            .getAnnotation(Scheduled::class.java)
        assertEquals("\${pios.identity.sms.relay.fixed-delay-ms:10000}", relay.fixedDelayString)
        val retention = SmsOutboxRetentionScheduler::class.java.getDeclaredMethod("runRetentionCleanup")
            .getAnnotation(Scheduled::class.java)
        assertEquals("\${pios.identity.sms.retention.cron:0 15 0 * * *}", retention.cron)
        assertEquals("UTC", retention.zone)
    }
}
